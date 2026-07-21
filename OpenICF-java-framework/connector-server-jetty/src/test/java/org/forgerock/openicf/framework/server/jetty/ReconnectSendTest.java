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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Does a SinglePrincipal that has seen one connection closed still send on a
 * subsequent connection? OpenICFWebSocketCreator caches SinglePrincipal per
 * principal name, so the same instance serves every connection of that name.
 */
public class ReconnectSendTest {

    private static final AtomicInteger SENT = new AtomicInteger();

    private static Session newSession() {
        final RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(
                ReconnectSendTest.class.getClassLoader(),
                new Class<?>[] { RemoteEndpoint.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("sendBytes".equals(m.getName())) {
                            SENT.incrementAndGet();
                        }
                        return null;
                    }
                });
        return (Session) Proxy.newProxyInstance(
                ReconnectSendTest.class.getClassLoader(),
                new Class<?>[] { Session.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("isOpen".equals(m.getName())) {
                            return Boolean.TRUE;
                        }
                        if ("getRemote".equals(m.getName())) {
                            return remote;
                        }
                        return null;
                    }
                });
    }

    @Test(timeOut = 30000)
    public void testSendWorksAfterReconnectOnCachedPrincipal() throws Exception {
        final AtomicReference<WebSocketConnectionHolder> holder =
                new AtomicReference<WebSocketConnectionHolder>();

        OperationMessageListener capturing = (OperationMessageListener) Proxy.newProxyInstance(
                ReconnectSendTest.class.getClassLoader(),
                new Class<?>[] { OperationMessageListener.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("onConnect".equals(m.getName())) {
                            holder.set((WebSocketConnectionHolder) a[0]);
                        }
                        return null;
                    }
                });

        SinglePrincipal principal = new SinglePrincipal("anonymous", capturing, null,
                new ConcurrentHashMap<String, WebSocketConnectionGroup>());

        // --- connection #1 ---
        principal.onWebSocketConnect(newSession());
        Future<?> first = holder.get().sendBytes(new byte[] { 1, 2, 3 });
        first.get(5, TimeUnit.SECONDS);
        Assert.assertEquals(SENT.get(), 1, "first connection should have sent");

        principal.onWebSocketClose(1000, "client went away");

        // --- connection #2 on the SAME cached principal instance ---
        principal.onWebSocketConnect(newSession());
        Future<?> second = holder.get().sendBytes(new byte[] { 4, 5, 6 });
        second.get(5, TimeUnit.SECONDS);

        Assert.assertEquals(SENT.get(), 2,
                "send after reconnect must reach the wire");
    }
}
