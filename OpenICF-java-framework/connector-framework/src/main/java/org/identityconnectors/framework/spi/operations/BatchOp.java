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
package org.identityconnectors.framework.spi.operations;

import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;

import java.util.List;

/**
 * Execute a batched list of operations.
 *
 * If a resource does not support batch operations in any form and it cannot be coerced into doing so then the
 * connector should not implement this interface.  If this interface is not implemented batch will still be
 * supported via the framework but the operations will be executed iteratively through the connector.  With
 * this every connector appears to support batch even if it's not the most efficient implementation possible.
 *
 * @since 1.5
 */
public interface BatchOp extends SPIOperation {

    /**
     * Execute a series of {@link BatchTask}.
     *
     * @param tasks
     *            the list of batch tasks to execute.
     * @param observer
     *            an observer/handler for the ongoing results of the batch.
     * @param options
     *            options for the operation.
     * @return a subscription object for managing the lifecycle of the observer.
     */
    Subscription executeBatch(List<BatchTask> tasks, Observer<BatchResult> observer, OperationOptions options);

    /**
     * Query an ongoing batch execution for new results.
     *
     * @param token
     *            a token for a previously started batch, the ongoing results of which should invoke the
     *            observer.
     * @param observer
     *            an observer/handler for the ongoing results of the batch.
     * @param options
     *            options for the operation.
     * @return a subscription object for managing the lifecycle of the observer.
     */
    Subscription queryBatch(BatchToken token, Observer<BatchResult> observer, OperationOptions options);
}
