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

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.FailureHandler;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.SubscriptionHandler;
import org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public class ConnectorEventSubscriptionApiOpImpl extends AbstractAPIOperation implements
        ConnectorEventSubscriptionApiOp {

    private static final Log logger = Log.getLog(ConnectorEventSubscriptionApiOpImpl.class);

    public ConnectorEventSubscriptionApiOpImpl(
            final RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public SubscriptionHandler subscribe(final ObjectClass objectClass, final Filter eventFilter,
            final ResultsHandler handler, final OperationOptions operationOptions) {
        final Promise<Void, RuntimeException> promise =
                trySubscribe(objectClass, eventFilter, handler, operationOptions);

        return new SubscriptionHandler() {
            public void unsubscribe() {
                promise.cancel(true);
            }

            public void onFailure(final FailureHandler<RuntimeException> onFailure) {
                promise.onFailure(new org.forgerock.util.promise.FailureHandler<RuntimeException>() {
                    public void handleError(final RuntimeException error) {
                        if (!(error instanceof CancellationException)) {
                            onFailure.handleError(error);
                        } else {
                            logger.ok("Subscription is cancelled");
                        }
                    }
                });
            }
        };
    }

    public Promise<Void, RuntimeException> trySubscribe(final ObjectClass objectClass,
            final Filter eventFilter, final ResultsHandler handler, final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(handler, "handler");
        OperationMessages.ConnectorEventSubscriptionOpRequest.Builder requestBuilder =
                OperationMessages.ConnectorEventSubscriptionOpRequest.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue());
        if (eventFilter != null) {
            requestBuilder.setEventFilter(MessagesUtil.serializeLegacy(eventFilter));
        }

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(OperationMessages.OperationRequest
                .newBuilder().setConnectorEventSubscriptionOpRequest(requestBuilder), handler));
    }

    private class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Void, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;
        final ResultsHandler handler;

        public InternalRequestFactory(
                final OperationMessages.OperationRequest.Builder operationRequest,
                final ResultsHandler handler) {
            this.operationRequest = operationRequest;
            this.handler = handler;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<MessageLite, Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder, handler);
            } else {
                return null;
            }
        }

        protected CommonObjectMessages.ConnectorKey.Builder createConnectorKey() {
            return ConnectorEventSubscriptionApiOpImpl.this.createConnectorKey();
        }

        protected ByteString createConnectorFacadeKey(final RemoteOperationContext context) {
            return ConnectorEventSubscriptionApiOpImpl.this.createConnectorFacadeKey(context);
        }

        protected OperationMessages.OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Void, OperationMessages.ConnectorEventSubscriptionOpResponse> {

        final ResultsHandler handler;
        final AtomicBoolean confirmed = new AtomicBoolean(Boolean.FALSE);

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<MessageLite, Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder, final ResultsHandler handler) {
            super(context, requestId, completionCallback, requestBuilder);
            this.handler = handler;

        }

        protected OperationMessages.ConnectorEventSubscriptionOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasConnectorEventSubscriptionOpResponse()) {
                return message.getConnectorEventSubscriptionOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(),
                        "ConnectorEventSubscriptionOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.ConnectorEventSubscriptionOpResponse message) {
            if (null != handler && message.hasConnectorObject()) {
                ConnectorObject delta =
                        MessagesUtil.deserializeMessage(message.getConnectorObject(),
                                ConnectorObject.class);
                if (!handler.handle(delta) && !getPromise().isDone()) {
                    getFailureHandler().handleError(
                            new ConnectorException("ResultsHandler stopped processing results"));
                    tryCancelRemote(getConnectionContext(), getRequestId());
                }
            }
            if (!message.hasConnectorObject()
                    && confirmed.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
                logger.ok("Subscription has been made successfully on remote side");
            }
        }
    }

    // ----

    public static OperationExecutorFactory.OperationExecutor<OperationMessages.ConnectorEventSubscriptionOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.ConnectorEventSubscriptionOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            OperationExecutorFactory.AbstractLocalOperationProcessor<ConnectorObject, OperationMessages.ConnectorEventSubscriptionOpRequest> {

        private final AtomicBoolean doContinue = new AtomicBoolean(Boolean.TRUE);
        private SubscriptionHandler subscriptionHandler = null;

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.ConnectorEventSubscriptionOpRequest message) {
            super(requestId, socket, message);
            stickToConnection = false;
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, ConnectorObject result) {
            OperationMessages.ConnectorEventSubscriptionOpResponse.Builder builder =
                    OperationMessages.ConnectorEventSubscriptionOpResponse.newBuilder();
            if (null != result) {
                builder.setConnectorObject(MessagesUtil.serializeMessage(result,
                        CommonObjectMessages.ConnectorObject.class));
            }

            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder()
                            .setConnectorEventSubscriptionOpResponse(builder));
        }

        protected ConnectorObject executeOperation(final ConnectorFacade connectorFacade,
                final OperationMessages.ConnectorEventSubscriptionOpRequest requestMessage) {

            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());

            Filter token = null;
            if (requestMessage.hasEventFilter()) {
                token = MessagesUtil.deserializeLegacy(requestMessage.getEventFilter());
            }

            OperationOptions operationOptions = null;
            if (requestMessage.hasOptions()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }

            subscriptionHandler =
                    connectorFacade.subscribe(objectClass, token, new ResultsHandler() {
                        public boolean handle(final ConnectorObject delta) {
                            tryHandleResult(delta);
                            return doContinue.get();
                        }
                    }, operationOptions);

            subscriptionHandler.onFailure(new FailureHandler<RuntimeException>() {
                public void handleError(final RuntimeException error) {
                    tryHandleError(error);
                }
            });

            return null;
        }

        protected boolean tryCancel() {
            doContinue.set(Boolean.FALSE);
            subscriptionHandler.unsubscribe();
            return super.tryCancel();
        }
    }

}
