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

import org.identityconnectors.framework.api.operations.batch.BatchEmptyResult;
import org.identityconnectors.framework.api.operations.batch.BatchTaskExecutor;
import org.identityconnectors.framework.api.operations.batch.CreateBatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateBatchTask;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

/**
 * A visitor for executing batch tasks.
 */
public class BatchTaskExecutorImpl implements BatchTaskExecutor {
    private final Connector connector;
    private final ConnectorOperationalContext context;

    public BatchTaskExecutorImpl(Connector connector, ConnectorOperationalContext context) {
        this.connector = connector;
        this.context = context;
    }

    public Uid execute(CreateBatchTask task) {
        return new CreateImpl(context, connector).create(
                task.getObjectClass(), task.getCreateAttributes(), task.getOptions());
    }

    public BatchEmptyResult execute(DeleteBatchTask task) {
        new DeleteImpl(context, connector).delete(
                task.getObjectClass(), task.getUid(), task.getOptions());
        return new BatchEmptyResult("Delete of " + task.getObjectClass() + " " + task.getUid() + " successful");
    }

    public Uid execute(UpdateBatchTask task) {
        UpdateImpl runner = new UpdateImpl(context, connector);
        switch (task.getUpdateType()) {
            case UPDATE:
                return runner.update(task.getObjectClass(), task.getUid(), task.getAttributes(), task.getOptions());
            case ADDVALUES:
                return runner.addAttributeValues(task.getObjectClass(), task.getUid(), task.getAttributes(),
                        task.getOptions());
            case REMOVEVALUES:
                return runner.removeAttributeValues(task.getObjectClass(), task.getUid(), task.getAttributes(),
                        task.getOptions());
        }
        // Cannot happen unless developer adds a new UpdateType not accounted for here
        throw new ConnectorException("Unknown update type " + task.getUpdateType());
    }
}
