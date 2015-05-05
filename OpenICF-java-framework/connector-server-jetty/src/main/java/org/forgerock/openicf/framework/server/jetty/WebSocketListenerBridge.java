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
package org.forgerock.openicf.framework.server.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.events.AbstractEventDriver;
import org.eclipse.jetty.websocket.common.message.SimpleBinaryMessage;
import org.eclipse.jetty.websocket.common.message.SimpleTextMessage;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

public class WebSocketListenerBridge extends AbstractEventDriver {

    private static final Logger logger = Log.getLogger(WebSocketListenerBridge.class);
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

        public Future<?> sendBytes(byte[] data) {
            if (isOperational()) {
                return getSession().getRemote().sendBytesByFuture(ByteBuffer.wrap(data));
            } else {
                return Promises.newFailedPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

        public Future<?> sendString(String data) {
            if (isOperational()) {
                return getSession().getRemote().sendStringByFuture(data);
            } else {
                return Promises.newFailedPromise(new ConnectorIOException(
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
            WebSocketListenerBridge.this.terminateConnection(StatusCode.NORMAL, "TEST003");
        }

    };

    public WebSocketListenerBridge(WebSocketPolicy policy, ConnectionPrincipal webSocket) {
        super(policy, webSocket);
    }

    protected ConnectionPrincipal getConnectionPrincipal() {
        return ConnectionPrincipal.class.cast(websocket);
    }

    @Override
    public void onBinaryFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (activeMessage == null) {
            activeMessage = new SimpleBinaryMessage(this);
        }

        appendMessage(buffer, fin);
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        logger.debug("onBinaryMessage('" + (null != data ? data.length : 0) + "')");
        getConnectionPrincipal().getOperationMessageListener().onMessage(adapter, data);
    }

    @Override
    public void onClose(CloseInfo close) {
        if (hasCloseBeenCalled) {
            // avoid duplicate close events (possible when using harsh
            // Session.disconnect())
            return;
        }
        hasCloseBeenCalled = true;
        getConnectionPrincipal().getOperationMessageListener().onClose(adapter,
                close.getStatusCode(), close.getReason());
    }

    @Override
    public void onConnect() {
        if (logger.isDebugEnabled())
            logger.debug("Connect from: " + session.getRemoteAddress());
        getConnectionPrincipal().getOperationMessageListener().onConnect(adapter);
    }

    @Override
    public void onPong(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        getConnectionPrincipal().getOperationMessageListener().onPong(adapter, b);
    }

    @Override
    public void onPing(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        getConnectionPrincipal().getOperationMessageListener().onPing(adapter, b);
    }

    // //
    @Override
    public void onError(Throwable t) {
        /* ignore, not supported by WebSocketListenerAdapter */
        logger.debug("onError:", t);
        getConnectionPrincipal().getOperationMessageListener().onError(t);
    }

    @Override
    public void onFrame(Frame frame) {
        /* ignore, not supported by WebSocketListenerAdapter */
        logger.debug("onFrame");
    }

    @Override
    public void onInputStream(InputStream stream) {
        /* ignore, not supported by WebSocketListenerAdapter */
        logger.debug("onInputStream");
    }

    @Override
    public void onReader(Reader reader) {
        /* ignore, not supported by WebSocketListenerAdapter */
        logger.debug("onReader");
    }

    @Override
    public void onTextFrame(ByteBuffer buffer, boolean fin) throws IOException {
        if (activeMessage == null) {
            activeMessage = new SimpleTextMessage(this);
        }

        appendMessage(buffer, fin);
    }

    @Override
    public void onTextMessage(String message) {
        logger.debug("onTextMessage('" + message + "')");
        getConnectionPrincipal().getOperationMessageListener().onMessage(adapter, message);
    }

}
