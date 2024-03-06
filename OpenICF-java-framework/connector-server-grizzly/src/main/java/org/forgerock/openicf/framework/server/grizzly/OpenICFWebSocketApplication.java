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

package org.forgerock.openicf.framework.server.grizzly;

import java.io.Closeable;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.OpenICFServerAdapter;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.openicf.framework.remote.security.SharedSecretPrincipal;
import org.forgerock.util.Utils;
import org.forgerock.util.promise.Promises;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.ClosingFrame;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.grizzly.websockets.frametypes.PingFrameType;
import org.glassfish.grizzly.websockets.frametypes.PongFrameType;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

public class OpenICFWebSocketApplication extends WebSocketApplication implements Closeable {

    private static final Logger logger = Grizzly.logger(OpenICFWebSocketApplication.class);

    protected final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups =
            new ConcurrentHashMap<String, WebSocketConnectionGroup>();

    protected final ConnectionPrincipal<?> singleTenant;

    protected final String keyHash;

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, Utils
            .newThreadFactory(null, "OpenICF WebSocket Servlet Scheduler %d", false));

    protected ScheduledFuture<?> healthChecker = executorService.scheduleWithFixedDelay(
            new Runnable() {
                public void run() {
                    for (WebSocketConnectionGroup e : globalConnectionGroups.values()) {
                        if (!e.checkIsActive()) {
                            globalConnectionGroups.remove(e.getRemoteSessionId());
                        }
                    }
                }
            }, 1, 4, TimeUnit.MINUTES);

    public OpenICFWebSocketApplication(final ConnectorFramework framework, final String keyHash) {
        singleTenant =
                new SinglePrincipal(new OpenICFServerAdapter(framework,
                        framework.getLocalManager(), false), globalConnectionGroups);
        this.keyHash = keyHash;
    }

    public List<String> getSupportedProtocols(List<String> subProtocol) {
        if (subProtocol.contains(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL)) {
            return Arrays.asList(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL);
        } else {
            return super.getSupportedProtocols(subProtocol);
        }
    }

    /**
     * Creates a customized {@link org.glassfish.grizzly.websockets.WebSocket}
     * implementation.
     *
     * @return customized {@link org.glassfish.grizzly.websockets.WebSocket}
     *         implementation - {@link DefaultWebSocket}
     */
    @Override
    public WebSocket createSocket(final ProtocolHandler handler, final HttpRequestPacket request,
            final WebSocketListener... listeners) {
        Object connectionManager = request.getAttribute(ConnectionPrincipal.class.getName());
        if (connectionManager instanceof ConnectionPrincipal) {
            return new OpenICFWebSocket(handler, request, (ConnectionPrincipal) connectionManager,
                    this);
        }
        throw new HandshakeException(
                "[Server]ConnectionManager is not set. OpenICFWebSocketFilter is required in FilterChain.");
    }

    public ConnectionPrincipal<?> authenticate(Principal principal) {
        if (principal instanceof SharedSecretPrincipal
                && ((SharedSecretPrincipal) principal).verify(keyHash)) {
            return singleTenant;
        } else {
            return null;
        }
    }

    public void close() {
        healthChecker.cancel(false);
        executorService.shutdown();
        for (WebSocketConnectionGroup e : globalConnectionGroups.values()) {
            // We should gracefully shut down the Group
            e.principalIsShuttingDown(singleTenant);
            if (!e.isOperational()) {
                globalConnectionGroups.remove(e.getRemoteSessionId());
            }
        }
        singleTenant.close();
    }

    public static class SinglePrincipal extends ConnectionPrincipal<SinglePrincipal> {

        public SinglePrincipal(final OperationMessageListener listener,
                final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups) {
            super(listener, globalConnectionGroups);
        }

        public RemoteOperationContext handshake(
                final WebSocketConnectionHolder webSocketConnection, final HandshakeMessage message) {
            return super.handshake(webSocketConnection, message);
        }

        protected void doClose() {

        }
    }

    public static class OpenICFWebSocket extends DefaultWebSocket {

        protected final Queue<OperationMessageListener> listeners =
                new ConcurrentLinkedQueue<OperationMessageListener>();

        private final ConnectionPrincipal<?> connectionPrincipal;
        private RemoteOperationContext context = null;

        private final WebSocketConnectionHolder adapter = new WebSocketConnectionHolder() {

            protected void handshake(HandshakeMessage message) {
                context = connectionPrincipal.handshake(this, message);
                if (null == context) {
                    logger.fine("Client Connection Handshake failed - Close Connection");
                    OpenICFWebSocket.this.close();
                } else {
                    logger.fine("Client Connection Handshake succeeded");
                }
            }

            public boolean isOperational() {
                return isConnected();
            }

            public RemoteOperationContext getRemoteConnectionContext() {
                return context;
            }

            public Future<?> sendBytes(byte[] data) {
                if (isConnected()) {
                    return protocolHandler.send(data);
                } else {
                    return Promises.newExceptionPromise(new ConnectorIOException(
                            "Socket is not connected."));
                }
            }

            public Future<?> sendString(String data) {
                if (isConnected()) {
                    return protocolHandler.send(data);
                } else {
                    return Promises.newExceptionPromise(new ConnectorIOException(
                            "Socket is not connected."));
                }
            }

            public void sendPing(byte[] applicationData) throws Exception {
                if (isConnected()) {
                    protocolHandler.send(new DataFrame(new PingFrameType(), applicationData));
                } else {
                    throw new ConnectorIOException("Socket is not connected.");
                }
            }

            public void sendPong(byte[] applicationData) throws Exception {
                if (isConnected()) {
                    protocolHandler.send(new DataFrame(new PongFrameType(), applicationData));
                } else {
                    throw new ConnectorIOException("Socket is not connected.");
                }
            }

            protected void tryClose() {
                logger.finest("Closing WebSocketConnectionHolder");
                OpenICFWebSocket.this.close();
            }

        };

        public OpenICFWebSocket(final ProtocolHandler protocolHandler,
                final HttpRequestPacket request, final ConnectionPrincipal<?> principal,
                final WebSocketListener... listeners) {
            super(protocolHandler, request, listeners);
            this.connectionPrincipal = principal;
            add(principal.getOperationMessageListener());

        }

        public final boolean add(final OperationMessageListener listener) {
            return listeners.add(listener);
        }

        public final boolean remove(final OperationMessageListener listener) {
            return listeners.remove(listener);
        }

        public void onClose(DataFrame frame) {
            super.onClose(frame);
            final ClosingFrame closing = (ClosingFrame) frame;

            OperationMessageListener listener;
            while ((listener = listeners.poll()) != null) {
                listener.onClose(adapter, closing.getCode(), closing.getReason());
            }
        }

        public void onConnect() {
            super.onConnect();
            for (OperationMessageListener listener : listeners) {
                listener.onConnect(adapter);
            }
        }

        public void onMessage(byte[] data) {
            super.onMessage(data);
            for (OperationMessageListener listener : listeners) {
                listener.onMessage(adapter, data);
            }
        }

        public void onMessage(String text) {
            super.onMessage(text);
            for (OperationMessageListener listener : listeners) {
                listener.onMessage(adapter, text);
            }
        }

        public void onPing(DataFrame frame) {
            super.onPing(frame);
            for (OperationMessageListener listener : listeners) {
                listener.onPing(adapter, frame.getBytes());
            }
        }

        public void onPong(DataFrame frame) {
            super.onPong(frame);
            for (OperationMessageListener listener : listeners) {
                listener.onPong(adapter, frame.getBytes());
            }
        }
    }
}
