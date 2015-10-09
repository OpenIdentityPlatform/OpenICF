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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.SyncAsyncApiOp;
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
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;

import com.google.protobuf.ByteString;

public class SyncAsyncApiOpImpl extends AbstractAPIOperation implements SyncAsyncApiOp {

    private static final Log logger = Log.getLog(SyncAsyncApiOpImpl.class);

    public SyncAsyncApiOpImpl(
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public SyncToken getLatestSyncToken(final ObjectClass objectClass) {
        return getLatestSyncTokenAsync(objectClass).getOrThrowUninterruptibly();
    }

    public SyncToken sync(final ObjectClass objectClass, final SyncToken token,
            final SyncResultsHandler handler, final OperationOptions options) {
        return syncAsync(objectClass, token, handler, options).getOrThrowUninterruptibly();
    }

    public Promise<SyncToken, RuntimeException> getLatestSyncTokenAsync(
            final ObjectClass objectClass) {
        Assertions.nullCheck(objectClass, "objectClass");
        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationMessages.OperationRequest.newBuilder().setSyncOpRequest(
                        OperationMessages.SyncOpRequest.newBuilder().setLatestSyncToken(
                                OperationMessages.SyncOpRequest.LatestSyncToken.newBuilder()
                                        .setObjectClass(objectClass.getObjectClassValue()))), null));
    }

    public Promise<SyncToken, RuntimeException> syncAsync(final ObjectClass objectClass,
            final SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(handler, "handler");
        OperationMessages.SyncOpRequest.Sync.Builder requestBuilder =
                OperationMessages.SyncOpRequest.Sync.newBuilder().setObjectClass(
                        objectClass.getObjectClassValue());
        if (token != null) {
            requestBuilder.setToken(MessagesUtil.serializeMessage(token,
                    CommonObjectMessages.SyncToken.class));
        }

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationMessages.OperationRequest.newBuilder().setSyncOpRequest(
                        OperationMessages.SyncOpRequest.newBuilder().setSync(requestBuilder)),
                handler));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<SyncToken, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;
        final SyncResultsHandler handler;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationMessages.OperationRequest.Builder operationRequest,
                final SyncResultsHandler handler) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
            this.handler = handler;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<SyncToken, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder, handler);
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
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<SyncToken, OperationMessages.SyncOpResponse> {

        final SyncResultsHandler handler;
        private final AtomicLong sequence = new AtomicLong(0);
        private long expectedResult = -1;
        private SyncToken result = null;
        private final ConcurrentMap<Long, SyncDelta> buffer = new ConcurrentSkipListMap<Long, SyncDelta>();

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<SyncToken, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder,
                final SyncResultsHandler handler) {
            super(context, requestId, completionCallback, requestBuilder);
            this.handler = handler;

        }

        protected OperationMessages.SyncOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasSyncOpResponse()) {
                return message.getSyncOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "SyncOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.SyncOpResponse message) {
            if (message.hasLatestSyncToken()) {
                if (message.getLatestSyncToken().hasSyncToken()) {
                    getResultHandler().handleResult(
                            MessagesUtil.deserializeMessage(message.getLatestSyncToken()
                                    .getSyncToken(), SyncToken.class));
                } else {
                    getResultHandler().handleResult(null);
                }
            } else if (message.hasSync()) {
                logger.ok("SyncOp Response received in sequence:{0} of {1}", message.getSync()
                        .getSequence(), sequence.get());
                if (message.getSync().hasSyncDelta()) {
                    try {
                        buffer.put(message.getSync().getSequence(), MessagesUtil
                                .deserializeMessage(message.getSync().getSyncDelta(),
                                        SyncDelta.class));
                    } finally {
                        sequence.incrementAndGet();
                    }
                } else {
                    try {
                        boolean doContinue = true;
                        long startTime = System.currentTimeMillis();
                        expectedResult = message.getSync().getSequence();
                        long next = 1;
                        do {
                            for (Long key: buffer.keySet()) {
                                if (key != next){
                                    continue;
                                }
                                next++;
                                if (!handler.handle(buffer.remove(key))) {
                                    doContinue = false;
                                    break;
                                }
                            }
                            if (System.currentTimeMillis() - startTime > 60000L){
                                doContinue = false;
                            }
                        } while (doContinue && (sequence.get() != expectedResult || !buffer.isEmpty()));
                        buffer.clear();
                    } finally {
                        if (message.getSync().hasSyncToken()) {
                            result =
                                    MessagesUtil.deserializeMessage(message.getSync().getSyncToken(),
                                            SyncToken.class);
                        }

                        if (expectedResult == 0 || sequence.get() == expectedResult) {
                            getResultHandler().handleResult(result);
                        }
                    }
                }
                if (expectedResult > 0 && sequence.get() != expectedResult) {
                    getResultHandler().handleResult(result);
                }
            } else {
                logger.info("Invalid SyncOpResponse Response:{0}", getRequestId());
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<OperationMessages.SyncOpResponse.Builder, OperationMessages.SyncOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.SyncOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            AbstractLocalOperationProcessor<OperationMessages.SyncOpResponse.Builder, OperationMessages.SyncOpRequest> {

        private final AtomicBoolean doContinue = new AtomicBoolean(Boolean.TRUE);
        final AtomicLong sequence = new AtomicLong(0);

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.SyncOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext,
                OperationMessages.SyncOpResponse.Builder result) {
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setSyncOpResponse(result));
        }

        protected OperationMessages.SyncOpResponse.Builder executeOperation(
                ConnectorFacade connectorFacade, OperationMessages.SyncOpRequest requestMessage) {
            if (requestMessage.hasLatestSyncToken()) {
                final ObjectClass objectClass =
                        new ObjectClass(requestMessage.getLatestSyncToken().getObjectClass());
                SyncToken token = connectorFacade.getLatestSyncToken(objectClass);

                // Enable returnNullTest
                OperationMessages.SyncOpResponse.LatestSyncToken.Builder builder =
                        OperationMessages.SyncOpResponse.LatestSyncToken.newBuilder();
                if (null != token) {
                    builder.setSyncToken(MessagesUtil.serializeMessage(token,
                            CommonObjectMessages.SyncToken.class));
                }
                return OperationMessages.SyncOpResponse.newBuilder().setLatestSyncToken(builder);
            } else if (requestMessage.hasSync()) {
                final ObjectClass objectClass =
                        new ObjectClass(requestMessage.getSync().getObjectClass());
                final SyncToken token =
                        MessagesUtil.deserializeMessage(requestMessage.getSync().getToken(),
                                SyncToken.class);

                OperationOptions operationOptions = null;
                if (!requestMessage.getSync().getOptions().isEmpty()) {
                    operationOptions =
                            MessagesUtil.deserializeLegacy(requestMessage.getSync().getOptions());
                }

                SyncToken result =
                        connectorFacade.sync(objectClass, token, new SyncResultsHandler() {
                            public boolean handle(SyncDelta delta) {

                                if (null != delta) {
                                    OperationMessages.SyncOpResponse.Builder result =
                                            OperationMessages.SyncOpResponse
                                                    .newBuilder()
                                                    .setSync(
                                                            OperationMessages.SyncOpResponse.Sync
                                                                    .newBuilder()
                                                                    .setSyncDelta(
                                                                            MessagesUtil
                                                                                    .serializeMessage(
                                                                                            delta,
                                                                                            CommonObjectMessages.SyncDelta.class))
                                                                    .setSequence(
                                                                            sequence.incrementAndGet()));

                                    if (tryHandleResult(result)) {
                                        logger.ok("SyncResult sent in sequence:{0}", sequence.get());
                                    } else {
                                        logger.info("Failed to send response {0}", sequence.get());
                                    }
                                }
                                return doContinue.get();
                            }
                        }, operationOptions);

                OperationMessages.SyncOpResponse.Sync.Builder builder =
                        OperationMessages.SyncOpResponse.Sync.newBuilder().setSequence(
                                sequence.get());
                if (null != result) {
                    builder.setSyncToken(MessagesUtil.serializeMessage(result,
                            CommonObjectMessages.SyncToken.class));
                }

                return OperationMessages.SyncOpResponse.newBuilder().setSync(builder);

            } else {
                logger.info("Invalid SyncOpRequest Request:{0}", getRequestId());
            }
            return null;
        }

        protected boolean tryCancel() {
            doContinue.set(Boolean.FALSE);
            return super.tryCancel();
        }
    }

}
