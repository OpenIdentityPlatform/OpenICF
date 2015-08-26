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

/**
 * Mocks a connector that processes batch operations synchronously with the request.
 */
public class MockAllOpsSyncBatchConnector extends MockAllOpsConnector implements BatchOp {
    /**
     * Simulates a connector with fully synchronous batch processing.
     *
     * @param tasks
     *            the list of batch tasks to execute.
     * @param observer
     *            a handler invoked for processing the results of each batch task.
     * @param options
     *            options for the operation.
     * @return null as a batch token since this type of batch processing does not support
     *            tokens.
     */
    public Subscription executeBatch(final List<BatchTask> tasks, Observer<BatchResult> observer,
                               OperationOptions options) {
        addCall(tasks);
        for (int i = 0; i < tasks.size(); i++) {
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
                observer.onNext(new BatchResult(result, null, String.valueOf(i), i == tasks.size() - 1, false));
            } catch (RuntimeException e) {
                observer.onError(e);
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

    /**
     * Because this is a synchronous batch processing connector there is nothing to continue; All work is
     * complete.
     *
     * @param batchToken
     *            a token from a previously started batch this operation should attach to to continue gathering
     *            results.
     * @param observer
     *            a handler invoked for processing the results of each batch task.
     * @param options
     *            options for the operation.
     * @return null batch token.
     */
    public Subscription queryBatch(BatchToken batchToken, Observer<BatchResult> observer, OperationOptions options) {
        addCall();
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
}
