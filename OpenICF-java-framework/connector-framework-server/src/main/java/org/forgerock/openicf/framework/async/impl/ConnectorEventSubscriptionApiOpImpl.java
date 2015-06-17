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
import org.forgerock.openicf.common.protobuf.OperationMessages.ConnectorEventSubscriptionOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.ConnectorEventSubscriptionOpResponse;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.SuccessHandler;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.filter.Filter;

import com.google.protobuf.ByteString;

public class ConnectorEventSubscriptionApiOpImpl extends AbstractAPIOperation implements
        ConnectorEventSubscriptionApiOp {

    private static final Log logger = Log.getLog(ConnectorEventSubscriptionApiOpImpl.class);

    public ConnectorEventSubscriptionApiOpImpl(
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public Subscription subscribe(final ObjectClass objectClass, final Filter eventFilter,
            final Observer<ConnectorObject> handler, final OperationOptions operationOptions) {
        final Promise<Void, RuntimeException> promise =
                trySubscribe(objectClass, eventFilter, handler, operationOptions).onFailure(
                        new FailureHandler<RuntimeException>() {
                            public void handleError(RuntimeException error) {
                                if (!(error instanceof CancellationException)) {
                                    handler.onError(error);
                                }
                            }
                        }).onSuccess(new SuccessHandler<Void>() {
                    public void handleResult(Void result) {
                        handler.onCompleted();
                    }
                });

        return new Subscription() {
            public void close() {
                promise.cancel(true);
            }

            public boolean isUnsubscribed() {
                return promise.isDone();
            }
        };
    }

    public Promise<Void, RuntimeException> trySubscribe(final ObjectClass objectClass,
            final Filter eventFilter, final Observer<ConnectorObject> handler,
            final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(handler, "handler");
        ConnectorEventSubscriptionOpRequest.Builder requestBuilder =
                ConnectorEventSubscriptionOpRequest.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue());
        if (eventFilter != null) {
            requestBuilder.setEventFilter(MessagesUtil.serializeLegacy(eventFilter));
        }

        if (options != null && !options.getOptions().isEmpty()) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationRequest.newBuilder()
                        .setConnectorEventSubscriptionOpRequest(requestBuilder), handler));
    }

    private class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Void, InternalRequest> {
        private final OperationRequest.Builder operationRequest;
        final Observer<ConnectorObject> handler;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationRequest.Builder operationRequest,
                final Observer<ConnectorObject> handler) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
            this.handler = handler;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder, handler);
            } else {
                return null;
            }
        }

        protected OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Void, ConnectorEventSubscriptionOpResponse> {

        final Observer<ConnectorObject> handler;
        final AtomicBoolean confirmed = new AtomicBoolean(Boolean.FALSE);

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder,
                final Observer<ConnectorObject> handler) {
            super(context, requestId, completionCallback, requestBuilder);
            this.handler = handler;

        }

        protected ConnectorEventSubscriptionOpResponse getOperationResponseMessages(
                OperationResponse message) {
            if (message.hasConnectorEventSubscriptionOpResponse()) {
                return message.getConnectorEventSubscriptionOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(),
                        "ConnectorEventSubscriptionOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                ConnectorEventSubscriptionOpResponse message) {
            if (null != handler && message.hasConnectorObject()) {
                ConnectorObject delta =
                        MessagesUtil.deserializeMessage(message.getConnectorObject(),
                                ConnectorObject.class);
                try {
                    handler.onNext(delta);
                } catch (Throwable t) {
                    if (!getPromise().isDone()) {
                        getFailureHandler()
                                .handleError(
                                        new ConnectorException(
                                                "ResultsHandler stopped processing results"));
                        tryCancelRemote(getConnectionContext(), getRequestId());
                    }
                }
            } else if (message.hasCompleted() && message.getCompleted()) {
                getSuccessHandler().handleResult(null);
                logger.ok("Subscription is completed");
            } else if (confirmed.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
                logger.ok("Subscription has been made successfully on remote side");
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<ConnectorEventSubscriptionOpResponse, ConnectorEventSubscriptionOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            ConnectorEventSubscriptionOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            AbstractLocalOperationProcessor<ConnectorEventSubscriptionOpResponse, ConnectorEventSubscriptionOpRequest> {
        private Subscription subscription = null;

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                ConnectorEventSubscriptionOpRequest message) {
            super(requestId, socket, message);
            stickToConnection = false;
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, ConnectorEventSubscriptionOpResponse result) {
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationResponse.newBuilder().setConnectorEventSubscriptionOpResponse(result));
        }

        protected ConnectorEventSubscriptionOpResponse executeOperation(
                final ConnectorFacade connectorFacade,
                final ConnectorEventSubscriptionOpRequest requestMessage) {

            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());

            Filter token = null;
            if (requestMessage.hasEventFilter()) {
                token = MessagesUtil.deserializeLegacy(requestMessage.getEventFilter());
            }

            OperationOptions operationOptions = null;
            if (requestMessage.hasOptions()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }

            subscription =
                    connectorFacade.subscribe(objectClass, token, new Observer<ConnectorObject>() {

                        public void onCompleted() {
                            tryHandleResult(ConnectorEventSubscriptionOpResponse.newBuilder()
                                    .setCompleted(Boolean.TRUE).build());
                        }

                        public void onError(Throwable error) {
                            try {
                                final byte[] responseMessage =
                                        MessagesUtil.createErrorResponse(getRequestId(), error)
                                                .build().toByteArray();
                                trySendBytes(responseMessage, true);
                            } catch (Throwable t) {
                                logger.ok(t,
                                        "Operation encountered an exception and failed to send the exception response");
                            }
                        }

                        public void onNext(ConnectorObject syncDelta) {
                            if (null != syncDelta) {
                                tryHandleResult(ConnectorEventSubscriptionOpResponse
                                        .newBuilder()
                                        .setConnectorObject(
                                                MessagesUtil.serializeMessage(syncDelta,
                                                        CommonObjectMessages.ConnectorObject.class))
                                        .build());
                            }
                        }

                    }, operationOptions);

            return ConnectorEventSubscriptionOpResponse.getDefaultInstance();
        }

        protected boolean tryCancel() {
            subscription.close();
            return super.tryCancel();
        }
    }

}
