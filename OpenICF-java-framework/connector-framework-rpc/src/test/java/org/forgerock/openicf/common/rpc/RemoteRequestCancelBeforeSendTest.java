/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openicf.common.rpc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.common.rpc.impl.TestConnectionContext;
import org.forgerock.openicf.common.rpc.impl.TestConnectionGroup;
import org.forgerock.openicf.common.rpc.impl.TestMessage;
import org.forgerock.openicf.common.rpc.impl.TestRemoteRequest;
import org.forgerock.util.promise.Promises;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * A {@link RemoteRequest} is registered in
 * {@link RemoteConnectionGroup#trySubmitRequest} before its message is sent,
 * so it can be cancelled - by {@link RemoteConnectionGroup#submitRequestCancel}
 * or by a group shutting down - while the send is still in progress. These
 * tests pin down what happens to such a request.
 */
public class RemoteRequestCancelBeforeSendTest {

    private static final long TIMEOUT_SECONDS = 10;

    private TestConnectionGroup<RecordingHolder> group;
    private ExecutorService executor;

    @BeforeMethod
    public void setUp() {
        group = new TestConnectionGroup<RecordingHolder>("client");
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterMethod
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void promiseExistsWhileRequestIsRegisteredButNotSent() throws Exception {
        RecordingHolder holder = new RecordingHolder(group, false);
        group.addConnection(holder);
        BlockingRequestFactory factory = new BlockingRequestFactory();

        Future<TestRemoteRequest<RecordingHolder>> submitted = submit(factory);
        try {
            factory.awaitBlockedBeforeSend();

            Assert.assertEquals(group.getRemoteRequests().size(), 1);
            Assert.assertNotNull(factory.request.get().getPromise(),
                    "registered request must expose its promise before the message is sent");
        } finally {
            factory.releaseSend();
            submitted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void cancelBeforeSendCancelsPromiseWithoutSendingAnything() throws Exception {
        RecordingHolder holder = new RecordingHolder(group, false);
        group.addConnection(holder);
        BlockingRequestFactory factory = new BlockingRequestFactory();

        Future<TestRemoteRequest<RecordingHolder>> submitted = submit(factory);
        factory.awaitBlockedBeforeSend();
        long requestId = group.getRemoteRequests().iterator().next();

        RemoteRequest<?, ?, ?, ?, ?> cancelled = group.submitRequestCancel(requestId);

        Assert.assertSame(cancelled, factory.request.get());
        Assert.assertTrue(cancelled.getPromise().isCancelled());

        factory.releaseSend();
        TestRemoteRequest<RecordingHolder> request = submitted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assert.assertSame(request, cancelled,
                "the caller gets its request back so it can observe the cancellation");
        Assert.assertTrue(request.getPromise().isCancelled());
        Assert.assertEquals(holder.sent.size(), 0, "nothing goes on the wire: " + holder.sent);
        Assert.assertTrue(group.getRemoteRequests().isEmpty());
    }

    @Test
    public void groupShutdownBeforeSendCancelsPromiseWithoutSendingAnything() throws Exception {
        RecordingHolder holder = new RecordingHolder(group, false);
        group.addConnection(holder);
        BlockingRequestFactory factory = new BlockingRequestFactory();

        Future<TestRemoteRequest<RecordingHolder>> submitted = submit(factory);
        factory.awaitBlockedBeforeSend();

        // Same loop as WebSocketConnectionGroup.shutdown(): cancel(true) on
        // every registered remote request.
        group.close();

        factory.releaseSend();
        TestRemoteRequest<RecordingHolder> request = submitted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assert.assertTrue(request.getPromise().isCancelled());
        Assert.assertEquals(holder.sent.size(), 0, "nothing goes on the wire: " + holder.sent);
        Assert.assertTrue(group.getRemoteRequests().isEmpty());
    }

    @Test
    public void cancelDuringSendNotifiesRemoteAfterTheRequestIsDelivered() throws Exception {
        RecordingHolder holder = new RecordingHolder(group, false);
        holder.blockFirstSend();
        group.addConnection(holder);
        BlockingRequestFactory factory = new BlockingRequestFactory();
        factory.releaseSend();

        Future<TestRemoteRequest<RecordingHolder>> submitted = submit(factory);
        holder.awaitBlockedInSend();

        // The request is on its way: cancel(true) must not race the cancel
        // message ahead of the request itself.
        Assert.assertTrue(factory.request.get().getPromise().cancel(true));

        holder.releaseSend();
        TestRemoteRequest<RecordingHolder> request = submitted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assert.assertTrue(request.getPromise().isCancelled());
        Assert.assertEquals(holder.sent.size(), 2, "request then cancel: " + holder.sent);
        Assert.assertEquals(read(holder.sent.get(0)).request, 0);
        Assert.assertEquals(read(holder.sent.get(1)).cancel, Boolean.TRUE);
        Assert.assertTrue(group.getRemoteRequests().isEmpty());
    }

    // Behaviour that must survive the change: the cases below pass before it.

    @Test
    public void cancelAfterSendNotifiesRemote() throws Exception {
        RecordingHolder holder = new RecordingHolder(group, false);
        group.addConnection(holder);
        BlockingRequestFactory factory = new BlockingRequestFactory();
        factory.releaseSend();

        TestRemoteRequest<RecordingHolder> request = group.trySubmitRequest(factory);
        Assert.assertNotNull(request);
        Assert.assertEquals(holder.sent.size(), 1);

        Assert.assertSame(group.submitRequestCancel(request.getRequestId()), request);

        Assert.assertTrue(request.getPromise().isCancelled());
        Assert.assertEquals(holder.sent.size(), 2, "request then cancel: " + holder.sent);
        Assert.assertEquals(read(holder.sent.get(1)).cancel, Boolean.TRUE);
        Assert.assertTrue(group.getRemoteRequests().isEmpty());
    }

    @Test
    public void sendFailsOverToTheNextConnectionAndCompletesNormally() throws Exception {
        RecordingHolder broken = new RecordingHolder(group, true);
        RecordingHolder working = new RecordingHolder(group, false);
        group.addConnection(broken);
        group.addConnection(working);
        BlockingRequestFactory factory = new BlockingRequestFactory();
        factory.releaseSend();

        TestRemoteRequest<RecordingHolder> request = group.trySubmitRequest(factory);
        Assert.assertNotNull(request);

        Assert.assertEquals(broken.attempted.size(), 1);
        Assert.assertEquals(broken.sent.size(), 0);
        Assert.assertEquals(working.sent.size(), 1);
        Assert.assertFalse(request.getPromise().isDone());
        Assert.assertTrue(group.getRemoteRequests().contains(request.getRequestId()));

        TestMessage response = new TestMessage();
        response.response = "OK";
        group.receiveRequestResponse(working, request.getRequestId(), response);

        Assert.assertEquals(request.getPromise().getOrThrow(TIMEOUT_SECONDS, TimeUnit.SECONDS), "OK");
        Assert.assertTrue(group.getRemoteRequests().isEmpty());
    }

    // ---- helpers ---------------------------------------------------------

    private Future<TestRemoteRequest<RecordingHolder>> submit(final BlockingRequestFactory factory) {
        return executor.submit(() -> group.trySubmitRequest(factory));
    }

    private TestMessage read(String message) {
        return group.getRemoteConnectionContext().read(message);
    }

    /**
     * Creates requests that block in {@code createMessageElement} - after
     * registration, before anything is sent - until {@link #releaseSend()}.
     */
    private static final class BlockingRequestFactory
            implements
            RemoteRequestFactory<TestRemoteRequest<RecordingHolder>, String, Exception, TestConnectionGroup<RecordingHolder>, RecordingHolder, TestConnectionContext<RecordingHolder>> {

        final AtomicReference<TestRemoteRequest<RecordingHolder>> request = new AtomicReference<>();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        public TestRemoteRequest<RecordingHolder> createRemoteRequest(
                TestConnectionContext<RecordingHolder> context,
                long requestId,
                CompletionCallback<String, Exception, TestConnectionGroup<RecordingHolder>, RecordingHolder, TestConnectionContext<RecordingHolder>> completionCallback) {
            TestRemoteRequest<RecordingHolder> created =
                    new TestRemoteRequest<RecordingHolder>(context, requestId, completionCallback) {
                        protected TestMessage getTestMessage() {
                            blocked.countDown();
                            try {
                                if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("send was never released");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                            TestMessage message = new TestMessage();
                            message.request = 0;
                            return message;
                        }

                        protected void handle(RecordingHolder sourceConnection,
                                TestRemoteRequest<RecordingHolder> request, TestMessage message) {
                        }
                    };
            request.set(created);
            return created;
        }

        void awaitBlockedBeforeSend() throws InterruptedException {
            Assert.assertTrue(blocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "request did not reach createMessageElement");
        }

        void releaseSend() {
            release.countDown();
        }
    }

    /** Records every message handed to it; completes sends immediately. */
    private static final class RecordingHolder
            implements
            RemoteConnectionHolder<TestConnectionGroup<RecordingHolder>, RecordingHolder, TestConnectionContext<RecordingHolder>> {

        final List<String> attempted = new CopyOnWriteArrayList<>();
        final List<String> sent = new CopyOnWriteArrayList<>();
        private final TestConnectionContext<RecordingHolder> context;
        private final boolean failing;
        private CountDownLatch inSend;
        private CountDownLatch releaseSend;

        RecordingHolder(TestConnectionGroup<RecordingHolder> group, boolean failing) {
            this.context = group.getRemoteConnectionContext();
            this.failing = failing;
        }

        /** The first send blocks until {@link #releaseSend()}. */
        void blockFirstSend() {
            inSend = new CountDownLatch(1);
            releaseSend = new CountDownLatch(1);
        }

        void awaitBlockedInSend() throws InterruptedException {
            Assert.assertTrue(inSend.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "request did not reach sendString");
        }

        void releaseSend() {
            releaseSend.countDown();
        }

        public TestConnectionContext<RecordingHolder> getRemoteConnectionContext() {
            return context;
        }

        public Future<?> sendBytes(byte[] data) {
            throw new UnsupportedOperationException("string messages only");
        }

        public Future<?> sendString(String data) {
            attempted.add(data);
            if (failing) {
                return Promises.newExceptionPromise(new IOException("send failed"));
            }
            if (null != inSend && inSend.getCount() > 0) {
                inSend.countDown();
                try {
                    if (!releaseSend.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        return Promises.newExceptionPromise(new IOException("send was never released"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Promises.newExceptionPromise(new IOException(e));
                }
            }
            sent.add(data);
            return Promises.newResultPromise(null);
        }

        public void sendPing(byte[] applicationData) {
        }

        public void sendPong(byte[] applicationData) {
        }

        public void close() {
        }
    }
}
