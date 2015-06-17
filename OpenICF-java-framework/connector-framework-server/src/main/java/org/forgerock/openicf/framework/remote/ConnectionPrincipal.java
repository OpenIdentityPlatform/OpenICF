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

package org.forgerock.openicf.framework.remote;

import java.io.Closeable;
import java.security.Principal;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.CloseListener;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.identityconnectors.common.logging.Log;

import com.google.protobuf.MessageLite;

/**
 * @since 1.5
 */
public abstract class ConnectionPrincipal<T extends ConnectionPrincipal<T>>
        implements
        Closeable,
        RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>,
        Principal {

    private static final Log logger = Log.getLog(ConnectionPrincipal.class);

    protected final AtomicBoolean isRunning = new AtomicBoolean(Boolean.TRUE);
    protected final ConcurrentMap<String, WebSocketConnectionGroup> connectionGroups =
            new ConcurrentHashMap<String, WebSocketConnectionGroup>();
    protected final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups;

    private final OperationMessageListener listener;

    public ConnectionPrincipal(final OperationMessageListener listener,
            final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups) {
        this.listener = listener;
        this.globalConnectionGroups = globalConnectionGroups;
    }

    public String getName() {
        return "DEFAULT";
    }

    public RemoteOperationContext handshake(final WebSocketConnectionHolder webSocketConnection,
            RPCMessages.HandshakeMessage message) {

        WebSocketConnectionGroup connectionGroup =
                new WebSocketConnectionGroup(message.getSessionId());
        WebSocketConnectionGroup tmp =
                globalConnectionGroups.putIfAbsent(message.getSessionId(), connectionGroup);
        if (null != tmp) {
            // Another thread created the group but it may be
            // uninitialized
            connectionGroup = tmp;
        } else {
            connectionGroups.putIfAbsent(message.getSessionId(), connectionGroup);
            try {
                onNewWebSocketConnectionGroup(connectionGroup);
            } catch (Exception ignore) {
                logger.ok(ignore, "Failed to notify onNewWebSocketConnectionGroup");
            }
        }
        return connectionGroup.handshake(this, webSocketConnection, message);

    }

    protected void onNewWebSocketConnectionGroup(final WebSocketConnectionGroup connectionGroup) {

    }

    public OperationMessageListener getOperationMessageListener() {
        return listener;
    }

    public <R extends RemoteRequest<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>, V, E extends Exception> R trySubmitRequest(
            RemoteRequestFactory<R, V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> requestFactory) {
        for (WebSocketConnectionGroup e : connectionGroups.values()) {
            if (e.isOperational()) {
                R result = e.trySubmitRequest(requestFactory);
                if (null != result) {
                    return result;
                }
            }
        }
        return null;
    }

    public boolean isOperational() {
        if (isRunning.get()) {
            for (WebSocketConnectionGroup e : connectionGroups.values()) {
                if (e.isOperational()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Closes this manager and releases any system resources associated with it.
     * If the manager is already closed then invoking this method has no effect.
     *
     */
    protected abstract void doClose();

    public final void close() {
        if (isRunning.compareAndSet(Boolean.TRUE, Boolean.FALSE)) {
            doClose();
            // Notify CloseListeners
            org.forgerock.openicf.framework.CloseListener<T> closeListener;
            while ((closeListener = closeListeners.poll()) != null) {
                invokeCloseListener(closeListener);
            }
        }
    }

    private final Queue<CloseListener<T>> closeListeners =
            new ConcurrentLinkedQueue<CloseListener<T>>();

    public void addCloseListener(final CloseListener<T> closeListener) {

        // check if connection is still open
        if (isRunning.get()) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again

            if (!isRunning.get() && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the
                // listener
                invokeCloseListener(closeListener);
            }
        } else { // if connection is closed - notify the listener
            invokeCloseListener(closeListener);
        }
    }

    public void removeCloseListener(CloseListener<T> closeListener) {
        closeListeners.remove(closeListener);
    }

    @SuppressWarnings("unchecked")
    protected void invokeCloseListener(CloseListener<T> closeListener) {
        try {
            closeListener.onClosed((T) this);
        } catch (Exception ignored) {
            logger.ok(ignored, "CloseListener failed");
        }
    }
}
