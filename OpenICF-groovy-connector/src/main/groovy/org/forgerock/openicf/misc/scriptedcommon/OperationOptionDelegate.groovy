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

import org.identityconnectors.framework.common.objects.OperationOptionInfo
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder
import org.identityconnectors.framework.common.objects.SchemaBuilder
import org.identityconnectors.framework.spi.operations.SPIOperation

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
class OperationOptionDelegate extends AbstractICFBuilder<SchemaBuilder> {

    private OperationOptionInfoBuilder infoBuilder = new OperationOptionInfoBuilder(null, String);
    private Set<Class<? extends SPIOperation>> unsupported = new HashSet<Class<? extends SPIOperation>>(12)

    OperationOptionDelegate(SchemaBuilder builder) {
        super(builder)
    }

    void disable(Class<? extends SPIOperation>... operation) {
        if (null != operation) {
            for (Class<? extends SPIOperation> clazz in operation) {
                unsupported.add(clazz)
            }
        }
    }

    void name(String name) {
        infoBuilder.setName(name)
    }

    void type(Class<?> type) {
        infoBuilder.setType(type)
    }

    void define(OperationOptionInfo info) {
        infoBuilder.setName(info.name);
        infoBuilder.setType(info.type);
    }

    @Override
    protected void complete() {
        final OperationOptionInfo info = infoBuilder.build();

        ((SchemaBuilder) builder).defineOperationOption(info);
        for (Class<? extends SPIOperation> clazz in unsupported) {
            ((SchemaBuilder) builder).removeSupportedOperationOption(clazz, info)
        }
    }
}
