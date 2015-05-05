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

import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.KEY_HASH;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.TEST_STATEFUL_CONNECTOR_KEY;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.buildRemoteProxyFrameworkConnectionInfo;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.buildRemoteWSFrameworkConnectionInfo;
import static org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase.findFreePort;
import static org.forgerock.openicf.framework.server.OpenICFWebSocketTest.createSSLContext;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.FailoverLoadBalancingAlgorithmFactory;
import org.forgerock.openicf.framework.remote.LoadBalancingAlgorithmFactory;
import org.forgerock.openicf.framework.remote.LoadBalancingConnectorFacadeContext;
import org.forgerock.openicf.framework.remote.LoadBalancingConnectorInfoManager;
import org.forgerock.openicf.framework.remote.OpenICFServerAdapter;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.remote.RoundRobinLoadBalancingAlgorithmFactory;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.security.SharedSecretPrincipal;
import org.forgerock.openicf.framework.server.grizzly.OpenICFWebSocketApplication;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.impl.api.remote.RemoteWrappedException;
import org.identityconnectors.testconnector.TstConnector;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

@Test
public class LoadBalancingConnectorInfoManagerTest {

    private int PLAIN_PORT = findFreePort();
    private int SECURE_PORT = findFreePort();
    private int PROXY_PORT = findFreePort();

    private RemoteWSFrameworkConnectionInfo CONNECTION_INFO = buildRemoteWSFrameworkConnectionInfo(
            false, PLAIN_PORT, "/proxy");
    private RemoteWSFrameworkConnectionInfo SECURE_CONNECTION_INFO =
            buildRemoteWSFrameworkConnectionInfo(true, SECURE_PORT, "/proxy");
    private RemoteWSFrameworkConnectionInfo PROXY_CONNECTION_INFO =
            buildRemoteProxyFrameworkConnectionInfo(false, PLAIN_PORT, PROXY_PORT, "/proxy");;

    private final ConnectorFrameworkFactory localConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private final ConnectorFrameworkFactory serverConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private ConnectorServer connectorServer = null;

    private HttpProxyServer proxyServer;
    private final AtomicBoolean reject = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger connectionAttempt = new AtomicInteger(0);
    private final List<ChannelHandlerContext> contexts =
            new CopyOnWriteArrayList<ChannelHandlerContext>();

    protected ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework = null;

    @BeforeClass
    public void beforeClass() throws Exception {
        Reporter.log(String.format("HTTP:%d, HTTPS:%d , HTTP-PROXY:%d", PLAIN_PORT, SECURE_PORT,
                PROXY_PORT), true);
        connectorServer = createConnectorServer();
        connectorServer.setConnectorFrameworkFactory(serverConnectorFrameworkFactory);
        connectorServer.setConnectorBundleURLs(Arrays.asList(TstConnector.class
                .getProtectionDomain().getCodeSource().getLocation()));

        connectorServer.addListener("grizzly-test", NetworkListener.DEFAULT_NETWORK_HOST,
                PLAIN_PORT);
        connectorServer.addListener("grizzly-secure-test", NetworkListener.DEFAULT_NETWORK_HOST,
                SECURE_PORT, createSSLContext(false));

        connectorServer.setKeyHash(KEY_HASH);
        connectorServer.init();
        connectorServer.start();
        Reporter.log("Grizzly LB Server Started", true);
        connectorFramework = localConnectorFrameworkFactory.acquire();
        proxyServer =
                DefaultHttpProxyServer.bootstrap().withPort(PROXY_PORT).withAllowLocalOnly(true)
                        .withFiltersSource(new HttpFiltersSourceAdapter() {
                            public HttpFilters filterRequest(final HttpRequest originalRequest,
                                    final ChannelHandlerContext ctx) {
                                if (reject.get()) {
                                    connectionAttempt.incrementAndGet();
                                    ctx.close();
                                } else {
                                    contexts.add(ctx);
                                }
                                return super.filterRequest(originalRequest, ctx);
                            }
                        }).start();
    }

