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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mocks a connector that uses batch tokens to identify result sets.
 */
public class MockAllOpsTokenBatchConnector extends MockAllOpsConnector implements BatchOp {
    private Executor executor = null;
    private final BatchToken token = new BatchToken(UUID.randomUUID().toString());

    public Subscription executeBatch(final List<BatchTask> tasks, Observer<BatchResult> observer,
                               OperationOptions options) {
        assert tasks != null && tasks.size() > 0;
        addCall(tasks);
        executor = new Executor(tasks, options);
        executor.start();
        return new Subscription() {
            public void close() {

            }

            public boolean isUnsubscribed() {
                return false;
            }

            public Object getReturnValue() {
                return token;
            }
        };
    }

    public Subscription queryBatch(BatchToken batchToken, Observer<BatchResult> observer, OperationOptions options) {
        assert batchToken != null && batchToken.getTokens().size() == 1;
        addCall();
        List<BatchResult> results = executor.getResults();
        for (int i = 0; i < results.size(); i++) {
            BatchResult result = results.get(i);
            if (result.getError()) {
                observer.onError((RuntimeException) result.getResult());
            } else {
                observer.onNext(result);
            }
        }
        return new Subscription() {
            public void close() {

            }

            public boolean isUnsubscribed() {
                return true;
            }

            public Object getReturnValue() {
                return null;
            }
        };
    }

    private class Executor extends Thread {
        private final List<BatchTask> tasks;
        private final OperationOptions options;
        private final List<BatchResult> results = new ArrayList<BatchResult>();

        public Executor(List<BatchTask> tasks, OperationOptions options) {
            this.tasks = tasks;
            this.options = options;
        }

        public List<BatchResult> getResults() {
            synchronized (results) {
                List<BatchResult> ret = new ArrayList<BatchResult>(results);
                results.clear();
                return ret;
            }
        }

        public void run() {
            int i = 0;
            for (BatchTask task : tasks) {
                try {
                    Thread.sleep(500);
                    Object result = null;
                    try {
                        if (task instanceof CreateBatchTask) {
                            create(task.getObjectClass(), ((CreateBatchTask) task).getCreateAttributes(),
                                    ((CreateBatchTask) task).getOptions());
                            result = new BatchEmptyResult("Create successful");
                        } else if (task instanceof DeleteBatchTask) {
                            delete(task.getObjectClass(), ((DeleteBatchTask) task).getUid(),
                                    ((DeleteBatchTask) task).getOptions());
                            result = new BatchEmptyResult("Delete successful");
                        } else if (task instanceof UpdateBatchTask) {
                            update(task.getObjectClass(), ((UpdateBatchTask) task).getUid(),
                                    ((UpdateBatchTask) task).getAttributes(), ((UpdateBatchTask) task).getOptions());
                            result = new BatchEmptyResult("Update successful");
                        }
                        synchronized (results) {
                            results.add(new BatchResult(result, token, String.valueOf(i),
                                    i++ == tasks.size() - 1, false));
                        }
                    } catch (Exception e) {
                        synchronized (results) {
                            results.add(new BatchResult(e, token, String.valueOf(i), i++ == tasks.size() - 1, false));
                        }
                        if (options.getFailOnError()) {
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
