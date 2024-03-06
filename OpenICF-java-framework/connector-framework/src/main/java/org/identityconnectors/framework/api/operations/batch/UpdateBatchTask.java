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

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Set;

public class UpdateBatchTask implements BatchTask<Uid> {

    private final UpdateType updateType;
    private final ObjectClass objectClass;
    private final Uid uid;
    private final Set<Attribute> attributes;
    private OperationOptions options;

    /**
     * See {@link org.identityconnectors.framework.api.operations.UpdateApiOp}.
     *
     * @param objectClass
     *            the type of object to modify. Must not be null.
     * @param uid
     *            the uid of the object to modify. Must not be null.
     * @param replaceAttributes
     *            set of new {@link org.identityconnectors.framework.common.objects.Attribute}. the values in this
     *            set represent the new, merged values to be applied to the object. This set may also include
     *            {@link org.identityconnectors.framework.common.objects.OperationalAttributes operational
     *            attributes}. Must not be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     * @param type
     *            the update type for this task.
     */
    public UpdateBatchTask(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes,
                           OperationOptions options, UpdateType type) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(uid, "uid");
        Assertions.nullCheck(replaceAttributes, "replaceAttributes");
        Assertions.nullCheck(type, "type");

        this.objectClass = objectClass;
        this.uid = uid;
        this.attributes = replaceAttributes;
        this.options = options;
        this.updateType = type;
    }

    /**
     * @{inherit}
     */
    public Uid execute(BatchTaskExecutor executor) {
        return executor.execute(this);
    }

    public UpdateType getUpdateType() {
        return updateType;
    }

    /**
     * @{inherit}
     */
    public ObjectClass getObjectClass() {
        return objectClass;
    }

    public Uid getUid() {
        return uid;
    }

    public Set<Attribute> getAttributes() {
        return attributes;
    }

    public OperationOptions getOptions() {
        return options == null ? (options = new OperationOptionsBuilder().build()) : options;
    }
}
