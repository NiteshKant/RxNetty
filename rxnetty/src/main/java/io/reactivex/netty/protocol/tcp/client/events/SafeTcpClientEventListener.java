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
package io.reactivex.netty.protocol.tcp.client.events;

import io.reactivex.netty.events.internal.SafeEventListener;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SafeTcpClientEventListener extends TcpClientEventListener implements SafeEventListener {

    private final TcpClientEventListener delegate;
    private final AtomicBoolean completed = new AtomicBoolean();

    public SafeTcpClientEventListener(TcpClientEventListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onCompleted() {
        if (completed.compareAndSet(false, true)) {
            delegate.onCompleted();
        }
    }

    @Override
    public void onConnectStart() {
        if (!completed.get()) {
            delegate.onConnectStart();
        }
    }

    @Override
    public void onConnectSuccess(long duration, TimeUnit timeUnit) {
        if (!completed.get()) {
            delegate.onConnectSuccess(duration, timeUnit);
        }
    }

    @Override
    public void onConnectFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        if (!completed.get()) {
            delegate.onConnectFailed(duration, timeUnit, throwable);
        }
    }

    @Override
    public void onPoolReleaseStart() {
        if (!completed.get()) {
            delegate.onPoolReleaseStart();
        }
    }

    @Override
    public void onPoolReleaseSuccess(long duration, TimeUnit timeUnit) {
        if (!completed.get()) {
            delegate.onPoolReleaseSuccess(duration, timeUnit);
        }
    }

    @Override
    public void onPoolReleaseFailed(long duration, TimeUnit timeUnit,
                                    Throwable throwable) {
        if (!completed.get()) {
            delegate.onPoolReleaseFailed(duration, timeUnit, throwable);
        }
    }

    @Override
    public void onPooledConnectionEviction() {
        if (!completed.get()) {
            delegate.onPooledConnectionEviction();
        }
    }

    @Override
    public void onPooledConnectionReuse() {
        if (!completed.get()) {
            delegate.onPooledConnectionReuse();
        }
    }

    @Override
    public void onPoolAcquireStart() {
        if (!completed.get()) {
            delegate.onPoolAcquireStart();
        }
    }

    @Override
    public void onPoolAcquireSuccess(long duration, TimeUnit timeUnit) {
        if (!completed.get()) {
            delegate.onPoolAcquireSuccess(duration, timeUnit);
        }
    }

    @Override
    public void onPoolAcquireFailed(long duration, TimeUnit timeUnit,
                                    Throwable throwable) {
        if (!completed.get()) {
            delegate.onPoolAcquireFailed(duration, timeUnit, throwable);
        }
    }

    @Override
    public void onByteRead(long bytesRead) {
        if (!completed.get()) {
            delegate.onByteRead(bytesRead);
        }
    }

    @Override
    public void onFlushStart() {
        if (!completed.get()) {
            delegate.onFlushStart();
        }
    }

    @Override
    public void onFlushSuccess(long duration, TimeUnit timeUnit) {
        if (!completed.get()) {
            delegate.onFlushSuccess(duration, timeUnit);
        }
    }

    @Override
    public void onFlushFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        if (!completed.get()) {
            delegate.onFlushFailed(duration, timeUnit, throwable);
        }
    }

    @Override
    public void onWriteStart() {
        if (!completed.get()) {
            delegate.onWriteStart();
        }
    }

    @Override
    public void onWriteSuccess(long duration, TimeUnit timeUnit, long bytesWritten) {
        if (!completed.get()) {
            delegate.onWriteSuccess(duration, timeUnit, bytesWritten);
        }
    }

    @Override
    public void onWriteFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        if (!completed.get()) {
            delegate.onWriteFailed(duration, timeUnit, throwable);
        }
    }

    @Override
    public void onConnectionCloseStart() {
        if (!completed.get()) {
            delegate.onConnectionCloseStart();
        }
    }

    @Override
    public void onConnectionCloseSuccess(long duration, TimeUnit timeUnit) {
        if (!completed.get()) {
            delegate.onConnectionCloseSuccess(duration, timeUnit);
        }
    }

    @Override
    public void onConnectionCloseFailed(long duration, TimeUnit timeUnit,
                                        Throwable throwable) {
        if (!completed.get()) {
            delegate.onConnectionCloseFailed(duration, timeUnit, throwable);
        }
    }

    @Override
    public void onCustomEvent(Object event) {
        if (!completed.get()) {
            delegate.onCustomEvent(event);
        }
    }

    @Override
    public void onCustomEvent(Object event, long duration, TimeUnit timeUnit) {
        if (!completed.get()) {
            delegate.onCustomEvent(event, duration, timeUnit);
        }
    }

    @Override
    public void onCustomEvent(Object event, long duration, TimeUnit timeUnit, Throwable throwable) {
        if (!completed.get()) {
            delegate.onCustomEvent(event, duration, timeUnit, throwable);
        }
    }

    @Override
    public void onCustomEvent(Object event, Throwable throwable) {
        if (!completed.get()) {
            delegate.onCustomEvent(event, throwable);
        }
    }

    @Override
    public void onRequestMoreItemsToRead(long itemsRequested) {
        if (!completed.get()) {
            delegate.onRequestMoreItemsToRead(itemsRequested);
        }
    }

    @Override
    public void onRequestMoreItemsToWrite(long itemsRequested) {
        if (!completed.get()) {
            delegate.onRequestMoreItemsToWrite(itemsRequested);
        }
    }

    @Override
    public void onItemRead() {
        if (!completed.get()) {
            delegate.onItemRead();
        }
    }

    @Override
    public void onItemReceivedToWrite() {
        if (!completed.get()) {
            delegate.onItemReceivedToWrite();
        }
    }

    @Override
    public void onReadCompletion(long remainingReadRequests) {
        if (!completed.get()) {
            delegate.onReadCompletion(remainingReadRequests);
        }
    }

    @Override
    public void onWriteCompletion(long remainingWriteRequests) {
        if (!completed.get()) {
            delegate.onWriteCompletion(remainingWriteRequests);
        }
    }
}
