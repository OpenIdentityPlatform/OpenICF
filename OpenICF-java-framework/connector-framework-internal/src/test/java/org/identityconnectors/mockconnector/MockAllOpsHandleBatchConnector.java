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
package org.identityconnectors.mockconnector;

import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchEmptyResult;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.CreateBatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateBatchTask;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.spi.operations.BatchOp;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mocks a connector that uses a handler exclusively to process batch results.
 */
public class MockAllOpsHandleBatchConnector extends MockAllOpsConnector implements BatchOp {
    private Executor executor = null;
    private BatchToken singleBatchToken = new BatchToken(UUID.randomUUID().toString());
    {
        singleBatchToken.setQueryRequired(false);
    }
    private final AtomicBoolean batchComplete = new AtomicBoolean(false);

    public Subscription executeBatch(final List<BatchTask> tasks, Observer<BatchResult> observer,
                               OperationOptions options) {
        assert tasks != null && tasks.size() > 0;
        addCall(tasks);
        executor = new Executor(tasks, observer, options);
        executor.start();
        return new Subscription() {
            public void close() {

            }

            public boolean isUnsubscribed() {
                return batchComplete.get();
            }

            public Object getReturnValue() {
                return batchComplete.get() ? null : singleBatchToken;
            }
        };
    }

    public Subscription queryBatch(BatchToken batchToken, Observer<BatchResult> observer, OperationOptions options) {
        assert batchToken != null && batchToken.hasToken(singleBatchToken.getTokens().get(0));
        addCall();
        if (!batchComplete.get()) {
            executor.setObserver(observer);
        }
        return new Subscription() {
            public void close() {

            }

            public boolean isUnsubscribed() {
                return batchComplete.get();
            }

            public Object getReturnValue() {
                return batchComplete.get() ? null : singleBatchToken;
            }
        };
    }

    private class Executor extends Thread {
        private final List<BatchTask> tasks;
        private Observer<BatchResult> observer;
        private final OperationOptions options;

        public Executor(List<BatchTask> tasks, Observer<BatchResult> observer, OperationOptions options) {
            this.tasks = tasks;
            this.observer = observer;
            this.options = options;
        }

        public void setObserver(Observer<BatchResult> observer) {
            this.observer = observer;
        }

        public void run() {

            for (int i = 0; i < tasks.size(); i++) {
                try {
                    Thread.sleep(500L);
                    BatchTask task = tasks.get(i);
                    Object result = null;
                    try {
                        if (task instanceof CreateBatchTask) {
                            result = create(task.getObjectClass(), ((CreateBatchTask) task).getCreateAttributes(),
                                    ((CreateBatchTask) task).getOptions());
                        } else if (task instanceof DeleteBatchTask) {
                            delete(task.getObjectClass(), ((DeleteBatchTask) task).getUid(),
                                    ((DeleteBatchTask) task).getOptions());
                            result = new BatchEmptyResult("Delete successful");
                        } else if (task instanceof UpdateBatchTask) {
                            result = update(task.getObjectClass(), ((UpdateBatchTask) task).getUid(),
                                    ((UpdateBatchTask) task).getAttributes(), ((UpdateBatchTask) task).getOptions());
                        }
                        observer.onNext(new BatchResult(result, singleBatchToken,
                                String.valueOf(i), i == tasks.size() - 1, false));
                        batchComplete.set(i == tasks.size() - 1);
                    } catch (RuntimeException e) {
                        observer.onError(e);
                        if (options.getFailOnError()) {
                            batchComplete.set(true);
                            return;
                        }
                    }
                } catch (Exception e) {
                    // interrupted
                }
            }
        }
    }
}
