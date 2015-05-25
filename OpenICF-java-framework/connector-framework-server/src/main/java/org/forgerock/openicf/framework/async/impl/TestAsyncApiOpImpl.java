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

package org.forgerock.openicf.framework.async.impl;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationResponse;
import org.forgerock.openicf.common.protobuf.OperationMessages.TestOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.TestOpResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.TestAsyncApiOp;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public class TestAsyncApiOpImpl extends AbstractAPIOperation implements TestAsyncApiOp {

    private static final Log logger = Log.getLog(TestAsyncApiOpImpl.class);

    public TestAsyncApiOpImpl(
            RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public void test() {
        testAsync().getOrThrowUninterruptibly();
    }

    public Promise<Void, RuntimeException> testAsync() {
        return submitRequest(new InternalRequestFactory(OperationRequest.newBuilder()
                .setTestOpRequest(TestOpRequest.getDefaultInstance())));
    }

    private class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Void, InternalRequest> {
        private final OperationRequest.Builder operationRequest;

        public InternalRequestFactory(final OperationRequest.Builder operationRequest) {
            this.operationRequest = operationRequest;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<MessageLite, Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder);
            } else {
                return null;
            }
        }

        protected CommonObjectMessages.ConnectorKey.Builder createConnectorKey() {
            return TestAsyncApiOpImpl.this.createConnectorKey();
        }

        protected ByteString createConnectorFacadeKey(final RemoteOperationContext context) {
            return TestAsyncApiOpImpl.this.createConnectorFacadeKey(context);
        }

        protected OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Void, TestOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<MessageLite, Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected TestOpResponse getOperationResponseMessages(OperationResponse message) {
            if (message.hasTestOpResponse()) {
                return message.getTestOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "TestOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                TestOpResponse message) {
            getSuccessHandler().handleResult(null);
        }

        public String toString() {
            return "Test Request {}" + getRequestId();
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<Void, TestOpRequest> createProcessor(long requestId,
            WebSocketConnectionHolder socket, TestOpRequest message) {
        return new TestLocalOperationProcessor(requestId, socket, message);
    }

    private static class TestLocalOperationProcessor extends
            AbstractLocalOperationProcessor<Void, TestOpRequest> {

        protected TestLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                TestOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, Void result) {
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationResponse.newBuilder().setTestOpResponse(
                            TestOpResponse.getDefaultInstance()));
        }

        protected Void executeOperation(ConnectorFacade connectorFacade,
                TestOpRequest requestMessage) {
            connectorFacade.test();
            return null;
        }
    }
}
