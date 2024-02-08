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

public class CreateBatchTask implements BatchTask<Uid> {
    private final ObjectClass objectClass;
    private final Set<Attribute> createAttributes;
    private OperationOptions options;

    /**
     * Create a target object based on the specified attributes.
     *
     * The Connector framework always requires attribute
     * <code>ObjectClass</code>. The <code>Connector</code> itself may require
     * additional attributes. The API will confirm that the set contains the
     * <code>ObjectClass</code> attribute and that no two attributes in the set
     * have the same {@link Attribute#getName() name}.
     *
     * @param objectClass
     *            the type of object to create. Must not be null.
     * @param createAttributes
     *            includes all the attributes necessary to create the target
     *            object (including the <code>ObjectClass</code> attribute).
     * @param options
     *            additional options that impact the way this operation is run.
     */
    public CreateBatchTask(ObjectClass objectClass, Set<Attribute> createAttributes,
                           OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        Assertions.nullCheck(createAttributes, "createAttributes");

        this.objectClass = objectClass;
        this.createAttributes = createAttributes;
        this.options = options;
    }

    /**
     * @{inherit}
     */
    public Uid execute(BatchTaskExecutor executor) {
        return executor.execute(this);
    }

    /**
     * @{inherit}
     */
    public ObjectClass getObjectClass() {
        return objectClass;
    }

    public Set<Attribute> getCreateAttributes() {
        return createAttributes;
    }

    public OperationOptions getOptions() {
        return options == null ? (options = new OperationOptionsBuilder().build()) : options;
    }
}
