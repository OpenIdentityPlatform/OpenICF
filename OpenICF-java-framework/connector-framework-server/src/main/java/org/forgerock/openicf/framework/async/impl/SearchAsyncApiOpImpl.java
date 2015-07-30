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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.SearchOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.SearchOpResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.SearchAsyncApiOp;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.filter.Filter;

import com.google.protobuf.ByteString;

public class SearchAsyncApiOpImpl extends AbstractAPIOperation implements SearchAsyncApiOp {

    private static final Log logger = Log.getLog(SearchAsyncApiOpImpl.class);

    public SearchAsyncApiOpImpl(
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            org.identityconnectors.framework.api.ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction) {
        super(remoteConnection, connectorKey, facadeKeyFunction);
    }

    public SearchResult search(final ObjectClass objectClass, final Filter filter,
            final ResultsHandler handler, final OperationOptions options) {
        return searchAsync(objectClass, filter, handler, options).getOrThrowUninterruptibly();
    }

    public Promise<SearchResult, RuntimeException> searchAsync(final ObjectClass objectClass,
            final Filter filter, final ResultsHandler handler, final OperationOptions options) {

        Assertions.nullCheck(objectClass, "objectClass");
        if (ObjectClass.ALL.equals(objectClass)) {
            throw new UnsupportedOperationException(
                    "Operation is not allowed on __ALL__ object class");
        }
        Assertions.nullCheck(handler, "handler");

        SearchOpRequest.Builder requestBuilder =
                SearchOpRequest.newBuilder().setObjectClass(objectClass.getObjectClassValue());
        if (filter != null) {
            requestBuilder.setFilter(MessagesUtil.serializeLegacy(filter));
        }
        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationRequest.newBuilder().setSearchOpRequest(requestBuilder), handler));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<SearchResult, InternalRequest> {
        private final OperationRequest.Builder operationRequest;
        private final ResultsHandler handler;

        public InternalRequestFactory(
                final org.identityconnectors.framework.api.ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationRequest.Builder operationRequest, final ResultsHandler handler) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
            this.handler = handler;
        }

        public InternalRequest createRemoteRequest(
                RemoteOperationContext context,
                long requestId,
                CompletionCallback<SearchResult, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            // This is the context aware request
            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder, handler);
            } else {
                return null;
            }
        }

        protected OperationRequest.Builder createOperationRequest(
                RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<SearchResult, SearchOpResponse> {

        private final ResultsHandler handler;
        private final AtomicLong sequence = new AtomicLong(0);
        private long expectedResult = -1;
        private SearchResult result = null;

        public InternalRequest(
                RemoteOperationContext context,
                long requestId,
                RemoteRequestFactory.CompletionCallback<SearchResult, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                RPCMessages.RPCRequest.Builder requestBuilder, ResultsHandler handler) {
            super(context, requestId, completionCallback, requestBuilder);
            this.handler = handler;
        }

        protected SearchOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasSearchOpResponse()) {
                return message.getSearchOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "SearchOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                SearchOpResponse message) {
            if (message.hasConnectorObject()) {
                try {
                    final ConnectorObject co =
                            MessagesUtil.deserializeMessage(message.getConnectorObject(),
                                    ConnectorObject.class);

                    if (!handler.handle(co) && !getPromise().isDone()) {
                        getExceptionHandler()
                                .handleException(
                                        new ConnectorException(
                                                "ResultsHandler stopped processing results"));
                        tryCancelRemote(getConnectionContext(), getRequestId());
                    }
                } finally {
                    sequence.incrementAndGet();
                }
            } else {
                if (message.hasResult()) {
                    result =
                            MessagesUtil
                                    .deserializeMessage(message.getResult(), SearchResult.class);
                }
                expectedResult = message.getSequence();
                if (expectedResult == 0 || sequence.get() == expectedResult) {
                    getResultHandler().handleResult(result);
                } else {
                    logger.ok("Response processed before all result has arrived");
                }
            }
            if (expectedResult > 0 && sequence.get() == expectedResult) {
                getResultHandler().handleResult(result);
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<SearchOpResponse.Builder, OperationMessages.SearchOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.SearchOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor
            extends
            AbstractLocalOperationProcessor<SearchOpResponse.Builder, OperationMessages.SearchOpRequest> {

        private final AtomicBoolean doContinue = new AtomicBoolean(Boolean.TRUE);
        final AtomicLong sequence = new AtomicLong(0);

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.SearchOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, SearchOpResponse.Builder result) {
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setSearchOpResponse(result));
        }

        protected SearchOpResponse.Builder executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.SearchOpRequest requestMessage) {

            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());
            Filter filter = null;
            if (!requestMessage.getFilter().isEmpty()) {
                filter = MessagesUtil.deserializeLegacy(requestMessage.getFilter());
            }

            OperationOptions operationOptions = null;
            if (!requestMessage.getOptions().isEmpty()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }
            final SearchResult result =
                    connectorFacade.search(objectClass, filter, new ResultsHandler() {
                        public boolean handle(ConnectorObject connectorObject) {

                            if (null != connectorObject) {
                                SearchOpResponse.Builder result =
                                        SearchOpResponse
                                                .newBuilder()
                                                .setConnectorObject(
                                                        MessagesUtil
                                                                .serializeMessage(
                                                                        connectorObject,
                                                                        CommonObjectMessages.ConnectorObject.class))
                                                .setSequence(sequence.incrementAndGet());

                                if (tryHandleResult(result)) {
                                    logger.ok("SearchResult sent in sequence:{0}", sequence.get());
                                } else {
                                    logger.info("Failed to send response {0}", sequence.get());
                                }
                            }
                            return doContinue.get();
                        }
                    }, operationOptions);

            SearchOpResponse.Builder response =
                    SearchOpResponse.newBuilder().setSequence(sequence.get());
            if (null != result) {
                response.setResult(MessagesUtil.serializeMessage(result,
                        CommonObjectMessages.SearchResult.class));
            }
            return response;
        }

        protected boolean tryCancel() {
            doContinue.set(Boolean.FALSE);
            return super.tryCancel();
        }
    }
}
