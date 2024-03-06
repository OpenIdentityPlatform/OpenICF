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
import org.forgerock.openicf.framework.async.DeleteAsyncApiOp;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import com.google.protobuf.ByteString;

public class DeleteAsyncApiOpImpl extends AbstractAPIOperation implements DeleteAsyncApiOp {

    private static final Log logger = Log.getLog(DeleteAsyncApiOpImpl.class);

    public DeleteAsyncApiOpImpl(
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction,timeout);
    }

    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        asyncTimeout(deleteAsync(objectClass, uid, options));
    }

    public Promise<Void, RuntimeException> deleteAsync(final ObjectClass objectClass,
            final Uid uid, final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        if (ObjectClass.ALL.equals(objectClass)) {
            throw new UnsupportedOperationException(
                    "Operation is not allowed on __ALL__ object class");
        }
        Assertions.nullCheck(uid, "uid");

        OperationMessages.DeleteOpRequest.Builder requestBuilder =
                OperationMessages.DeleteOpRequest.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue());

        requestBuilder.setUid(MessagesUtil.serializeMessage(uid, CommonObjectMessages.Uid.class));

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationMessages.OperationRequest.newBuilder().setDeleteOpRequest(requestBuilder)));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Void, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationMessages.OperationRequest.Builder operationRequest) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder);
            } else {
                return null;
            }
        }

        protected OperationMessages.OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Void, OperationMessages.DeleteOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected OperationMessages.DeleteOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasDeleteOpResponse()) {
                return message.getDeleteOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "DeleteOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.DeleteOpResponse message) {
            getResultHandler().handleResult(null);
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<Void, OperationMessages.DeleteOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.DeleteOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor extends
            AbstractLocalOperationProcessor<Void, OperationMessages.DeleteOpRequest> {

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.DeleteOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, Void result) {
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setDeleteOpResponse(
                            OperationMessages.DeleteOpResponse.getDefaultInstance()));
        }

        protected Void executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.DeleteOpRequest requestMessage) {
            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());
            final Uid uid = MessagesUtil.deserializeMessage(requestMessage.getUid(), Uid.class);

            OperationOptions operationOptions = null;
            if (!requestMessage.getOptions().isEmpty()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }
            connectorFacade.delete(objectClass, uid, operationOptions);
            return null;
        }
    }
}
