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
package org.forgerock.openicf.framework.server.jetty;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.forgerock.openicf.framework.CloseListener;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * OpenICFWebSocketServletBase.destroy() must end the lifecycle of everything
 * the servlet created lazily for itself: close the websocket creator (and
 * with it the cached principals), shut the private scheduler down and release
 * the lazily acquired ConnectorFramework
 * (OpenIdentityPlatform/OpenICF#112).
 */
public class ServletLifecycleTest {

    static class TestServlet extends OpenICFWebSocketServletBase {

        private final ConnectorFrameworkFactory factory = new ConnectorFrameworkFactory();

        volatile ConnectorFramework framework;

        @Override
        protected ConnectorFrameworkFactory getConnectorFrameworkFactory() {
            return factory;
        }

        @Override
        protected void configure(ConnectorFramework connectorFramework) {
            framework = connectorFramework;
        }
    }

    private static JettyServerUpgradeRequest upgradeRequest() {
        return (JettyServerUpgradeRequest) Proxy.newProxyInstance(
                ServletLifecycleTest.class.getClassLoader(),
                new Class<?>[] { JettyServerUpgradeRequest.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("getSubProtocols".equals(m.getName())) {
                            return Collections.emptyList();
                        }
                        return null;
                    }
                });
    }

    private static JettyServerUpgradeResponse upgradeResponse() {
        return (JettyServerUpgradeResponse) Proxy.newProxyInstance(
                ServletLifecycleTest.class.getClassLoader(),
                new Class<?>[] { JettyServerUpgradeResponse.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("isCommitted".equals(m.getName())) {
                            return Boolean.FALSE;
                        }
                        return null;
                    }
                });
    }

    @Test(timeOut = 30000)
    @SuppressWarnings("unchecked")
    public void testDestroyReleasesLazilyAcquiredResources() throws Exception {
        TestServlet servlet = new TestServlet();

        final AtomicReference<OpenICFWebSocketCreator> creatorRef =
                new AtomicReference<OpenICFWebSocketCreator>();
        JettyWebSocketServletFactory factory =
                (JettyWebSocketServletFactory) Proxy.newProxyInstance(
                        ServletLifecycleTest.class.getClassLoader(),
                        new Class<?>[] { JettyWebSocketServletFactory.class },
                        new InvocationHandler() {
                            public Object invoke(Object p, Method m, Object[] a) {
                                if ("setCreator".equals(m.getName())) {
                                    creatorRef.set((OpenICFWebSocketCreator) a[0]);
                                }
                                return null;
                            }
                        });

        // The no-arg servlet acquires its framework and scheduler lazily.
        servlet.configure(factory);

        OpenICFWebSocketCreator creator = creatorRef.get();
        Assert.assertNotNull(creator, "configure() must install the creator");
        Assert.assertNotNull(servlet.framework, "the lazy path must configure the framework");
        Assert.assertTrue(servlet.framework.isRunning());

        ScheduledExecutorService scheduler = servlet.getExecutorService();
        Assert.assertFalse(scheduler.isShutdown());

        ConnectionPrincipal<?> principal = creator.authenticate(upgradeRequest(), upgradeResponse());
        Assert.assertNotNull(principal);
        final AtomicInteger closed = new AtomicInteger();
        ((ConnectionPrincipal<SinglePrincipal>) principal)
                .addCloseListener(new CloseListener<SinglePrincipal>() {
                    public void onClosed(SinglePrincipal source) {
                        closed.incrementAndGet();
                    }
                });

        servlet.destroy();

        Assert.assertEquals(closed.get(), 1,
                "destroy() must close the creator and with it the cached principals");
        Assert.assertNull(creator.createWebSocket(upgradeRequest(), upgradeResponse()),
                "a closed creator must not accept new connections");
        Assert.assertTrue(scheduler.isShutdown(),
                "destroy() must shut the private scheduler down");
        Assert.assertFalse(servlet.framework.isRunning(),
                "destroy() must release the lazily acquired framework");
    }
}
