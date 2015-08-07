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
package io.reactivex.netty.channel.events;

import io.reactivex.netty.events.EventListener;

import java.util.concurrent.TimeUnit;

/**
 * An event listener for all events releated to a {@link io.reactivex.netty.channel.Connection}
 */
public abstract class ConnectionEventListener implements EventListener {

    /**
     * Whenever a subscriber to the connection input requests more items to be read.
     *
     * This does <em>not</em> represent number of bytes requested to be read but the number of messages requested, which
     * depends on what the connection is configured to be read, from a {@link io.netty.buffer.ByteBuf} to a custom
     * object.
     *
     * @param itemsRequested Number of items requested for read.
     */
    public void onRequestMoreItemsToRead(long itemsRequested) { }

    /**
     * For all writes, this event is fired whenever the connection reads another item.
     *
     * Typically, this would be used with {@link #onRequestMoreItemsToRead(long)} in order to infer outstanding demand.
     */
    public void onItemRead() { }

    /**
     * For all writes, this event is fired whenever the connection requests more items to be emitted and written. Since,
     * this does not refer to the actual {@code Observable} written on the connection, if there are multiple writes on
     * the same connection, this event will be fired for all of them and there is no way to map these requests to the
     * writes performed on the connection.
     *
     * This does <em>not</em> represent number of bytes requested to be written but the number of messages requested,
     * which depends on what the connection is configured to be written, from a {@link io.netty.buffer.ByteBuf} to a
     * custom object.
     *
     * @param itemsRequested Number of items requested for read.
     */
    public void onRequestMoreItemsToWrite(long itemsRequested) { }

    /**
     * For all writes, this event is fired whenever the connection receives another item to be written. Since,
     * this does not refer to the actual {@code Observable} written on the connection, if there are multiple writes on
     * the same connection, this event will be fired for all of them and there is no way to map these requests to the
     * writes performed on the connection.
     *
     * Typically, this would be used with {@link #onRequestMoreItemsToWrite(long)} in order to infer outstanding demand.
     */
    public void onItemReceivedToWrite() { }

    /**
     * This event is fired for every read completion (via error, completion or unsubscribe) from a connection. This
     * event is useful to correctly counting pending read item requests since this event contains the number of
     * outstanding read requests which will never be fulfilled (since the stream has completed either due to unsubscribe
     * or a terminal event).
     *
     * @param remainingReadRequests The number of read requests as informed by {@link #onRequestMoreItemsToRead(long)}
     * that will never be fulfilled.
     */
    public void onReadCompletion(long remainingReadRequests) { }

    /**
     * This event is fired for every write completion (via error, completion or unsubscribe) from a connection. This
     * event is useful to correctly counting pending write item requests since this event contains the number of
     * outstanding read requests which will never be fulfilled (since the stream has completed either due to unsubscribe
     * or a terminal event).
     *
     * @param remainingWriteRequests The number of write requests as informed by
     * {@link #onRequestMoreItemsToWrite(long)} that will never be fulfilled.
     */
    public void onWriteCompletion(long remainingWriteRequests) { }

    /**
     * Event whenever any bytes are read on any open connection.
     *
     * @param bytesRead Number of bytes read.
     */
    public void onByteRead(long bytesRead) { }

    /**
     * Event whenever a flush is issued on a connection.
     */
    public void onFlushStart() {}

    /**
     * Event whenever flush succeeds.
     *
     * @param duration Duration between flush start and completion.
     * @param timeUnit Timeunit for the duration.
     */
    @SuppressWarnings("unused")
    public void onFlushSuccess(long duration, TimeUnit timeUnit) {}

    /**
     * Event whenever flush fails.
     *
     * @param duration Duration between flush start and failure.
     * @param timeUnit Timeunit for the duration.
     * @param throwable Error that caused the failure.
     */
    @SuppressWarnings("unused")
    public void onFlushFailed(long duration, TimeUnit timeUnit, Throwable throwable) {}

    /**
     * Event whenever a write is issued on a connection.
     */
    public void onWriteStart() {}

    /**
     * Event whenever data is written successfully on a connection.
     *
     * @param duration Duration between write start and completion.
     * @param timeUnit Timeunit for the duration.
     * @param bytesWritten Number of bytes written.
     */
    @SuppressWarnings("unused")
    public void onWriteSuccess(long duration, TimeUnit timeUnit, long bytesWritten) {}

    /**
     * Event whenever a write failed on a connection.
     *
     * @param duration Duration between write start and failure.
     * @param timeUnit Timeunit for the duration.
     * @param throwable Error that caused the failure..
     */
    @SuppressWarnings("unused")
    public void onWriteFailed(long duration, TimeUnit timeUnit, Throwable throwable) {}

    /**
     * Event whenever a close of any connection is issued. This event will only be fired when the physical connection
     * is closed and not when a pooled connection is closed and put back in the pool.
     */
    @SuppressWarnings("unused")
    public void onConnectionCloseStart() {}

    /**
     * Event whenever a close of any connection is successful.
     *
     * @param duration Duration between close start and completion.
     * @param timeUnit Timeunit for the duration.
     */
    @SuppressWarnings("unused")
    public void onConnectionCloseSuccess(long duration, TimeUnit timeUnit) {}

    /**
     * Event whenever a connection close failed.
     *
     * @param duration Duration between close start and failure.
     * @param timeUnit Timeunit for the duration.
     * @param throwable Error that caused the failure.
     */
    @SuppressWarnings("unused")
    public void onConnectionCloseFailed(long duration, TimeUnit timeUnit, Throwable throwable) {}

    @Override
    public void onCustomEvent(Object event) { }

    @Override
    public void onCustomEvent(Object event, long duration, TimeUnit timeUnit) { }

    @Override
    public void onCustomEvent(Object event, Throwable throwable) { }

    @Override
    public void onCustomEvent(Object event, long duration, TimeUnit timeUnit, Throwable throwable) { }

    @Override
    public void onCompleted() { }

}
