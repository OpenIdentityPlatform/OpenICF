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

package org.forgerock.openicf.connectors.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import expect4j.Expect4j
import org.codehaus.groovy.runtime.InvokerHelper
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.Assertions
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.spi.AbstractConfiguration
import org.identityconnectors.framework.spi.ConfigurationClass
import org.identityconnectors.framework.spi.ConfigurationProperty

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the SSH Connector.
 *
 */
@ConfigurationClass(skipUnsupported = true)
public class SSHConfiguration extends ScriptedConfiguration {

    /**
     * Setup logging for the {@link SSHConfiguration}.
     */
    private static final Log logger = Log.getLog(SSHConfiguration.class);

    /**
     * Enum for authentication method
     */
    private static final enum AuthN {
        PASSWORD, PUBKEY
    }

    // Exposed configuration properties.

    /**
     * The host to connect to.
     */
    private String host;

    /**
     * Port number used to communicate with the resource. This value may depend
     * on the value of Connection Type.
     * The standard value for SSH is 22.
     */
    private int port = 22;

    /**
     * The user to authenticate with.
     */
    private String user = null;

    /**
     * The Password used for PASSWORD authentication
     */
    private GuardedString password = null;

    /**
     * The passphrase used for PUBKEY authentication
     */
    private GuardedString passphrase = null;

    /**
     * The private key used for PUBKEY authentication
     */
    private GuardedString privateKey = null

    /**
     * The default prompt
     */
    private String prompt = "root@localhost:# "

    /**
     * The sudo command
     */
    private String sudoCommand = "/usr/bin/sudo -k"

    /**
     * The sudo password prompt
     */
    private String sudoPwdPrompt = "assword for "

    /**
     * Input command echo on/off
     */
    private boolean echoOff = true

    /**
     * Terminal type
     */
    private String terminalType = "vt102"

    /**
     * Connection timeout in milliseconds
     */
    private int connectionTimeout = 5000

    /**
     * Global timeout in milliseconds
     * This timeout is used by expect/send
     */
    private long globalTimeout = 5000

    /**
     * Authentication type
     */
    private String authenticationType = AuthN.PASSWORD.toString()

    /**
     * Constructor.
     */
    public SSHConfiguration() {
    }


