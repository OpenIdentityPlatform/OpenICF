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
import org.forgerock.openicf.common.protobuf.ConnectorObjects;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationRequest;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.identityconnectors.common.l10n.CurrentLocale;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public abstract class AbstractRemoteOperationRequestFactory<V, R extends RemoteOperationRequest<V>>
        implements
        RemoteRequestFactory<MessageLite, R, V, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

    protected abstract CommonObjectMessages.ConnectorKey.Builder createConnectorKey();

    protected abstract ByteString createConnectorFacadeKey(final RemoteOperationContext context);

    protected abstract OperationMessages.OperationRequest.Builder createOperationRequest(
            RemoteOperationContext remoteContext);

    protected RPCMessages.RPCRequest.Builder createRPCRequest(final RemoteOperationContext context) {
        ByteString facadeKey = createConnectorFacadeKey(context);
        if (null != facadeKey) {

            final OperationMessages.OperationRequest.Builder operationBuilder =
                    createOperationRequest(context);
            operationBuilder.setConnectorKey(createConnectorKey()).setConnectorFacadeKey(facadeKey)
                    .setLocale(
                            MessagesUtil.serializeMessage(CurrentLocale.get(),
                                    CommonObjectMessages.Locale.class));
            return RPCMessages.RPCRequest.newBuilder().setOperationRequest(operationBuilder);
        } else {
            return null;
        }
    }

    public static abstract class AbstractRemoteOperationRequest<V, M extends MessageLite> extends
            RemoteOperationRequest<V> {

        public static final String OPERATION_EXPECTS_MESSAGE = "RemoteOperation[{0}] expects {1}";

        private final RPCMessages.RPCRequest.Builder request;

        public AbstractRemoteOperationRequest(
                RemoteOperationContext context,
                long requestId,
                RemoteRequestFactory.CompletionCallback<MessageLite, V, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback);
            request = requestBuilder;
        }

        protected abstract M getOperationResponseMessages(
                OperationMessages.OperationResponse message);

        protected abstract void handleOperationResponseMessages(
                WebSocketConnectionHolder sourceConnection, M message);

        protected RPCMessages.RPCRequest.Builder createOperationRequest(
                RemoteOperationContext remoteContext) {
            return request;
        }

        protected boolean handleResponseMessage(final WebSocketConnectionHolder sourceConnection,
                final MessageLite message) {
            if (message instanceof OperationMessages.OperationResponse) {
                M responseMessage =
                        getOperationResponseMessages((OperationMessages.OperationResponse) message);
                if (null != responseMessage) {
                    handleOperationResponseMessages(sourceConnection, responseMessage);
                    return true;
                }
            }
            return false;
        }
    }
}
