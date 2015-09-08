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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConfigurationPropertyChangeListener;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test
public class AsyncDotNetPlainConnectorInfoManagerTest extends
        AsyncConnectorInfoManagerTestBase<AsyncRemoteConnectorInfoManager> {

    public static final ConnectorKey TEST_POOLABLE_STATEFUL_CONNECTOR_KEY = new ConnectorKey(
            "TestBundleV1.Connector", "1.0.0.0",
            "org.identityconnectors.testconnector.TstStatefulPoolableConnector");
    public static final ConnectorKey TEST_STATEFUL_CONNECTOR_KEY = new ConnectorKey(
            "TestBundleV1.Connector", "1.0.0.0",
            "org.identityconnectors.testconnector.TstStatefulConnector");
    public static final ConnectorKey TEST_CONNECTOR_KEY = new ConnectorKey(
            "TestBundleV1.Connector", "1.0.0.0",
            "org.identityconnectors.testconnector.TstConnector");

    public RemoteWSFrameworkConnectionInfo CONNECTION_INFO = null;

    private final ConnectorFrameworkFactory localConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    protected ConnectorFrameworkFactory getConnectorFrameworkFactory() throws Exception {
        return localConnectorFrameworkFactory;
    }

    protected void setupTest(ITestContext context) throws Exception {
        Reporter.log(context.getName() + ": Ready to test", true);
    }

    protected void shutdownTest(ITestContext context) throws Exception {
    }

    protected void setupClass(ITestContext context) throws Exception {
        CONNECTION_INFO =
                RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                        //URI.create("ws://localhost:8759/openicf")).setPrincipal(
                        URI.create("http://192.168.40.239:8759/openicf")).setPrincipal(
                        ConnectionPrincipal.DEFAULT_NAME).setPassword(DEFAULT_GUARDED_PASSWORD)
                        //.setProxyHost("127.0.0.1").setProxyPort(8888)
                        .build();

        System.setProperty("javax.net.ssl.trustStore", "/pointTo/TrustStore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", JSK_PASSWORD);
    }

    protected AsyncRemoteConnectorInfoManager getConnectorInfoManager() throws Exception {
        return getConnectorFramework().getRemoteManager(CONNECTION_INFO);
    }

    public ConnectorKey getTestConnectorKey() {
        return TEST_CONNECTOR_KEY;
    }

    public ConnectorKey getTestStatefulConnectorKey() {
        return TEST_STATEFUL_CONNECTOR_KEY;
    }

    public ConnectorKey getTestPoolableStatefulConnectorKey() {
        return TEST_POOLABLE_STATEFUL_CONNECTOR_KEY;
    }

    @Test
    public void testNullScriptOperations() throws Exception {
        final ConnectorFacade facade = getConnectorFacade(true, true);
        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        facade.test();

        ScriptContextBuilder contextBuilder =
                new ScriptContextBuilder().setScriptLanguage("Boo").setScriptText(
                        "arg").addScriptArgument("arg", "test");

        Object o = facade.runScriptOnConnector(contextBuilder.build(), optionsBuilder.build());
        Assert.assertEquals(o, "test");
        o = facade.runScriptOnResource(contextBuilder.build(), optionsBuilder.build());
        Assert.assertNull(o);
    }

    @Test
    public void testScriptOperations() throws Exception {
        ConnectorFacade facade = getConnectorFacade();
        facade.test();
        ScriptContextBuilder contextBuilder =
                new ScriptContextBuilder().setScriptLanguage("Boo").setScriptText(
                        "arg").addScriptArgument("arg", "test");

        Object o = facade.runScriptOnConnector(contextBuilder.build(), null);
        Assert.assertEquals(o, "test");
        o = facade.runScriptOnResource(contextBuilder.build(), null);
        Assert.assertEquals(o, "test");
    }

    @Test
    public void testConfigurationUpdate() throws Exception {
        ConnectorInfo[] infos =
                new ConnectorInfo[] {
                        getConnectorInfoManager().findConnectorInfoAsync(getTestStatefulConnectorKey())
                                .get(5, TimeUnit.MINUTES),
                        getConnectorInfoManager().findConnectorInfoAsync(
                                getTestPoolableStatefulConnectorKey()).get(5, TimeUnit.MINUTES) };
        for (ConnectorInfo info : infos) {
            APIConfiguration api = info.createDefaultAPIConfiguration();

            ConfigurationProperties props = api.getConfigurationProperties();
            props.getProperty("randomString").setValue(StringUtil.randomString());
            api.setProducerBufferSize(0);

            final AtomicReference<List<ConfigurationProperty>> current =
                    new AtomicReference<List<ConfigurationProperty>>();
            api.setChangeListener(new ConfigurationPropertyChangeListener() {
                public void configurationPropertyChange(List<ConfigurationProperty> changes) {
                    current.set(changes);
                }
            });

            ConnectorFacade facade = getConnectorFramework().newInstance(api);

            ScriptContextBuilder builder = new ScriptContextBuilder();
            builder.setScriptLanguage("Boo");

            builder.setScriptText("connector.Update()");
            facade.runScriptOnConnector(builder.build(), null);

            for (int i = 0; (i < 5 && null == current.get()); i++) {
                Thread.sleep(1000);
            }
            assertNotNull(current.get());
            assertEquals(current.get().size(), 1);
            assertEquals(current.get().get(0).getValue(), "change");
        }
    }
}
