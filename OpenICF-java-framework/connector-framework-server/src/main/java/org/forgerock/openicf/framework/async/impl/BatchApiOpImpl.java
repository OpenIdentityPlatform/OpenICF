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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages.BatchOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.BatchOpResult;
import org.forgerock.openicf.common.protobuf.OperationMessages.CreateOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.DeleteOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationResponse;
import org.forgerock.openicf.common.protobuf.OperationMessages.UpdateOpRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.BatchEmptyResponse;
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
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.BatchApiOp;
import org.identityconnectors.framework.api.operations.batch.BatchEmptyResult;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.CreateBatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateType;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;

import com.google.protobuf.ByteString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.Uid;

public class BatchApiOpImpl extends AbstractAPIOperation implements BatchApiOp {

    private static final Log logger = Log.getLog(BatchApiOpImpl.class);

    public BatchApiOpImpl(
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction,timeout);
    }

    @Override
    public Subscription executeBatch(List<BatchTask> tasks, final Observer<BatchResult> observer,
                                     OperationOptions options) {
        final Promise<BatchToken, RuntimeException> promise =
                tryExecuteBatch(tasks, observer, options)
                        .thenOnException(
                                new ExceptionHandler<RuntimeException>() {
                                    public void handleException(RuntimeException error) {
                                        if (!(error instanceof CancellationException)) {
                                            observer.onError(error);
                                        }
                                    }
                                });

        promise.getOrThrowUninterruptibly();

        return new Subscription() {
            public void close() {
                promise.cancel(true);
            }

            public boolean isUnsubscribed() {
                return promise.isDone();
            }

            public Object getReturnValue() {
                try {
                    return promise.get();
                } catch (Exception e) {
                    return new BatchToken();
                }
            }
        };
    }

    @Override
    public Subscription queryBatch(BatchToken token, final Observer<BatchResult> observer, OperationOptions options) {
        final Promise<BatchToken, RuntimeException> promise =
                tryQueryBatch(token, observer, options)
                        .thenOnException(
                                new ExceptionHandler<RuntimeException>() {
                                    public void handleException(RuntimeException error) {
                                        if (!(error instanceof CancellationException)) {
                                            observer.onError(error);
                                        }
                                    }
                                });

        promise.getOrThrowUninterruptibly();

        return new Subscription() {
            public void close() {
                promise.cancel(true);
            }

            public boolean isUnsubscribed() {
                return promise.isDone();
            }

            public Object getReturnValue() {
                try {
                    return promise.get();
                } catch (Exception e) {
                    return new BatchToken();
                }
            }
        };
    }

    public Promise<BatchToken, RuntimeException> tryExecuteBatch(List<BatchTask> tasks,
                                                                 Observer<BatchResult> observer,
                                                                 OperationOptions options) {
        Assertions.nullCheck(tasks, "tasks");
        // make sure the list is non-empty
        if (tasks.size() == 0) {
            throw new InvalidAttributeValueException("Parameter 'tasks' cannot be empty.");
        }

        BatchOpRequest.Builder requestBuilder = BatchOpRequest.newBuilder().setQuery(false);

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        int index = 0;
        for (BatchTask task : tasks) {
            if (task instanceof CreateBatchTask) {
                CreateBatchTask batchTask = (CreateBatchTask) task;
                OperationMessages.BatchOpTask.Builder subTask = OperationMessages.BatchOpTask.newBuilder();
                subTask.setTaskId(String.valueOf(index++));
                OperationMessages.CreateOpRequest.Builder requestOp = OperationMessages.CreateOpRequest.newBuilder();
                requestOp.setObjectClass(batchTask.getObjectClass().getObjectClassValue());
                requestOp.setCreateAttributes(MessagesUtil.serializeLegacy(batchTask.getCreateAttributes()));
                requestOp.setOptions(MessagesUtil.serializeLegacy(batchTask.getOptions()));
                subTask.setCreateRequest(requestOp.build());
                requestBuilder.addTasks(subTask.build());
            } else if (task instanceof DeleteBatchTask) {
                DeleteBatchTask batchTask = (DeleteBatchTask) task;
                OperationMessages.BatchOpTask.Builder subTask = OperationMessages.BatchOpTask.newBuilder();
                subTask.setTaskId(String.valueOf(index++));
                OperationMessages.DeleteOpRequest.Builder requestOp = OperationMessages.DeleteOpRequest.newBuilder();
                requestOp.setObjectClass(batchTask.getObjectClass().getObjectClassValue());
                CommonObjectMessages.Uid.Builder uid = CommonObjectMessages.Uid.newBuilder();
                uid.setValue(batchTask.getUid().getUidValue());
                uid.setRevision(batchTask.getUid().getRevision() != null ? batchTask.getUid().getRevision() : "0");
                requestOp.setUid(uid.build());
                requestOp.setOptions(MessagesUtil.serializeLegacy(batchTask.getOptions()));
                subTask.setDeleteRequest(requestOp.build());
                requestBuilder.addTasks(subTask.build());
            } else if (task instanceof UpdateBatchTask) {
                UpdateBatchTask batchTask = (UpdateBatchTask) task;
                OperationMessages.BatchOpTask.Builder subTask = OperationMessages.BatchOpTask.newBuilder();
                subTask.setTaskId(String.valueOf(index++));
                OperationMessages.UpdateOpRequest.Builder requestOp = OperationMessages.UpdateOpRequest.newBuilder();
                requestOp.setObjectClass(batchTask.getObjectClass().getObjectClassValue());
                requestOp.setOptions(MessagesUtil.serializeLegacy(batchTask.getOptions()));
                requestOp.setReplaceAttributes(MessagesUtil.serializeLegacy(batchTask.getAttributes()));
                CommonObjectMessages.Uid.Builder uid = CommonObjectMessages.Uid.newBuilder();
                uid.setValue(batchTask.getUid().getUidValue());
                uid.setRevision(batchTask.getUid().getRevision() != null ? batchTask.getUid().getRevision() : "0");
                requestOp.setUid(uid.build());
                requestOp.setUpdateType(batchTask.getUpdateType().equals(UpdateType.UPDATE)
                        ? OperationMessages.UpdateOpRequest.UpdateType.REPLACE
                        : batchTask.getUpdateType().equals(UpdateType.ADDVALUES)
                        ? OperationMessages.UpdateOpRequest.UpdateType.ADD
                        : OperationMessages.UpdateOpRequest.UpdateType.REMOVE);
                subTask.setUpdateRequest(requestOp.build());
                requestBuilder.addTasks(subTask.build());
            }
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationRequest.newBuilder().setBatchOpRequest(requestBuilder), observer));
    }

    public Promise<BatchToken, RuntimeException> tryQueryBatch(BatchToken batchToken,
                                                               Observer<BatchResult> observer,
                                                               OperationOptions options) {
        if (batchToken == null) {
            throw new InvalidAttributeValueException("Parameter 'batchToken' cannot be null.");
        }

        BatchOpRequest.Builder requestBuilder = BatchOpRequest.newBuilder().setQuery(true);

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        for (String token : batchToken.getTokens()) {
            requestBuilder.addBatchToken(token);
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationRequest.newBuilder().setBatchOpRequest(requestBuilder), observer));
    }


    private static class InternalRequestFactory
            extends AbstractRemoteOperationRequestFactory<BatchToken, InternalRequest> {
        private final OperationRequest.Builder operationRequest;
        private final Observer<BatchResult> observer;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationRequest.Builder operationRequest,
                final Observer<BatchResult> observer) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
            this.observer = observer;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<BatchToken,
                                        RuntimeException,
                                        WebSocketConnectionGroup,
                                        WebSocketConnectionHolder,
                                        RemoteOperationContext> completionCallback) {
            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder, observer);
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
            extends AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<BatchToken, BatchOpResult> {
        private final Observer<BatchResult> observer;

        // Flow control flags
        private final AtomicBoolean resultChannelComplete = new AtomicBoolean(false);
        private final AtomicBoolean completeEventComplete = new AtomicBoolean(false);
        private BatchToken returnToken = null;
        private final AtomicLong completionTimeout = new AtomicLong(new Date().getTime() + 5000);
        private final CompletionListener completionListener = new CompletionListener();

        // For sorting result responses
        private final TreeMap<Integer, BatchOpResult> responseQueue = new TreeMap<Integer, BatchOpResult>();
        private final AtomicInteger responseIdSent = new AtomicInteger(-1);

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<BatchToken,
                                                            RuntimeException,
                                                            WebSocketConnectionGroup,
                                                            WebSocketConnectionHolder,
                                                            RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder,
                final Observer<BatchResult> observer) {
            super(context, requestId, completionCallback, requestBuilder);
            this.observer = observer;
        }

        protected BatchOpResult getOperationResponseMessages(OperationResponse message) {
            if (message.hasBatchOpResult()) {
                return message.getBatchOpResult();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "BatchOpResult");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                BatchOpResult message) {
            /**
             * Messages come in 3 flavors:
             *  - Batch task result (Uid or Empty)
             *  - Token only (result of original call to executeBatch or queryBatch
             *  - Complete, called once at the end of the executeBatch/queryBatch call
             */
            if (message.hasUid() || message.hasEmptyResult()) {
                logger.ok("Received message: " + message.getTaskId());
                completionTimeout.set(new Date().getTime() + 5000);
                // Order the results which may have become unordered due to http delays
                synchronized (responseQueue) {
                    responseQueue.put(Integer.valueOf(message.getTaskId()), message);
                    while (responseQueue.size() > 0 && responseQueue.firstKey() == responseIdSent.get() + 1) {
                        Integer key = responseQueue.firstKey();
                        sendResponse(responseQueue.get(key));
                        responseIdSent.incrementAndGet();
                        responseQueue.remove(key);
                    }
                }
            } else if (message.getTaskId().length() == 0 && !message.getComplete()) {
                // BatchToken message (token list may be empty)
                logger.ok("Batch token received.");
                returnToken = new BatchToken();
                for (int i = 0; i < message.getBatchTokenCount(); i++) {
                    returnToken.addToken(message.getBatchToken(i));
                }
                returnToken.setQueryRequired(message.getQueryRequired());
                returnToken.setAsynchronousResults(message.getAsynchronousResults());
                returnToken.setReturnsResults(message.getReturnsResults());

                if (!message.getReturnsResults()) {
                    // No results returned with this token
                    resultChannelComplete.set(true);
                }
                completionListener.start();
            } else {
                // "Complete" message
                logger.ok("Batch operation complete.");
                completeEventComplete.set(true);
                completionListener.start();
            }
        }

        private class CompletionListener extends Thread {
            private boolean running = false;

            public void start() {
                if (running) {
                    return;
                }
                running = true;
                super.start();
            }

            public void run() {
                logger.ok("CompletionListener waiting for final result to complete.");
                while ((!resultChannelComplete.get() || returnToken == null || !completeEventComplete.get())
                        && new Date().getTime() < completionTimeout.get()) {
                    try {
                        sleep(100);
                    } catch (Exception e) {
                        break;
                    }
                }

                if (returnToken != null) {
                    getResultHandler().handleResult(returnToken);
                    logger.ok("Token returned.");
                } else {
                    logger.ok("Batch CompletionListener timed out. Unable to return batch token.");
                }
                observer.onCompleted();
                logger.ok("CompletionListener finished.");
            }
        }

        private void sendResponse(BatchOpResult message) {
            Object result = message.hasUid()
                    ? MessagesUtil.deserializeMessage(message.getUid(), Uid.class)
                    : message.hasEmptyResult()
                    ? MessagesUtil.deserializeMessage(message.getEmptyResult(), BatchEmptyResult.class)
                    : null;

            try {
                BatchToken newToken = new BatchToken();
                if (message.getBatchTokenCount() > 0) {
                    newToken = new BatchToken();
                    for (String token : message.getBatchTokenList()) {
                        newToken.addToken(token);
                    }
                    newToken.setQueryRequired(message.getQueryRequired());
                    newToken.setAsynchronousResults(message.getAsynchronousResults());
                    newToken.setReturnsResults(message.getReturnsResults());
                }

                observer.onNext(new BatchResult(result, newToken, message.getTaskId(),
                        message.getComplete(), message.getError()));
                logger.ok("Handled message: " + message.getTaskId());

                if (message.getComplete()) {
                    resultChannelComplete.set(true);
                    logger.ok("Last batch result processed.");
                }
            } catch (Throwable t) {
                if (!getPromise().isDone()) {
                    getExceptionHandler().handleException(
                            new ConnectorException("BatchResultsHandler stopped processing results."));
                    tryCancelRemote(getConnectionContext(), getRequestId());
                }
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<BatchOpResult, BatchOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket, BatchOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor extends
            AbstractLocalOperationProcessor<BatchOpResult, BatchOpRequest> {

        private Subscription subscription = null;
        private final AtomicBoolean commandChannelComplete = new AtomicBoolean(false);
        private final AtomicInteger resultChannelComplete = new AtomicInteger(0);

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                                                  BatchOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(RemoteOperationContext remoteContext,
                                                                          BatchOpResult response) {
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationResponse.newBuilder().setBatchOpResult(response));
        }

        @Override
        public void execute(final ConnectorFacade connectorFacade) {
            try {
                BatchOpResult result = executeOperation(connectorFacade, super.requestMessage);
                if (commandChannelComplete.get() && resultChannelComplete.get() >= 2) {
                    handleResult(result);
                } else {
                    tryHandleResult(result);
                }
            } catch (Error t) {
                handleException(ConnectorException.wrap(t));
            } catch (RuntimeException error) {
                handleException(error);
            }
        }

        @Override
        protected BatchOpResult executeOperation(ConnectorFacade connectorFacade,
                                                           final BatchOpRequest requestMessage) {
            final OperationOptions options = MessagesUtil.deserializeLegacy(requestMessage.getOptions());

            Observer<BatchResult> observer = new Observer<BatchResult>() {
                public void onCompleted() {
                    resultChannelComplete.incrementAndGet();
                    if (resultChannelComplete.get() >= 2 && commandChannelComplete.get()) {
                        handleResult(BatchOpResult.newBuilder().setComplete(true).build());
                    } else {
                        tryHandleResult(BatchOpResult.newBuilder().setComplete(true).build());
                    }
                }

                public void onError(Throwable error) {
                    try {
                        final byte[] responseMessage =
                                MessagesUtil.createErrorResponse(getRequestId(), error).build().toByteArray();
                        trySendBytes(responseMessage);
                    } catch (Throwable t) {
                        logger.ok(t, "Operation encountered an exception and failed to send the exception response");
                    }
                }

                public void onNext(BatchResult result) {
                    if (result != null) {
                        BatchOpResult.Builder opResult = BatchOpResult.newBuilder()
                                .setComplete(result.getComplete())
                                .setError(result.getError())
                                .setTaskId(result.getResultId());
                        if (result.getToken() != null) {
                            for (String token : result.getToken().getTokens()) {
                                opResult.addBatchToken(token);
                            }
                        }
                        if (result.getResult() instanceof Uid) {
                            opResult.setUid(MessagesUtil.serializeMessage(result.getResult(),
                                    org.forgerock.openicf.common.protobuf.CommonObjectMessages.Uid.class));
                        } else if (result.getResult() instanceof BatchEmptyResult) {
                            opResult.setEmptyResult(MessagesUtil.serializeMessage(result.getResult(),
                                    BatchEmptyResponse.class));
                        } else {
                            opResult.setEmptyResult(MessagesUtil.serializeMessage(
                                    new BatchEmptyResult(result.getResult().toString()), BatchEmptyResponse.class));
                        }
                        if (opResult.getComplete()) {
                            resultChannelComplete.incrementAndGet();
                        }
                        if (resultChannelComplete.get() >= 2 && commandChannelComplete.get()) {
                            handleResult(opResult.build());
                        } else {
                            tryHandleResult(opResult.build());
                        }
                    }
                }
            };


            if (!requestMessage.getQuery()) {
                final List<BatchTask> tasks = new ArrayList<BatchTask>();
                for (OperationMessages.BatchOpTask task : requestMessage.getTasksList()) {
                    if (task.hasCreateRequest()) {
                        CreateOpRequest req = task.getCreateRequest();
                        tasks.add(new CreateBatchTask(
                                new ObjectClass(req.getObjectClass()),
                                (Set<Attribute>) MessagesUtil.deserializeLegacy(req.getCreateAttributes()),
                                (OperationOptions) MessagesUtil.deserializeLegacy(req.getOptions())
                        ));
                    } else if (task.hasDeleteRequest()) {
                        DeleteOpRequest req = task.getDeleteRequest();
                        tasks.add(new DeleteBatchTask(
                                new ObjectClass(req.getObjectClass()),
                                new Uid(req.getUid().getValue(), req.getUid().getRevision()),
                                (OperationOptions) MessagesUtil.deserializeLegacy(req.getOptions())
                        ));
                    } else if (task.hasUpdateRequest()) {
                        UpdateOpRequest req = task.getUpdateRequest();
                        tasks.add(new UpdateBatchTask(
                                new ObjectClass(req.getObjectClass()),
                                new Uid(req.getUid().getValue(), req.getUid().getRevision()),
                                (Set<Attribute>) MessagesUtil.deserializeLegacy(req.getReplaceAttributes()),
                                (OperationOptions) MessagesUtil.deserializeLegacy(req.getOptions()),
                                (req.getUpdateType().equals(UpdateOpRequest.UpdateType.ADD)
                                        ? UpdateType.ADDVALUES
                                        : req.getUpdateType().equals(UpdateOpRequest.UpdateType.REMOVE)
                                        ? UpdateType.REMOVEVALUES
                                        : UpdateType.UPDATE)
                        ));
                    }
                }

                try {
                    subscription = connectorFacade.executeBatch(tasks, observer, options);
                } catch (Throwable t) {
                    tryHandleError(new ConnectorException(t));
                    return BatchOpResult.newBuilder().build();
                }
            } else {
                try {
                    BatchToken queryToken = new BatchToken();
                    for (int i = 0; i < requestMessage.getBatchTokenCount(); i++) {
                        queryToken.addToken(requestMessage.getBatchToken(i));
                    }
                    subscription = connectorFacade.queryBatch(queryToken, observer, options);
                } catch (Throwable t) {
                    tryHandleError(new ConnectorException(t));
                    return BatchOpResult.newBuilder().build();
                }
            }

            BatchOpResult.Builder result = BatchOpResult.newBuilder();
            if (subscription != null && subscription.getReturnValue() != null) {
                BatchToken returnedToken = (BatchToken) subscription.getReturnValue();
                for (String token : returnedToken.getTokens()) {
                    result.addBatchToken(token);
                }
                result.setQueryRequired(returnedToken.isQueryRequired());
                result.setAsynchronousResults(returnedToken.hasAsynchronousResults());
                result.setReturnsResults(returnedToken.returnsResults());
                if (returnedToken.isQueryRequired()) {
                    resultChannelComplete.incrementAndGet();
                }
            }
            commandChannelComplete.set(true);
            return result.build();
        }

        protected boolean tryCancel() {
            subscription.close();
            return super.tryCancel();
        }
    }
}
