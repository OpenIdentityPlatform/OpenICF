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

import java.util.Set;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages.UpdateOpRequest.UpdateType;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.UpdateAsyncApiOp;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.impl.api.local.operations.UpdateImpl;

import com.google.protobuf.ByteString;

public class UpdateAsyncApiOpImpl extends AbstractAPIOperation implements UpdateAsyncApiOp {

    private static final Log logger = Log.getLog(UpdateAsyncApiOpImpl.class);

    public UpdateAsyncApiOpImpl(
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction, timeout);
    }

    public Uid update(final ObjectClass objectClass, final Uid uid,
            final Set<Attribute> replaceAttributes, final OperationOptions options) {
        return asyncTimeout(updateAsync(objectClass, uid, replaceAttributes, options));
    }

    public Uid addAttributeValues(final ObjectClass objectClass, final Uid uid,
            final Set<Attribute> valuesToAdd, final OperationOptions options) {
        return asyncTimeout(addAttributeValuesAsync(objectClass, uid, valuesToAdd, options));
    }

    public Uid removeAttributeValues(final ObjectClass objectClass, final Uid uid,
            final Set<Attribute> valuesToRemove, final OperationOptions options) {
        return asyncTimeout(removeAttributeValuesAsync(objectClass, uid, valuesToRemove, options));
    }

    public Promise<Uid, RuntimeException> updateAsync(final ObjectClass objectClass, final Uid uid,
            final Set<Attribute> replaceAttributes, final OperationOptions options) {
        return doUpdate(objectClass, uid, UpdateType.REPLACE, replaceAttributes, options);
    }

    public Promise<Uid, RuntimeException> addAttributeValuesAsync(final ObjectClass objectClass,
            final Uid uid, final Set<Attribute> valuesToAdd, final OperationOptions options) {
        return doUpdate(objectClass, uid, UpdateType.ADD, valuesToAdd, options);
    }

    public Promise<Uid, RuntimeException> removeAttributeValuesAsync(final ObjectClass objectClass,
            final Uid uid, final Set<Attribute> valuesToRemove, final OperationOptions options) {
        return doUpdate(objectClass, uid, UpdateType.REMOVE, valuesToRemove, options);
    }

    public Promise<Uid, RuntimeException> doUpdate(final ObjectClass objectClass, final Uid uid,
            final UpdateType updateType, final Set<Attribute> replaceAttributes,
            final OperationOptions options) {
        UpdateImpl.validateInput(objectClass, uid, replaceAttributes, !UpdateType.REPLACE
                .equals(updateType));
        OperationMessages.UpdateOpRequest.Builder requestBuilder =
                OperationMessages.UpdateOpRequest.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue()).setUid(
                        MessagesUtil.serializeMessage(uid, CommonObjectMessages.Uid.class))
                        .setUpdateType(updateType);

        requestBuilder.setReplaceAttributes(MessagesUtil.serializeLegacy(replaceAttributes));

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationMessages.OperationRequest.newBuilder().setUpdateOpRequest(requestBuilder)));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Uid, InternalRequest> {
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
                final CompletionCallback<Uid, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

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
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Uid, OperationMessages.UpdateOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Uid, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected OperationMessages.UpdateOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasUpdateOpResponse()) {
                return message.getUpdateOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "UpdateOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.UpdateOpResponse message) {
            if (message.hasUid()) {
                getResultHandler().handleResult(
                        MessagesUtil.deserializeMessage(message.getUid(), Uid.class));
            } else {
                getResultHandler().handleResult(null);
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<Uid, OperationMessages.UpdateOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.UpdateOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor extends
            AbstractLocalOperationProcessor<Uid, OperationMessages.UpdateOpRequest> {

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.UpdateOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, Uid result) {

            OperationMessages.UpdateOpResponse.Builder response =
                    OperationMessages.UpdateOpResponse.newBuilder();
            if (null != result) {
                response.setUid(MessagesUtil.serializeMessage(result,
                        CommonObjectMessages.Uid.class));
            }
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setUpdateOpResponse(response));
        }

        protected Uid executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.UpdateOpRequest requestMessage) {

            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());
            final Uid uid = MessagesUtil.deserializeMessage(requestMessage.getUid(), Uid.class);
            final Set<Attribute> attributes =
                    MessagesUtil.deserializeLegacy(requestMessage.getReplaceAttributes());

            OperationOptions operationOptions = null;
            if (!requestMessage.getOptions().isEmpty()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }

            switch (requestMessage.getUpdateType()) {
            case REPLACE:
                return connectorFacade.update(objectClass, uid, attributes, operationOptions);
            case ADD:
                return connectorFacade.addAttributeValues(objectClass, uid, attributes,
                        operationOptions);
            case REMOVE:
                return connectorFacade.removeAttributeValues(objectClass, uid, attributes,
                        operationOptions);
            default:
                logger.info("Invalid UpdateOpRequest#UpdateType Request:{0}", getRequestId());
            }

            return null;
        }
    }

}
