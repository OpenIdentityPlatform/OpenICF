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
import org.forgerock.openicf.framework.async.GetAsyncApiOp;
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
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public class GetAsyncApiOpImpl extends AbstractAPIOperation implements GetAsyncApiOp {

    private static final Log logger = Log.getLog(GetAsyncApiOpImpl.class);

    public GetAsyncApiOpImpl(
            RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public ConnectorObject getObject(final ObjectClass objectClass, final Uid uid,
            final OperationOptions options) {
        return getObjectAsync(objectClass, uid, options).getOrThrowUninterruptibly();
    }

    public Promise<ConnectorObject, RuntimeException> getObjectAsync(final ObjectClass objectClass,
            final Uid uid, final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        if (ObjectClass.ALL.equals(objectClass)) {
            throw new UnsupportedOperationException(
                    "Operation is not allowed on __ALL__ object class");
        }
        Assertions.nullCheck(uid, "uid");

        OperationMessages.GetOpRequest.Builder requestBuilder =
                OperationMessages.GetOpRequest.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue());

        requestBuilder.setUid(MessagesUtil.serializeMessage(uid,
                CommonObjectMessages.Uid.class));

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(OperationMessages.OperationRequest
                .newBuilder().setGetOpRequest(requestBuilder)));
    }

    private class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<ConnectorObject, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;

        public InternalRequestFactory(
                final OperationMessages.OperationRequest.Builder operationRequest) {
            this.operationRequest = operationRequest;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<MessageLite, ConnectorObject, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder);
            } else {
                return null;
            }
        }

        protected CommonObjectMessages.ConnectorKey.Builder createConnectorKey() {
            return GetAsyncApiOpImpl.this.createConnectorKey();
        }

        protected ByteString createConnectorFacadeKey(final RemoteOperationContext context) {
            return GetAsyncApiOpImpl.this.createConnectorFacadeKey(context);
        }

        protected OperationMessages.OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<ConnectorObject, OperationMessages.GetOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<MessageLite, ConnectorObject, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected OperationMessages.GetOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasGetOpResponse()) {
                return message.getGetOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "GetOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.GetOpResponse message) {
            if (message.hasConnectorObject()) {
                getSuccessHandler().handleResult(
                        MessagesUtil.<ConnectorObject> deserializeLegacy(message
                                .getConnectorObject()));
            } else {
                getSuccessHandler().handleResult(null);
            }
        }
    }

    // -------

    public static OperationExecutorFactory.OperationExecutor<OperationMessages.GetOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket, OperationMessages.GetOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            OperationExecutorFactory.AbstractLocalOperationProcessor<ConnectorObject, OperationMessages.GetOpRequest> {

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.GetOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, ConnectorObject result) {
            OperationMessages.GetOpResponse.Builder response =
                    OperationMessages.GetOpResponse.newBuilder();
            if (null != result) {
                response.setConnectorObject(MessagesUtil.serializeLegacy(result));
            }
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setGetOpResponse(response));
        }

        protected ConnectorObject executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.GetOpRequest requestMessage) {
            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());
            final Uid uid =
                    MessagesUtil.deserializeMessage(requestMessage.getUid(), Uid.class);

            OperationOptions operationOptions = null;
            if (requestMessage.hasOptions()) {
                operationOptions =
                        MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }
            return connectorFacade.getObject(objectClass, uid, operationOptions);
        }
    }
}
