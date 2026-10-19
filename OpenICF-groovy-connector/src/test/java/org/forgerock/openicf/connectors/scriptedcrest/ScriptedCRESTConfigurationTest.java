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
package org.forgerock.openicf.connectors.scriptedcrest;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Checks the HTTP client that the default {@code CustomizerScript.groovy} builds. Nothing is
 * sent: the client is only built, inspected and closed.
 */
public class ScriptedCRESTConfigurationTest {

    /** A configuration whose default customizer has run and whose HTTP client is built. */
    static ScriptedCRESTConfiguration newConfiguration(final URI proxyAddress) throws Exception {
        return newConfiguration(proxyAddress, ScriptedCRESTConfiguration.AuthMethod.BASIC);
    }

    static ScriptedCRESTConfiguration newConfiguration(final URI proxyAddress,
            final ScriptedCRESTConfiguration.AuthMethod authMethod) throws Exception {
        final ScriptedCRESTConfiguration configuration = new ScriptedCRESTConfiguration();
        final File scriptRoot = Files.createTempDirectory("crest-configuration").toFile();
        scriptRoot.deleteOnExit();
        configuration.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        configuration.setServiceAddress(URI.create("http://localhost:28080/test/crest/"));
        configuration.setProxyAddress(proxyAddress);
        configuration.setDefaultAuthMethod(authMethod.name());
        configuration.setUsername("admin");
        configuration.setPassword(new GuardedString("Passw0rd".toCharArray()));
        // getGroovyScriptEngine() is protected in another package; it runs the default customizer.
        final Method engine = ScriptedConfiguration.class.getDeclaredMethod("getGroovyScriptEngine");
        engine.setAccessible(true);
        engine.invoke(configuration);
        configuration.getHttpAsyncClient(); // runs the customizer's init closure
        return configuration;
    }

    /** Reads a private field of an httpasyncclient 4.0.2 class; the names follow that version. */
    static Object field(final Object target, final String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static RequestConfig defaultRequestConfig(final ScriptedCRESTConfiguration configuration)
            throws Exception {
        return (RequestConfig) field(configuration.getHttpAsyncClient(), "defaultConfig");
    }

    @Test
    public void testDefaultCustomizerBoundsConnectAndPoolWaits() throws Exception {
        final ScriptedCRESTConfiguration configuration = newConfiguration(null);
        try {
            final RequestConfig config = defaultRequestConfig(configuration);
            Assert.assertEquals(config.getConnectTimeout(), 10000);
            Assert.assertEquals(config.getConnectionRequestTimeout(), 60000);
            Assert.assertEquals(config.getSocketTimeout(), -1);
            Assert.assertNull(config.getProxy());
        } finally {
            configuration.release();
        }
    }

    @Test
    public void testDefaultCustomizerKeepsTheTimeoutsWithAProxy() throws Exception {
        final ScriptedCRESTConfiguration configuration =
                newConfiguration(URI.create("http://proxy.example:3128"));
        try {
            final RequestConfig config = defaultRequestConfig(configuration);
            Assert.assertEquals(config.getProxy(), new HttpHost("proxy.example", 3128, "http"));
            Assert.assertEquals(config.getConnectTimeout(), 10000);
            Assert.assertEquals(config.getConnectionRequestTimeout(), 60000);
            Assert.assertEquals(config.getSocketTimeout(), -1);
        } finally {
            configuration.release();
        }
    }

    @Test
    public void testClientDropsConnectionsAfterTenIdleSeconds() throws Exception {
        final ScriptedCRESTConfiguration configuration = newConfiguration(null);
        try {
            // InternalHttpAsyncClient.exec is a MainClientExec, which holds the keep-alive strategy.
            final Object exec = field(configuration.getHttpAsyncClient(), "exec");
            Assert.assertSame(field(exec, "keepaliveStrategy"), IdleKeepAliveStrategy.INSTANCE);
        } finally {
            configuration.release();
        }
    }

    @Test
    public void testPreemptiveBasicGivesEachRequestItsOwnScheme() throws Exception {
        final ScriptedCRESTConfiguration configuration =
                newConfiguration(null, ScriptedCRESTConfiguration.AuthMethod.BASIC_PREEMPTIVE);
        try {
            // The cache that the default customizer's beforeRequest puts into every request context.
            final AuthCache cache =
                    (AuthCache) configuration.getPropertyBag().get(HttpClientContext.AUTH_CACHE);
            final HttpHost host = new HttpHost("localhost", 28080, "http");
            final AuthScheme first = cache.get(host);
            final AuthScheme second = cache.get(host);
            Assert.assertTrue(first instanceof BasicScheme, String.valueOf(first));
            Assert.assertTrue(second instanceof BasicScheme, String.valueOf(second));
            Assert.assertNotSame(first, second);
        } finally {
            configuration.release();
        }
    }
}
