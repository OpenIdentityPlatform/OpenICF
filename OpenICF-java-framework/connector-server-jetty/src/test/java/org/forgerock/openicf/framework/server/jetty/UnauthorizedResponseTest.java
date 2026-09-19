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
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.callback.NameCallback;

import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * When createWebSocket() cannot resolve a principal, it must reject the upgrade with the
 * specific reason it determined, not a generic message that hides why (OpenIdentityPlatform/OpenICF).
 */
public class UnauthorizedResponseTest {

    private static OperationMessageListener noopListener() {
        return (OperationMessageListener) Proxy.newProxyInstance(
                UnauthorizedResponseTest.class.getClassLoader(),
                new Class<?>[] { OperationMessageListener.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        return null;
                    }
                });
    }

    private static JettyServerUpgradeRequest upgradeRequest() {
        return (JettyServerUpgradeRequest) Proxy.newProxyInstance(
                UnauthorizedResponseTest.class.getClassLoader(),
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

    @Test(timeOut = 30000)
    public void testUnauthorizedResponseIncludesTheReason() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            // Never sets a name on the callback, so authenticate() cannot resolve a principal.
            Authenticator silentAuthenticator = new Authenticator() {
                @Override
                public void authenticate(JettyServerUpgradeRequest request,
                        JettyServerUpgradeResponse response, NameCallback callback) {
                }
            };
            OpenICFWebSocketCreator creator = new OpenICFWebSocketCreator(null, noopListener(),
                    silentAuthenticator, scheduler);

            final AtomicReference<String> sentMessage = new AtomicReference<String>();
            JettyServerUpgradeResponse response = (JettyServerUpgradeResponse) Proxy.newProxyInstance(
                    UnauthorizedResponseTest.class.getClassLoader(),
                    new Class<?>[] { JettyServerUpgradeResponse.class },
                    new InvocationHandler() {
                        public Object invoke(Object p, Method m, Object[] a) {
                            if ("isCommitted".equals(m.getName())) {
                                return Boolean.FALSE;
                            }
                            if ("sendError".equals(m.getName())) {
                                sentMessage.set((String) a[1]);
                            }
                            return null;
                        }
                    });

            Object result = creator.createWebSocket(upgradeRequest(), response);

            Assert.assertNull(result, "an unresolved principal must not get a websocket endpoint");
            Assert.assertNotNull(sentMessage.get(), "createWebSocket() must reject with an error response");
            Assert.assertTrue(sentMessage.get().contains("Unknown Principal"),
                    "the rejection must surface the specific reason it was passed, not just the generic explanation");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
