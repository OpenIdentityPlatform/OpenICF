/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS All rights reserved.
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

package org.forgerock.openicf.connectors.scriptedrest

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import groovyx.net.http.StringHashMap
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.Assertions
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.spi.AbstractConfiguration
import org.identityconnectors.framework.spi.ConfigurationProperty

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the ScriptedREST Connector.
 *
 * @author Gael Allioux <gael.allioux@gmail.com>
 */
public class ScriptedRESTConfiguration extends ScriptedConfiguration {

    // Exposed configuration properties.

    // ===============================================
    // HTTP authentication
    // ===============================================

    public enum AuthMethod {
        NONE, BASIC, BASIC_PREEMPTIVE, DIGEST, NTLM, SPNEGO, CERT, OAUTH
    }

    /*
     * authMethod
     * Can be:
     *  BASIC
     *  BASIC_PREEMPTIVE
     *  CERT
     *  OAUTH
     */
    String defaultAuthMethod = AuthMethod.BASIC.name();

    /**
     * The Remote user to authenticate with.
     */
    private String username = null;

    /**
     * The Password to authenticate with.
     */
    private GuardedString password = null;

    @ConfigurationProperty(order = 1, displayMessageKey = "username.display",
            groupMessageKey = "basic.group", helpMessageKey = "username.help",
            required = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "password.display",
            groupMessageKey = "basic.group", helpMessageKey = "password.help",
            confidential = true)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    // ===============================================
    // HTTP connection
    // ===============================================

    URI serviceAddress = null;

    @ConfigurationProperty(required = true)
    URI getServiceAddress() {
        return serviceAddress
    }

    // ===============================================
    // HTTP Proxy
    // ===============================================

    URI proxyAddress = null;

    // ===============================================
    // HTTP content
    // ===============================================

    /*
     * default content type
     * Can be:
     *  ANY
     *  TEXT("text/plain")
     *  JSON("application/json","application/javascript","text/javascript")
     *  XML("application/xml","text/xml","application/xhtml+xml","application/atom+xml")
     *  HTML("text/html")
     *  URLENC("application/x-www-form-urlencoded")
     *  BINARY("application/octet-stream")
     */
    String defaultContentType = ContentType.JSON.name();

    protected final Map<Object, Object> defaultRequestHeaders = new StringHashMap<Object>();

    String[] getDefaultRequestHeaders() {
        def headers = []
        return defaultRequestHeaders.each { key, value ->
            headers.add("${key}=${value}")
        }
        return headers as String[]
    }

    void setDefaultRequestHeaders(String[] headers) {
        defaultRequestHeaders.clear()
        if (null != headers) {
            headers.each {
                def kv = it.split('=')
                assert kv.size() == 2
                defaultRequestHeaders.put(kv[0], kv[1])
            }
        }
    }

    private HttpHost getHttpHost() {
        return new HttpHost(serviceAddress?.host, serviceAddress?.port, serviceAddress?.scheme);
    }

    private HttpHost getProxyHost() {
        if (proxyAddress != null) {
            return new HttpHost(proxyAddress?.host, proxyAddress?.port, proxyAddress?.scheme);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate() {
        Assertions.nullCheck(getServiceAddress(), "serviceAddress")
        super.validate()
    }

    private CloseableHttpClient httpClient = null;
    //Need for Preemptive Auth
    private AuthCache authCache = null;

    @SuppressWarnings("fallthrough")
    RESTClient getRESTClient() {
        if (null == httpClient) {
            synchronized (this) {
                if (null == httpClient) {

                    //SETUP: org.apache.http

                    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
                    // Increase max total connection to 200
                    cm.setMaxTotal(200);
                    // Increase default max connection per route to 20
                    cm.setDefaultMaxPerRoute(20);
                    // Increase max connections for localhost:8080 to 50

                    cm.setMaxPerRoute(new HttpRoute(getHttpHost()), 50);

                    // configure timeout on the entire client
                    RequestConfig requestConfig = RequestConfig.custom()/*
                                                             * .
                                                             * setConnectionRequestTimeout
                                                             * ( 50).
                                                             * setConnectTimeout
                                                             * (50)
                                                             * .setSocketTimeout
                                                             * (50)
                                                             */.build();

                    HttpClientBuilder builder =
                            HttpClientBuilder.create().
                                    setConnectionManager(cm).
                                    setDefaultRequestConfig(requestConfig).
                                    setProxy(getProxyHost());


                    switch (AuthMethod.valueOf(getDefaultAuthMethod())) {
                        case AuthMethod.BASIC_PREEMPTIVE:

                            // Create AuthCache instance
                            authCache = new BasicAuthCache();
                            // Generate BASIC scheme object and add it to the local auth cache
                            authCache.put(getHttpHost(), new BasicScheme());

                        case AuthMethod.BASIC:
                            // It's part of the http client spec to request the resource anonymously
                            // first and respond to the 401 with the Authorization header.
                            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

                            getPassword().access(new GuardedString.Accessor() {
                                @Override
                                public void access(char[] clearChars) {
                                    credentialsProvider.setCredentials(new AuthScope(getHttpHost().getHostName(), getHttpHost().getPort()),
                                            new UsernamePasswordCredentials(getUsername(), new String(clearChars)));
                                }
                            });

                            builder.setDefaultCredentialsProvider(credentialsProvider);
                            break;
                        case AuthMethod.NONE:
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }

                    httpClient = builder.build();

                }
            }
        }

        //SETUP: groovyx.net.http

        RESTClient restClient = new RESTClient() {
            @Override
            protected Object doRequest(
                    final groovyx.net.http.HTTPBuilder.RequestConfigDelegate delegate) throws ClientProtocolException, IOException {
                // Add AuthCache to the execution context
                if (null != authCache) {
                    //do Preemptive Auth
                    delegate.getContext().setAttribute(HttpClientContext.AUTH_CACHE, authCache);
                }
                return super.doRequest(delegate)
            }

            @Override
            void shutdown() {
                //Do not allow to shutdown the HttpClient
            }
        };
        restClient.setClient(httpClient);
        restClient.setUri(getHttpHost().toURI());
        restClient.setContentType(defaultContentType);
        restClient.setHeaders(defaultRequestHeaders)
        return restClient;
    }

    @Override
    void release() {
        super.release()
        if (null != httpClient) {
            httpClient.close();
            httpClient = null;
        }
    }
}
