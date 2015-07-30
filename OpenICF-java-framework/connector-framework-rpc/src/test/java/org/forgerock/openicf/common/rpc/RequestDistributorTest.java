/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.common.rpc;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.common.rpc.impl.NIOSimulator;
import org.forgerock.openicf.common.rpc.impl.TestConnectionContext;
import org.forgerock.openicf.common.rpc.impl.TestConnectionGroup;
import org.forgerock.openicf.common.rpc.impl.TestLocalRequest;
import org.forgerock.openicf.common.rpc.impl.TestMessage;
import org.forgerock.openicf.common.rpc.impl.TestMessageListener;
import org.forgerock.openicf.common.rpc.impl.TestRemoteRequest;
import org.forgerock.openicf.common.rpc.impl.TestRequestFactory;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.ResultHandler;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RequestDistributorTest<H extends RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>>> {

    private TestConnectionGroup<H> client = null;
    private TestConnectionGroup<H> server = null;
    private NIOSimulator<TestConnectionGroup<H>, H, TestConnectionContext<H>> simulator =
            null;

    @BeforeClass
    public void beforeClass() throws Exception {

        client = new TestConnectionGroup<H>("client");

        server = new TestConnectionGroup<H>("server");

        TestMessageListener<H> serverListener = new TestMessageListener<H>(server) {

            public void onError(Throwable t) {
            }

            public void onMessage(H socket, byte[] bytes) {
            }

            public void onMessage(H socket, String message) {
                TestMessage obj = socket.getRemoteConnectionContext().read(message);
                if (obj.request >= 0) {
                    TestLocalRequest<H> request = new TestLocalRequest<H>(obj.messageId, socket);
                    try {
                        getConnectionGroup().receiveRequest(request).execute(obj.request);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // request.handleException(e);
                    }
                } else {
                    getConnectionGroup().receiveRequestUpdate(socket, obj.messageId, obj);
                }
            }

            public void onPing(H socket, byte[] bytes) {

            }

            public void onPong(H socket, byte[] bytes) {

            }
        };

        simulator =
                new NIOSimulator<TestConnectionGroup<H>, H, TestConnectionContext<H>>(
                        serverListener);

        Assert.assertFalse(client.isOperational());
        Assert.assertFalse(server.isOperational());

    }

    @AfterClass
    public void afterClass() throws Exception {
        server.close();
        client.close();
        simulator.close();
    }

    public RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> getConnection() {
        return simulator.connect(new TestMessageListener<H>(client) {
            public void onError(Throwable t) {

            }

            public void onMessage(H socket, byte[] bytes) {
            }

            public void onMessage(H socket, String message) {
                TestMessage obj = socket.getRemoteConnectionContext().read(message);
                getConnectionGroup().receiveRequestResponse(socket, obj.messageId, obj);

            }

            public void onPing(H socket, byte[] bytes) {

            }

            public void onPong(H socket, byte[] bytes) {

            }
        }, server.getRemoteConnectionContext(), client.getRemoteConnectionContext());
    }

    @Test(dependsOnMethods = { "testNoConnectionRequest" })
    public void testSimpleRequest() throws Exception {
        RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> connection =
                getConnection();
        try {
            Assert.assertTrue(client.isOperational());
            Assert.assertTrue(server.isOperational());
            TestRemoteRequest<H> request = client.trySubmitRequest(new TestRequestFactory<H>(0));
            request.getPromise();
            
            Assert.assertEquals(request.getPromise()
                    .getOrThrowUninterruptibly(5, TimeUnit.SECONDS), "OK");
            Assert.assertTrue(client.getRemoteRequests().isEmpty());
            Assert.assertTrue(server.getLocalRequests().isEmpty());
        } finally {
            connection.close();
        }
    }

    @Test(dependsOnMethods = { "testNoConnectionRequest" })
    public void testCallbackRequest() throws Exception {
        RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> connection =
                getConnection();
        try {
            Assert.assertTrue(client.isOperational());
            Assert.assertTrue(server.isOperational());
            TestRemoteRequest<H> request = client.trySubmitRequest(new TestRequestFactory<H>(1));
            
            Assert.assertEquals(request.getPromise().getOrThrowUninterruptibly(5, TimeUnit.SECONDS), "OK");
            for (int i = 0; i < 5 && request.getResults().size() != 3; i++) {
                Reporter.log("Wait for complete request cleanup: " + i, true);
                Thread.sleep(1000); // Wait to complete all other threads
            }
            Assert.assertEquals(request.getResults().size(), 3);
            Assert.assertTrue(client.getRemoteRequests().isEmpty());
            Assert.assertTrue(server.getLocalRequests().isEmpty());
        } finally {
            connection.close();
        }
    }

    @Test(dependsOnMethods = { "testNoConnectionRequest" })
    public void testBlockingCallbackRequest() throws Exception {
        RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> connection =
                getConnection();
        try {
            Assert.assertTrue(client.isOperational());
            Assert.assertTrue(server.isOperational());
            
            TestRemoteRequest<H> request = client.trySubmitRequest(new TestRequestFactory<H>(2));
      
            Assert.assertEquals(request.getPromise().getOrThrowUninterruptibly(15, TimeUnit.SECONDS), "OK");
            Assert.assertEquals(request.getResults().size(), 3);
            Assert.assertTrue(client.getRemoteRequests().isEmpty());
            Assert.assertTrue(server.getLocalRequests().isEmpty());
        } finally {
            connection.close();
        }
    }

    @Test(dependsOnMethods = { "testNoConnectionRequest" })
    public void testCancelRequest() throws Exception {
        RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> connection =
                getConnection();
        try {
            Assert.assertTrue(client.isOperational());
            Assert.assertTrue(server.isOperational());

            TestRemoteRequest<H> request = client.trySubmitRequest(new TestRequestFactory<H>(2) {
                public void handleCallback(H sourceConnection, TestRemoteRequest<H> request,
                        TestMessage message) {
                    request.getPromise().cancel(true);
                }
            });
            try {
                request.getPromise().thenOnResult(new ResultHandler<String>() {
                    public void handleResult(String result) {
                        Assert.fail("Canceled");
                    }
                }).thenOnException(new ExceptionHandler<Exception>() {
                    public void handleException(Exception error) {
                        Assert.assertTrue(error instanceof CancellationException);
                    }
                }).getOrThrowUninterruptibly(15, TimeUnit.SECONDS);
                Assert.fail("Not Canceled");
            } catch (CancellationException e) {
                // Expected
            }
            Assert.assertEquals(request.getResults().size(), 1);
            for (int i = 0; i < 5 && !(client.getRemoteRequests().isEmpty()
                    && server.getLocalRequests().isEmpty()); i++) {
                Reporter.log("Wait for Cancel complete: " + i, true);
                Thread.sleep(1000); // Wait to complete all other threads
            }
            Assert.assertTrue(client.getRemoteRequests().isEmpty());
            Assert.assertTrue(server.getLocalRequests().isEmpty());
        } finally {
            connection.close();
        }
    }

    @Test(dependsOnMethods = { "testNoConnectionRequest" },
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Unknown Test case number")
    public void testFailedRequest() throws Exception {
        RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>> connection =
                getConnection();
        try {

            Assert.assertTrue(client.isOperational());
            Assert.assertTrue(server.isOperational());

            TestRemoteRequest<H> request = client.trySubmitRequest(new TestRequestFactory<H>(3));
            request.getPromise().getOrThrowUninterruptibly();
            Assert.assertTrue(client.getRemoteRequests().isEmpty());
            Assert.assertTrue(server.getLocalRequests().isEmpty());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testNoConnectionRequest() throws Exception {
        Assert.assertFalse(client.isOperational());
        Assert.assertFalse(server.isOperational());
        Assert.assertNull(client.trySubmitRequest(new TestRequestFactory<H>(0)));
    }
}