    @AfterClass
    public void afterClass() throws Exception {
        Reporter.log("Shutdown Test:" + getClass());
        connectorFramework.release();
        proxyServer.stop();
        connectorServer.stop();
        connectorServer.destroy();
        Reporter.log("Grizzly LB Server Stopped", true);
    }

    protected ConnectorServer createConnectorServer() {
        final Map<String, OpenICFWebSocketApplication.SinglePrincipal> tenants =
                new HashMap<String, OpenICFWebSocketApplication.SinglePrincipal>();

        return new ConnectorServer() {

            protected String getContextPath() {
                return "/proxy";
            }

            protected OpenICFWebSocketApplication createOpenICFWebSocketApplication(
                    final ConnectorFramework framework, final String sharedKeyHash) {
                return new OpenICFWebSocketApplication(framework, sharedKeyHash) {
                    public ConnectionPrincipal authenticate(Principal principal) {
                        if (principal instanceof SharedSecretPrincipal) {
                            final SharedSecretPrincipal sharedSecretPrincipal =
                                    (SharedSecretPrincipal) principal;
                            SinglePrincipal sp = tenants.get(sharedSecretPrincipal.getName());
                            if (sp == null) {
                                synchronized (this) {
                                    sp = tenants.get(sharedSecretPrincipal.getName());
                                    if (sp == null) {
                                        try {
                                            sp =
                                                    new SinglePrincipal(
                                                            new OpenICFServerAdapter(framework,
                                                                    framework.getLocalManager(),
                                                                    false),
                                                            new ConcurrentHashMap<String, WebSocketConnectionGroup>()) {
                                                        public String getName() {
                                                            return sharedSecretPrincipal.getName();
                                                        }
                                                    };
                                        } catch (Throwable t) {
                                            Assert.fail("Failed to authenticate", t);
                                        }
                                        tenants.put(sharedSecretPrincipal.getName(), sp);
                                    }
                                }
                            }
                            return sp;
                        } else {
                            return super.authenticate(principal);
                        }
                    }
                };
            }
        };
    }

    protected ConnectorFramework getConnectorFramework() {
        return connectorFramework.get();
    }

