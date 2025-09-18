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
 * Portions copyright 2025 3A Systems LLC.
 */


package org.forgerock.openicf.framework.server.jetty;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.Frame;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketFrameListener;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WebSocketPingPongListener;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SinglePrincipal extends ConnectionPrincipal<SinglePrincipal> implements
        WebSocketPingPongListener, WebSocketListener, WebSocketFrameListener {


    final String name;
    final ConnectorFramework connectorFramework;

    public SinglePrincipal(final String name,
                           final OperationMessageListener listener,
                           final ConnectorFramework connectorFramework,
                           final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups) {
        super(listener, globalConnectionGroups);
        this.name = name;
        this.connectorFramework = connectorFramework;
    }

    @Override
    public String getName() {
        return StringUtil.isBlank(name) ? super.getName() : name;
    }

    public RemoteOperationContext handshake(
            final WebSocketConnectionHolder webSocketConnection,
            final RPCMessages.HandshakeMessage message) {
        return super.handshake(webSocketConnection, message);
    }

    protected void doClose() {

    }


    @Override
    protected void onNewWebSocketConnectionGroup(final WebSocketConnectionGroup connectionGroup) {
        connectorFramework.getServerManager(getName()).addWebSocketConnectionGroup(connectionGroup);
    }

    @Override
    public void onWebSocketPing(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        getConnectionPrincipal().getOperationMessageListener().onPing(adapter, b);
    }

    @Override
    public void onWebSocketPong(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        getConnectionPrincipal().getOperationMessageListener().onPong(adapter, b);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        if (hasCloseBeenCalled) {
            return;
        }
        hasCloseBeenCalled = true;
        getConnectionPrincipal().getOperationMessageListener().onClose(adapter,
                statusCode, reason);
    }

    Session session;

    @Override
    public void onWebSocketConnect(Session session) {
        WebSocketPingPongListener.super.onWebSocketConnect(session);
        this.session = session;
        getConnectionPrincipal().getOperationMessageListener().onConnect(adapter);
    }

    Session getSession() {
        return this.session;
    }

    @Override
    public void onWebSocketError(Throwable t) {
        logger.debug("onError:", t);
        getConnectionPrincipal().getOperationMessageListener().onError(t);
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        logger.debug("onBinaryMessage('" + (null != payload ? payload.length : 0) + "')");
        getConnectionPrincipal().getOperationMessageListener().onMessage(adapter, payload);
    }

    @Override
    public void onWebSocketText(String message) {
        logger.debug("onTextMessage('" + message + "')");
        getConnectionPrincipal().getOperationMessageListener().onMessage(adapter, message);
    }

    @Override
    public void onWebSocketFrame(Frame frame) {
        logger.debug("onWebSocketFrame('" + frame + "')");
    }

    private static final Logger logger = Log.getLogger(SinglePrincipal.class);
    private boolean hasCloseBeenCalled = false;

    private RemoteOperationContext context = null;

    private final WebSocketConnectionHolder adapter = new WebSocketConnectionHolder() {

        protected void handshake(RPCMessages.HandshakeMessage message) {
            context = getConnectionPrincipal().handshake(this, message);
        }

        public boolean isOperational() {
            return getSession().isOpen();
        }

        public RemoteOperationContext getRemoteConnectionContext() {
            return context;
        }

        private final ExecutorService executorService = Executors.newFixedThreadPool(10);

        public Future<?> sendBytes(byte[] data) {
            if (isOperational()) {
                return executorService.submit(() -> {
                    try {
                        getSession().getRemote().sendBytes(ByteBuffer.wrap(data));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                return Promises.newExceptionPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

        public Future<?> sendString(String data) {
            if (isOperational()) {
                return executorService.submit(() -> {
                    try {
                        getSession().getRemote().sendString(data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                return Promises.newExceptionPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

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

        protected void tryClose() {
            getSession().close(StatusCode.NORMAL, "TEST003");
        }

    };

    protected ConnectionPrincipal<?> getConnectionPrincipal() {
        return this;
    }


}
