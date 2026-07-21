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
 * Copyright 2015-2016 ForgeRock AS.
 * Portions Copyrighted 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.framework.server.jetty;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.Frame;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketFrameListener;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WebSocketPingPongListener;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Utils;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

/**
 * The Jetty endpoint of a single WebSocket connection.
 * <p>
 * One instance is created per connection by {@link OpenICFWebSocketCreator},
 * while the {@link ConnectionPrincipal} it belongs to is cached per principal
 * name and shared by every connection of that name. Keeping the session, the
 * {@link WebSocketConnectionHolder} and the close state here (instead of on
 * the shared principal) lets concurrent and successive connections of one
 * principal coexist: each connection delivers its own close event and cleans
 * up its own resources.
 */
public class OpenICFWebSocket implements
        WebSocketPingPongListener, WebSocketListener, WebSocketFrameListener {

    private static final Log logger = Log.getLog(OpenICFWebSocket.class);

    private final ConnectionPrincipal<?> principal;

    private Session session;

    // Jetty invokes the callbacks of one connection sequentially, and this
    // instance serves exactly one connection, so a plain field is enough.
    private boolean closed = false;

    // Written on the handshake-processing pool thread, read by other message
    // threads via getRemoteConnectionContext()/isHandHooked().
    private volatile RemoteOperationContext context = null;

    // Single send thread per connection: frames must leave in submission
    // order (the peer drops e.g. an operation response that overtakes the
    // handshake response). Jetty's RemoteEndpoint is thread-safe, but
    // concurrent blocking sends may reach the wire in any order. The executor
    // is shut down when this connection closes.
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(
            Utils.newThreadFactory(null, "OpenICF Jetty WebSocket Send %d", true));

    public OpenICFWebSocket(final ConnectionPrincipal<?> principal) {
        this.principal = principal;
    }

    Session getSession() {
        return session;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        WebSocketPingPongListener.super.onWebSocketConnect(session);
        this.session = session;
        principal.getOperationMessageListener().onConnect(adapter);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        if (closed) {
            return;
        }
        closed = true;
        try {
            principal.getOperationMessageListener().onClose(adapter, statusCode, reason);
        } finally {
            // Notifies the holder's close listeners so the
            // WebSocketConnectionGroup drops this connection.
            adapter.close();
            sendExecutor.shutdown();
        }
    }

    @Override
    public void onWebSocketError(Throwable t) {
        logger.ok(t, "onError");
        principal.getOperationMessageListener().onError(t);
    }

    @Override
    public void onWebSocketPing(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        principal.getOperationMessageListener().onPing(adapter, b);
    }

    @Override
    public void onWebSocketPong(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        principal.getOperationMessageListener().onPong(adapter, b);
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        logger.ok("onBinaryMessage({0})", null != payload ? payload.length : 0);
        principal.getOperationMessageListener().onMessage(adapter, payload);
    }

    @Override
    public void onWebSocketText(String message) {
        logger.ok("onTextMessage({0})", message);
        principal.getOperationMessageListener().onMessage(adapter, message);
    }

    @Override
    public void onWebSocketFrame(Frame frame) {
        logger.ok("onWebSocketFrame({0})", frame);
    }

    private final WebSocketConnectionHolder adapter = new WebSocketConnectionHolder() {

        @Override
        protected void handshake(RPCMessages.HandshakeMessage message) {
            context = principal.handshake(this, message);
        }

        @Override
        public boolean isOperational() {
            return null != getSession() && getSession().isOpen();
        }

        @Override
        public RemoteOperationContext getRemoteConnectionContext() {
            return context;
        }

        @Override
        public Future<?> sendBytes(byte[] data) {
            if (isOperational()) {
                try {
                    return sendExecutor.submit(() -> {
                        try {
                            getSession().getRemote().sendBytes(ByteBuffer.wrap(data));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    // The connection was closed and the executor shut down.
                    return Promises.newExceptionPromise(new ConnectorIOException(
                            "Socket is not connected."));
                }
            } else {
                return Promises.newExceptionPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

        @Override
        public Future<?> sendString(String data) {
            if (isOperational()) {
                try {
                    return sendExecutor.submit(() -> {
                        try {
                            getSession().getRemote().sendString(data);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    return Promises.newExceptionPromise(new ConnectorIOException(
                            "Socket is not connected."));
                }
            } else {
                return Promises.newExceptionPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

        @Override
        public void sendPing(byte[] applicationData) throws Exception {
            if (isOperational()) {
                getSession().getRemote().sendPing(ByteBuffer.wrap(applicationData));
                if (getSession().getRemote().getBatchMode() == BatchMode.ON) {
                    getSession().getRemote().flush();
                }
            } else {
                throw new ConnectorIOException("Socket is not connected.");
            }
        }

        @Override
        public void sendPong(byte[] applicationData) throws Exception {
            if (isOperational()) {
                getSession().getRemote().sendPong(ByteBuffer.wrap(applicationData));
                if (getSession().getRemote().getBatchMode() == BatchMode.ON) {
                    getSession().getRemote().flush();
                }
            } else {
                throw new ConnectorIOException("Socket is not connected.");
            }
        }

        @Override
        protected void tryClose() {
            final Session current = getSession();
            if (null != current) {
                current.close(StatusCode.NORMAL, "Shutdown");
            }
        }

    };
}
