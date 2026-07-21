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
package org.forgerock.openicf.framework.server.grizzly;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.openicf.framework.server.grizzly.OpenICFWebSocketApplication.OpenICFWebSocket;
import org.forgerock.openicf.framework.server.grizzly.OpenICFWebSocketApplication.SinglePrincipal;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.websockets.ClosingFrame;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The connection group learns about a departed connection only through the
 * holder's close listeners (OpenIdentityPlatform/OpenICF#118): before the fix
 * {@link OpenICFWebSocket#onClose} never called {@code adapter.close()}, so
 * every reconnect of the same session left a stale duplicate holder in
 * {@link WebSocketConnectionGroup}.
 */
public class ReconnectGroupTest {

    /**
     * A ProtocolHandler without a Connection. SimpleWebSocket.onClose invokes
     * close()/doClose() on it, which would try to write the close frame to
     * the wire (and throw on the null connection) if not stubbed out.
     */
    private static ProtocolHandler stubHandler() {
        return new ProtocolHandler(false) {

            @Override
            public GrizzlyFuture<DataFrame> close(int code, String reason) {
                return null;
            }

            @Override
            public void doClose() {
            }

            @Override
            public GrizzlyFuture<DataFrame> send(DataFrame frame,
                    CompletionHandler<DataFrame> completionHandler) {
                return null;
            }

            @Override
            public GrizzlyFuture<DataFrame> send(byte[] data) {
                return null;
            }

            @Override
            public GrizzlyFuture<DataFrame> send(String data) {
                return null;
            }

            @Override
            public byte[] frame(DataFrame frame) {
                return new byte[0];
            }

            @Override
            public HandShake createServerHandShake(HttpContent requestContent) {
                return null;
            }

            @Override
            public HandShake createClientHandShake(URI uri) {
                return null;
            }

            @Override
            public DataFrame parse(Buffer buffer) {
                return null;
            }

            @Override
            protected boolean isControlFrame(byte opcode) {
                return false;
            }
        };
    }

    @Test(timeOut = 30000)
    public void testGroupDropsHolderOnClose() {
        final AtomicReference<WebSocketConnectionHolder> holder =
                new AtomicReference<WebSocketConnectionHolder>();
        final AtomicInteger closes = new AtomicInteger();

        OperationMessageListener capturing = (OperationMessageListener) Proxy.newProxyInstance(
                ReconnectGroupTest.class.getClassLoader(),
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

        SinglePrincipal principal = new SinglePrincipal(capturing,
                new ConcurrentHashMap<String, WebSocketConnectionGroup>());
        WebSocketConnectionGroup group = new WebSocketConnectionGroup("session-1");
        RPCMessages.HandshakeMessage handshake =
                RPCMessages.HandshakeMessage.newBuilder().setSessionId("session-1").build();

        // --- connection #1 joins and leaves the group ---
        OpenICFWebSocket first = new OpenICFWebSocket(stubHandler(), null, principal);
        first.onConnect();
        group.handshake(principal, holder.get(), handshake);
        Assert.assertTrue(group.isOperational(), "handshake must register the holder");

        first.onClose(new ClosingFrame(1000, "client went away"));
        Assert.assertFalse(group.isOperational(),
                "the group must drop the holder of a closed connection");
        Assert.assertEquals(closes.get(), 1, "close must reach the listener");

        // Grizzly delivers onClose once per direction of the close handshake
        // - the duplicate must stay harmless.
        first.onClose(new ClosingFrame(1000, "duplicate"));
        Assert.assertFalse(group.isOperational());
        Assert.assertEquals(closes.get(), 1, "a duplicate close event must stay suppressed");

        // --- reconnect: no stale holder of connection #1 may keep the group
        // alive after connection #2 leaves as well ---
        OpenICFWebSocket second = new OpenICFWebSocket(stubHandler(), null, principal);
        second.onConnect();
        group.handshake(principal, holder.get(), handshake);
        Assert.assertTrue(group.isOperational(), "reconnect must register the new holder");

        second.onClose(new ClosingFrame(1000, "client went away again"));
        Assert.assertFalse(group.isOperational(),
                "no stale holder may survive a reconnect cycle");
    }
}
