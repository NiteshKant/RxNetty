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

import io.reactivex.netty.channel.events.ConnectionEventPublisherTest.ConnectionEventListenerImpl;
import io.reactivex.netty.channel.events.ConnectionEventPublisherTest.ConnectionEventListenerImpl.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class TcpClientEventListenerImpl extends TcpClientEventListener {

    public enum ClientEvent {
        ConnectStart, ConnectSuccess, ConnectFailed, ReleaseStart, ReleaseSuccess, ReleaseFailed, Eviction, Reuse,
        AcquireStart, AcquireSuccess, AcquireFailed
    }

    private final List<ClientEvent> methodsCalled = new ArrayList<>();
    private long duration;
    private TimeUnit timeUnit;
    private Throwable recievedError;

    private final ConnectionEventListenerImpl delegate = new ConnectionEventListenerImpl();

    @Override
    public void onConnectStart() {
        methodsCalled.add(ClientEvent.ConnectStart);
    }

    @Override
    public void onConnectSuccess(long duration, TimeUnit timeUnit) {
        this.duration = duration;
        this.timeUnit = timeUnit;
        methodsCalled.add(ClientEvent.ConnectSuccess);
    }

    @Override
    public void onConnectFailed(long duration, TimeUnit timeUnit, Throwable recievedError) {
        methodsCalled.add(ClientEvent.ConnectFailed);
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.recievedError = recievedError;
    }

    @Override
    public void onPoolReleaseStart() {
        methodsCalled.add(ClientEvent.ReleaseStart);
    }

    @Override
    public void onPoolReleaseSuccess(long duration, TimeUnit timeUnit) {
        methodsCalled.add(ClientEvent.ReleaseSuccess);
        this.duration = duration;
        this.timeUnit = timeUnit;
    }

    @Override
    public void onPoolReleaseFailed(long duration, TimeUnit timeUnit, Throwable recievedError) {
        methodsCalled.add(ClientEvent.ReleaseFailed);
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.recievedError = recievedError;
    }

    @Override
    public void onPooledConnectionEviction() {
        methodsCalled.add(ClientEvent.Eviction);
    }

    @Override
    public void onPooledConnectionReuse() {
        methodsCalled.add(ClientEvent.Reuse);
    }

    @Override
    public void onPoolAcquireStart() {
        methodsCalled.add(ClientEvent.AcquireStart);
    }

    @Override
    public void onPoolAcquireSuccess(long duration, TimeUnit timeUnit) {
        methodsCalled.add(ClientEvent.AcquireSuccess);
        this.duration = duration;
        this.timeUnit = timeUnit;
    }

    @Override
    public void onPoolAcquireFailed(long duration, TimeUnit timeUnit, Throwable recievedError) {
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.recievedError = recievedError;
        methodsCalled.add(ClientEvent.AcquireFailed);
    }

    @Override
    public void onConnectionCloseFailed(long duration, TimeUnit timeUnit,
                                        Throwable recievedError) {
        delegate.onConnectionCloseFailed(duration, timeUnit, recievedError);
    }

    @Override
    public void onConnectionCloseSuccess(long duration, TimeUnit timeUnit) {
        delegate.onConnectionCloseSuccess(duration, timeUnit);
    }

    @Override
    public void onConnectionCloseStart() {
        delegate.onConnectionCloseStart();
    }

    @Override
    public void onWriteFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        delegate.onWriteFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onWriteSuccess(long duration, TimeUnit timeUnit, long bytesWritten) {
        delegate.onWriteSuccess(duration, timeUnit, bytesWritten);
    }

    @Override
    public void onWriteStart() {
        delegate.onWriteStart();
    }

    @Override
    public void onFlushFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        delegate.onFlushFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onFlushSuccess(long duration, TimeUnit timeUnit) {
        delegate.onFlushSuccess(duration, timeUnit);
    }

    @Override
    public void onFlushStart() {
        delegate.onFlushStart();
    }

    @Override
    public void onByteRead(long bytesRead) {
        delegate.onByteRead(bytesRead);
    }

    @Override
    public void onCustomEvent(Object event) {
        delegate.onCustomEvent(event);
    }

    @Override
    public void onCustomEvent(Object event, long duration, TimeUnit timeUnit) {
        delegate.onCustomEvent(event, duration, timeUnit);
    }

    @Override
    public void onCustomEvent(Object event, long duration, TimeUnit timeUnit, Throwable throwable) {
        delegate.onCustomEvent(event, duration, timeUnit, throwable);
    }

    @Override
    public void onCustomEvent(Object event, Throwable throwable) {
        delegate.onCustomEvent(event, throwable);
    }

    @Override
    public void onRequestMoreItemsToRead(long itemsRequested) {
        delegate.onRequestMoreItemsToRead(itemsRequested);
    }

    @Override
    public void onRequestMoreItemsToWrite(long itemsRequested) {
        delegate.onRequestMoreItemsToWrite(itemsRequested);
    }

    @Override
    public void onItemReceivedToWrite() {
        delegate.onItemReceivedToWrite();
    }

    @Override
    public void onItemRead() {
        delegate.onItemRead();
    }

    @Override
    public void onReadCompletion(long remainingReadRequests) {
        delegate.onReadCompletion(remainingReadRequests);
    }

    @Override
    public void onWriteCompletion(long remainingWriteRequests) {
        delegate.onWriteCompletion(remainingWriteRequests);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }

    public void assertMethodsCalled(Event... events) {
        delegate.assertMethodsCalled(events);
    }

    public void assertMethodsCalled(ClientEvent... events) {
        assertThat("Unexpected methods called count.", methodsCalled, hasSize(events.length));
        assertThat("Unexpected methods called.", methodsCalled, contains(events));
    }

    public long getDuration() {
        return duration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public Throwable getRecievedError() {
        return recievedError;
    }
}
