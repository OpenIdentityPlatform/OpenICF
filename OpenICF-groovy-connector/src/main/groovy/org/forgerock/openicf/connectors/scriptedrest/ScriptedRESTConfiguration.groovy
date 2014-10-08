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

import org.apache.http.client.HttpClient
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.codehaus.groovy.runtime.InvokerHelper
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.Assertions
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.ConfigurationException
import org.identityconnectors.framework.spi.AbstractConfiguration
import org.identityconnectors.framework.spi.ConfigurationClass
import org.identityconnectors.framework.spi.ConfigurationProperty

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the ScriptedREST Connector.
 *
 * @author Gael Allioux <gael.allioux@gmail.com>
 */
@ConfigurationClass(skipUnsupported = true)
public class ScriptedRESTConfiguration extends ScriptedConfiguration {

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

    @ConfigurationProperty(required = true)
    URI getServiceAddress() {
        return serviceAddress
    }

    void setServiceAddress(URI serviceAddress) {
        this.serviceAddress = serviceAddress
    }

    // ===============================================
    // HTTP Proxy
    // ===============================================

    URI proxyAddress = null;

    URI getProxyAddress() {
        return proxyAddress
    }

    void setProxyAddress(URI proxyAddress) {
        this.proxyAddress = proxyAddress
    }

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
    String defaultContentType = "application/json";

    String[] defaultRequestHeaders = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate() {
        Assertions.nullCheck(getServiceAddress(), "serviceAddress")
        super.validate()
        if (StringUtil.isNotBlank(defaultAuthMethod)) {
            switch (defaultAuthMethod) {
                case AuthMethod.NONE.name():
                    break;
                case AuthMethod.NTLM.name():
                    Assertions.blankCheck("workstation", "workstation")
                    Assertions.blankCheck("domain", "domain")
                    Assertions.blankCheck(getUsername(), "username")
                    Assertions.nullCheck(getPassword(), "password")
                case AuthMethod.DIGEST.name():
                    Assertions.blankCheck("realm", "realm")
                    Assertions.blankCheck("nonce", "nonce")
                case AuthMethod.BASIC_PREEMPTIVE.name():
                case AuthMethod.BASIC.name():
                    Assertions.blankCheck(getUsername(), "username")
                    Assertions.nullCheck(getPassword(), "password")
                    break;
                default:
                    break;
            }
        }
        if (null == initClosure) {
            throw new ConfigurationException("The Customizer Script must define the 'init' Closure")
        }
    }

    private CloseableHttpClient httpClient = null;

    Closure initClosure = null;
    Closure releaseClosure = null;
    Closure decorateClosure = null;

    protected Script createCustomizerScript(Class customizerClass, Binding binding) {

        customizerClass.metaClass.customize << { Closure cl ->
            initClosure = null
            releaseClosure = null
            decorateClosure = null

            def delegate = [
                    init    : { Closure c ->
                        initClosure = c
                    },
                    release : { Closure c ->
                        releaseClosure = c
                    },
                    decorate: { Closure c ->
                        decorateClosure = c
                    }
            ]
            cl.setDelegate(new Reference(delegate));
            cl.setResolveStrategy(Closure.DELEGATE_FIRST);
            cl.call();
        }

        return InvokerHelper.createScript(customizerClass, binding);
    }

    HttpClient getHttpClient() {
        if (null == httpClient) {
            synchronized (this) {
                if (null == httpClient) {
                    getGroovyScriptEngine()
                    Closure clone = initClosure.rehydrate(this, this, this);
                    clone.setResolveStrategy(Closure.DELEGATE_FIRST);
                    HttpClientBuilder builder = HttpClientBuilder.create()
                    clone(builder)
                    httpClient = builder.build();
                }
            }
        }
        return httpClient;
    }

    Object getDecoratedObject(HttpClient client) {
        if (null != decorateClosure) {
            Closure clone = decorateClosure.rehydrate(this, this, this);
            clone.setResolveStrategy(Closure.DELEGATE_FIRST);
            return clone(client)
        }
        return client
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
}
