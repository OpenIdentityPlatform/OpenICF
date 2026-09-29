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
package org.forgerock.openicf.framework.server;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singleton;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.DEFAULT_GUARDED_PASSWORD;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.JSK_PASSWORD;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.KEY_HASH;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.TEST_CONNECTOR_KEY;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.buildRemoteWSFrameworkConnectionInfo;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.findFreePort;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.ClientRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.client.ConnectionManager;
import org.forgerock.openicf.framework.client.ConnectionManagerConfig;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * The WebSocket client must check the connector server certificate against
 * the host of the remote URI. {@code serverKeystore.jks} names
 * {@code localhost}, {@code 127.0.0.1} and {@code ::1} as subjectAltName;
 * {@code serverKeystore-cn-only.jks} only has {@code CN=localhost}. Both are
 * trusted by {@code truststore.jks}, which the client picks up through the
 * {@code javax.net.ssl.trustStore} system properties.
 */
public class ClientHostnameVerificationTest {

    private static final long TIMEOUT_SECONDS = 30;

    private final int sanPort = findFreePort();
    private final int cnOnlyPort = findFreePort();

    private ConnectorServer connectorServer;

    /**
     * Like the other tests of this module, the server lives for the whole
     * run: every {@link ConnectorServer} registers with the JVM-wide Grizzly
     * {@code WebSocketEngine}, and unregistering one while other test
     * servers are still in use leaves their WebSocket upgrades unanswered.
     */
    @BeforeTest
    public void startServer() throws Exception {
        String truststore = resourcePath("truststore.jks");
        System.setProperty(SSLContextConfigurator.TRUST_STORE_FILE, truststore);
        System.setProperty(SSLContextConfigurator.TRUST_STORE_PASSWORD, JSK_PASSWORD);

        connectorServer = new ConnectorServer();
        connectorServer.setConnectorFrameworkFactory(new ConnectorFrameworkFactory());
        connectorServer.setConnectorBundleURLs(Arrays.asList(TstConnector.class
                .getProtectionDomain().getCodeSource().getLocation()));
        connectorServer.init();
        connectorServer.addListener("san", NetworkListener.DEFAULT_NETWORK_HOST, sanPort,
                serverSSLContext("serverKeystore.jks", truststore));
        connectorServer.addListener("cn-only", NetworkListener.DEFAULT_NETWORK_HOST, cnOnlyPort,
                serverSSLContext("serverKeystore-cn-only.jks", truststore));
        connectorServer.setKeyHash(KEY_HASH);
        connectorServer.start();
    }

    @AfterTest
    public void stopServer() throws Exception {
        connectorServer.stop();
        connectorServer.destroy();
    }

    @Test
    public void rejectsServerCertificateWithoutMatchingName() throws Exception {
        try (Client client = new Client(new ConnectionManagerConfig())) {
            try {
                client.connect(cnOnlyPort);
                fail("Expected the handshake to fail: certificate has no name matching 127.0.0.1");
            } catch (ConnectorException e) {
                assertHostnameVerificationFailure(e);
            }
        }
    }

    @Test
    public void acceptsServerCertificateWithMatchingSubjectAltName() throws Exception {
        try (Client client = new Client(new ConnectionManagerConfig())) {
            assertNotNull(client.connect(sanPort));
        }
    }

    @Test
    public void connectsToMismatchedCertificateWhenVerificationIsDisabled() throws Exception {
        ConnectionManagerConfig config = new ConnectionManagerConfig();
        config.setHostnameVerification(false);
        try (Client client = new Client(config)) {
            assertNotNull(client.connect(cnOnlyPort));
        }
    }

