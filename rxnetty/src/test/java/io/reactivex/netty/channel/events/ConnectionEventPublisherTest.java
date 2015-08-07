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

import io.reactivex.netty.channel.events.ConnectionEventPublisherTest.ConnectionEventListenerImpl.Event;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class ConnectionEventPublisherTest {

    @Rule
    public final PublisherRule rule = new PublisherRule();

    @Test(timeout = 60000)
    public void testOnByteRead() throws Exception {
        rule.publisher.onByteRead(1);
        rule.listener.assertMethodsCalled(Event.BytesRead);

        assertThat("Listener not called with bytes read.", rule.listener.getBytesRead(), is(1L));
    }

    @Test(timeout = 60000)
    public void testOnItemRead() throws Exception {
        rule.publisher.onItemRead();
        rule.listener.assertMethodsCalled(Event.ItemRead);

        assertThat("Listener not called with items read.", rule.listener.getItemsRead(), is(1L));
    }

    @Test(timeout = 60000)
    public void testOnItemReceivedToWrite() throws Exception {
        rule.publisher.onItemReceivedToWrite();
        rule.listener.assertMethodsCalled(Event.ItemWritten);

        assertThat("Listener not called with items received to write.", rule.listener.getItemsWritten(), is(1L));
    }

    @Test(timeout = 60000)
    public void testOnRequestReadMore() throws Exception {
        rule.publisher.onRequestMoreItemsToRead(1);
        rule.listener.assertMethodsCalled(Event.RequestReadMore);

        assertThat("Listener not called with read requested.", rule.listener.getTotalReadRequested(), is(1L));
    }

    @Test(timeout = 60000)
    public void testOnRequestWriteMore() throws Exception {
        rule.publisher.onRequestMoreItemsToWrite(1);
        rule.listener.assertMethodsCalled(Event.RequestWriteMore);

        assertThat("Listener not called with write requested.", rule.listener.getTotalWriteRequested(), is(1L));
    }

    @Test(timeout = 60000)
    public void testOnFlushStart() throws Exception {
        rule.publisher.onFlushStart();
        rule.listener.assertMethodsCalled(Event.FlushStart);
    }

    @Test(timeout = 60000)
    public void testOnFlushSuccess() throws Exception {
        rule.publisher.onFlushSuccess(1, TimeUnit.MILLISECONDS);
        rule.listener.assertMethodsCalled(Event.FlushSuccess);

        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
    }

    @Test(timeout = 60000)
    public void testOnFlushFailed() throws Exception {
        final Throwable expected = new NullPointerException("Deliberate");
        rule.publisher.onFlushFailed(1, TimeUnit.MILLISECONDS, expected);
        rule.listener.assertMethodsCalled(Event.FlushFailed);

        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat("Listener not called with error.", rule.listener.getRecievedError(), is(expected));
    }

    @Test(timeout = 60000)
    public void testOnWriteStart() throws Exception {
        rule.publisher.onWriteStart();
        rule.listener.assertMethodsCalled(Event.WriteStart);
    }

    @Test(timeout = 60000)
    public void testOnWriteSuccess() throws Exception {
        rule.publisher.onWriteSuccess(1, TimeUnit.MILLISECONDS, 10);
        rule.listener.assertMethodsCalled(Event.WriteSuccess);

        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat("Listener not called with bytes written.", rule.listener.getBytesWritten(), is(10L));
    }

    @Test(timeout = 60000)
    public void testOnWriteFailed() throws Exception {
        final Throwable expected = new NullPointerException("Deliberate");
        rule.publisher.onWriteFailed(1, TimeUnit.MILLISECONDS, expected);
        rule.listener.assertMethodsCalled(Event.WriteFailed);

        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat("Listener not called with error.", rule.listener.getRecievedError(), is(expected));
    }

    @Test(timeout = 60000)
    public void testOnConnectionCloseStart() throws Exception {
        rule.publisher.onConnectionCloseStart();
        rule.listener.assertMethodsCalled(Event.CloseStart);
    }

    @Test(timeout = 60000)
    public void testOnConnectionCloseSuccess() throws Exception {
        rule.publisher.onConnectionCloseSuccess(1, TimeUnit.MILLISECONDS);
        rule.listener.assertMethodsCalled(Event.CloseSuccess);

        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
    }

    @Test(timeout = 60000)
    public void testOnConnectionCloseFailed() throws Exception {
        final Throwable expected = new NullPointerException("Deliberate");
        rule.publisher.onConnectionCloseFailed(1, TimeUnit.MILLISECONDS, expected);
        rule.listener.assertMethodsCalled(Event.CloseFailed);

        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat("Listener not called with error.", rule.listener.getRecievedError(), is(expected));
    }

    @Test(timeout = 60000)
    public void testCustomEvent() throws Exception {
        Object event = "Hello";
        rule.publisher.onCustomEvent(event);
        rule.listener.assertMethodsCalled(Event.CustomEvent);

        assertThat("Listener not called with event.", rule.listener.getCustomEvent(), is(event));
    }

    @Test(timeout = 60000)
    public void testCustomEventWithError() throws Exception {
        final Throwable expected = new NullPointerException("Deliberate");
        Object event = "Hello";
        rule.publisher.onCustomEvent(event, expected);
        rule.listener.assertMethodsCalled(Event.CustomEventWithError);

        assertThat("Listener not called with event.", rule.listener.getCustomEvent(), is(event));
        assertThat("Listener not called with error.", rule.listener.getRecievedError(), is(expected));
    }

    @Test(timeout = 60000)
    public void testCustomEventWithDuration() throws Exception {
        Object event = "Hello";
        rule.publisher.onCustomEvent(event, 1, TimeUnit.MILLISECONDS);
        rule.listener.assertMethodsCalled(Event.CustomEventWithDuration);

        assertThat("Listener not called with event.", rule.listener.getCustomEvent(), is(event));
        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
    }

    @Test(timeout = 60000)
    public void testCustomEventWithDurationAndError() throws Exception {
        final Throwable expected = new NullPointerException("Deliberate");
        Object event = "Hello";
        rule.publisher.onCustomEvent(event, 1, TimeUnit.MILLISECONDS, expected);
        rule.listener.assertMethodsCalled(Event.CustomEventWithDurationAndError);

        assertThat("Listener not called with event.", rule.listener.getCustomEvent(), is(event));
        assertThat("Listener not called with duration.", rule.listener.getDuration(), is(1L));
        assertThat("Listener not called with time unit.", rule.listener.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat("Listener not called with error.", rule.listener.getRecievedError(), is(expected));
    }

    @Test(timeout = 60000)
    public void testPublishingEnabled() throws Exception {
        assertThat("Publishing not enabled.", rule.publisher.publishingEnabled(), is(true));
    }

    @Test(timeout = 60000)
    public void testCopy() throws Exception {
        ConnectionEventPublisher<ConnectionEventListenerImpl> copy = rule.publisher.copy();

        assertThat("Publisher not copied.", copy, is(not(sameInstance(rule.publisher))));
        assertThat("Listeners not copied.", copy.getListeners(), is(not(sameInstance(rule.publisher.getListeners()))));
    }

    public static class PublisherRule extends ExternalResource {

        private ConnectionEventListenerImpl listener;
        private ConnectionEventPublisher<ConnectionEventListenerImpl> publisher;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    listener = new ConnectionEventListenerImpl();
                    publisher = new ConnectionEventPublisher<>();
                    publisher.subscribe(listener);
                    base.evaluate();
                }
            };
        }
    }

    public static class ConnectionEventListenerImpl extends ConnectionEventListener {

        public enum Event {
            ItemRead, ItemWritten, RequestReadMore, RequestWriteMore, ReadComplete, WriteComplete, BytesRead,
            FlushStart, FlushSuccess, FlushFailed, WriteStart, WriteSuccess, WriteFailed, CloseStart, CloseSuccess,
            CloseFailed, CustomEvent,
            CustomEventWithDuration, CustomEventWithDurationAndError, CustomEventWithError, Complete
        }

        private final List<Event> methodsCalled = new ArrayList<>();
        private long bytesRead;
        private long duration;
        private TimeUnit timeUnit;
        private long bytesWritten;
        private Throwable recievedError;
        private Object customEvent;
        private long totalReadRequested;
        private long itemsRead;
        private long totalWriteRequested;
        private long itemsWritten;
        private long remainingReads;
        private long remainingWrites;

        @Override
        public void onRequestMoreItemsToRead(long itemsRequested) {
            methodsCalled.add(Event.RequestReadMore);
            totalReadRequested += itemsRequested;
        }

        @Override
        public void onRequestMoreItemsToWrite(long itemsRequested) {
            methodsCalled.add(Event.RequestWriteMore);
            totalWriteRequested += itemsRequested;
        }

        @Override
        public void onItemRead() {
            methodsCalled.add(Event.ItemRead);
            itemsRead++;
        }

        @Override
        public void onItemReceivedToWrite() {
            methodsCalled.add(Event.ItemWritten);
            itemsWritten++;
        }

        @Override
        public void onReadCompletion(long remainingReadRequests) {
            methodsCalled.add(Event.ReadComplete);
            remainingReads += remainingReadRequests;
        }

        @Override
        public void onWriteCompletion(long remainingWriteRequests) {
            methodsCalled.add(Event.WriteComplete);
            remainingWrites += remainingWriteRequests;
        }

        @Override
        public void onByteRead(long bytesRead) {
            methodsCalled.add(Event.BytesRead);
            this.bytesRead = bytesRead;
        }

        @Override
        public void onFlushStart() {
            methodsCalled.add(Event.FlushStart);
        }

        @Override
        public void onFlushSuccess(long duration, TimeUnit timeUnit) {
            methodsCalled.add(Event.FlushSuccess);
            this.duration = duration;
            this.timeUnit = timeUnit;
        }

        @Override
        public void onFlushFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
            methodsCalled.add(Event.FlushFailed);
            this.duration = duration;
            this.timeUnit = timeUnit;
            recievedError = throwable;
        }

        @Override
        public void onWriteStart() {
            methodsCalled.add(Event.WriteStart);
        }

        @Override
        public void onWriteSuccess(long duration, TimeUnit timeUnit, long bytesWritten) {
            methodsCalled.add(Event.WriteSuccess);
            this.duration = duration;
            this.timeUnit = timeUnit;
            this.bytesWritten = bytesWritten;
        }

        @Override
        public void onWriteFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
            methodsCalled.add(Event.WriteFailed);
            this.duration = duration;
            this.timeUnit = timeUnit;
            recievedError = throwable;
        }

        @Override
        public void onConnectionCloseStart() {
            methodsCalled.add(Event.CloseStart);
        }

        @Override
        public void onConnectionCloseSuccess(long duration, TimeUnit timeUnit) {
            methodsCalled.add(Event.CloseSuccess);
            this.duration = duration;
            this.timeUnit = timeUnit;
        }

        @Override
        public void onConnectionCloseFailed(long duration, TimeUnit timeUnit, Throwable recievedError) {
            methodsCalled.add(Event.CloseFailed);
            this.duration = duration;
            this.timeUnit = timeUnit;
            this.recievedError = recievedError;
        }

        @Override
        public void onCustomEvent(Object event) {
            methodsCalled.add(Event.CustomEvent);
            customEvent = event;
        }

        @Override
        public void onCustomEvent(Object event, long duration, TimeUnit timeUnit) {
            methodsCalled.add(Event.CustomEventWithDuration);
            customEvent = event;
            this.duration = duration;
            this.timeUnit = timeUnit;
        }

        @Override
        public void onCustomEvent(Object event, long duration, TimeUnit timeUnit, Throwable throwable) {
            methodsCalled.add(Event.CustomEventWithDurationAndError);
            customEvent = event;
            this.duration = duration;
            this.timeUnit = timeUnit;
            recievedError = throwable;
        }

        @Override
        public void onCustomEvent(Object event, Throwable throwable) {
            methodsCalled.add(Event.CustomEventWithError);
            customEvent = event;
            recievedError = throwable;
        }

        @Override
        public void onCompleted() {
            methodsCalled.add(Event.Complete);
        }

        public void assertMethodsCalled(Event... events) {
            assertThat("Unexpected methods called count. Methods called: " + methodsCalled, methodsCalled, hasSize(events.length));
            assertThat("Unexpected methods called.", methodsCalled, contains(events));
        }

        public List<Event> getMethodsCalled() {
            return methodsCalled;
        }

        public long getBytesRead() {
            return bytesRead;
        }

        public long getDuration() {
            return duration;
        }

        public TimeUnit getTimeUnit() {
            return timeUnit;
        }

        public long getBytesWritten() {
            return bytesWritten;
        }

        public Throwable getRecievedError() {
            return recievedError;
        }

        public Object getCustomEvent() {
            return customEvent;
        }

        public long getTotalReadRequested() {
            return totalReadRequested;
        }

        public long getTotalWriteRequested() {
            return totalWriteRequested;
        }

        public long getItemsRead() {
            return itemsRead;
        }

        public long getItemsWritten() {
            return itemsWritten;
        }
    }

}