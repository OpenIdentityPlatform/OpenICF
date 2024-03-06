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
package org.identityconnectors.framework.impl.api.local.operations;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.BatchTaskExecutor;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl.ReferenceCounter;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.BatchOp;

import java.util.List;

public class BatchImpl extends ConnectorAPIOperationRunner implements
        org.identityconnectors.framework.api.operations.BatchApiOp {

    private final ReferenceCounter referenceCounter;

    /**
     * Construct a batch operation.
     *
     * @param context
     *          the context for the connector operation.
     * @param connector
     *          the connector on which to execute the operation.
     */
    public BatchImpl(final ConnectorOperationalContext context, final Connector connector) {
        super(context, connector);
        this.referenceCounter = new ReferenceCounter();
    }

    /**
     * Construct a batch operation.
     *
     * @param context
     *          the context for the connector operation.
     * @param connector
     *          the connector on which to execute the operation.
     */
    public BatchImpl(final ConnectorOperationalContext context, final Connector connector,
                     final ReferenceCounter referenceCounter) {
        super(context, connector);
        this.referenceCounter = referenceCounter;
    }

    /**
     * {@inherit}
     */
    public Subscription executeBatch(final List<BatchTask> tasks, final Observer<BatchResult> observer,
                               final OperationOptions options) {
        if (tasks == null || tasks.size() == 0) {
            // Nothing to do
            return null;
        }

        try {
            referenceCounter.acquire();

            Connector connector = getConnector();
            if (!(connector instanceof BatchOp)) {
                // The connector does not implement batch support.  Process the batch iteratively.
                BatchTaskExecutor executor = new BatchTaskExecutorImpl(connector, getOperationalContext());
                for (int i = 0; i < tasks.size(); i++) {
                    boolean complete = i == tasks.size() - 1;
                    try {
                        Object result = tasks.get(i).execute(executor);
                        observer.onNext(new BatchResult(result, null, String.valueOf(i), complete, false));
                    } catch (RuntimeException e) {
                        observer.onError(e);
                        if (options.getFailOnError()) {
                            return null;
                        }
                    }
                }
                // Batch processing complete, no future results
                return null;
            }
            // Connector implements batch support
            return ((BatchOp) connector).executeBatch(tasks, observer, options);
        } finally {
            referenceCounter.release();
        }

    }

    /**
     * {@inherit}
     */
    public Subscription queryBatch(final BatchToken batchToken, final Observer<BatchResult> observer,
                             final OperationOptions options) {
        Assertions.nullCheck(batchToken, "batchToken");
        Connector connector = getConnector();
        if (!(connector instanceof BatchOp)) {
            throw new UnsupportedOperationException("Connector does not support queryBatch");
        }
        try {
            referenceCounter.acquire();

            return ((BatchOp) connector).queryBatch(batchToken, observer, options);
        } finally {
            referenceCounter.release();
        }
    }
}
