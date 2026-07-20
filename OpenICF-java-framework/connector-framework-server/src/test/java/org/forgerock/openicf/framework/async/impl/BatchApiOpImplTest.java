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
package org.forgerock.openicf.framework.async.impl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.common.protobuf.OperationMessages.BatchOpResult;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.protobuf.ByteString;

/**
 * Tests that a batch operation whose batch-token response is never delivered
 * fails the caller with a {@link ConnectorException} when the
 * CompletionListener times out, instead of leaving the caller blocked forever.
 */
public class BatchApiOpImplTest {

    private static final ConnectorKey CONNECTOR_KEY =
            new ConnectorKey("testbundle", "1.0", "test.Connector");

    private static class RecordingObserver implements Observer<BatchResult> {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

        public void onCompleted() {
            completed.set(true);
        }

        public void onError(Throwable e) {
            error.set(e);
        }

        public void onNext(BatchResult batchResult) {
        }
    }

    private static final WebSocketConnectionHolder FAKE_SOCKET = new WebSocketConnectionHolder() {

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

    /**
     * Submits the request over the fake socket and immediately delivers a
     * "complete" BatchOpResult carrying no batch token, simulating a lost
     * token response.
     */
    private static class NoTokenRequestDistributor
            implements
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

        public <R extends RemoteRequest<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>, V, E extends Exception> R trySubmitRequest(
                RemoteRequestFactory<R, V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> requestFactory) {
            R request =
                    requestFactory.createRemoteRequest(null, 1L,
                            new RemoteRequestFactory.CompletionCallback<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>() {
                                public void complete(
                                        RemoteRequest<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> request) {
                                }
                            });
            try {
                request.getSendFunction().apply(FAKE_SOCKET);
            } catch (Exception e) {
                throw ConnectorException.wrap(e);
            }
            request.handleIncomingMessage(FAKE_SOCKET, OperationResponse.newBuilder()
                    .setBatchOpResult(BatchOpResult.newBuilder().setComplete(true)).build());
            return request;
        }

        public boolean isOperational() {
            return true;
        }
    }

    @Test(timeOut = 30000)
    public void testExecuteBatchFailsWhenNoTokenArrives() {
        BatchApiOpImpl operation =
                new BatchApiOpImpl(new NoTokenRequestDistributor(), CONNECTOR_KEY,
                        new Function<RemoteOperationContext, ByteString, RuntimeException>() {
                            public ByteString apply(RemoteOperationContext context) {
                                return ByteString.EMPTY;
                            }
                        }, APIOperation.NO_TIMEOUT);

        List<BatchTask> tasks =
                Collections.<BatchTask> singletonList(new DeleteBatchTask(ObjectClass.ACCOUNT,
                        new Uid("1"), new OperationOptionsBuilder().build()));
        RecordingObserver observer = new RecordingObserver();

        long start = System.currentTimeMillis();
        try {
            operation.executeBatch(tasks, observer, new OperationOptionsBuilder().build());
            Assert.fail("executeBatch must fail when no batch token arrives");
        } catch (ConnectorException expected) {
            Assert.assertTrue(expected.getMessage().contains("batch token"),
                    "Unexpected failure: " + expected.getMessage());
        }
        long elapsed = System.currentTimeMillis() - start;

        // CompletionListener gives up ~5s after the request was created.
        Assert.assertTrue(elapsed < 20000, "Failure took too long: " + elapsed + "ms");
        Assert.assertNotNull(observer.error.get(), "observer.onError must be called");
        Assert.assertTrue(observer.error.get() instanceof ConnectorException);
        Assert.assertFalse(observer.completed.get(),
                "observer.onCompleted must not be called without a token");
    }
}
