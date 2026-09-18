/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.framework.impl.api.remote;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.serializer.BinaryObjectDeserializer;
import org.identityconnectors.framework.common.serializer.BinaryObjectSerializer;
import org.identityconnectors.framework.common.serializer.ObjectSerializerFactory;

public class RemoteFrameworkConnection implements Closeable {

    private static final Log LOG = Log.getLog(RemoteFrameworkConnection.class);

    /**
     * System property controlling whether the connector server certificate is
     * checked against the configured host when {@code useSSL} is on
     * (RFC 2818 / RFC 6125 "HTTPS" endpoint identification). Verification is
     * on unless the property is set to exactly {@code false}; disabling it
     * leaves the connection open to man-in-the-middle attacks by anyone
     * holding a certificate the client trusts.
     */
    public static final String HOSTNAME_VERIFICATION_PROPERTY =
            "org.identityconnectors.framework.remote.hostnameVerification";

    /** Servers already reported as mismatching while verification is off. */
    static final Set<String> REPORTED_MISMATCHES = ConcurrentHashMap.newKeySet();

    private Socket socket;
    private BinaryObjectSerializer encoder;
    private BinaryObjectDeserializer decoder;

    public RemoteFrameworkConnection(RemoteFrameworkConnectionInfo info) {
        try {
            init(info);
        } catch (SocketException e) {
            throw new ConnectorIOException("Failed to init remote connection to "
                    + (null != info ? info.toString() : "null"), e);
        } catch (Exception e) {
            throw new ConnectorException("Failed to init remote connection to "
                    + (null != info ? info.toString() : "null"), e);
        }
    }

    public RemoteFrameworkConnection(Socket socket) {
        try {
            init(socket);
        } catch (SocketException e) {
            throw new ConnectorIOException("Failed to init remote connection to "
                    + (null != socket ? socket.toString() : "null"), e);
        } catch (Exception e) {
            throw new ConnectorException("Failed to init remote connection to "
                    + (null != socket ? socket.toString() : "null"), e);
        }
    }

    private void init(RemoteFrameworkConnectionInfo connectionInfo) throws Exception {
        Socket socket = new Socket();
        socket.setSoTimeout(connectionInfo.getTimeout());
        socket.connect(new InetSocketAddress(connectionInfo.getHost(), connectionInfo.getPort()),
                connectionInfo.getTimeout());
        try {
            if (connectionInfo.getUseSSL()) {
                List<TrustManager> trustManagers = connectionInfo.getTrustManagers();
                TrustManager[] trustManagerArr = null;
                if (null != trustManagers && trustManagers.size() > 0) {
                    // convert empty to null
                    trustManagerArr = trustManagers.toArray(new TrustManager[trustManagers.size()]);
                }
                SSLSocketFactory factory;
                // the only way to get the default keystore is this way
                if (trustManagers == null) {
                    factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                } else {
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(null, trustManagerArr, null);
                    factory = context.getSocketFactory();
                }

                SSLSocket sslSocket =
                        (SSLSocket) factory.createSocket(socket, connectionInfo.getHost(),
                                connectionInfo.getPort(), true);
                // SSLSocket does not check the server certificate against the
                // host on its own: have JSSE do it during the handshake.
                boolean verifyHostname = isHostnameVerificationEnabled();
                if (verifyHostname) {
                    SSLParameters parameters = sslSocket.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    sslSocket.setSSLParameters(parameters);
                }
                sslSocket.startHandshake();
                if (!verifyHostname) {
                    reportCertificateMismatch(connectionInfo, sslSocket);
                }
                socket = sslSocket;
            }
        } catch (Exception e) {
            try {
                socket.close();
            } catch (Exception e2) {
                /* ignore */
            }
            throw e;
        }
        init(socket);
    }

    /**
     * Only the literal {@code false} disables verification, so that a typo in
     * the property value can not silently weaken the connection.
     */
    static boolean isHostnameVerificationEnabled() {
        return !"false".equals(System.getProperty(HOSTNAME_VERIFICATION_PROPERTY));
    }

    /**
     * With verification switched off, still tell the administrator (once per
     * server) when the connection would not have passed, so the certificate
     * or the configured host can be fixed and verification re-enabled.
     */
    private static void reportCertificateMismatch(RemoteFrameworkConnectionInfo connectionInfo,
            SSLSocket sslSocket) {
        String server = connectionInfo.getHost() + ":" + connectionInfo.getPort();
        if (REPORTED_MISMATCHES.contains(server)) {
            return;
        }
        Certificate[] chain;
        try {
            chain = sslSocket.getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            chain = null;
        }
        String mismatch = describeMismatch(connectionInfo.getHost(), chain);
        if (mismatch != null && REPORTED_MISMATCHES.add(server)) {
            LOG.warn("TLS hostname verification is disabled ({0}=false) and connector server {1} {2}",
                    HOSTNAME_VERIFICATION_PROPERTY, server, mismatch);
        }
    }

    /**
     * Says why a connection to {@code host} that presented {@code chain}
     * ({@code null} when no certificate could be verified) would fail
     * hostname verification and what to do about it, or returns {@code null}
     * when it would pass.
     */
    static String describeMismatch(String host, Certificate[] chain) {
        String problem;
        String remedy = "fix the server certificate and re-enable verification";
        if (!CertificateHostnameMatcher.isVerifiableHost(host)) {
            problem = "is configured by a name that is not a valid DNS host name,"
                    + " so no certificate can pass hostname verification for it";
            remedy = "configure the server by a valid host name or by its IP address"
                    + " and re-enable verification";
        } else if (chain == null) {
            problem = "presented no verifiable certificate";
        } else if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
            problem = "presented no X.509 certificate";
        } else if (CertificateHostnameMatcher.matches(host, (X509Certificate) chain[0])) {
            return null;
        } else {
            problem = "presented a certificate that may not match the host: "
                    + CertificateHostnameMatcher.describe((X509Certificate) chain[0]);
        }
        return problem + ". The connection is exposed to man-in-the-middle attacks; "
                + remedy + ".";
    }

    private void init(Socket socket) throws Exception {
        this.socket = socket;
        InputStream inputStream = this.socket.getInputStream();
        OutputStream outputStream = this.socket.getOutputStream();
        ObjectSerializerFactory factory = ObjectSerializerFactory.getInstance();
        encoder = factory.newBinarySerializer(outputStream);
        decoder = factory.newBinaryDeserializer(inputStream);
    }

    @Override
    public void close() {
        flush();
        try {
            if (socket instanceof SSLSocket) {
                // SSLSocket doesn't like shutdownOutput/shutdownInput
                socket.close();
            } else {
                socket.shutdownOutput();
                socket.shutdownInput();
                socket.close();
            }
        } catch (Exception e) {
            LOG.info(e, "Failed to close connection.");
            throw ConnectorException.wrap(e);
        }
    }

    public void flush() {
        encoder.flush();
    }

    public void writeObject(Object object) {
        encoder.writeObject(object);
    }

    public Object readObject() {
        // flush first in case there is any data in the
        // output buffer
        flush();
        return decoder.readObject();
    }
}
