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
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.framework.api.operations.batch;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;

public class DeleteBatchTask implements BatchTask<BatchEmptyResult> {
    private final ObjectClass objectClass;
    private final Uid uid;
    private OperationOptions options;

    /**
     * Delete the object that the specified Uid identifies (if any).
     *
     * @param objectClass
     *            type of object to delete.
     * @param uid
     *            The unique id that specifies the object to delete.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     */
    public DeleteBatchTask(ObjectClass objectClass, Uid uid, OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(uid, "uid");

        this.objectClass = objectClass;
        this.uid = uid;
        this.options = options;
    }

    /**
     * Executes this delete task by delegating back to the given executor.
     *
     * @param executor
     *            the executor that will perform the actual delete operation.
     * @return an empty result confirming the delete completed.
     */
    public BatchEmptyResult execute(BatchTaskExecutor executor) {
        return executor.execute(this);
    }

    /**
     * Returns the object class of the object to delete.
     *
     * @return the object class specified when this task was constructed.
     */
    public ObjectClass getObjectClass() {
        return objectClass;
    }

    public Uid getUid() {
        return uid;
    }

    public OperationOptions getOptions() {
        return options == null ? (options = new OperationOptionsBuilder().build()) : options;
    }
}
