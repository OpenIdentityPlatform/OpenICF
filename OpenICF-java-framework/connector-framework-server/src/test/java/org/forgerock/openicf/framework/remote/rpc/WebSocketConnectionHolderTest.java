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
package org.forgerock.openicf.framework.remote.rpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * Tests the per-socket serial dispatch queue of
 * {@link WebSocketConnectionHolder#executeSerially}: tasks of one holder run
 * in submission order without overlapping, tasks of different holders still
 * run concurrently, a failing task does not stall the queue, and a rejected
 * dispatch does not wedge it.
 */
public class WebSocketConnectionHolderTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @AfterClass
    public void shutdownPool() {
        pool.shutdownNow();
    }

    private static WebSocketConnectionHolder newHolder() {
        return new WebSocketConnectionHolder() {

            protected void handshake(HandshakeMessage message) {
            }

            protected void tryClose() {
            }

            public boolean isOperational() {
                return true;
            }

            public RemoteOperationContext getRemoteConnectionContext() {
                return null;
            }

            public Future<?> sendBytes(byte[] data) {
                return CompletableFuture.completedFuture(null);
            }

            public Future<?> sendString(String data) {
                return CompletableFuture.completedFuture(null);
            }

            public void sendPing(byte[] applicationData) throws Exception {
            }

            public void sendPong(byte[] applicationData) throws Exception {
            }
        };
    }

    @Test(timeOut = 30000)
    public void testTasksRunInSubmissionOrderWithoutOverlap() throws Exception {
        final WebSocketConnectionHolder holder = newHolder();
        final int taskCount = 1000;
        final List<Integer> executed = new ArrayList<Integer>(taskCount);
        final AtomicInteger inFlight = new AtomicInteger(0);
        final AtomicInteger maxInFlight = new AtomicInteger(0);
        final CountDownLatch done = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            holder.executeSerially(pool, new Runnable() {
                public void run() {
                    int current = inFlight.incrementAndGet();
                    maxInFlight.accumulateAndGet(current, Math::max);
                    // executed is not synchronized on purpose: tasks never
                    // overlap, and each task's writes are published to the
                    // next one by the release/acquire chain through
                    // dispatchScheduled (set(false) after a drain, winning
                    // CAS before the next) - non-overlap alone would not
                    // guarantee visibility.
                    executed.add(id);
                    inFlight.decrementAndGet();
                    done.countDown();
                }
            });
        }

        Assert.assertTrue(done.await(20, TimeUnit.SECONDS), "tasks did not finish");
        Assert.assertEquals(maxInFlight.get(), 1, "tasks of one socket overlapped");
        Assert.assertEquals(executed.size(), taskCount);
        for (int i = 0; i < taskCount; i++) {
            Assert.assertEquals(executed.get(i).intValue(), i, "task order broken at " + i);
        }
    }

    @Test(timeOut = 30000)
    public void testBlockedHolderDoesNotBlockOtherHolder() throws Exception {
        final WebSocketConnectionHolder blocked = newHolder();
        final WebSocketConnectionHolder free = newHolder();
        final CountDownLatch blockedStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch freeDone = new CountDownLatch(1);
        final CountDownLatch blockedDone = new CountDownLatch(1);

        blocked.executeSerially(pool, new Runnable() {
            public void run() {
                blockedStarted.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        blocked.executeSerially(pool, new Runnable() {
            public void run() {
                blockedDone.countDown();
            }
        });

        Assert.assertTrue(blockedStarted.await(10, TimeUnit.SECONDS));

        free.executeSerially(pool, new Runnable() {
            public void run() {
                freeDone.countDown();
            }
        });

        // The other socket keeps working while the first one is busy...
        Assert.assertTrue(freeDone.await(10, TimeUnit.SECONDS),
                "an unrelated socket was blocked by a busy one");
        // ...and the second task of the busy socket is still pending.
        Assert.assertEquals(blockedDone.getCount(), 1,
                "second task of the busy socket must wait for the first");

        release.countDown();
        Assert.assertTrue(blockedDone.await(10, TimeUnit.SECONDS));
    }

    @Test(timeOut = 30000)
    public void testFailingTaskDoesNotStallTheQueue() throws Exception {
        final WebSocketConnectionHolder holder = newHolder();
        final CountDownLatch survived = new CountDownLatch(1);

        holder.executeSerially(pool, new Runnable() {
            public void run() {
                throw new RuntimeException("boom");
            }
        });
        holder.executeSerially(pool, new Runnable() {
            public void run() {
                survived.countDown();
            }
        });

        Assert.assertTrue(survived.await(10, TimeUnit.SECONDS),
                "a failing task stalled the serial queue");
    }

    @Test(timeOut = 30000)
    public void testRejectedDispatchDoesNotWedgeTheQueue() throws Exception {
        final WebSocketConnectionHolder holder = newHolder();
        final AtomicBoolean rejectNext = new AtomicBoolean(true);
        final Executor rejectingOnce = new Executor() {
            public void execute(Runnable command) {
                if (rejectNext.getAndSet(false)) {
                    throw new RejectedExecutionException("simulated shutdown");
                }
                pool.execute(command);
            }
        };
        final List<Integer> executed = new ArrayList<Integer>();
        final CountDownLatch done = new CountDownLatch(2);

        try {
            holder.executeSerially(rejectingOnce, recordingTask(1, executed, done));
            Assert.fail("the rejection must reach the caller");
        } catch (RejectedExecutionException expected) {
            // The task stays queued; the dispatchScheduled flag must have
            // been reset so a later submission can reschedule the drain.
        }

        holder.executeSerially(rejectingOnce, recordingTask(2, executed, done));

        Assert.assertTrue(done.await(10, TimeUnit.SECONDS),
                "a rejected dispatch wedged the serial queue");
        Assert.assertEquals(executed, Arrays.asList(1, 2),
                "both tasks must drain in submission order");
    }

    private static Runnable recordingTask(final int id, final List<Integer> executed,
            final CountDownLatch done) {
        return new Runnable() {
            public void run() {
                executed.add(id);
                done.countDown();
            }
        };
    }
}
