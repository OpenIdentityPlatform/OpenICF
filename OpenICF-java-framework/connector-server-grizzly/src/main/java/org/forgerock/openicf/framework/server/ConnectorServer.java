/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.server;

import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.local.AsyncLocalConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.server.grizzly.OpenICFWebSocketAddOn;
import org.forgerock.openicf.framework.server.grizzly.OpenICFWebSocketApplication;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;

public class ConnectorServer {

    private static final Log logger = Log.getLog(ConnectorServer.class);

    // @formatter:off
    // http://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html
    public static final String[] RECOMMENDED_CIPHERS = new String[]{
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",//Java7
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_WITH_RC4_128_MD5",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSVFo", // per RFC 5746
    };
    // @formatter:on

    private final AtomicInteger status = new AtomicInteger(0);
    private HttpServer server = null;

    private OpenICFWebSocketApplication connectorApplication = null;

    private ConnectorFrameworkFactory connectorFrameworkFactory = null;
    private ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework = null;

    /**
     * The bundle URLs for connectors to be hosted in this server.
     */
    private List<URL> bundleURLs = null;

    /**
     * The class loader that will be used as the parent of the bundle class
     * loaders. May be null. MUST be able to load framework and
     * framework-internal classes.
     */
    private ClassLoader connectorBundleParentClassLoader;

    /**
     * The base 64-encoded hash of the key
     */
    private String keyHash;

    /**
     * Sets the bundle URLs for connectors to expose by this server.
     *
     * @param urls
     *            The bundle URLs for connectors to expose by this server.
     */
    public void setConnectorBundleURLs(final List<URL> urls) {
        bundleURLs = CollectionUtil.newReadOnlyList(urls);
    }

    /**
     * Sets the class loader that will be used as the parent of the bundle class
     * loaders.
     *
     * @param bundleParentClassLoader
     *            the class loader that will be used as the parent of the bundle
     *            class loaders.
     */
    public void setBundleParentClassLoader(final ClassLoader bundleParentClassLoader) {
        this.connectorBundleParentClassLoader = bundleParentClassLoader;
    }

    /**
     * Sets the base-64 encoded SHA1 hash of the key.
     *
     * @param hash
     *            the base-64 encoded SHA1 hash of the key.
     */
    public void setKeyHash(final String hash) {
        keyHash = hash;
    }

    /**
     *
     * @param name
     *            the logical name of the listener.
     * @param host
     *            the network host to which this listener will bind.
     * @param port
     *            the network port to which this listener will bind.
     */
    public void addListener(final String name, final String host, final int port) {
        if (null != server) {
            addListener(name, host, port, null);
        } else {
            throw new IllegalStateException(
                    "The init method has to be called before a listener is added");
        }
    }

    /**
     *
     * @param name
     *            the logical name of the listener.
     * @param host
     *            the network host to which this listener will bind.
     * @param port
     *            the network port to which this listener will bind.
     */
    public void addListener(final String name, final String host, final int port,
            final SSLContextConfigurator contextConfigurator) {
        if (null != server) {

            final String serverHost =
                    StringUtil.isNotBlank(host) ? host : NetworkListener.DEFAULT_NETWORK_HOST;

            if (null != contextConfigurator) {
                // HTTPS
                final int serverPort = getServerPort(port, true);
                final String listenerName =
                        StringUtil.isNotBlank(name) ? name : "OpenICF-Secure:" + port;
                final NetworkListener listenerSecure =
                        new NetworkListener(listenerName, serverHost, serverPort);
                listenerSecure.registerAddOn(new OpenICFWebSocketAddOn());
                listenerSecure.setSecure(true);
                listenerSecure.setSSLEngineConfig(createSSLConfig(contextConfigurator));

                server.addListener(listenerSecure);
            } else {
                // HTTP
                final int serverPort = getServerPort(port, false);
                final String listenerName =
                        StringUtil.isNotBlank(name) ? name : "OpenICF-Plain:" + port;
                final NetworkListener listener =
                        new NetworkListener(listenerName, serverHost, serverPort);
                listener.registerAddOn(new OpenICFWebSocketAddOn());

                server.addListener(listener);
            }
        } else {
            throw new IllegalStateException(
                    "The init method has to be called before a listener is added");
        }
    }

    private int getServerPort(int serverPort, boolean isSecure) {
        return 0 < serverPort && serverPort <= 65535 ? serverPort : (isSecure ? 8443
                : NetworkListener.DEFAULT_NETWORK_PORT);
    }

