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
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * OpenICFWebSocketCreator caches one SinglePrincipal per principal name and
 * creates a fresh OpenICFWebSocket endpoint for every connection. A close on
 * one connection must neither break sends on a later connection of the same
 * principal nor swallow the close events of later connections.
 */
public class ReconnectSendTest {

    private final AtomicInteger sent = new AtomicInteger();

    private Session newSession() {
        final RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(
                ReconnectSendTest.class.getClassLoader(),
                new Class<?>[] { RemoteEndpoint.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("sendBytes".equals(m.getName())) {
                            sent.incrementAndGet();
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
    public void testSendAndCloseWorkPerConnectionOnCachedPrincipal() throws Exception {
        final AtomicReference<WebSocketConnectionHolder> holder =
                new AtomicReference<WebSocketConnectionHolder>();
        final AtomicInteger closes = new AtomicInteger();

        OperationMessageListener capturing = (OperationMessageListener) Proxy.newProxyInstance(
                ReconnectSendTest.class.getClassLoader(),
                new Class<?>[] { OperationMessageListener.class },
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) {
                        if ("onConnect".equals(m.getName())) {
                            holder.set((WebSocketConnectionHolder) a[0]);
                        }
                        if ("onClose".equals(m.getName())) {
                            closes.incrementAndGet();
                        }
                        return null;
                    }
                });

        SinglePrincipal principal = new SinglePrincipal("anonymous", capturing, null,
                new ConcurrentHashMap<String, WebSocketConnectionGroup>());

        // --- connection #1 ---
        OpenICFWebSocket first = new OpenICFWebSocket(principal);
        first.onWebSocketConnect(newSession());
        Future<?> firstSend = holder.get().sendBytes(new byte[] { 1, 2, 3 });
        firstSend.get(5, TimeUnit.SECONDS);
        Assert.assertEquals(sent.get(), 1, "first connection should have sent");

        first.onWebSocketClose(1000, "client went away");
        Assert.assertEquals(closes.get(), 1, "first close must reach the listener");

        // duplicate close events of the same connection stay suppressed
        first.onWebSocketClose(1000, "duplicate");
        Assert.assertEquals(closes.get(), 1);

        // --- connection #2 on the SAME cached principal ---
        OpenICFWebSocket second = new OpenICFWebSocket(principal);
        second.onWebSocketConnect(newSession());
        Future<?> secondSend = holder.get().sendBytes(new byte[] { 4, 5, 6 });
        secondSend.get(5, TimeUnit.SECONDS);
        Assert.assertEquals(sent.get(), 2, "send after reconnect must reach the wire");

        second.onWebSocketClose(1000, "client went away again");
        Assert.assertEquals(closes.get(), 2,
                "close of a later connection must not be swallowed by an earlier one");
    }

    /**
     * The connection group learns about a departed connection only through
     * the holder's close listeners (OpenIdentityPlatform/OpenICF#112): before
     * the fix onWebSocketClose never called adapter.close(), so every
     * reconnect left a stale duplicate holder in the group.
     */
    @Test(timeOut = 30000)
    public void testGroupDropsHolderOnClose() throws Exception {
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
        WebSocketConnectionGroup group = new WebSocketConnectionGroup("session-1");
        RPCMessages.HandshakeMessage handshake =
                RPCMessages.HandshakeMessage.newBuilder().setSessionId("session-1").build();

        // --- connection #1 joins and leaves the group ---
        OpenICFWebSocket first = new OpenICFWebSocket(principal);
        first.onWebSocketConnect(newSession());
        group.handshake(principal, holder.get(), handshake);
        Assert.assertTrue(group.isOperational(), "handshake must register the holder");

        first.onWebSocketClose(1000, "client went away");
        Assert.assertFalse(group.isOperational(),
                "the group must drop the holder of a closed connection");

        // --- reconnect: no stale holder of connection #1 may keep the group
        // alive after connection #2 leaves as well ---
        OpenICFWebSocket second = new OpenICFWebSocket(principal);
        second.onWebSocketConnect(newSession());
        group.handshake(principal, holder.get(), handshake);
        Assert.assertTrue(group.isOperational(), "reconnect must register the new holder");

        second.onWebSocketClose(1000, "client went away again");
        Assert.assertFalse(group.isOperational(),
                "no duplicate holder may survive a reconnect cycle");
    }
}
