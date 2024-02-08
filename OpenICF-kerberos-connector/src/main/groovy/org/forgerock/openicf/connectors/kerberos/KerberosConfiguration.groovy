/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for
 * the specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file
 * and include the License file at legal/CDDLv1.0.txt. If applicable, add the following
 * below the CDDL Header, with the fields enclosed by brackets [] replaced by your
 * own identifying information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openicf.connectors.kerberos

import org.forgerock.openicf.connectors.ssh.SSHConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.spi.AbstractConfiguration
import org.identityconnectors.framework.spi.ConfigurationClass
import org.identityconnectors.framework.spi.ConfigurationProperty

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the Kerberos Connector.
 *
 */
@ConfigurationClass(skipUnsupported = true)
public class KerberosConfiguration extends SSHConfiguration {

    /**
     * Setup logging for the {@link KerberosConfiguration}.
     */
    private static final Log logger = Log.getLog(KerberosConfiguration.class);

    /**
     * Enum for authentication method
     */
    private static final enum AuthN {
        PASSWORD, PUBKEY
    }

    /**
     * Constructor.
     */
    public KerberosConfiguration() {
    }

    @ConfigurationProperty(order = 1, displayMessageKey = "host.display",
            groupMessageKey = "basic.group", helpMessageKey = "host.help",
            required = true, confidential = false)
    public String getHost() {
        return super.getHost();
    }

    public void setHost(String host) {
        super.setHost(host);
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "port.display",
            groupMessageKey = "basic.group", helpMessageKey = "port.help",
            required = true, confidential = false)
    public int getPort() {
        return super.getPort();
    }

    public void setPort(int port) {
        super.setPort(port);
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "user.display",
            groupMessageKey = "basic.group", helpMessageKey = "user.help",
            required = true, confidential = false)
    public String getUser() {
        return super.getUser();
    }

    public void setUser(String user) {
        super.setUser(user);
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "password.display",
            groupMessageKey = "basic.group", helpMessageKey = "password.help",
            confidential = true)
    public GuardedString getPassword() {
        return super.getPassword();
    }

    public void setPassword(GuardedString password) {
        super.setPassword(password);
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "passphrase.display",
            groupMessageKey = "basic.group", helpMessageKey = "passphrase.help",
            confidential = true)
    public GuardedString getPassphrase() {
        return super.getPassphrase();
    }

    public void setPassphrase(GuardedString passphrase) {
        super.setPassphrase(passphrase);
    }

    @ConfigurationProperty(order = 5, displayMessageKey = "privateKey.display",
            groupMessageKey = "basic.group", helpMessageKey = "privateKey.help",
            confidential = true)
    public String[] getPrivateKey() {
        return super.getPrivateKey();
    }

    public void setPrivateKey(String[] privateKey) {
        super.setPrivateKey(privateKey);
    }


    @ConfigurationProperty(order = 6, displayMessageKey = "authenticationType.display",
            groupMessageKey = "basic.group", helpMessageKey = "authenticationType.help",
            required = true, confidential = false)
    public String getAuthenticationType() {
        return super.getAuthenticationType();
    }

    public void setAuthenticationType(String authenticationType) {
        super.setAuthenticationType(authenticationType);
    }

    @ConfigurationProperty(order = 7, displayMessageKey = "prompt.display",
            groupMessageKey = "basic.group", helpMessageKey = "prompt.help",
            required = true, confidential = false)
    public String getPrompt() {
        return super.getPrompt();
    }

    public void setPrompt(String prompt) {
        super.setPrompt(prompt);
    }

    @ConfigurationProperty(order = 8, displayMessageKey = "sudoCommand.display",
            groupMessageKey = "basic.group", helpMessageKey = "sudoCommand.help",
            required = true, confidential = false)
    public String getSudoCommand() {
        return super.getSudoCommand();
    }

    public void setSudoCommand(String sudoCommand) {
        super.setSudoCommand(sudoCommand);
    }

    @ConfigurationProperty(order = 9, displayMessageKey = "echoOff.display",
            groupMessageKey = "basic.group", helpMessageKey = "echoOff.help",
            required = true, confidential = false)
    public boolean isEchoOff() {
        return super.isEchoOff();
    }

    public void setEchoOff(boolean echoOff) {
        super.setEchoOff(echoOff);
    }

    @ConfigurationProperty(order = 10, displayMessageKey = "terminalType.display",
            groupMessageKey = "basic.group", helpMessageKey = "terminalType.help",
            required = true, confidential = false)
    public String getTerminalType() {
        return super.getTerminalType();
    }

    public void setTerminalType(String ttype) {
        super.setTerminalType(ttype);
    }

    @ConfigurationProperty(order = 11, displayMessageKey = "locale.display",
            groupMessageKey = "basic.group", helpMessageKey = "locale.help",
            required = true, confidential = false)
    public String getLocale() {
        return super.getLocale();
    }

    public void setLocale(String locale) {
        super.setLocale(locale);
    }

    @ConfigurationProperty(order = 12, displayMessageKey = "setLocale.display",
            groupMessageKey = "basic.group", helpMessageKey = "setLocale.help",
            required = true, confidential = false)
    public boolean isSetLocale() {
        return super.isSetLocale();
    }

    public void setSetLocale(boolean setLocale) {
        super.setSetLocale(setLocale);
    }

    @ConfigurationProperty(order = 13, displayMessageKey = "connectionTimeout.display",
            groupMessageKey = "basic.group", helpMessageKey = "connectionTimeout.help",
            required = true, confidential = false)
    public int getConnectionTimeout() {
        return super.getConnectionTimeout();
    }

    public void setConnectionTimeout(int timeout) {
        super.setConnectionTimeout(timeout);
    }

    @ConfigurationProperty(order = 14, displayMessageKey = "expectTimeout.display",
            groupMessageKey = "basic.group", helpMessageKey = "expectTimeout.help",
            required = true, confidential = false)
    public long getExpectTimeout() {
        return super.getExpectTimeout();
    }

    public void setExpectTimeout(long timeout) {
        super.setExpectTimeout(timeout);
    }

    @ConfigurationProperty(order = 15, displayMessageKey = "throwOperationTimeoutException.display",
            groupMessageKey = "basic.group", helpMessageKey = "throwOperationTimeoutException.help",
            required = true, confidential = false)
    public boolean isThrowOperationTimeoutException() {
        return super.isThrowOperationTimeoutException();
    }

    public void setThrowOperationTimeoutException(boolean throwOperationTimeoutException) {
        super.setThrowOperationTimeoutException(throwOperationTimeoutException);
    }

    public void validate() {
        super.validate();
    }

    public GuardedString fetchPrivateKey() {
        return super.fetchPrivateKey();
    }

    public initGroovyEngine() {
        super.initGroovyEngine();
    }
}