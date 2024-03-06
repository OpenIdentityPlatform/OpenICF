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
package org.identityconnectors.framework.api.operations.batch;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Builds a list of BatchTask objects.
 */
public class BatchBuilder {

    private LinkedList<BatchTask> tasks = new LinkedList<BatchTask>();

    /**
     * Add a Create operation to the batch.
     *
     * @param objectClass
     *            the type of object to create. Must not be null.
     * @param createAttributes
     *            includes all the attributes necessary to create the target
     *            object (including the <code>ObjectClass</code> attribute).
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     */
    public void addCreateOp(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        tasks.add(new CreateBatchTask(objectClass, createAttributes, options));
    }

    /**
     * Add a Delete operation to the batch.
     *
     * @param objectClass
     *            type of object to delete.
     * @param uid
     *            The unique id that specifies the object to delete.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     */
    public void addDeleteOp(ObjectClass objectClass, Uid uid, OperationOptions options) {
        tasks.add(new DeleteBatchTask(objectClass, uid, options));
    }

    /**
     * Add an Update:Replace operation to the batch.
     *
     * @param objectClass
     *            the type of object to modify. Must not be null.
     * @param uid
     *            the uid of the object to modify. Must not be null.
     * @param attributes
     *            set of new {@link org.identityconnectors.framework.common.objects.Attribute}. the values in this
     *            set represent the new, merged values to be applied to the object. This set may also include
     *            {@link org.identityconnectors.framework.common.objects.OperationalAttributes operational
     *            attributes}. Must not be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     */
    public void addUpdateReplaceOp(ObjectClass objectClass, Uid uid, Set<Attribute> attributes,
                                   OperationOptions options) {
        tasks.add(new UpdateBatchTask(objectClass, uid, attributes, options, UpdateType.UPDATE));
    }

    /**
     * Add an Update:Add operation to the batch.
     *
     * @param objectClass
     *            the type of object to modify. Must not be null.
     * @param uid
     *            the uid of the object to modify. Must not be null.
     * @param attributes
     *            set of new {@link org.identityconnectors.framework.common.objects.Attribute}. the values in this
     *            set represent the new, merged values to be applied to the object. This set may also include
     *            {@link org.identityconnectors.framework.common.objects.OperationalAttributes operational
     *            attributes}. Must not be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     */
    public void addUpdateAddOp(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options) {
        tasks.add(new UpdateBatchTask(objectClass, uid, attributes, options, UpdateType.ADDVALUES));
    }

    /**
     * Add an Update:Remove operation to the batch.
     *
     * @param objectClass
     *            the type of object to modify. Must not be null.
     * @param uid
     *            the uid of the object to modify. Must not be null.
     * @param attributes
     *            set of new {@link org.identityconnectors.framework.common.objects.Attribute}. the values in this
     *            set represent the new, merged values to be applied to the object. This set may also include
     *            {@link org.identityconnectors.framework.common.objects.OperationalAttributes operational
     *            attributes}. Must not be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     */
    public void addUpdateRemoveOp(ObjectClass objectClass, Uid uid, Set<Attribute> attributes,
                                  OperationOptions options) {
        tasks.add(new UpdateBatchTask(objectClass, uid, attributes, options, UpdateType.REMOVEVALUES));
    }

    /**
     * Return the current task list as a copy of the original.  This is to prevent external modification of the task
     * list thus preventing this builder from being reused.
     *
     * @return the list of batched tasks.
     */
    public List<BatchTask> build() {
        return Collections.unmodifiableList(tasks);
    }
}
