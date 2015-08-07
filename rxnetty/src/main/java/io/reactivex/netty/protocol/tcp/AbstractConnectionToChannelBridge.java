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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.channel.ConnectionImpl;
import io.reactivex.netty.channel.events.ConnectionEventListener;
import io.reactivex.netty.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;

import java.nio.channels.ClosedChannelException;

/**
 * A bridge between a {@link Connection} instance and the associated {@link Channel}.
 *
 * All operations on {@link Connection} will pass through this bridge to an appropriate action on the {@link Channel}
 *
 * <h2>Lazy {@link Connection#getInput()} subscription</h2>
 *
 * Lazy subscriptions are allowed on {@link Connection#getInput()} if and only if the channel is configured to
 * not read data automatically (i.e. {@link ChannelOption#AUTO_READ} is set to {@code false}). Otherwise,
 * if {@link Connection#getInput()} is subscribed lazily, the subscriber always receives an error. The content
 * in this case is disposed upon reading.
 *
 * @param <R> Type read from the connection held by this handler.
 * @param <W> Type written to the connection held by this handler.
 */
public abstract class AbstractConnectionToChannelBridge<R, W> extends BackpressureManagingHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractConnectionToChannelBridge.class);

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private static final IllegalStateException ONLY_ONE_CONN_SUB_ALLOWED =
            new IllegalStateException("Only one subscriber allowed for connection observable.");
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private static final IllegalStateException ONLY_ONE_CONN_INPUT_SUB_ALLOWED =
            new IllegalStateException("Only one subscriber allowed for connection input.");
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private static final IllegalStateException LAZY_CONN_INPUT_SUB =
            new IllegalStateException("Channel is set to auto-read but the subscription was lazy.");

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION = new ClosedChannelException();

    static {
        ONLY_ONE_CONN_INPUT_SUB_ALLOWED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        ONLY_ONE_CONN_SUB_ALLOWED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        LAZY_CONN_INPUT_SUB.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        CLOSED_CHANNEL_EXCEPTION.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private Subscriber<? super Connection<R, W>> newConnectionSub;
    private ReadProducer<R> readProducer;
    private boolean raiseErrorOnInputSubscription;
    private boolean connectionEmitted;

    protected AbstractConnectionToChannelBridge(String thisHandlerName, ConnectionEventListener eventListener,
                                                EventPublisher eventPublisher) {
        super(thisHandlerName, eventListener, eventPublisher);
    }

    protected AbstractConnectionToChannelBridge(String thisHandlerName,
                                                AttributeKey<ConnectionEventListener> eventListenerAttributeKey,
                                                AttributeKey<EventPublisher> eventPublisherAttributeKey) {
        super(thisHandlerName, eventListenerAttributeKey, eventPublisherAttributeKey);
    }

    protected AbstractConnectionToChannelBridge(String thisHandlerName, Subscriber<? super Connection<R, W>> connSub,
                                                AttributeKey<ConnectionEventListener> eventListenerAttributeKey,
                                                AttributeKey<EventPublisher> eventPublisherAttributeKey) {
        this(thisHandlerName, eventListenerAttributeKey, eventPublisherAttributeKey);
        newConnectionSub = connSub;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

        if (isValidToEmitToReadSubscriber(readProducer)) {
            /*If the subscriber is still active, then it expects data but the channel is closed.*/
            readProducer.sendOnError(CLOSED_CHANNEL_EXCEPTION);
        }

        super.channelUnregistered(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof EmitConnectionEvent) {
            if (!connectionEmitted) {
                createNewConnection(ctx.channel());
                connectionEmitted = true;
            }
        } else if (evt instanceof ConnectionCreationFailedEvent) {
            if (isValidToEmit(newConnectionSub)) {
                newConnectionSub.onError(((ConnectionCreationFailedEvent)evt).getThrowable());
            }
        } else if (evt instanceof ConnectionSubscriberEvent) {
            @SuppressWarnings("unchecked")
            final ConnectionSubscriberEvent<R, W> connectionSubscriberEvent = (ConnectionSubscriberEvent<R, W>) evt;

            newConnectionSubscriber(connectionSubscriberEvent);
        } else if (evt instanceof ConnectionInputSubscriberEvent) {
            @SuppressWarnings("unchecked")
            ConnectionInputSubscriberEvent<R, W> event = (ConnectionInputSubscriberEvent<R, W>) evt;

            newConnectionInputSubscriber(ctx.channel(), event.getSubscriber(), event.getConnection());
        } else if (evt instanceof ConnectionInputSubscriberResetEvent) {
            resetConnectionInputSubscriber();
        }

        super.userEventTriggered(ctx, evt);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void newMessage(ChannelHandlerContext ctx, Object msg) {
        if (isValidToEmitToReadSubscriber(readProducer)) {
            try {
                readProducer.sendOnNext((R) msg);
            } catch (ClassCastException e) {
                ReferenceCountUtil.release(msg); // Since, this was not sent to the subscriber, release the msg.
                readProducer.sendOnError(e);
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("Data received on channel, but no subscriber registered. Discarding data. Message class: "
                            + msg.getClass().getName() + ", channel: " + ctx.channel());
            }
            ReferenceCountUtil.release(msg); // No consumer of the message, so discard.
        }
    }

    @Override
    public boolean shouldReadMore(ChannelHandlerContext ctx) {
        return null != readProducer && readProducer.shouldReadMore(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!connectionEmitted && isValidToEmit(newConnectionSub)) {
            newConnectionSub.onError(cause);
        } else if (isValidToEmitToReadSubscriber(readProducer)) {
            readProducer.sendOnError(cause);
        } else {
            logger.info("Exception in the pipeline and none of the subscribers are active.", cause);
        }
    }

    protected static boolean isValidToEmit(Subscriber<?> subscriber) {
        return null != subscriber && !subscriber.isUnsubscribed();
    }

    private static boolean isValidToEmitToReadSubscriber(ReadProducer<?> readProducer) {
        return null != readProducer && !readProducer.getSubscriber().isUnsubscribed();
    }

    protected void onNewReadSubscriber(Connection<R, W> connection, Subscriber<? super R> subscriber) {
        // NOOP
    }

    protected final void checkEagerSubscriptionIfConfigured(Connection<R, W> connection, Channel channel) {
        if (channel.config().isAutoRead() && null == readProducer) {
            // If the channel is set to auto-read and there is no eager subscription then, we should raise errors
            // when a subscriber arrives.
            raiseErrorOnInputSubscription = true;
            final Subscriber<? super R> discardAll = ConnectionInputSubscriberEvent.discardAllInput(connection)
                                                                                   .getSubscriber();
            final ReadProducer<R> producer = new ReadProducer<>(discardAll, channel, eventListener, eventPublisher);
            discardAll.setProducer(producer);
            readProducer = producer;
        }
    }

    protected final Subscriber<? super Connection<R, W>> getNewConnectionSub() {
        return newConnectionSub;
    }

    protected final boolean isConnectionEmitted() {
        return connectionEmitted;
    }

    private void createNewConnection(Channel channel) {
        if (isValidToEmit(newConnectionSub)) {
            Connection<R, W> connection = ConnectionImpl.create(channel, eventListener, eventPublisher);
            newConnectionSub.onNext(connection);
            connectionEmitted = true;
            checkEagerSubscriptionIfConfigured(connection, channel);
            newConnectionSub.onCompleted();
        } else {
            channel.close(); // Closing the connection if not sent to a subscriber.
        }
    }

    private void resetConnectionInputSubscriber() {
        final Subscriber<? super R> connInputSub = null == readProducer? null : readProducer.getSubscriber();
        if (isValidToEmit(connInputSub)) {
            connInputSub.onCompleted();
        }
        raiseErrorOnInputSubscription = false;
        readProducer = null; // A subsequent event should set it to the desired subscriber.
    }

    private void newConnectionInputSubscriber(final Channel channel, final Subscriber<? super R> subscriber,
                                              final Connection<R, W> connection) {
        final Subscriber<? super R> connInputSub = null == readProducer? null : readProducer.getSubscriber();
        if (isValidToEmit(connInputSub)) {
            /*Allow only once concurrent input subscriber but allow concatenated subscribers*/
            subscriber.onError(ONLY_ONE_CONN_INPUT_SUB_ALLOWED);
        } else if (raiseErrorOnInputSubscription) {
            subscriber.onError(LAZY_CONN_INPUT_SUB);
        } else {
            final ReadProducer<R> producer = new ReadProducer<>(subscriber, channel, eventListener, eventPublisher);
            subscriber.setProducer(producer);
            onNewReadSubscriber(connection, subscriber);
            readProducer = producer;
        }
    }

    private void newConnectionSubscriber(ConnectionSubscriberEvent<R, W> event) {
        if (null == newConnectionSub) {
            newConnectionSub = event.getSubscriber();
        } else {
            event.getSubscriber().onError(ONLY_ONE_CONN_SUB_ALLOWED);
        }
    }
}
