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

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.common.rpc.impl.TestConnectionContext;
import org.forgerock.openicf.common.rpc.impl.TestConnectionGroup;
import org.forgerock.openicf.common.rpc.impl.TestLocalRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the explicit {@link LocalRequest#register()} step and the handling of
 * a {@code CancelOpRequest} that is processed before the addressed operation
 * has registered itself (OpenIdentityPlatform/OpenICF#112).
 */
public class LocalRequestRegistrationTest<H extends RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>>> {

    /**
     * Minimal connection holder: the tests never transmit anything, they only
     * need {@link #getRemoteConnectionContext()} to reach the group.
     */
    private class StubConnectionHolder implements
            RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> {

        private final TestConnectionGroup<H> group;

        private StubConnectionHolder(TestConnectionGroup<H> group) {
            this.group = group;
        }

        public TestConnectionContext<H> getRemoteConnectionContext() {
            return group.getRemoteConnectionContext();
        }

        public Future<?> sendBytes(byte[] data) {
            throw new UnsupportedOperationException();
        }

        public Future<?> sendString(String data) {
            throw new UnsupportedOperationException();
        }

        public void sendPing(byte[] applicationData) throws Exception {
            throw new UnsupportedOperationException();
        }

        public void sendPong(byte[] applicationData) throws Exception {
            throw new UnsupportedOperationException();
        }

        public void close() {
        }
    }

    @SuppressWarnings("unchecked")
    private H newSocket(TestConnectionGroup<H> group) {
        return (H) new StubConnectionHolder(group);
    }

    @Test
    public void testConstructorDoesNotRegister() throws Exception {
        TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");
        new TestLocalRequest<H>(1L, newSocket(group));
        Assert.assertTrue(group.getLocalRequests().isEmpty());
    }

    @Test
    public void testRegisterThenCancel() throws Exception {
        TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");
        TestLocalRequest<H> request = new TestLocalRequest<H>(1L, newSocket(group));

        Assert.assertTrue(request.register());
        Assert.assertTrue(group.getLocalRequests().contains(1L));

        Assert.assertSame(group.receiveRequestCancel(1L), request);
        Assert.assertTrue(request.isCancelled());
        Assert.assertTrue(group.getLocalRequests().isEmpty());
    }

    @Test
    public void testCancelBeforeRegistrationIsParked() throws Exception {
        TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");

        // The cancel is processed before the operation registered itself.
        Assert.assertNull(group.receiveRequestCancel(1L));

        TestLocalRequest<H> request = new TestLocalRequest<H>(1L, newSocket(group));
        Assert.assertFalse(request.register());
        Assert.assertTrue(request.isCancelled());
        Assert.assertTrue(group.getLocalRequests().isEmpty());
    }

    @Test
    public void testParkedCancelDoesNotAffectOtherRequests() throws Exception {
        TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");

        Assert.assertNull(group.receiveRequestCancel(1L));

        TestLocalRequest<H> other = new TestLocalRequest<H>(2L, newSocket(group));
        Assert.assertTrue(other.register());
        Assert.assertFalse(other.isCancelled());
        Assert.assertTrue(group.getLocalRequests().contains(2L));
    }

    @Test
    public void testCancelIsDeliveredOnce() throws Exception {
        TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");
        final int[] cancelCount = new int[1];
        TestLocalRequest<H> request = new TestLocalRequest<H>(1L, newSocket(group)) {
            public boolean tryCancel() {
                cancelCount[0]++;
                return super.tryCancel();
            }
        };

        Assert.assertTrue(request.register());
        Assert.assertTrue(request.cancel());
        Assert.assertFalse(request.cancel());
        Assert.assertEquals(cancelCount[0], 1);
    }

    /**
     * Replays the interleaving where the whole {@code receiveRequestCancel}
     * runs between the registration's {@code putIfAbsent} and its pending
     * cancel check: the re-registration below stands in for the registering
     * thread resuming after the cancel was delivered directly. The verdict
     * must come from the request's own cancelled state, so register() must
     * still report the request dead.
     */
    @Test
    public void testRegisterObservesDirectlyDeliveredCancel() throws Exception {
        TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");
        TestLocalRequest<H> request = new TestLocalRequest<H>(1L, newSocket(group));

        Assert.assertTrue(request.register());
        Assert.assertSame(group.receiveRequestCancel(1L), request);

        Assert.assertFalse(request.register());
        Assert.assertTrue(request.isCancelled());
        Assert.assertTrue(group.getLocalRequests().isEmpty());
    }

    /**
     * Races {@link LocalRequest#register()} against
     * {@link TestConnectionGroup#receiveRequestCancel(long)}. Whatever the
     * interleaving, the cancel must be delivered exactly once and the request
     * must end up unregistered.
     */
    @Test
    public void testConcurrentRegisterAndCancel() throws Exception {
        final TestConnectionGroup<H> group = new TestConnectionGroup<H>("test");
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (long requestId = 1; requestId <= 1000; requestId++) {
                final long id = requestId;
                final AtomicInteger cancelCount = new AtomicInteger(0);
                final TestLocalRequest<H> request =
                        new TestLocalRequest<H>(id, newSocket(group)) {
                            public boolean tryCancel() {
                                cancelCount.incrementAndGet();
                                return super.tryCancel();
                            }
                        };
                final CyclicBarrier barrier = new CyclicBarrier(2);
                Future<Boolean> registered = executor.submit(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        barrier.await();
                        return request.register();
                    }
                });
                Future<Object> cancelled = executor.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        barrier.await();
                        return group.receiveRequestCancel(id);
                    }
                });
                cancelled.get();
                Boolean live = registered.get();

                Assert.assertTrue(request.isCancelled(), "round " + id
                        + ": cancel was lost");
                Assert.assertEquals(cancelCount.get(), 1, "round " + id
                        + ": tryCancel() delivered more than once");
                Assert.assertFalse(group.getLocalRequests().contains(id), "round " + id
                        + ": cancelled request left registered (register() == " + live + ")");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
