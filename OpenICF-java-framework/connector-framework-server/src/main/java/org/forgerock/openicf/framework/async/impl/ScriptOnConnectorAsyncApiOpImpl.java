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
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.ScriptOnConnectorAsyncApiOp;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ScriptContext;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public class ScriptOnConnectorAsyncApiOpImpl extends AbstractAPIOperation implements
        ScriptOnConnectorAsyncApiOp {

    private static final Log logger = Log.getLog(ScriptOnConnectorAsyncApiOpImpl.class);

    public ScriptOnConnectorAsyncApiOpImpl(
            RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public Object runScriptOnConnector(final ScriptContext request, final OperationOptions options) {
        return runScriptOnConnectorAsync(request, options).getOrThrowUninterruptibly();
    }

    public Promise<Object, RuntimeException> runScriptOnConnectorAsync(final ScriptContext request,
            final OperationOptions options) {
        Assertions.nullCheck(request, "request");

        OperationMessages.ScriptOnConnectorOpRequest.Builder requestBuilder =
                OperationMessages.ScriptOnConnectorOpRequest.newBuilder();

        requestBuilder.setScriptContext(MessagesUtil.serializeMessage(request,
                CommonObjectMessages.ScriptContext.class));

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(OperationMessages.OperationRequest
                .newBuilder().setScriptOnConnectorOpRequest(requestBuilder)));
    }

    private class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Object, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;

        public InternalRequestFactory(
                final OperationMessages.OperationRequest.Builder operationRequest) {
            this.operationRequest = operationRequest;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<MessageLite, Object, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder);
            } else {
                return null;
            }
        }

        protected CommonObjectMessages.ConnectorKey.Builder createConnectorKey() {
            return ScriptOnConnectorAsyncApiOpImpl.this.createConnectorKey();
        }

        protected ByteString createConnectorFacadeKey(final RemoteOperationContext context) {
            return ScriptOnConnectorAsyncApiOpImpl.this.createConnectorFacadeKey(context);
        }

        protected OperationMessages.OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Object, OperationMessages.ScriptOnConnectorOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<MessageLite, Object, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected OperationMessages.ScriptOnConnectorOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasScriptOnConnectorOpResponse()) {
                return message.getScriptOnConnectorOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "ScriptOnConnectorOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.ScriptOnConnectorOpResponse message) {
            if (message.hasObject()) {
                getSuccessHandler().handleResult(
                        MessagesUtil.deserializeLegacy(message.getObject()));
            } else {
                getSuccessHandler().handleResult(null);
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<ByteString, OperationMessages.ScriptOnConnectorOpRequest> createProcessor(long requestId,
            WebSocketConnectionHolder socket, OperationMessages.ScriptOnConnectorOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            AbstractLocalOperationProcessor<ByteString, OperationMessages.ScriptOnConnectorOpRequest> {

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.ScriptOnConnectorOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, ByteString result) {
            OperationMessages.ScriptOnConnectorOpResponse.Builder response =
                    OperationMessages.ScriptOnConnectorOpResponse.newBuilder();
            if (null != result) {
                response.setObject(result);
            }
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder()
                            .setScriptOnConnectorOpResponse(response));
        }

        protected ByteString executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.ScriptOnConnectorOpRequest requestMessage) {
            final ScriptContext request =
                    MessagesUtil.deserializeMessage(requestMessage.getScriptContext(),
                            ScriptContext.class);

            OperationOptions operationOptions = null;
            if (requestMessage.hasOptions()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }
            Object result = connectorFacade.runScriptOnConnector(request, operationOptions);
            if (null != result) {
                return MessagesUtil.serializeLegacy(result);
            }
            return null;
        }
    }
}
