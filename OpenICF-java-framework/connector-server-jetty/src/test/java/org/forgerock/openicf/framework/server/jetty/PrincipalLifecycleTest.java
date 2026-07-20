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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.forgerock.openicf.framework.CloseListener;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * OpenICFWebSocketCreator must cache one principal per name, hand out a fresh
 * endpoint per connection, and end the lifecycle of the cached principals
 * from close() (OpenIdentityPlatform/OpenICF#112).
 */
public class PrincipalLifecycleTest {

    private static OperationMessageListener noopListener() {
        return (OperationMessageListener) Proxy.newProxyInstance(
                PrincipalLifecycleTest.class.getClassLoader(),
                new Class<?>[] { OperationMessageListener.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        return null;
                    }
                });
    }

    private static JettyServerUpgradeRequest upgradeRequest() {
        return (JettyServerUpgradeRequest) Proxy.newProxyInstance(
                PrincipalLifecycleTest.class.getClassLoader(),
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
                PrincipalLifecycleTest.class.getClassLoader(),
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
    public void testCreatorCachesPrincipalAndClosesItOnClose() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            OpenICFWebSocketCreator creator =
                    new OpenICFWebSocketCreator(null, noopListener(), null, scheduler);

            Object first = creator.createWebSocket(upgradeRequest(), upgradeResponse());
            Object second = creator.createWebSocket(upgradeRequest(), upgradeResponse());

            Assert.assertTrue(first instanceof OpenICFWebSocket,
                    "creator must hand out a per-connection endpoint");
            Assert.assertNotSame(first, second,
                    "every connection must get its own endpoint");

            ConnectionPrincipal<?> principal = creator.authenticate(upgradeRequest(), upgradeResponse());
            Assert.assertSame(creator.authenticate(upgradeRequest(), upgradeResponse()), principal,
                    "the principal must be cached per name");

            final AtomicInteger closed = new AtomicInteger();
            ((ConnectionPrincipal<SinglePrincipal>) principal)
                    .addCloseListener(new CloseListener<SinglePrincipal>() {
                        public void onClosed(SinglePrincipal source) {
                            closed.incrementAndGet();
                        }
                    });

            creator.close();

            Assert.assertEquals(closed.get(), 1,
                    "close() must end the lifecycle of the cached principal");
            Assert.assertTrue(creator.principalCache.isEmpty(),
                    "close() must drop the cached principals");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
