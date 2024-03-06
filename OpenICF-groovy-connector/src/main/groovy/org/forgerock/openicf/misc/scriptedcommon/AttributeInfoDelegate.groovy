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

import org.codehaus.groovy.runtime.InvokerHelper
import org.identityconnectors.framework.common.objects.AttributeInfo
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
class AttributeInfoDelegate extends AbstractICFBuilder<ObjectClassInfoBuilder> {

    AttributeInfoDelegate(ObjectClassInfoBuilder builder) {
        super(builder)
    }

    @Override
    Object invokeMethod(String name, Object args) {
        AttributeInfoBuilder infoBuilder = new AttributeInfoBuilder()
        infoBuilder.setName(name);
        def flags = EnumSet.noneOf(AttributeInfo.Flags.class)
        InvokerHelper.asList(args).each {
            if (it instanceof Class) {
                infoBuilder.setType(it as Class)
            } else if (it instanceof AttributeInfo.Flags) {
                flags.add(it as AttributeInfo.Flags)
            }
        }
        infoBuilder.setFlags(flags);
        ((ObjectClassInfoBuilder) builder).addAttributeInfo(infoBuilder.build())
    }
}
