/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openicf.connectors.kerberos;

import groovy.lang.Binding;
import groovy.lang.Closure;
import org.forgerock.openicf.connectors.ssh.SSHConnector;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;

/**
 * Main implementation of the ScriptedKerberosConnector Connector.
 *
 * @author ForgeRock
 * @description Kerberos connector used to connect to kerberos systems via ssh
 * @version 1.0
 */
@ConnectorClass(displayNameKey = "kerberos.connector.display",
        configurationClass = KerberosConfiguration.class,
        messageCatalogPaths = {
                "org/forgerock/openicf/connectors/groovy/Messages",
                "org/forgerock/openicf/connectors/ssh/Messages",
                "org/forgerock/openicf/connectors/kerberos/Messages"
        })
public class KerberosConnector extends SSHConnector implements Connector {

    @Override
    public void init(final Configuration config) {
        super.init(config);
    }

    @Override
    public void checkAlive() {
        super.checkAlive();
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    protected Object evaluateScript(String scriptName, Binding arguments,
            Closure<Object> scriptEvaluator) throws Exception {
        return super.evaluateScript(scriptName, arguments, scriptEvaluator);
    }
}

