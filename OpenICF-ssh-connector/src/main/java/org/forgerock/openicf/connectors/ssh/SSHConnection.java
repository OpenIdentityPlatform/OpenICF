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

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import expect4j.Expect4j;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

/**
 * Class to represent an SSH Connection
 */
public class SSHConnection {

    protected static enum AuthN {PASSWORD, PUBKEY}

    /**
     * Setup logging for the {@link SSHConnection}.
     */
    private static final Log logger = Log.getLog(SSHConnection.class);

    /**
     * the configuration object from which this connection is created.
     */
    private final SSHConfiguration configuration;

    private Session session;
    private ChannelShell channel;
    private Expect4j expect4j;

    public SSHConnection(SSHConfiguration configuration) {
        this.configuration = configuration;
        switch (AuthN.valueOf(configuration.getAuthenticationType().toUpperCase())) {
            case PASSWORD:
                channel = createPasswordConnection(configuration.getHost(), configuration.getPort(), configuration.getUser());
                break;
            case PUBKEY:
                channel = createPubKeyConnection(configuration.getHost(), configuration.getPort(), configuration.getUser());
        }
        channel.setPtyType(configuration.getTerminalType());

        try {
            expect4j = new Expect4j(channel.getInputStream(), channel.getOutputStream());
            channel.connect(configuration.getConnectionTimeout() * 1000);

            if (configuration.isEchoOff()) {
                logger.info("Turning Off command echo");
                expect4j.expect(configuration.getPrompt());
                expect4j.send("stty -echo\r");
            }
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    public Session getSession() {
        return session;
    }

    public ChannelShell getChannel() {
        return channel;
    }

    public Expect4j getExpect() {
        return expect4j;
    }

    public void dispose() {
        if (expect4j != null) {
            expect4j.close();
            expect4j = null;
        }

        if (channel != null) {
            channel.disconnect();
        }

        if (session != null) {
            session.disconnect();
        }
    }

    protected ChannelShell createPasswordConnection(final String host, final int port, final String user) {
        final JSch jsch = new JSch();
        try {
            configuration.getPassword().access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    try {
                        session = jsch.getSession(user, host, port);
                        session.setPassword(new String(clearChars));
                        session.setConfig("StrictHostKeyChecking", "no");
                        session.setDaemonThread(true);
                        logger.info("Connecting to {0}:{1}", host, port);
                        session.connect(configuration.getConnectionTimeout() * 1000);
                        logger.ok("Connected");
                    } catch (JSchException e) {
                        logger.error("Unable to connect to {0}:{1}: {2}", host, port, e.getMessage());
                        throw ConnectorException.wrap(e);
                    } finally {
                        session.setPassword(""); // cleaning password, as it is no longer used
                    }
                }
            });

            return (ChannelShell) session.openChannel("shell");
        } catch (Exception ex) {
            throw ConnectorException.wrap(ex);
        }
    }

    private ChannelShell createPubKeyConnection(final String host, final int port, final String user) {
        final JSch jsch = new JSch();
        try {
            configuration.fetchPrivateKey().access(new GuardedString.Accessor() {
                public void access(final char[] privateKey) {
                    configuration.getPassphrase().access(new GuardedString.Accessor() {
                        public void access(final char[] passphrase) {
                            try {
                                jsch.addIdentity("SSHConnector", charsToBytes(privateKey),
                                        null, charsToBytes(passphrase));
                                session = jsch.getSession(user, host, port);
                                session.setConfig("StrictHostKeyChecking", "no");
                                session.setDaemonThread(true);
                                logger.info("Connecting to {0}:{1}", host, port);
                                session.connect(configuration.getConnectionTimeout() * 1000);
                                logger.ok("Connected");
                            } catch (JSchException e) {
                                logger.error("Unable to connect to {0}:{1}: {2}", host, port, e.getMessage());
                                throw ConnectorException.wrap(e);
                            } finally {
                                try {
                                    jsch.removeAllIdentity();
                                } catch (JSchException e) {
                                }
                            }
                        }
                    });
                }
            });

            return (ChannelShell) session.openChannel("shell");
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    private byte[] charsToBytes(char[] text) {
        byte[] bytes = new byte[text.length];
        for (int i = 0; i < text.length; i++) {
            bytes[i] = (byte) text[i];
        }
        return bytes;
    }
}