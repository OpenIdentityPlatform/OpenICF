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


package org.forgerock.openicf.connectors.ssh;

import expect4j.Expect4j;
import groovy.lang.Binding;
import groovy.lang.Closure;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.Configuration;

import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;

/**
 * Main implementation of the SSH Connector.
 *
 */
@ConnectorClass(displayNameKey = "SSH.connector.display",
        configurationClass = SSHConfiguration.class,
        messageCatalogPaths = {"org/forgerock/openicf/connectors/groovy/Messages",
            "org/forgerock/openicf/connectors/ssh/Messages" })
public class SSHConnector extends ScriptedConnectorBase<SSHConfiguration> implements PoolableConnector{

    /**
     * Setup logging for the {@link SSHConnector}.
     */
    private static final Log logger = Log.getLog(SSHConnector.class);
    
    private SSHConfiguration configuration;
    
    private SSHConnection connection;

    private E4JWrapper wrapper;
    
    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @param config
     *            the new {@link Configuration}
     */
    @Override
    public void init(final Configuration config) {
        super.init(config);
        this.configuration = (SSHConfiguration)config;
        configuration.initGroovyEngine();
        this.connection = new SSHConnection(configuration);
        this.wrapper = new E4JWrapper(connection.getExpect(), configuration);
        // TODO: Pass appropriate args
        this.configuration.callOnCreateConnection();
    }

    @Override
    public void checkAlive() {
        //Check if the Connection is still active
        try {
            connection.getSession().sendKeepAliveMsg();
        } catch (Exception e){
            throw new ConnectionBrokenException("Failed to send Keep-Alive message");
        }
    }

    /**
     * Disposes of {@link SSHConnector}'s resources.
     *
     * {@see org.identityconnectors.framework.spi.Connector#dispose()}
     */
    @Override
    public void dispose() {
        //Release all allocated resource of Connection
        logger.info("dispose()");
        configuration.callOnCloseConnection(connection);
        connection.dispose();
    }


    /**
     *
     * @param scriptName
     * @param arguments The Groovy bindings.
     * @param scriptEvaluator
     * @return The result of the script
     * @throws Exception
     */
    @Override
    protected Object evaluateScript(String scriptName, Binding arguments,
                                    Closure<Object> scriptEvaluator) throws Exception {
        try {
            arguments.setVariable(CONNECTION, connection);
            arguments.setVariable(LOGGER, logger);
            arguments.setVariable("sudo", wrapper.getSudo());
            arguments.setVariable("send", wrapper.getSender());
            arguments.setVariable("sendln", wrapper.getSenderln());
            arguments.setVariable("sendControlC", wrapper.getSendControlC());
            arguments.setVariable("sendControlD", wrapper.getSendControlD());
            arguments.setVariable("expect", wrapper.getExpectGlobal());
            arguments.setVariable("setTimeout", wrapper.getGlobalTimeout());
            arguments.setVariable("setTimeoutSec", wrapper.getGlobalTimeoutSec());
            arguments.setVariable("match", wrapper.getGlobalMatch());
            arguments.setVariable("regexp", wrapper.getRegexpMatch());
            arguments.setVariable("timeout", wrapper.getTimeoutMatch());
            arguments.setVariable("eof", wrapper.getEofMatch());
            arguments.setVariable("promptReady", wrapper.getPromptReady());
            arguments.setVariable("TIMEOUT_FOREVER", Expect4j.TIMEOUT_FOREVER);
            arguments.setVariable("TIMEOUT_NEVER", Expect4j.TIMEOUT_NEVER);
            arguments.setVariable("TIMEOUT_EXPIRED", Expect4j.RET_TIMEOUT);
            arguments.setVariable("EOF_FOUND", Expect4j.RET_EOF);

            return scriptEvaluator.call(scriptName, arguments);
        } finally {
            logger.ok("Done");
        }
    }
}