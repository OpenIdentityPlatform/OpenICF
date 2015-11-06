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
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.filter.Filter;

import com.google.protobuf.ByteString;

public class SearchAsyncApiOpImpl extends AbstractAPIOperation implements SearchApiOp {

    private static final Log logger = Log.getLog(SearchAsyncApiOpImpl.class);

    public SearchAsyncApiOpImpl(
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            org.identityconnectors.framework.api.ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
            long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction, timeout);
    }

    public SearchResult search(final ObjectClass objectClass, final Filter filter,
            final ResultsHandler handler, final OperationOptions options) {
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

        InternalRequest request =
                getRemoteConnection().trySubmitRequest(
                        new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                                OperationRequest.newBuilder().setSearchOpRequest(requestBuilder),
                                handler, getTimeout()));
        if (null != request) {
            return asyncTimeout(request.process());
        }
        throw FAILED_EXCEPTION;
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<SearchResult, InternalRequest> {
        private final OperationRequest.Builder operationRequest;
        private final ResultsHandler handler;
        private final long timeout;

        public InternalRequestFactory(
                final org.identityconnectors.framework.api.ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationRequest.Builder operationRequest, final ResultsHandler handler,
                long timeout) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
            this.handler = handler;
            this.timeout = timeout;
        }

        public InternalRequest createRemoteRequest(
                RemoteOperationContext context,
                long requestId,
                CompletionCallback<SearchResult, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            // This is the context aware request
            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder,
                        handler, timeout);
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

        private final ResultBuffer<ConnectorObject, SearchResult> resultBuffer;
        private int remaining = -1;

        public InternalRequest(
                RemoteOperationContext context,
                long requestId,
                RemoteRequestFactory.CompletionCallback<SearchResult, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                RPCMessages.RPCRequest.Builder requestBuilder, final ResultsHandler handler,
                long timeout) {
            super(context, requestId, completionCallback, requestBuilder);
            resultBuffer = new ResultBuffer<ConnectorObject, SearchResult>(timeout) {
                protected boolean handle(Object result) {
                    if (result instanceof ConnectorObject) {
                        try {
                            if (handler.handle((ConnectorObject) result)) {
                                return true;
                            } else {
                                getPromise().cancel(true);
                            }
                        } catch (RuntimeException t) {
                            getPromise().cancel(true);
                            getExceptionHandler().handleException(t);
                        } catch (Throwable t) {
                            getPromise().cancel(true);
                            getExceptionHandler().handleException(
                                    new ConnectorException(t.getMessage(), t));
                        }
                    } else if (result instanceof RuntimeException) {
                        getExceptionHandler().handleException((RuntimeException) result);
                    } else if (result instanceof SearchResult) {
                        getResultHandler().handleResult((SearchResult) result);
                    } else if (null != result) {
                        // Exception
                        getExceptionHandler().handleException(
                                new ConnectorException("Unknown object type"));
                    } else {
                        getResultHandler().handleResult(null);
                    }
                    return false;
                }
            };
        }

        public Promise<SearchResult, RuntimeException> process() {
            // Use the application thread to process results synchronously
            try {
                resultBuffer.process();
            } catch (RuntimeException e) {
                getExceptionHandler().handleException(e);
            }
            return getPromise();
        }

        public boolean check() {
            boolean stopped = resultBuffer.isStopped();
            if (stopped) {
                logger.ok("RemoteRequest:{0} -> Application thread is not reading responses.",
                        getRequestId());
                getExceptionHandler().handleException(
                        new ConnectorException("Operation finished on local with unknown reason"));
                return false;
            } else {
                return super.check();
            }
        }

        public void inconsistent() {
            if (!resultBuffer.hasLast() || !resultBuffer.hasAll()) {
                inconsistencyCounter++;
            } else {
                logger.ok("Application is slow to process results");
                int size = resultBuffer.getRemaining();
                if (remaining == size) {
                    // Application is slow or not processing results
                    inconsistencyCounter++;
                }
                remaining = size;
            }
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
                resultBuffer.receiveNext(message.getSequence(), MessagesUtil.deserializeMessage(
                        message.getConnectorObject(), ConnectorObject.class));
            } else if (message.hasResult()) {
                resultBuffer.receiveLast(message.getSequence(), MessagesUtil.deserializeMessage(
                        message.getResult(), SearchResult.class));
                inconsistencyCounter = 0;
            } else {
                resultBuffer.receiveLast(message.getSequence(), null);
                inconsistencyCounter = 0;
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

                            if (doContinue.get() && null != connectorObject) {
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
                                    doContinue.set(Boolean.FALSE);
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
