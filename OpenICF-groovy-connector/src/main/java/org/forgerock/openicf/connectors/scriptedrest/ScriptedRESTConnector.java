/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS All rights reserved.
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
package org.forgerock.openicf.connectors.scriptedrest;

import java.util.Set;

import org.forgerock.openicf.misc.scriptedcommon.OperationType;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;

import groovy.lang.Binding;

/**
 * Main implementation of the ScriptedREST Connector.
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */
@ConnectorClass(displayNameKey = "groovy.rest.connector.display",
        configurationClass = ScriptedRESTConfiguration.class,
        messageCatalogPaths = {"org/forgerock/openicf/connectors/groovy/Messages",
                "org/forgerock/openicf/connectors/scriptedrest/Messages"})
public class ScriptedRESTConnector extends ScriptedConnectorBase<ScriptedRESTConfiguration>
        implements Connector {

    protected Binding createBinding(Binding arguments, OperationType action, ObjectClass objectClass,
                                    Uid uid, Set<Attribute> attributes, OperationOptions options) {
        Binding b = super.createBinding(arguments, action, objectClass, uid, attributes, options);
        b.setVariable(CONNECTION, ((ScriptedRESTConfiguration) getScriptedConfiguration())
                .getRESTClient());
        return b;
    }
}
