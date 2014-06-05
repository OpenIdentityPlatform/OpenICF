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

package org.forgerock.openicf.connectors.groovy;

import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;

/**
 * A ScriptedConnector is a generic Groovy Connector.
 *
 * @author Laszlo Hordos
 */
@ConnectorClass(displayNameKey = "groovy.connector.display",
        configurationClass = ScriptedConfiguration.class)
public class ScriptedConnector extends ScriptedConnectorBase<ScriptedConfiguration> implements
        Connector {
}