    /**
     * Initialize this <code>ConnectorServer</code> instance.
     *
     * <p>
     * Under certain operating systems (typically Unix based operating systems)
     * and if the native invocation framework is configured to do so, this
     * method might be called with <i>super-user</i> privileges.
     * </p>
     * <p>
     * For example, it might be wise to create <code>ServerSocket</code>
     * instances within the scope of this method, and perform all operations
     * requiring <i>super-user</i> privileges in the underlying operating
     * system.
     * </p>
     * <p>
     * Apart from set up and allocation of native resources, this method must
     * not start the actual operation of the <code>ConnectorServer</code> (such
     * as starting threads calling the <code>ServerSocket.accept()</code>
     * method) as this would impose some serious security hazards. The start of
     * operation must be performed in the <code>start()</code> method.
     * </p>
     *
     * @exception Exception
     *                Any exception preventing a successful initialization.
     */
    public void init() throws Exception {
        synchronized (status) {
            if (null != connectorFrameworkFactory) {
                connectorFramework = connectorFrameworkFactory.acquire();

                AsyncLocalConnectorInfoManager manager =
                        connectorFramework.get().getLocalConnectorInfoManager(
                                connectorBundleParentClassLoader);
                if (null != bundleURLs) {
                    for (URL connectorBundleURL : bundleURLs) {
                        manager.addConnectorBundle(connectorBundleURL);
                    }
                }

                server = new HttpServer();
                final ServerConfiguration config = server.getServerConfiguration();
                config.setName("OpenICF Connector Server");
                config.setHttpServerName("OpenICF Connector Server");

                status.set(1);
            } else {
                throw new IllegalStateException(
                        "ConnectorFrameworkFactory must be set before the server is initialised");
            }
        }
    }

    public void start() throws Exception {
        if (status.compareAndSet(1, 2)) {
            // register the application
            if (null == connectorApplication) {
                connectorApplication =
                        createOpenICFWebSocketApplication(connectorFramework.get(), keyHash);
                WebSocketEngine.getEngine().register(getContextPath(), getUrlPattern(), connectorApplication);
            }
            server.start();

        } else if (status.get() != 2) {
            throw new IllegalStateException("ConnectorServer must be initialised first");
        }
    }

    protected String getContextPath() {
        return "";
    }

    protected String getUrlPattern() {
        return "/openicf";
    }

    public void stop() {
        if (status.compareAndSet(2, 1)) {
            server.shutdownNow();
            if (null != connectorApplication) {
                logger.info("Server is shutting down {0}", this);
                try {
                    WebSocketEngine.getEngine().unregister(connectorApplication);
                } catch (NegativeArraySizeException e) {
                    // IGNORE this Grizzly Bug
                    // org.glassfish.grizzly.http.server.util.Mapper.removeWrapper(Mapper.java:682)
                }
                connectorApplication.close();
                connectorApplication = null;
            }
        }
    }

    public void destroy() throws Exception {
        synchronized (status) {
            if (status.compareAndSet(1, 0)) {
                server = null;
                
                connectorFramework.release();
                connectorFramework = null;
            } else if (status.get() == 2) {
                throw new IllegalStateException("ConnectorServer must be stopped before");
            }
        }
    }

    public boolean isRunning() {
        return null != server && server.isStarted();
    }

    protected OpenICFWebSocketApplication createOpenICFWebSocketApplication(
            final ConnectorFramework framework, String sharedKeyHash) {
        return new OpenICFWebSocketApplication(framework, sharedKeyHash);
    }

    /**
     * create SSL Configuration
     *
     * @return
     * @throws Exception
     */
    protected SSLEngineConfigurator createSSLConfig(final SSLContextConfigurator contextConfigurator) {

        // force client Authentication ...
        // Require
        boolean needClientAuth = false;
        // Optional
        boolean wantClientAuth = false;

        SSLEngineConfigurator result =
                new SSLEngineConfigurator(contextConfigurator.createSSLContext(), false,
                        needClientAuth, wantClientAuth);

        result.setEnabledCipherSuites(ConnectorServer.RECOMMENDED_CIPHERS);

        result.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.1", "TLSv1" });
        return result;
    }

    public void setConnectorFrameworkFactory(ConnectorFrameworkFactory connectorFrameworkFactory) {
        this.connectorFrameworkFactory = connectorFrameworkFactory;
    }

    String getListeners() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (NetworkListener nl : server.getListeners()) {
            if (first) {
                first = !first;
            } else {
                sb.append(", ");
            }
            sb.append("ServerListener[").append(nl.getHost()).append(":").append(nl.getPort());
            if (nl.isSecure()) {
                sb.append(" - secure]");
            } else {
                sb.append(" - plain]");
            }
        }
        return sb.toString();
    }
}
