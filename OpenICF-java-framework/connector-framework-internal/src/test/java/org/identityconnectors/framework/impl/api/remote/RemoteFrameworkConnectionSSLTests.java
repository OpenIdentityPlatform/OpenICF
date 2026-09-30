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
package org.identityconnectors.framework.impl.api.remote;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.CapturingLogSpi;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Verifies that the legacy connector server client checks the server
 * certificate against the configured host (TLS hostname verification).
 *
 * {@code KeyStore.jks} holds a certificate with {@code CN=localhost} and no
 * subjectAltName; {@code KeyStore-san.jks} holds one with
 * {@code SAN=dns:localhost,ip:127.0.0.1,ip:::1}.
 */
public class RemoteFrameworkConnectionSSLTests {

    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final int TIMEOUT = 10000;

    @Test
    public void rejectsServerCertificateWithoutMatchingName() throws Exception {
        KeyStore store = loadKeyStore("KeyStore.jks");
        try (TlsServer server = new TlsServer(store, InetAddress.getByName("127.0.0.1"))) {
            try {
                connect("127.0.0.1", server.getPort(), trustManagers(store)).close();
                fail("Expected the handshake to fail: certificate has no name matching 127.0.0.1");
            } catch (ConnectorException e) {
                assertHandshakeFailure(e);
            }
        }
    }

    @Test
    public void verifiesHostnameEvenWithPlainX509TrustManager() throws Exception {
        KeyStore store = loadKeyStore("KeyStore.jks");
        try (TlsServer server = new TlsServer(store, InetAddress.getByName("127.0.0.1"))) {
            try {
                connect("127.0.0.1", server.getPort(),
                        CollectionUtil.<TrustManager> newList(new PinningTrustManager(store)))
                        .close();
                fail("Expected the handshake to fail: certificate has no name matching 127.0.0.1");
            } catch (ConnectorException e) {
                assertHandshakeFailure(e);
            }
        }
    }

    @Test
    public void acceptsServerCertificateWithMatchingSubjectAltName() throws Exception {
        KeyStore store = loadKeyStore("KeyStore-san.jks");
        try (TlsServer server = new TlsServer(store, InetAddress.getByName("127.0.0.1"))) {
            connect("127.0.0.1", server.getPort(), trustManagers(store)).close();
        }
    }

    @Test
    public void fallsBackToCommonNameWhenCertificateHasNoSubjectAltName() throws Exception {
        KeyStore store = loadKeyStore("KeyStore.jks");
        InetAddress localhost = InetAddress.getByName("localhost");
        try (TlsServer server = new TlsServer(store, localhost)) {
            connect("localhost", server.getPort(), trustManagers(store)).close();
        }
    }

    @Test
    public void connectsWhenHostnameVerificationIsDisabled() throws Exception {
        KeyStore store = loadKeyStore("KeyStore.jks");
        try (TlsServer server = new TlsServer(store, InetAddress.getByName("127.0.0.1"))) {
            withHostnameVerificationProperty("false", () ->
                    connect("127.0.0.1", server.getPort(), trustManagers(store)).close());
        }
    }

    @Test
    public void keepsVerificationUnlessPropertyIsExactlyFalse() throws Exception {
        KeyStore store = loadKeyStore("KeyStore.jks");
        try (TlsServer server = new TlsServer(store, InetAddress.getByName("127.0.0.1"))) {
            for (String value : new String[] { "off", "FALSE" }) {
                withHostnameVerificationProperty(value, () -> {
                    try {
                        connect("127.0.0.1", server.getPort(), trustManagers(store)).close();
                        fail("'" + value + "' must not disable hostname verification");
                    } catch (ConnectorException e) {
                        assertHandshakeFailure(e);
                    }
                });
            }
        }
    }