    @ConfigurationProperty(order = 1, displayMessageKey = "host.display",
            groupMessageKey = "basic.group", helpMessageKey = "host.help",
            required = true, confidential = false)
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "port.display",
            groupMessageKey = "basic.group", helpMessageKey = "port.help",
            required = true, confidential = false)
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "user.display",
            groupMessageKey = "basic.group", helpMessageKey = "user.help",
            required = true, confidential = false)
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "password.display",
            groupMessageKey = "basic.group", helpMessageKey = "password.help",
            confidential = true)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "passphrase.display",
            groupMessageKey = "basic.group", helpMessageKey = "passphrase.help",
            confidential = true)
    public GuardedString getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(GuardedString passphrase) {
        this.passphrase = passphrase;
    }

    @ConfigurationProperty(order = 5, displayMessageKey = "privateKey.display",
            groupMessageKey = "basic.group", helpMessageKey = "privateKey.help",
            confidential = true)
    public GuardedString getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(GuardedString privateKey) {
        this.privateKey = privateKey;
    }


    @ConfigurationProperty(order = 6, displayMessageKey = "authenticationType.display",
            groupMessageKey = "basic.group", helpMessageKey = "authenticationType.help",
            required = true, confidential = false)
    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    @ConfigurationProperty(order = 7, displayMessageKey = "prompt.display",
            groupMessageKey = "basic.group", helpMessageKey = "prompt.help",
            required = true, confidential = false)
    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    @ConfigurationProperty(order = 8, displayMessageKey = "sudoCommand.display",
            groupMessageKey = "basic.group", helpMessageKey = "sudoCommand.help",
            required = true, confidential = false)
    public String getSudoCommand() {
        return sudoCommand;
    }

    public void setSudoCommand(String sudoCommand) {
        this.sudoCommand = sudoCommand;
    }

    @ConfigurationProperty(order = 9, displayMessageKey = "sudoPwdPrompt.display",
            groupMessageKey = "basic.group", helpMessageKey = "sudoPwdPrompt.help",
            required = true, confidential = false)
    public String getsudoPwdPrompt() {
        return sudoPwdPrompt;
    }

    public void setSudoPwdPrompt(String sudoPwdPrompt) {
        this.sudoPwdPrompt = sudoPwdPrompt;
    }

    @ConfigurationProperty(order = 10, displayMessageKey = "echoOff.display",
            groupMessageKey = "basic.group", helpMessageKey = "echoOff.help",
            required = true, confidential = false)
    public boolean isEchoOff() {
        return echoOff;
    }

    public void setEchoOff(boolean echoOff) {
        this.echoOff = echoOff;
    }

    @ConfigurationProperty(order = 11, displayMessageKey = "terminalType.display",
            groupMessageKey = "basic.group", helpMessageKey = "terminalType.help",
            required = true, confidential = false)
    public String getTerminalType() {
        return terminalType;
    }

    public void setTerminalType(String ttype) {
        this.terminalType = ttype;
    }

    @ConfigurationProperty(order = 12, displayMessageKey = "connectionTimeout.display",
            groupMessageKey = "basic.group", helpMessageKey = "connectionTimeout.help",
            required = true, confidential = false)
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    @ConfigurationProperty(order = 13, displayMessageKey = "globalTimeout.display",
            groupMessageKey = "basic.group", helpMessageKey = "globalTimeout.help",
            required = true, confidential = false)
    public long getGlobalTimeout() {
        return globalTimeout;
    }

    public void setGlobalTimeout(long timeout) {
        this.globalTimeout = timeout;
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {
        if (StringUtil.isBlank(host)) {
            throw new IllegalArgumentException("Host cannot be null or empty.");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port is invalid.");
        }

        Assertions.blankCheck(user, "user");
        Assertions.nullCheck(password, "password");
    }

    private Closure init = null;
    private Closure onCreateConnection = null;
    private Closure onCloseConnection = null;


    protected Object callInit(Object... args) {
        if (null != init) {
            Closure clone = init.rehydrate(this, this, this);
            clone.setResolveStrategy(Closure.DELEGATE_FIRST);
            return clone(args);
        }
    }

    protected Object callOnCreateConnection(Object... args) {
        if (null != onCreateConnection) {
            Closure clone = onCreateConnection.rehydrate(this, this, this);
            clone.setResolveStrategy(Closure.DELEGATE_FIRST);
            return clone(args)
        }
    }

    protected Object callOnCloseConnection(Object... args) {
        if (null != onCloseConnection) {
            Closure clone = onCloseConnection.rehydrate(this, this, this);
            clone.setResolveStrategy(Closure.DELEGATE_FIRST);
            return clone(args)
        }
    }


    protected Script createCustomizerScript(Class customizerClass, Binding binding) {

        customizerClass.metaClass.customize << { Closure cl ->
            init = null
            onCreateConnection = null
            onCloseConnection = null

            def delegate = [
                    init              : { Closure paramClosure ->
                        init = paramClosure
                    },
                    release           : { Closure paramClosure ->
                        setReleaseClosure(paramClosure)
                    },
                    onCreateConnection: { Closure paramClosure ->
                        onCreateConnection = paramClosure
                    },
                    onCloseConnection : { Closure paramClosure ->
                        onCloseConnection = paramClosure
                    }
            ]
            cl.setDelegate(new Reference(delegate));
            cl.setResolveStrategy(Closure.DELEGATE_FIRST);
            cl.call();
        }

        return InvokerHelper.createScript(customizerClass, binding);
    }

    public initGroovyEngine() {
        //Make sure the Customizer Script is called
        getGroovyScriptEngine();
    }

    SSHConnection createSSHConnection() {

        JSch jsch = new JSch();
        ChannelShell channel = null;
        Expect4j expect4j = null;

        try {
            final Session session = jsch.getSession(getUser(), getHost(), getPort());

            getPassword().access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    session.setPassword(new String(clearChars));
                    java.util.Hashtable<Object, Object> config =
                            new java.util.Hashtable<Object, Object>();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    session.setDaemonThread(true);

                    try {
                        logger.info("Connecting to {0}:{1}", getHost(), getPort());
                        session.connect();
                        logger.ok("Connected");
                    } catch (JSchException e) {
                        logger.error("Unable to connect to {0}:{1}: {2}", getHost(), getPort(), e.getMessage());
                        throw ConnectorException.wrap(e);
                    } finally {
                        session.setPassword(""); // cleaning password, as it is no longer used
                    }
                }
            });

            channel = (ChannelShell) session.openChannel("shell");
            channel.setPtyType(terminalType);

            expect4j = new Expect4j(channel.getInputStream(), channel.getOutputStream());

            channel.connect(connectionTimeout);
            //logger.info("Setting Expect4j timeout to {0}", globalTimeout)
            //expect4j.setDefaultTimeout(globalTimeout)

            if (echoOff) {
                logger.info("Turning Off command echo")
                expect4j.expect(prompt)
                expect4j.send("stty -echo\r")
            }
            // Call Custom script
            callOnCreateConnection(session, channel, expect4j)
            return new SSHConnection(session, channel, expect4j)
        } catch (Exception ex) {
            throw ConnectorException.wrap(ex);
        }
    }
}