    /**
     * Through a CONNECT proxy the socket peer is the proxy, not the connector
     * server, and the certificate must still be checked against the host of
     * the remote URI: {@code CN=localhost} passes for {@code localhost} (with
     * no subjectAltName JSSE falls back to the CN for a DNS name), while the
     * proxy's {@code 127.0.0.1} would need an IP subjectAltName and fail.
     */
    @Test
    public void verifiesRemoteUriHostRatherThanProxy() throws Exception {
        try (ConnectProxy proxy = new ConnectProxy();
                Client client = new Client(new ConnectionManagerConfig())) {
            RemoteWSFrameworkConnectionInfo info =
                    RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                            URI.create("wss://localhost:" + cnOnlyPort + "/openicf"))
                            .setPrincipal("secure").setPassword(DEFAULT_GUARDED_PASSWORD)
                            .setProxyHost("127.0.0.1").setProxyPort(proxy.getPort()).build();
            assertNotNull(client.connect(info));
            // the client keeps more than one connection, each through its own tunnel
            assertEquals(new HashSet<>(proxy.getTargets()), singleton("localhost:" + cnOnlyPort));
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static void assertHostnameVerificationFailure(ConnectorException e) {
        Throwable cause = e;
        while (cause != null && !(cause instanceof SSLHandshakeException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Expected an SSLHandshakeException in the cause chain of: " + e);
        String message = String.valueOf(cause.getMessage());
        assertTrue(message.contains("subject alternative") || message.contains("No name matching"),
                "Expected a hostname verification failure, got: " + message);
    }

    private static SSLContextConfigurator serverSSLContext(String keystore, String truststore)
            throws Exception {
        SSLContextConfigurator configurator = new SSLContextConfigurator(false);
        configurator.setKeyStoreFile(resourcePath(keystore));
        configurator.setKeyStorePass(JSK_PASSWORD);
        configurator.setTrustStoreFile(truststore);
        configurator.setTrustStorePass(JSK_PASSWORD);
        configurator.setSecurityProtocol("TLS");
        return configurator;
    }

    private static String resourcePath(String name) throws Exception {
        URL url = ClientHostnameVerificationTest.class.getClassLoader().getResource(name);
        assertNotNull(url, "missing test resource " + name);
        return URLDecoder.decode(url.getFile(), "UTF-8");
    }

    /** A client-side framework with its own connection manager configuration. */
    private static final class Client implements AutoCloseable {
        private final ReferenceCountedObject<ConnectorFramework>.Reference framework;

        Client(ConnectionManagerConfig config) {
            framework = new ConnectorFrameworkFactory().acquire();
            framework.get().setConnectionManagerConfig(config);
        }

        /**
         * Opens the WebSocket to the server on {@code port} and, once it is
         * up, looks up the test connector over it, so that the initial
         * exchange has completed by the time the client is closed.
         */
        ConnectorInfo connect(int port) throws Exception {
            return connect(buildRemoteWSFrameworkConnectionInfo(true, port, null));
        }

        ConnectorInfo connect(RemoteWSFrameworkConnectionInfo info) throws Exception {
            ConnectionManager connectionManager =
                    (ConnectionManager) framework.get().getRemoteConnectionInfoManagerFactory();
            ClientRemoteConnectorInfoManager manager = connectionManager.connect(info);
            manager.connect().getOrThrow(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return manager.getAsyncConnectorInfoManager().findConnectorInfoAsync(
                    TEST_CONNECTOR_KEY).getOrThrow(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        public void close() {
            framework.release();
        }
    }

    /**
     * A minimal HTTP CONNECT proxy on {@code 127.0.0.1} that records the
     * authority of every tunnel it opens.
     */
    private static final class ConnectProxy implements AutoCloseable {
        private final ServerSocket serverSocket =
                new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        private final List<String> targets = new CopyOnWriteArrayList<>();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();

        ConnectProxy() throws IOException {
            start("connect-proxy", this::accept);
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        List<String> getTargets() {
            return targets;
        }

        private void accept() {
            while (!serverSocket.isClosed()) {
                try {
                    final Socket client = serverSocket.accept();
                    sockets.add(client);
                    start("connect-proxy-tunnel", () -> tunnel(client));
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void tunnel(Socket client) {
            try {
                String[] requestLine = readRequestLine(client.getInputStream()).split(" ");
                if (requestLine.length < 2 || !"CONNECT".equals(requestLine[0])) {
                    client.close();
                    return;
                }
                String authority = requestLine[1];
                targets.add(authority);
                int colon = authority.lastIndexOf(':');
                final Socket server = new Socket(authority.substring(0, colon),
                        Integer.parseInt(authority.substring(colon + 1)));
                sockets.add(server);
                client.getOutputStream().write(
                        "HTTP/1.0 200 Connection established\r\n\r\n".getBytes(US_ASCII));
                start("connect-proxy-pump", () -> pump(server, client));
                pump(client, server);
            } catch (IOException e) {
                closeQuietly(client);
            }
        }

        /** Reads the request up to the empty line and returns its first line. */
        private static String readRequestLine(InputStream in) throws IOException {
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("Connection closed inside the request header");
                }
                header.write(b);
                matched = b == "\r\n\r\n".charAt(matched) ? matched + 1 : (b == '\r' ? 1 : 0);
            }
            String text = header.toString("US-ASCII");
            return text.substring(0, text.indexOf("\r\n"));
        }

        private static void pump(Socket from, Socket to) {
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) >= 0;) {
                    out.write(buffer, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
                // either side went away
            } finally {
                closeQuietly(from);
                closeQuietly(to);
            }
        }

        private static void start(String name, Runnable task) {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.start();
        }

        private static void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }

        public void close() throws IOException {
            serverSocket.close();
            for (Socket socket : sockets) {
                closeQuietly(socket);
            }
        }
    }
}
