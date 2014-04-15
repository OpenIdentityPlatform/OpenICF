/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openicf.connectors.scriptedcrest

import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.concurrent.FutureCallback
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.ssl.SSLContexts
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.nio.client.methods.HttpAsyncMethods
import org.apache.http.nio.conn.NHttpClientConnectionManager
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer
import org.apache.http.nio.reactor.ConnectingIOReactor
import org.forgerock.json.resource.ResourceName
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.Assertions
import org.identityconnectors.framework.spi.ConfigurationProperty

import javax.net.ssl.SSLContext
import java.security.KeyStore
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A ScriptedCRESTConfiguration.
 *
 * @author Laszlo Hordos
 */
class ScriptedCRESTConfiguration extends ScriptedConfiguration {

    URI serviceAddress = null;

    URI proxyAddress = null;

    @ConfigurationProperty(required = true)
    URI getServiceAddress() {
        return serviceAddress
    }

    void setServiceAddress(URI serviceAddress) {
        this.serviceAddress = serviceAddress
        host = null;
        resourceName = null;
    }

    URI getProxyAddress() {
        return proxyAddress
    }

    void setProxyAddress(URI proxyAddress) {
        this.proxyAddress = proxyAddress
        proxy = null;
    }

    @Override
    void validate() {
        Assertions.nullCheck(serviceAddress, "serviceAddress")
        super.validate()
    }

    private HttpHost host = null;

    private HttpHost proxy = null;

    private ResourceName resourceName = null;


    ResourceName getResourceName() {
        resourceName = ResourceName.valueOf(serviceAddress?.path);
    }

    private HttpHost getHttpHost() {
        host = new HttpHost(serviceAddress?.host, serviceAddress?.port, serviceAddress?.scheme);

    }

    private HttpHost getProxyHost() {
        if (null != proxyAddress) {
            return new HttpHost(proxyAddress?.host, proxyAddress?.port, proxyAddress?.scheme);
        }
        return null;
    }


    private CloseableHttpAsyncClient httpClient = null;

    private IdleConnectionEvictor connectionEvictor = null;

    // Create a local instance of cookie store
    private final BasicCookieStore cookieStore = new BasicCookieStore();


    boolean isClosed() {
        return null == httpClient || !httpClient.isRunning();
    }

    public <T> Future<T> execute(
            final HttpUriRequest request,
            final HttpAsyncResponseConsumer<T> responseConsumer, final FutureCallback<T> callback) {
        HttpClientContext localContext = HttpClientContext.create();
        localContext.setCookieStore(cookieStore);

        return getHttpAsyncClient().execute(
                HttpAsyncMethods.create(getHttpHost(), request),
                responseConsumer,
                localContext, callback);
    }

    HttpAsyncClient getHttpAsyncClient() {
        if (null == httpClient) {
            synchronized (this) {
                if (null == httpClient) {

                    //SEE: http://hc.apache.org/httpcomponents-asyncclient-4.0.x/httpasyncclient/examples/org/apache/http/examples/nio/client/AsyncClientConfiguration.java

                    if (false) {

                        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                        FileInputStream instream = new FileInputStream(new File("my.keystore"));
                        try {
                            trustStore.load(instream, "nopassword".toCharArray());
                        } finally {
                            instream.close();
                        }
                        // Trust own CA and all self-signed certs
                        SSLContext sslcontext = SSLContexts.custom()
                                .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                                .build();
                        // Allow TLSv1 protocol only
                        SSLIOSessionStrategy sslSessionStrategy = new SSLIOSessionStrategy(
                                sslcontext,
                                ["TLSv1"] as String[],
                                null,
                                SSLIOSessionStrategy.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
                    }

                    // Authentication

                    // It's part of the http client spec to request the resource anonymously
                    // first and respond to the 401 with the Authorization header.
                    CredentialsProvider credsProvider = new BasicCredentialsProvider();
                    credsProvider.setCredentials(
                            new AuthScope(getHttpHost().getHostName(), getHttpHost().getPort()),
                            new UsernamePasswordCredentials("admin", "Passw0rd"));

                    //PROXY
                    if (false) {
                        HttpHost proxy = new HttpHost("someproxy", 8080);
                        RequestConfig config = RequestConfig.custom()
                                .setProxy(proxy)

                        // configure timeout on the entire client
                        /*
                                                             * .
                                                             * setConnectionRequestTimeout
                                                             * ( 50).
                                                             * setConnectTimeout
                                                             * (50)
                                                             * .setSocketTimeout
                                                             * (50)

                        */
                                .build();

                        credsProvider.setCredentials(
                                new AuthScope(proxy.getHostName(), proxy.getPort()),
                                new UsernamePasswordCredentials("username", "password"));
                    }



                    ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                    PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
                    // Increase max total connection to 200
                    cm.setMaxTotal(200);
                    // Increase default max connection per route to 20
                    cm.setDefaultMaxPerRoute(20);
                    // Increase max connections for target host to 50
                    cm.setMaxPerRoute(new HttpRoute(getHttpHost()), 50);


                    httpClient = HttpAsyncClients.custom()
                            .setConnectionManager(cm)
                    //.setSSLStrategy(sslSessionStrategy)
                            .setDefaultCredentialsProvider(credsProvider)
                    //.setDefaultRequestConfig(config)
                            .build();


                    httpClient.start();

                    connectionEvictor = new IdleConnectionEvictor(cm);
                    connectionEvictor.start();

                }
            }
        }
        return httpClient;
    }

    @Override
    void release() {
        synchronized (this) {
            super.release()
            if (null != httpClient) {
                httpClient.close();
                httpClient = null;
            }
            if (null != connectionEvictor) {
                // Shut down the evictor thread
                connectionEvictor.shutdown();
                connectionEvictor.join();
                connectionEvictor = null;
            }
        }
    }


    public static class IdleConnectionEvictor extends Thread {

        private final NHttpClientConnectionManager connMgr;

        private volatile boolean shutdown;

        public IdleConnectionEvictor(NHttpClientConnectionManager connMgr) {
            super();
            this.connMgr = connMgr;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);
                        // Close expired connections
                        connMgr.closeExpiredConnections();
                        // Optionally, close connections
                        // that have been idle longer than 5 sec
                        connMgr.closeIdleConnections(5, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                // terminate
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
