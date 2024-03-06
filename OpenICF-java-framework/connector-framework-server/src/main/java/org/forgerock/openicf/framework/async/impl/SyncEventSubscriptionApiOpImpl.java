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
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationResponse;
import org.forgerock.openicf.common.protobuf.OperationMessages.SyncEventSubscriptionOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.SyncEventSubscriptionOpResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncToken;

import com.google.protobuf.ByteString;

public class SyncEventSubscriptionApiOpImpl extends AbstractAPIOperation implements
        SyncEventSubscriptionApiOp {

    private static final Log logger = Log.getLog(SyncEventSubscriptionApiOpImpl.class);

    public SyncEventSubscriptionApiOpImpl(
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction, timeout);
    }

    public Subscription subscribe(final ObjectClass objectClass, final SyncToken token,
            final Observer<SyncDelta> handler, final OperationOptions operationOptions) {
        final Promise<Void, RuntimeException> promise =
                trySubscribe(objectClass, token, handler, operationOptions).thenOnException(
                        new ExceptionHandler<RuntimeException>() {
                            public void handleException(RuntimeException error) {
                                if (!(error instanceof CancellationException)) {
                                    handler.onError(error);
                                }
                            }
                        }).thenOnResult(new ResultHandler<Void>() {
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

            public Object getReturnValue() {
                return null;
            }
        };
    }

    public Promise<Void, RuntimeException> trySubscribe(final ObjectClass objectClass,
            final SyncToken token, final Observer<SyncDelta> handler, final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(handler, "handler");
        SyncEventSubscriptionOpRequest.Builder requestBuilder =
                SyncEventSubscriptionOpRequest.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue());
        if (token != null) {
            requestBuilder.setToken(MessagesUtil.serializeMessage(token,
                    CommonObjectMessages.SyncToken.class));
        }

        if (options != null && !options.getOptions().isEmpty()) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationRequest.newBuilder().setSyncEventSubscriptionOpRequest(requestBuilder),
                handler));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Void, InternalRequest> {
        private final OperationRequest.Builder operationRequest;
        final Observer<SyncDelta> handler;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationRequest.Builder operationRequest, final Observer<SyncDelta> handler) {
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
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Void, SyncEventSubscriptionOpResponse> {

        final Observer<SyncDelta> handler;
        final AtomicBoolean confirmed = new AtomicBoolean(Boolean.FALSE);

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Void, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder,
                final Observer<SyncDelta> handler) {
            super(context, requestId, completionCallback, requestBuilder);
            this.handler = handler;

        }

        protected SyncEventSubscriptionOpResponse getOperationResponseMessages(
                OperationResponse message) {
            if (message.hasSyncEventSubscriptionOpResponse()) {
                return message.getSyncEventSubscriptionOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(),
                        "SyncEventSubscriptionOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                SyncEventSubscriptionOpResponse message) {
            if (null != handler && message.hasSyncDelta()) {
                SyncDelta delta =
                        MessagesUtil.deserializeMessage(message.getSyncDelta(), SyncDelta.class);
                try {
                    handler.onNext(delta);
                } catch (Throwable t) {
                    if (!getPromise().isDone()) {
                        getExceptionHandler().handleException(
                                new ConnectorException(
                                        "SyncResultsHandler stopped processing results"));
                        tryCancelRemote(getConnectionContext(), getRequestId());
                    }
                }
            } else if (message.getCompleted()) {
                getResultHandler().handleResult(null);
                logger.ok("Subscription is completed");
            } else if (confirmed.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
                logger.ok("Subscription has been made successfully on remote side");
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<SyncEventSubscriptionOpResponse, SyncEventSubscriptionOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket, SyncEventSubscriptionOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            AbstractLocalOperationProcessor<SyncEventSubscriptionOpResponse, SyncEventSubscriptionOpRequest> {

        private Subscription subscription = null;

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                SyncEventSubscriptionOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, SyncEventSubscriptionOpResponse result) {

            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationResponse.newBuilder().setSyncEventSubscriptionOpResponse(result));
        }

        public void execute(final ConnectorFacade connectorFacade) {
            try {
                tryHandleResult(executeOperation(connectorFacade, requestMessage));
            } catch (RuntimeException error) {
                handleException(error);
            } catch (Throwable t) {
                handleException(new ConnectorException(t.getMessage(), t));
            }
        }

        protected SyncEventSubscriptionOpResponse executeOperation(
                final ConnectorFacade connectorFacade,
                final SyncEventSubscriptionOpRequest requestMessage) {

            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());

            SyncToken token = null;
            if (requestMessage.hasToken()) {
                token = MessagesUtil.deserializeMessage(requestMessage.getToken(), SyncToken.class);
            }

            OperationOptions operationOptions = null;
            if (!requestMessage.getOptions().isEmpty()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }

            subscription = connectorFacade.subscribe(objectClass, token, new Observer<SyncDelta>() {

                public void onCompleted() {
                    handleResult(SyncEventSubscriptionOpResponse.newBuilder().setCompleted(
                            Boolean.TRUE).build());
                }

                public void onError(Throwable error) {
                    if (error instanceof RuntimeException) {
                        handleException((RuntimeException) error);
                    } else {
                        handleException(new ConnectorException(error.getMessage(), error));
                    }
                }

                public void onNext(SyncDelta syncDelta) {
                    if (null != syncDelta) {
                        tryHandleResult(SyncEventSubscriptionOpResponse.newBuilder().setSyncDelta(
                                MessagesUtil.serializeMessage(syncDelta,
                                        CommonObjectMessages.SyncDelta.class)).build());
                    }
                }

            }, operationOptions);

            return SyncEventSubscriptionOpResponse.getDefaultInstance();
        }

        protected boolean tryCancel() {
            subscription.close();
            return super.tryCancel();
        }
    }

}