    @Test
    protected void testLoadBalancer() throws Exception {

        AsyncRemoteConnectorInfoManager managerA =
                getConnectorFramework().getRemoteManager(CONNECTION_INFO);
        AsyncRemoteConnectorInfoManager managerB =
                getConnectorFramework().getRemoteManager(SECURE_CONNECTION_INFO);
        AsyncRemoteConnectorInfoManager managerC =
                getConnectorFramework().getRemoteManager(PROXY_CONNECTION_INFO);

        managerA.findConnectorInfoAsync(TEST_STATEFUL_CONNECTOR_KEY).getOrThrow(10, TimeUnit.SECONDS);
        managerB.findConnectorInfoAsync(TEST_STATEFUL_CONNECTOR_KEY).getOrThrow(10, TimeUnit.SECONDS);
        managerC.findConnectorInfoAsync(TEST_STATEFUL_CONNECTOR_KEY).getOrThrow(10, TimeUnit.SECONDS);
        
        // Group the site A and B into a RoundRobin group
        LoadBalancingAlgorithmFactory algorithmFactory =
                new RoundRobinLoadBalancingAlgorithmFactory();
        algorithmFactory.addAsyncRemoteConnectorInfoManager(managerA);
        algorithmFactory.addAsyncRemoteConnectorInfoManager(managerB);

        LoadBalancingConnectorInfoManager managerAB =
                getConnectorFramework().getRemoteManager(algorithmFactory);

        // Group the previous site and site C into a Failover group
        algorithmFactory = new FailoverLoadBalancingAlgorithmFactory();
        // Primary server
        algorithmFactory.addAsyncRemoteConnectorInfoManager(managerC);
        // Failover server
        algorithmFactory.addAsyncRemoteConnectorInfoManager(managerAB);

        LoadBalancingConnectorInfoManager managerABC =
                getConnectorFramework().getRemoteManager(algorithmFactory);

        // Use the same configuration on each server
        Promise<ConnectorFacade, RuntimeException> facadePromise =
                managerABC.findConnectorInfoAsync(TEST_STATEFUL_CONNECTOR_KEY).then(
                        new Function<ConnectorInfo, ConnectorFacade, RuntimeException>() {
                            public ConnectorFacade apply(ConnectorInfo value)
                                    throws RuntimeException {

                                // Create the ConnectorFacade
                                final APIConfiguration configuration =
                                        value.createDefaultAPIConfiguration();

                                ConfigurationProperties props =
                                        configuration.getConfigurationProperties();

                                props.getProperty("randomString").setValue(
                                        StringUtil.randomString());
                                props.getProperty("caseIgnore").setValue(false);
                                props.getProperty("returnNullTest").setValue(false);
                                props.getProperty("failValidation").setValue(false);
                                props.getProperty("testObjectClass").setValue(
                                        new String[] { ObjectClass.ACCOUNT_NAME,
                                            ObjectClass.GROUP_NAME });

                                return getConnectorFramework().newInstance(configuration);

                            }
                        });

        facadePromise.get(1, TimeUnit.MINUTES).test();

        // Test Failover
        for (ChannelHandlerContext ctx : contexts) {
            ctx.close();
        }
        reject.set(true);

        // Wait until the Connection is closed to avoid the race condition
        // when
        // message is silently rejected on remote side. Request must have
        // max
        // lifeTime
        Thread.sleep(1000L);
        facadePromise.get().test();

        Assert.assertFalse(connectionAttempt.get() == 0);

        // Use different configuration per server to test RoundRobin.
        facadePromise =
                managerABC
                        .newInstance(
                                TEST_STATEFUL_CONNECTOR_KEY,
                                new Function<LoadBalancingConnectorFacadeContext, APIConfiguration, RuntimeException>() {
                                    public APIConfiguration apply(
                                            final LoadBalancingConnectorFacadeContext context)
                                            throws RuntimeException {
                                        Reporter.log("Create FacadeKey for "
                                                + context.getPrincipalName(), true);
                                        final APIConfiguration configuration =
                                                context.getAPIConfiguration();
                                        ConfigurationProperties props =
                                                configuration.getConfigurationProperties();

                                        props.getProperty("caseIgnore").setValue(false);
                                        props.getProperty("returnNullTest").setValue(false);
                                        props.getProperty("failValidation").setValue(false);
                                        props.getProperty("testObjectClass").setValue(
                                                new String[] { ObjectClass.ACCOUNT_NAME,
                                                    ObjectClass.GROUP_NAME });

                                        if ("plain".equals(context.getPrincipalName())) {
                                            props.getProperty("randomString").setValue(
                                                    "PlainFacade");

                                        } else if ("secure".equals(context.getPrincipalName())) {
                                            props.getProperty("randomString").setValue(
                                                    "SecureFacade");

                                        } else if ("proxy".equals(context.getPrincipalName())) {
                                            props.getProperty("randomString").setValue(
                                                    "ProxyFacade");

                                        } else {
                                            throw new IllegalArgumentException(
                                                    "Unknown remote connector server");
                                        }

                                        return configuration;
                                    }
                                });

        facadePromise.get(1, TimeUnit.MINUTES).test();

        // The Create should succeed 3 time on each server.

        Set<Attribute> createAttributes =
                CollectionUtil.newSet(new Name("LB1"), AttributeBuilder.buildPassword("Passw0rd"
                        .toCharArray()));

        facadePromise.get().create(ObjectClass.ACCOUNT, createAttributes, null);
        try {
            facadePromise.get().create(ObjectClass.ACCOUNT, createAttributes, null);
        } catch (RemoteWrappedException e) {
            Assert.fail("Failed on 2nd site", e);
        }
        reject.set(false);
        try {
            facadePromise.get().create(ObjectClass.ACCOUNT, createAttributes, null);
        } catch (RemoteWrappedException e) {
            Assert.fail("Failed on proxy site", e);
        }
        try {
            facadePromise.get().create(ObjectClass.ACCOUNT, createAttributes, null);
        } catch (RemoteWrappedException e) {
            // Expected
            Assert.assertTrue(e.is(AlreadyExistsException.class));
        }
    }
}