    @Test
    public void reportsMismatchOnceAndOnlyForNonMatchingCertificateWhenVerificationIsDisabled()
            throws Exception {
        assertCapturingLog();
        KeyStore cnOnly = loadKeyStore("KeyStore.jks");
        KeyStore san = loadKeyStore("KeyStore-san.jks");
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        RemoteFrameworkConnection.REPORTED_MISMATCHES.clear();
        CapturingLogSpi.clear();
        // both servers are bound at once, so their ports differ
        try (TlsServer cnOnlyServer = new TlsServer(cnOnly, loopback);
                TlsServer sanServer = new TlsServer(san, loopback)) {
            withHostnameVerificationProperty("false", () -> {
                for (int i = 0; i < 2; i++) {
                    connect("127.0.0.1", cnOnlyServer.getPort(), trustManagers(cnOnly)).close();
                    connect("127.0.0.1", sanServer.getPort(), trustManagers(san)).close();
                }
            });
            List<String> warnings = mismatchWarnings("127.0.0.1:" + cnOnlyServer.getPort());
            assertEquals(warnings.size(), 1, "one warning per server: " + warnings);
            assertTrue(warnings.get(0).contains("may not match the host"), warnings.get(0));
            assertEquals(mismatchWarnings("127.0.0.1:" + sanServer.getPort()).size(), 0);
        }
    }

