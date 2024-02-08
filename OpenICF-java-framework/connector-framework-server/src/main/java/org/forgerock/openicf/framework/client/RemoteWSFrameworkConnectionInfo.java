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

package org.forgerock.openicf.framework.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;

public class RemoteWSFrameworkConnectionInfo {

    public static final String OPENICF_PROTOCOL = "v1.openicf.forgerock.org";

    /**
     * The host to use as proxy.
     */
    public static final String PROXY_HOST = "http.proxyHost";

    /**
     * The port to use for the proxy.
     */
    public static final String PROXY_PORT = "http.proxyPort";

    private boolean secure = Boolean.FALSE;
    private URI remoteURI;
    private String principal = null;
    private GuardedString password = null;
    private String encoding = "UTF-8";
    private long heartbeatInterval;

    private String proxyHost;
    private int proxyPort = -1;
    private String proxyPrincipal = null;
    private GuardedString proxyPassword = null;

    private InetAddress localAddress;
    private List<TrustManager> trustManagers;
    private List<KeyManager> keyManagers;

    void setRemoteURI(URI uri) {
        Assertions.nullCheck(uri, "remoteURI");
        try {
            if ("https".equalsIgnoreCase(uri.getScheme())
                    || "wss".equalsIgnoreCase(uri.getScheme())) {
                int port = uri.getPort() > 0 ? uri.getPort() : 443;
                remoteURI =
                        new URI("wss", uri.getUserInfo(), uri.getHost(), port, uri.getPath(), uri
                                .getQuery(), "");
                secure = Boolean.TRUE;
            } else if ("http".equalsIgnoreCase(uri.getScheme())
                    || "ws".equalsIgnoreCase(uri.getScheme())) {
                int port = uri.getPort() > 0 ? uri.getPort() : 80;
                remoteURI =
                        new URI("ws", uri.getUserInfo(), uri.getHost(), port, uri.getPath(), uri
                                .getQuery(), "");
                secure = Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Unsupported protocol:" + uri);
            }
        } catch (URISyntaxException e) {
            // This should not happen
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public URI getRemoteURI() {
        return remoteURI;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public GuardedString getPassword() {
        return password;
    }

    protected void setPassword(GuardedString password) {
        this.password = password;
    }

    public boolean isSecure() {
        return secure;
    }

    /**
     * Returns the heartbeat interval (in seconds) to use for the connection. A value
     * of zero means default 60 seconds timeout.
     *
     * @return the heartbeat interval (in seconds) to use for the connection.
     */
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }
    
    
    public void loadSystemProxy() {
        String host = System.getProperty(PROXY_HOST);
        if (host != null) {
            proxyHost = host;
            proxyPort = Integer.valueOf(System.getProperty(PROXY_PORT, "80"));
        }
    }

    public void setLocalAddress(InetAddress localAddress) {
        this.localAddress = localAddress;
    }

    public boolean isUseProxy() {
        return StringUtil.isNotBlank(proxyHost);
    }

    // --- Proxy Settings

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return 0 < proxyPort && proxyPort <= 65535 ? proxyPort : 80;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyPrincipal() {
        return proxyPrincipal;
    }

    public void setProxyPrincipal(String proxyPrincipal) {
        this.proxyPrincipal = proxyPrincipal;
    }

    public GuardedString getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(GuardedString proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    // --- SSL Settings

    public List<TrustManager> getTrustManagers() {
        return trustManagers;
    }

    void setTrustManagers(List<TrustManager> trustManagers) {
        this.trustManagers = trustManagers;
    }

    public List<KeyManager> getKeyManagers() {
        return keyManagers;
    }

    void setKeyManagers(List<KeyManager> keyManagers) {
        this.keyManagers = keyManagers;
    }

    // --- Set InetAddress

    public InetAddress getLocalAddress() {
        return localAddress;
    }

    public SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManager[] keyManagerArray = null;
        if (null != keyManagers && !keyManagers.isEmpty()) {
            keyManagerArray = keyManagers.toArray(new KeyManager[keyManagers.size()]);
        }

        TrustManager[] trustManagerArray = null;
        if (null != trustManagers && !trustManagers.isEmpty()) {
            trustManagerArray = trustManagers.toArray(new TrustManager[trustManagers.size()]);
        }
        sslContext.init(keyManagerArray, trustManagerArray, null);
        return sslContext;
    }

    // --- Pool Configuration

    private int minimumConnectionCount = 1;
    private int expectedConnectionCount = 2;
    private int maximumConnectionCount = 10;

    public int getMinimumConnectionCount() {
        return minimumConnectionCount;
    }

    public int getExpectedConnectionCount() {
        return expectedConnectionCount;
    }

    public int getMaximumConnectionCount() {
        return maximumConnectionCount;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilderFrom(final RemoteFrameworkConnectionInfo info) {
        Builder builder = new Builder();
        if (info.getUseSSL()) {
            builder.setRemoteURI(URI.create("wss://" + info.getHost() + ":" + info.getPort()
                    + "/openicf"));
            builder.setTrustManagers(info.getTrustManagers());
        } else {
            builder.setRemoteURI(URI.create("ws://" + info.getHost() + ":" + info.getPort()
                    + "/openicf"));
        }
        builder.setPrincipal(ConnectionPrincipal.DEFAULT_NAME);
        builder.setPassword(info.getKey());
        return builder;
    }

    public static class Builder {

        private RemoteWSFrameworkConnectionInfo instance = new RemoteWSFrameworkConnectionInfo();

        public Builder setRemoteURI(URI remoteURI) {
            instance.setRemoteURI(remoteURI);
            return this;
        }

        public Builder setPrincipal(String principal) {
            instance.setPrincipal(principal);
            return this;
        }

        public Builder setPassword(GuardedString password) {
            instance.setPassword(password);
            return this;
        }

        public Builder loadSystemProxy() {
            instance.loadSystemProxy();
            return this;
        }

        public Builder setLocalAddress(InetAddress localAddress) {
            instance.setLocalAddress(localAddress);
            return this;
        }

        // --- Proxy Settings

        public Builder setProxyHost(String proxyHost) {
            instance.setProxyHost(proxyHost);
            return this;
        }

        public Builder setProxyPort(int proxyPort) {
            instance.setProxyPort(proxyPort);
            return this;
        }

        public Builder setProxyPrincipal(String proxyPrincipal) {
            instance.setProxyPrincipal(proxyPrincipal);
            return this;
        }

        public Builder setProxyPassword(GuardedString proxyPassword) {
            instance.setProxyPassword(proxyPassword);
            return this;
        }

        // --- SSL Settings

        public Builder setTrustManagers(List<TrustManager> trustManagers) {
            instance.setTrustManagers(trustManagers);
            return this;
        }

        public Builder setKeyManagers(List<KeyManager> keyManagers) {
            instance.setKeyManagers(keyManagers);
            return this;
        }

        public RemoteWSFrameworkConnectionInfo build() {
            RemoteWSFrameworkConnectionInfo result = instance;
            instance = new RemoteWSFrameworkConnectionInfo();
            return result;
        }
    }
}
