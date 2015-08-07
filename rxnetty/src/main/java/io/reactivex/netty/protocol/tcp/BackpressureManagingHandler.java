/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.protocol.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.RecyclableArrayList;
import io.reactivex.netty.channel.events.ConnectionEventListener;
import io.reactivex.netty.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;
import rx.exceptions.MissingBackpressureException;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public abstract class BackpressureManagingHandler extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(BackpressureManagingHandler.class);
    protected final AttributeKey<ConnectionEventListener> eventListenerAttributeKey;
    protected final AttributeKey<EventPublisher> eventPublisherAttributeKey;
    protected ConnectionEventListener eventListener;
    protected EventPublisher eventPublisher;

    /*Visible for testing*/  enum State {
        ReadRequested,
        Reading,
        Buffering,
        DrainingBuffer,
        Stopped,
    }

    private RecyclableArrayList buffer;
    private int currentBufferIndex;
    private State currentState = State.Buffering; /*Buffer unless explicitly asked to read*/
    private boolean continueDraining;
    private final BytesWriteInterceptor bytesWriteInterceptor;

    protected BackpressureManagingHandler(String thisHandlerName, ConnectionEventListener eventListener,
                                          EventPublisher eventPublisher) {
        if (null == eventListener) {
            throw new IllegalArgumentException("Event listener can not be null.");
        }
        if (null == eventPublisher) {
            throw new IllegalArgumentException("Event publisher can not be null.");
        }
        this.eventListener = eventListener;
        this.eventPublisher = eventPublisher;
        eventListenerAttributeKey = null;
        eventPublisherAttributeKey = null;
        bytesWriteInterceptor = new BytesWriteInterceptor(thisHandlerName);
    }

    protected BackpressureManagingHandler(String thisHandlerName,
                                          AttributeKey<ConnectionEventListener> eventListenerAttributeKey,
                                          AttributeKey<EventPublisher> eventPublisherAttributeKey) {
        if (null == eventListenerAttributeKey) {
            throw new IllegalArgumentException("Event listener key can not be null.");
        }
        if (null == eventPublisherAttributeKey) {
            throw new IllegalArgumentException("Event publisher key can not be null.");
        }
        this.eventListener = null;
        this.eventPublisher = null;
        this.eventListenerAttributeKey = eventListenerAttributeKey;
        this.eventPublisherAttributeKey = eventPublisherAttributeKey;
        bytesWriteInterceptor = new BytesWriteInterceptor(thisHandlerName);
    }

    @SuppressWarnings("fallthrough")
    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (State.Stopped != currentState && !shouldReadMore(ctx)) {
            currentState = State.Buffering;
        }

        switch (currentState) {
        case ReadRequested:
            currentState = State.Reading;
        case Reading:
            newMessage(ctx, msg);
            break;
        case Buffering:
        case DrainingBuffer:
            if (null == buffer) {
                buffer = RecyclableArrayList.newInstance();
            }
            buffer.add(msg);
            break;
        case Stopped:
            logger.warn("Message read after handler removed, discarding the same. Message class: "
                        + msg.getClass().getName());
            ReferenceCountUtil.release(msg);
            break;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (null == eventListener && null == eventPublisher) {
            eventListener = ctx.channel().attr(eventListenerAttributeKey).get();
            eventPublisher = ctx.channel().attr(eventPublisherAttributeKey).get();
        }

        if (null == eventPublisher) {
            logger.error("No Event publisher bound to the channel, closing channel.");
            ctx.channel().close();
            return;
        }

        if (eventPublisher.publishingEnabled() && null == eventListener) {
            logger.error("No Event listener bound to the channel and publising is enabled, closing channel.");
            ctx.channel().close();
            return;
        }

        ctx.pipeline().addFirst(bytesWriteInterceptor);
        currentState = State.Buffering;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        /*On shut down, all the handlers are removed from the pipeline, so we don't need to explicitly remove the
        additional handlers added in handlerAdded()*/
        currentState = State.Stopped;
        if (null != buffer) {
            if (!buffer.isEmpty()) {
                for (Object item : buffer) {
                    ReferenceCountUtil.release(item);
                }
            }
            buffer.recycle();
            buffer = null;
        }
    }

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {

        switch (currentState) {
        case ReadRequested:
            /*Nothing read from the last request, forward to read() and let it take the decision on what to do.*/
            break;
        case Reading:
            /*
             * After read completion, move to Buffering, unless an explicit read is issued, which moves to an
             * appropriate state.
             */
            currentState = State.Buffering;
            break;
        case Buffering:
            /*Keep buffering, unless the buffer drains and more items are requested*/
            break;
        case DrainingBuffer:
            /*Keep draining, unless the buffer drains and more items are requested*/
            break;
        case Stopped:
            break;
        }

        ctx.fireChannelReadComplete();

        if (!ctx.channel().config().isAutoRead() && shouldReadMore(ctx)) {
            read(ctx);
        }
    }

    @Override
    public final void read(ChannelHandlerContext ctx) throws Exception {
        switch (currentState) {
        case ReadRequested:
            /*Nothing read since last request, but requested more, so push the demand upstream.*/
            ctx.read();
            break;
        case Reading:
            /*
             * We are already reading data and the read has not completed as that would move the state to buffering.
             * So, ignore this read, or otherwise, read is requested on the channel, unnecessarily.
             */
            break;
        case Buffering:
            /*
             * We were buffering and now a read was requested, so start draining the buffer.
             */
            currentState = State.DrainingBuffer;
            continueDraining = true;
            /*
             * Looping here to drain, instead of having it done via readComplete -> read -> readComplete loop to reduce
             * call stack depth. Otherwise, the stackdepth is proportional to number of items in the buffer and hence
             * for large buffers will overflow stack.
             */
            while (continueDraining && null != buffer && currentBufferIndex < buffer.size()) {
                Object nextItem = buffer.get(currentBufferIndex++);
                newMessage(ctx, nextItem); /*Send the next message.*/
                /*
                 * If there is more read demand then that should come as part of read complete or later as another
                 * read (this method) invocation. */
                continueDraining = false;
                channelReadComplete(ctx);
            }

            if (continueDraining) {
                if (null != buffer) {
                    /*Outstanding read demand and buffer is empty, so recycle the buffer and pass the read upstream.*/
                    recycleBuffer();
                }
                /*
                 * Since, continueDraining is true and we have broken out of the drain loop, it means that there are no
                 * items in the buffer and there is more read demand. Switch to read requested and send the read demand
                 * downstream.
                 */
                currentState = State.ReadRequested;
                ctx.read();
            } else {
                /*
                 * There is no more demand, so set the state to buffering and so another read invocation can start
                 * draining.
                 */
                currentState = State.Buffering;
                /*If buffer is empty, then recycle.*/
                if (null != buffer && currentBufferIndex >= buffer.size()) {
                    recycleBuffer();
                }
            }
            break;
        case DrainingBuffer:
            /*Already draining buffer, so break the call stack and let the caller keep draining.*/
            continueDraining = true;
            break;
        case Stopped:
            /*Invalid, pass it downstream.*/
            ctx.read();
            break;
        }
    }

    /**
     * Intercepts a write on the channel. The following message types are handled:
     *
     * <ul>
     <li>String: If the pipeline is not configured to write a String, this converts the string to a {@link io.netty.buffer.ByteBuf} and
     then writes it on the channel.</li>
     <li>byte[]: If the pipeline is not configured to write a byte[], this converts the byte[] to a {@link io.netty.buffer.ByteBuf} and
     then writes it on the channel.</li>
     <li>Observable: Subscribes to the {@link Observable} and writes all items, requesting the next item if an only if
     the channel is writable as indicated by {@link Channel#isWritable()}</li>
     </ul>
     *
     * @param ctx Channel handler context.
     * @param msg Message to write.
     * @param promise Promise for the completion of write.
     *
     * @throws Exception If there is an error handling this write.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Observable) {
            @SuppressWarnings("rawtypes")
            Observable observable = (Observable) msg; /*One can write heterogneous objects on a channel.*/
            final WriteStreamSubscriber subscriber = new WriteStreamSubscriber(ctx, promise, eventListener,
                                                                               eventPublisher);
            bytesWriteInterceptor.addSubscriber(subscriber);
            subscriber.subscribeTo(observable);
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof RequestReadIfRequiredEvent) {
            RequestReadIfRequiredEvent requestReadIfRequiredEvent = (RequestReadIfRequiredEvent) evt;
            if (requestReadIfRequiredEvent.shouldReadMore(ctx)) {
                read(ctx);
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    protected abstract void newMessage(ChannelHandlerContext ctx, Object msg);

    protected abstract boolean shouldReadMore(ChannelHandlerContext ctx);

    /*Visible for testing*/ RecyclableArrayList getBuffer() {
        return buffer;
    }

    /*Visible for testing*/ int getCurrentBufferIndex() {
        return currentBufferIndex;
    }

    /*Visible for testing*/ State getCurrentState() {
        return currentState;
    }

    private void recycleBuffer() {
        buffer.recycle();
        currentBufferIndex = 0;
        buffer = null;
    }

    protected static abstract class RequestReadIfRequiredEvent {

        protected abstract boolean shouldReadMore(ChannelHandlerContext ctx);
    }

    /**
     * This handler inspects write to see if a write made it to {@link BytesWriteInterceptor} inline with a write call.
     * The reasons why a write would not make it to the channel, would be:
     * <ul>
     <li>If there is a handler in the pipeline that runs in a different group.</li>
     <li>If there is a handler that collects many items to produce a single item.</li>
     </ul>
     *
     * When a write did not reach the {@link BytesWriteInterceptor}, no request for more items will be generated and
     * we could get into a deadlock where a handler is waiting for more items (collect case) but no more items arrive as
     * no more request is generated. In order to avoid this deadlock, this handler will detect the situation and
     * trigger more request in this case.
     *
     * Why a separate handler?
     *
     * This needs to be different than {@link BytesWriteInterceptor} as we need it immediately after
     * {@link BackpressureManagingHandler} so that no other handler eats a write and {@link BytesWriteInterceptor} is
     * always the first handler in the pipeline to be right before the channel and hence maintain proper demand.
     */
    static final class WriteInspector extends ChannelDuplexHandler {

        private final BytesWriteInterceptor bytesWriteInterceptor;

        WriteInspector(BytesWriteInterceptor bytesWriteInterceptor) {
            this.bytesWriteInterceptor = bytesWriteInterceptor;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            /*Both these handlers always run in the same executor, so it's safe to access this variable.*/
            bytesWriteInterceptor.messageReceived = false; /*reset flag for this write*/
            ctx.write(msg, promise);
            if (!bytesWriteInterceptor.messageReceived) {
                bytesWriteInterceptor.requestMoreIfWritable(ctx.channel());
            }
        }
    }

    /**
     * Regulates write->request more->write process on the channel.
     *
     * Why is this a separate handler?
     * The sole purpose of this handler is to request more items from each of the Observable streams producing items to
     * write. It is important to request more items only when the current item is written on the channel i.e. added to
     * the ChannelOutboundBuffer. If we request more from outside the pipeline (from WriteStreamSubscriber.onNext())
     * then it may so happen that the onNext is not from within this eventloop and hence instead of being written to
     * the channel, is added to the task queue of the EventLoop. Requesting more items in such a case, would mean we
     * keep adding the writes to the eventloop queue and not on the channel buffer. This would mean that the channel
     * writability would not truly indicate the buffer.
     */
    /*Visible for testing*/ static final class BytesWriteInterceptor extends ChannelDuplexHandler {

        /*Visible for testing*/ static final String WRITE_INSPECTOR_HANDLER_NAME = "write-inspector";

        /*
         * Since, unsubscribes can happen on a different thread, this has to be thread-safe.
         */
        private final ConcurrentLinkedQueue<WriteStreamSubscriber> subscribers = new ConcurrentLinkedQueue<>();
        private final String parentHandlerName;

        /* This should always be access from the eventloop and can be used to manage state before and after a write to see
         * if a write started from {@link WriteInspector} made it to this handler.*/
        private boolean messageReceived;

        BytesWriteInterceptor(String parentHandlerName) {
            this.parentHandlerName = parentHandlerName;
        }

        @Override
        public void write(ChannelHandlerContext ctx, final Object msg, ChannelPromise promise) throws Exception {
            ctx.write(msg, promise);
            messageReceived = true;
            requestMoreIfWritable(ctx.channel());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            WriteInspector writeInspector = new WriteInspector(this);
            ChannelHandler parent = ctx.pipeline().get(parentHandlerName);
            if (null != parent) {
                ctx.pipeline().addBefore(parentHandlerName, WRITE_INSPECTOR_HANDLER_NAME, writeInspector);
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            if (ctx.channel().isWritable()) {
                requestMoreIfWritable(ctx.channel());
            }
            super.channelWritabilityChanged(ctx);
        }

        public void addSubscriber(final WriteStreamSubscriber streamSubscriber) {
            streamSubscriber.add(Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    /*Remove the subscriber on unsubscribe.*/
                    subscribers.remove(streamSubscriber);
                }
            }));

            subscribers.add(streamSubscriber);
        }

        /*Visible for testing*/List<WriteStreamSubscriber> getSubscribers() {
            return Collections.unmodifiableList(new ArrayList<>(subscribers));
        }

        private void requestMoreIfWritable(Channel channel) {
            /**
             * Predicting which subscriber is going to give the next item isn't possible, so all subscribers are
             * requested an item. This means that we buffer a max of one item per subscriber.
             */
            for (WriteStreamSubscriber subscriber: subscribers) {
                if (!subscriber.isUnsubscribed() && channel.isWritable()) {
                    subscriber.requestMore(1);
                }
            }
        }
    }

    /**
     * Backpressure enabled subscriber to an Observable written on this channel. This connects the promise for writing
     * the Observable to all the promises created per write (per onNext).
     */
    /*Visible for testing*/static class WriteStreamSubscriber extends Subscriber<Object> {

        private final ChannelHandlerContext ctx;
        private final ChannelPromise overarchingWritePromise;
        private final ConnectionEventListener eventListener;
        private final EventPublisher eventPublisher;
        private final Object guard = new Object();
        private boolean isDone; /*Guarded by guard*/
        private boolean isPromiseCompletedOnWriteComplete; /*Guarded by guard. Only transition should be false->true*/

        /*Updater for pendingRequested*/
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<WriteStreamSubscriber> PENDING_RQUESTED_UPDATER =
                AtomicLongFieldUpdater.newUpdater(WriteStreamSubscriber.class, "pendingRequested");

        private volatile long pendingRequested; // Updated by REQUEST_UPDATER, required to be volatile.

        private int listeningTo;

        /*Visible for testing*/ WriteStreamSubscriber(ChannelHandlerContext ctx, ChannelPromise promise,
                                                      final ConnectionEventListener eventListener,
                                                      final EventPublisher eventPublisher) {
            this.ctx = ctx;
            overarchingWritePromise = promise;
            this.eventListener = eventListener;
            this.eventPublisher = eventPublisher;
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isCancelled()) {
                        unsubscribe(); /*Unsubscribe from source if the promise is cancelled.*/
                    }
                }
            });
            add(Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    if (null != eventPublisher && eventPublisher.publishingEnabled()) {
                        eventListener.onWriteCompletion(pendingRequested);
                    }
                }
            }));
        }

        @Override
        public void onStart() {
            request(1); /*Only request one item at a time. Every write, requests one more, if channel is writable.*/
        }

        @Override
        public void onCompleted() {
            onTermination(null);
        }

        @Override
        public void onError(Throwable e) {
            onTermination(e);
        }

        @Override
        public void onNext(Object nextItem) {

            if (null != eventPublisher && eventPublisher.publishingEnabled()) {
                eventListener.onItemReceivedToWrite();
                PENDING_RQUESTED_UPDATER.decrementAndGet(this);
            }

            final ChannelFuture channelFuture = ctx.write(nextItem);
            synchronized (guard) {
                listeningTo++;
            }

            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {

                    if (overarchingWritePromise.isDone()) {
                        /*
                         * Overarching promise will be done if and only if there was an error or all futures have
                         * completed. In both cases, this callback is useless, hence return from here.
                         * IOW, if we are here, it can be two cases:
                         *
                         * - There has already been a write that has failed. So, the promise is done with failure.
                         * - There was a write that arrived after termination of the Observable.
                         *
                         * Two above isn't possible as per Rx contract.
                         * One above is possible but is not of any consequence w.r.t this listener as this listener does
                         * not give callbacks to specific writes
                         */
                        return;
                    }

                    boolean _isPromiseCompletedOnWriteComplete;

                    /**
                     * The intent here is to NOT give listener callbacks via promise completion within the sync block.
                     * So, a co-ordination b/w the thread sending Observable terminal event and thread sending write
                     * completion event is required.
                     * The only work to be done in the Observable terminal event thread is to whether the
                     * overarchingWritePromise is to be completed or not.
                     * The errors are not buffered, so the overarchingWritePromise is completed in this callback w/o
                     * knowing whether any more writes will arive or not.
                     * This co-oridantion is done via the flag isPromiseCompletedOnWriteComplete
                     */
                    synchronized (guard) {
                        listeningTo--;
                        if (0 == listeningTo && isDone) {
                            /**
                             * If the listening count is 0 and no more items will arive, this thread wins the race of
                             * completing the overarchingWritePromise
                             */
                            isPromiseCompletedOnWriteComplete = true;
                        }
                        _isPromiseCompletedOnWriteComplete = isPromiseCompletedOnWriteComplete;
                    }

                    /**
                     * Exceptions are not buffered but completion is only sent when there are no more items to be
                     * received for write.
                     */
                    if (!future.isSuccess()) {
                        overarchingWritePromise.tryFailure(future.cause());
                        /*
                         * Unsubscribe this subscriber when write fails as we are completing the promise which is
                         * attached to the listener of the write results.
                         */
                        unsubscribe();
                    } else if (_isPromiseCompletedOnWriteComplete) { /*Once set to true, never goes back to false.*/
                        /*Complete only when no more items will arrive and all writes are completed*/
                        overarchingWritePromise.trySuccess();
                    }
                }
            });
        }

        private void onTermination(Throwable throwableIfAny) {
            int _listeningTo;
            boolean _shouldCompletePromise;

            /**
             * The intent here is to NOT give listener callbacks via promise completion within the sync block.
             * So, a co-ordination b/w the thread sending Observable terminal event and thread sending write
             * completion event is required.
             * The only work to be done in the Observable terminal event thread is to whether the
             * overarchingWritePromise is to be completed or not.
             * The errors are not buffered, so the overarchingWritePromise is completed in this callback w/o
             * knowing whether any more writes will arive or not.
             * This co-oridantion is done via the flag isPromiseCompletedOnWriteComplete
             */
            synchronized (guard) {
                isDone = true;
                _listeningTo = listeningTo;
                /**
                 * Flag to indicate whether the write complete thread won the race and will complete the
                 * overarchingWritePromise
                 */
                _shouldCompletePromise = 0 == _listeningTo && !isPromiseCompletedOnWriteComplete;
            }

            if (null != throwableIfAny) {
                overarchingWritePromise.tryFailure(throwableIfAny);
            } else {
                if (_shouldCompletePromise) {
                    overarchingWritePromise.trySuccess();
                }
            }
        }

        private void requestMore(long more) {
            if (null != eventPublisher && eventPublisher.publishingEnabled()) {
                eventListener.onRequestMoreItemsToWrite(more);
                /*This is only used for event publishing*/
                PENDING_RQUESTED_UPDATER.addAndGet(this, more);
            }
            request(more);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void subscribeTo(Observable observable) {
            observable.subscribe(this); /*Need safe subscription as this is the subscriber and not a sub passed in*/
        }
    }

    /*Visible for testing*/ static final class ReadProducer<T> extends RequestReadIfRequiredEvent implements Producer {

        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ReadProducer> REQUEST_UPDATER =
                AtomicLongFieldUpdater.newUpdater(ReadProducer.class, "requested");/*Updater for requested*/

        private volatile long requested; // Updated by REQUEST_UPDATER, required to be volatile.

        private final Subscriber<? super T> subscriber;
        private final Channel channel;
        private final ConnectionEventListener eventListener;
        private final EventPublisher eventPublisher;

        /*Visible for testing*/ ReadProducer(Subscriber<? super T> subscriber, Channel channel,
                                             final ConnectionEventListener eventListener, final EventPublisher eventPublisher) {
            this.subscriber = subscriber;
            this.channel = channel;
            this.eventListener = eventListener;
            this.eventPublisher = eventPublisher;
            this.subscriber.add(Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    if (null != eventPublisher && eventPublisher.publishingEnabled()) {
                        eventListener.onReadCompletion(requested);
                    }
                }
            }));
        }

        @Override
        public void request(long n) {
            if (null != eventPublisher && eventPublisher.publishingEnabled()) {
                eventListener.onRequestMoreItemsToRead(n);
            }
            if (Long.MAX_VALUE != requested) {
                if (Long.MAX_VALUE == n) {
                    // Now turning off backpressure
                    REQUEST_UPDATER.set(this, Long.MAX_VALUE);
                } else {
                    // add n to field but check for overflow
                    while (true) {
                        final long current = requested;
                        long next = current + n;
                        // check for overflow
                        if (next < 0) {
                            next = Long.MAX_VALUE;
                        }
                        if (REQUEST_UPDATER.compareAndSet(this, current, next)) {
                            break;
                        }
                    }
                }
            }

            if (!channel.config().isAutoRead()) {
                channel.pipeline().fireUserEventTriggered(this);
            }
        }

        public void sendOnError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        public void sendOnComplete() {
            subscriber.onCompleted();
        }

        public void sendOnNext(T nextItem) {
            if (requested > 0) {
                if (REQUEST_UPDATER.get(this) != Long.MAX_VALUE) {
                    REQUEST_UPDATER.decrementAndGet(this);
                    if (null != eventPublisher && eventPublisher.publishingEnabled()) {
                        eventListener.onItemRead();
                    }
                }
                subscriber.onNext(nextItem);
            } else {
                subscriber.onError(new MissingBackpressureException(
                        "Received more data on the channel than demanded by the subscriber."));
            }
        }

        @Override
        protected boolean shouldReadMore(ChannelHandlerContext ctx) {
            return !subscriber.isUnsubscribed() && REQUEST_UPDATER.get(this) > 0;
        }

        public Subscriber<? super T> getSubscriber() {
            return subscriber;
        }

        /*Visible for testing*/long getRequested() {
            return requested;
        }
    }
}
