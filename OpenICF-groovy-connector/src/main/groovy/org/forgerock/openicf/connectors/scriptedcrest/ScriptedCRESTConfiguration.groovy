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
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.nio.client.methods.HttpAsyncMethods
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer
import org.codehaus.groovy.runtime.InvokerHelper
import org.forgerock.json.resource.Context
import org.forgerock.json.resource.ResourceName
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.Assertions
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.spi.ConfigurationProperty

import java.util.concurrent.Future

/**
 * A ScriptedCRESTConfiguration.
 *
 * @author Laszlo Hordos
 */
class ScriptedCRESTConfiguration extends ScriptedConfiguration {

    // Exposed configuration properties.

    // ===============================================
    // HTTP authentication
    // ===============================================

    public enum AuthMethod {
        NONE, BASIC, BASIC_PREEMPTIVE, DIGEST, NTLM, SPNEGO, CERT, OAUTH, CUSTOM
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
            groupMessageKey = "basic.group", helpMessageKey = "username.help")
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

    @Override
    void release() {
        synchronized (this) {
            super.release()
            if (null != httpClient) {
                httpClient.close();
                httpClient = null;
            }
        }
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

    boolean isClosed() {
        return null == httpClient || !httpClient.isRunning();
    }

    public <T> Future<T> execute(
            final Context context,
            final HttpUriRequest request,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {

        HttpClientContext localContext = HttpClientContext.create();
        if (null != beforeRequest) {
            Closure beforeRequestClone = beforeRequest.rehydrate(this, this, this);
            beforeRequestClone.setResolveStrategy(Closure.DELEGATE_FIRST);
            beforeRequestClone(context, localContext, request)
        }

        return getHttpAsyncClient().execute(
                HttpAsyncMethods.create(getHttpHost(), request),
                responseConsumer,
                localContext, callback);
    }

    private Closure init = null;
    private Closure release = null;
    private Closure beforeRequest = null;
    private Closure onComplete = null;
    private Closure onFail = null;

    HttpAsyncClient getHttpAsyncClient() {
        if (null == httpClient) {
            synchronized (this) {
                if (null == httpClient) {
                    Closure clone = init.rehydrate(this, this, this);
                    clone.setResolveStrategy(Closure.DELEGATE_FIRST);
                    HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                    clone(builder)
                    httpClient = builder.build();
                    httpClient.start();
                }
            }
        }
        return httpClient;
    }

    protected Script createCustomizerScript(Class customizerClass, Binding binding) {

        customizerClass.metaClass.customize << { Closure cl ->
            init = null
            release = null
            beforeRequest = null
            onComplete = null
            onFail = null

            def delegate = [
                    init         : { Closure paramClosure ->
                        init = paramClosure
                    },
                    release      : { Closure paramClosure ->
                        release = paramClosure
                    },
                    beforeRequest: { Closure paramClosure ->
                        beforeRequest = paramClosure
                    },
                    onComplete   : { Closure paramClosure ->
                        onComplete = paramClosure
                    },
                    onFail       : { Closure paramClosure ->
                        onFail = paramClosure
                    }
            ]
            cl.setDelegate(new Reference(delegate));
            cl.setResolveStrategy(Closure.DELEGATE_FIRST);
            cl.call();
        }

        return InvokerHelper.createScript(customizerClass, binding);
    }

}
