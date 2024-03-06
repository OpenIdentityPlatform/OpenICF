/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openicf.misc.scriptedcommon

import org.identityconnectors.framework.common.objects.AttributeInfo
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder
import org.identityconnectors.framework.common.objects.ObjectClassInfo
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder
import org.identityconnectors.framework.common.objects.SchemaBuilder
import org.identityconnectors.framework.spi.operations.SPIOperation

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
class ObjectClassDelegate extends AbstractICFBuilder<SchemaBuilder> {

    private ObjectClassInfoBuilder infoBuilder = new ObjectClassInfoBuilder()
    private Set<Class<? extends SPIOperation>> unsupported = new HashSet<Class<? extends SPIOperation>>(12)

    ObjectClassDelegate(SchemaBuilder builder) {
        super(builder)
    }

    public void type(String type) {
        infoBuilder.setType(type);
    }

    public void container() {
        infoBuilder.setContainer(true)
    }

    public void attribute(AttributeInfo info) {
        infoBuilder.addAttributeInfo(info)
    }

    public void attribute(String name) {
        infoBuilder.addAttributeInfo(AttributeInfoBuilder.build(name))
    }
    
    public void attribute(String name, Class<?> type) {
        infoBuilder.addAttributeInfo(AttributeInfoBuilder.build(name, type))
    }

    public void attribute(String name, Class<?> type, Set<AttributeInfo.Flags> flags) {
        infoBuilder.addAttributeInfo(AttributeInfoBuilder.build(name, type, flags))
    }

    void attributes(@DelegatesTo(AttributeInfoDelegate) Closure attribute) {
        new AttributeInfoDelegate(infoBuilder).delegateToTag(AttributeInfoDelegate, attribute);
    }

    void disable(Class<? extends SPIOperation>... operation) {
        if (null != operation) {
            for (Class<? extends SPIOperation> clazz in operation) {
                unsupported.add(clazz)
            }
        }
    }

    @Override
    protected void complete() {
        final ObjectClassInfo info = infoBuilder.build();

        ((SchemaBuilder) builder).defineObjectClass(info);
        for (Class<? extends SPIOperation> clazz in unsupported) {
            ((SchemaBuilder) builder).removeSupportedObjectClass(clazz, info)
        }
    }
}
