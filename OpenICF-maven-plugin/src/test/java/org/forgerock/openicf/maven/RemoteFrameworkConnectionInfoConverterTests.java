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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.maven;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;
import org.identityconnectors.framework.impl.api.remote.RemoteFrameworkConnection;
import org.testng.annotations.Test;

/**
 * The plugin trusts every connector server certificate, so the framework's
 * hostname verification must not reject a server whose certificate does not
 * name the configured host. {@code KeyStore.jks} holds a certificate with
 * {@code CN=localhost} and no subjectAltName, which JSSE never matches against
 * {@code 127.0.0.1}.
 */
public class RemoteFrameworkConnectionInfoConverterTests {

    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final int TIMEOUT = 10000;

    @Test
    public void trustAllConnectionSkipsHostnameVerification() throws Exception {
        assertNull(System.getProperty(RemoteFrameworkConnection.HOSTNAME_VERIFICATION_PROPERTY),
                "hostname verification must be on for this test");
        try (TlsServer server = new TlsServer(loadKeyStore("KeyStore.jks"),
                InetAddress.getByName("127.0.0.1"))) {
            new RemoteFrameworkConnection(new RemoteFrameworkConnectionInfo("127.0.0.1",
                    server.getPort(), new GuardedString(PASSWORD), true,
                    new RemoteFrameworkConnectionInfoConverter().getTrustManager(), TIMEOUT))
                    .close();
        }
    }

    private static KeyStore loadKeyStore(String name) throws Exception {
        try (InputStream in =
                RemoteFrameworkConnectionInfoConverterTests.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, "missing test resource " + name);
            KeyStore store = KeyStore.getInstance("JKS");
            store.load(in, PASSWORD);
            return store;
        }
    }

    /**
     * Minimal TLS server: completes the handshake for every accepted
     * connection and then waits for the client to hang up.
     */
    private static final class TlsServer implements AutoCloseable {
        private final SSLServerSocket serverSocket;

        TlsServer(KeyStore store, InetAddress bindAddress) throws Exception {
            KeyManagerFactory factory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, PASSWORD);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            serverSocket = (SSLServerSocket) context.getServerSocketFactory()
                    .createServerSocket(0, 1, bindAddress);
            Thread acceptor = new Thread(this::serve, "RemoteFrameworkConnectionInfoConverterTests-server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    socket.setSoTimeout(TIMEOUT);
                    socket.startHandshake();
                    socket.getInputStream().read();
                } catch (IOException e) {
                    // handshake rejected by the client or server closed: next connection
                }
            }
        }

        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
