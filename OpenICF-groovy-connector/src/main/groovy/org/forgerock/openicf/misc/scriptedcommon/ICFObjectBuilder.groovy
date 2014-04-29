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

import groovy.transform.CompileStatic
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.Schema
import org.identityconnectors.framework.common.objects.SchemaBuilder
import org.identityconnectors.framework.common.objects.SyncDelta
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder
import org.identityconnectors.framework.spi.Connector

/**
 * An ICFObjectBuilder supports Groovy DSL Builder for OpenICF.
 *
 * @author Laszlo Hordos
 */
@CompileStatic
class ICFObjectBuilder extends AbstractICFBuilder<Void> {

    private final Class<? extends Connector> connectorClass;

    ICFObjectBuilder(Class<? extends Connector> connectorClass) {
        super(null)
        this.connectorClass = connectorClass
    }

    static
    private <B> AbstractICFBuilder<B> delegateToTag(Class<? extends AbstractICFBuilder<B>> clazz, Closure body, B builder) {
        AbstractICFBuilder<B> tag = (AbstractICFBuilder<B>) clazz.newInstance(builder)
        def clone = body.rehydrate(tag, this, this)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        return tag
    }

    static ConnectorObject co(@DelegatesTo(ConnectorObjectDelegate) Closure attribute) {
        delegateToTag(ConnectorObjectDelegate, attribute, new ConnectorObjectBuilder()).builder.build();
    }

    static SyncDelta delta(@DelegatesTo(SyncDeltaDelegate) Closure attribute) {
        delegateToTag(SyncDeltaDelegate, attribute, new SyncDeltaBuilder()).builder.build();
    }

    Schema schema(@DelegatesTo(SchemaDelegate) Closure attribute) {
        delegateToTag(SchemaDelegate, attribute, new SchemaBuilder(connectorClass)).builder.build();
    }
}
