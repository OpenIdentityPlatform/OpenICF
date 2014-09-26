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

import org.identityconnectors.common.security.GuardedByteArray
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeBuilder
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.Uid

/**
 * A ConnectorObjectDelegate ...
 *
 * @author Laszlo Hordos
 */
class ConnectorObjectDelegate extends AbstractICFBuilder<ConnectorObjectBuilder> {

    @Delegate
    private final ConnectorObjectBuilder builder;

    ConnectorObjectDelegate(ConnectorObjectBuilder builder) {
        super(builder)
        this.builder = builder;
    }

    void uid(String uid) {
        ((ConnectorObjectBuilder) builder).setUid(uid);
    }

    void uid(Uid uid) {
        ((ConnectorObjectBuilder) builder).setUid(uid);
    }

    void uid(String uid, String revision) {
        ((ConnectorObjectBuilder) builder).setUid(new Uid(uid, revision));
    }

    void id(String id) {
        ((ConnectorObjectBuilder) builder).setName(id);
    }

    void objectClass(String objectClass) {
        ((ConnectorObjectBuilder) builder).setObjectClass(new ObjectClass(objectClass));
    }

    void objectClass(ObjectClass objectClass) {
        ((ConnectorObjectBuilder) builder).setObjectClass(objectClass);
    }

    void attribute(@DelegatesTo(AttributeDelegate) Closure attribute) {
        delegateToTag(AttributeDelegate, attribute)
    }

    void attribute(String name, Collection<Object> args) {
        if (null != args) {
            ((ConnectorObjectBuilder) builder).addAttribute(name, args)
        } else {
            ((ConnectorObjectBuilder) builder).addAttribute(AttributeBuilder.build(name))
        }
    }
    
    void attribute(String name, String... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, long... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Long... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, char... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Character... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, double... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Double... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, float... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Float... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, int... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Integer... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, boolean... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Boolean... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, byte... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Byte... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, byte[]... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, BigDecimal... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, BigInteger... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, GuardedByteArray... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, GuardedString... args) {
        addConnectorAttribute(name, args)
    }

    void attribute(String name, Map... args) {
        addConnectorAttribute(name, args)
    }
    
    private void addConnectorAttribute(String name, Object... args) {
        if (null != args) {
            ((ConnectorObjectBuilder) builder).addAttribute(name, args.toList())
        } else {
            ((ConnectorObjectBuilder) builder).addAttribute(AttributeBuilder.build(name))
        }
    }

    void attribute(String name) {
        ((ConnectorObjectBuilder) builder).addAttribute(name)
    }

    void attribute(Attribute... attrs) {
        ((ConnectorObjectBuilder) builder).addAttribute(attrs)
    }
    
    void attributes(Attribute... attrs) {
        ((ConnectorObjectBuilder) builder).addAttribute(attrs)
    }

    void attributes(Collection<Attribute> attrs) {
        ((ConnectorObjectBuilder) builder).addAttributes(attrs)
    }
}