    /**
     * JSSE drops the trailing dot of a fully qualified host name before
     * checking it, so {@code localhost.} matches {@code dns:localhost}, and
     * with verification off no warning is logged for it.
     */
    @Test
    public void acceptsFullyQualifiedHostWithTrailingDot() throws Exception {
        assertCapturingLog();
        String host = "localhost.";
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new SkipException(host + " does not resolve here");
        }
        KeyStore san = loadKeyStore("KeyStore-san.jks");
        CapturingLogSpi.clear();
        try (TlsServer server = new TlsServer(san, address)) {
            connect(host, server.getPort(), trustManagers(san)).close();
            withHostnameVerificationProperty("false", () ->
                    connect(host, server.getPort(), trustManagers(san)).close());
            assertEquals(mismatchWarnings(host + ":" + server.getPort()).size(), 0);
        }
    }

    @Test
    public void describesWhyVerificationWouldFail() throws Exception {
        Certificate[] cnOnly = { certificate("KeyStore.jks") };
        Certificate[] san = { certificate("KeyStore-san.jks") };
        assertNull(RemoteFrameworkConnection.describeMismatch("localhost", san));
        assertNull(RemoteFrameworkConnection.describeMismatch("127.0.0.1", san));
        String cnOnlyByIp = RemoteFrameworkConnection.describeMismatch("127.0.0.1", cnOnly);
        assertTrue(cnOnlyByIp.contains("may not match the host: subject 'CN=localhost"), cnOnlyByIp);
        assertTrue(cnOnlyByIp.contains("no subjectAltName"), cnOnlyByIp);
        assertTrue(cnOnlyByIp.contains("fix the server certificate"), cnOnlyByIp);
        assertTrue(RemoteFrameworkConnection.describeMismatch("localhost", null)
                .contains("presented no verifiable certificate"));
        // JSSE rejects the name itself, so no certificate can fix it
        String invalidHost = RemoteFrameworkConnection.describeMismatch("my_host.example", san);
        assertTrue(invalidHost.contains("not a valid DNS host name"), invalidHost);
        assertTrue(invalidHost.contains("by its IP address"), invalidHost);
    }

    /**
     * Without trust managers the connection trusts the JVM default trust
     * store ({@code javax.net.ssl.trustStore}), the typical deployment; a
     * PKIX failure instead of the hostname failure would mean the store was
     * not used.
     */
    @Test
    public void verifiesHostnameWithDefaultTrustStore() throws Exception {
        KeyStore store = loadKeyStore("KeyStore.jks");
        String trustStore = new File(RemoteFrameworkConnectionSSLTests.class
                .getResource("/KeyStore.jks").toURI()).getPath();
        withSystemProperties(new String[][] {
            { "javax.net.ssl.trustStore", trustStore },
            { "javax.net.ssl.trustStorePassword", new String(PASSWORD) },
            { "javax.net.ssl.trustStoreType", "JKS" } }, () -> {
                try (TlsServer server = new TlsServer(store, InetAddress.getByName("127.0.0.1"))) {
                    connect("127.0.0.1", server.getPort(), null).close();
                    fail("Expected the handshake to fail: certificate has no name matching 127.0.0.1");
                } catch (ConnectorException e) {
                    assertHandshakeFailure(e);
                }
            });
    }

    // ---- helpers ---------------------------------------------------------

    private interface Action {
        void run() throws Exception;
    }

    private static void withHostnameVerificationProperty(String value, Action action)
            throws Exception {
        withSystemProperties(new String[][] {
            { RemoteFrameworkConnection.HOSTNAME_VERIFICATION_PROPERTY, value } }, action);
    }

    private static void withSystemProperties(String[][] properties, Action action)
            throws Exception {
        String[] previous = new String[properties.length];
        for (int i = 0; i < properties.length; i++) {
            previous[i] = System.setProperty(properties[i][0], properties[i][1]);
        }
        try {
            action.run();
        } finally {
            for (int i = 0; i < properties.length; i++) {
                if (previous[i] == null) {
                    System.clearProperty(properties[i][0]);
                } else {
                    System.setProperty(properties[i][0], previous[i]);
                }
            }
        }
    }

    private static void assertCapturingLog() {
        assertTrue(CapturingLogSpi.isActive(), "the surefire configuration must select "
                + CapturingLogSpi.class.getName() + " through " + Log.LOGSPI_PROP);
    }

    /** WARN messages about the given server logged since the last clear. */
    private static List<String> mismatchWarnings(String server) {
        List<String> warnings = new ArrayList<String>();
        for (String message : CapturingLogSpi.messages(RemoteFrameworkConnection.class,
                Log.Level.WARN)) {
            if (message.contains("connector server " + server + " ")) {
                warnings.add(message);
            }
        }
        return warnings;
    }

    private static void assertHandshakeFailure(ConnectorException e) {
        Throwable cause = e;
        while (cause != null && !(cause instanceof SSLHandshakeException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Expected an SSLHandshakeException in the cause chain of: " + e);
        String message = String.valueOf(cause.getMessage());
        assertTrue(message.contains("subject alternative") || message.contains("No name matching"),
                "Expected a hostname verification failure, got: " + message);
    }

    private static RemoteFrameworkConnection connect(String host, int port,
            List<TrustManager> trustManagers) {
        return new RemoteFrameworkConnection(new RemoteFrameworkConnectionInfo(host, port,
                new GuardedString(PASSWORD), true, trustManagers, TIMEOUT));
    }

    private static KeyStore loadKeyStore(String name) throws Exception {
        try (InputStream in = RemoteFrameworkConnectionSSLTests.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, "missing test resource " + name);
            KeyStore store = KeyStore.getInstance("JKS");
            store.load(in, PASSWORD);
            return store;
        }
    }

    private static Certificate certificate(String keyStore) throws Exception {
        KeyStore store = loadKeyStore(keyStore);
        return store.getCertificate(store.aliases().nextElement());
    }

    private static List<TrustManager> trustManagers(KeyStore store) throws Exception {
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return Arrays.asList(factory.getTrustManagers());
    }

    /**
     * A legacy-style trust manager (not an {@code X509ExtendedTrustManager})
     * that trusts exactly the certificates found in a key store, mirroring
     * what integrators commonly plug into {@link RemoteFrameworkConnectionInfo}.
     */
    private static final class PinningTrustManager implements X509TrustManager {
        private final KeyStore store;

        PinningTrustManager(KeyStore store) {
            this.store = store;
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            checkTrusted(chain);
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            checkTrusted(chain);
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private void checkTrusted(X509Certificate[] chain) throws CertificateException {
            try {
                if (store.getCertificateAlias(chain[0]) == null) {
                    throw new CertificateException("untrusted certificate");
                }
            } catch (CertificateException e) {
                throw e;
            } catch (Exception e) {
                throw new CertificateException(e);
            }
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
            Thread acceptor = new Thread(this::serve, "RemoteFrameworkConnectionSSLTests-server");
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
