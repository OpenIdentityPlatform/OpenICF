/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2016 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.remote.rpc;

import java.io.Closeable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages.ControlRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages.ControlRequest.InfoLevel;
import org.forgerock.openicf.common.protobuf.RPCMessages.ControlResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.common.rpc.LocalRequest;
import org.forgerock.openicf.common.rpc.RemoteConnectionGroup;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.framework.CloseListener;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ManagedAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.RemoteConnectorInfoImpl;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.ConnectorKeyRange;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.Encryptor;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConfigurationPropertyChangeListener;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.impl.api.AbstractConnectorInfo;

import com.google.protobuf.MessageLite;

public class WebSocketConnectionGroup
        extends
        RemoteConnectionGroup<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>
        implements AsyncConnectorInfoManager, Closeable {

    private static final Log logger = Log.getLog(WebSocketConnectionGroup.class);

    private long lastActivity = System.currentTimeMillis();
    
    private Encryptor encryptor = null;

    private RemoteOperationContext operationContext = null;

    private final AtomicBoolean isRunning = new AtomicBoolean(Boolean.TRUE);
    private final Set<String> principals = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

    private final ConcurrentMap<String, ConfigurationPropertyChangeListener> configurationChangeListenerMap =
            new ConcurrentHashMap<String, ConfigurationPropertyChangeListener>();

    private final CloseListener<WebSocketConnectionHolder> closeListener =
            new CloseListener<WebSocketConnectionHolder>() {
                public void onClosed(final WebSocketConnectionHolder connection) {
                    for (Pair<String, WebSocketConnectionHolder> p : webSockets) {
                        if (connection.equals(p.getSecond())) {
                            webSockets.remove(p);
                        }
                    }
                }
            };

    private boolean receivedConnectorInfo = false;
    private final RemoteConnectorInfoManager delegate = new RemoteConnectorInfoManager();

    public WebSocketConnectionGroup(final String remoteSessionId) {
        super(remoteSessionId);
    }

    public RemoteOperationContext handshake(final Principal connectionPrincipal,
            final WebSocketConnectionHolder webSocketConnection, final HandshakeMessage message) {
        if (null == operationContext) {
            synchronized (this) {
                if (null == operationContext) {
                    operationContext = new RemoteOperationContext(connectionPrincipal, this);
                }
            }
        }
        if (remoteSessionId.equals(message.getSessionId())) {
            final Pair<String, WebSocketConnectionHolder> entry =
                    Pair.of(connectionPrincipal.getName(), webSocketConnection);
            webSockets.add(entry);
            webSocketConnection.listeners.add(closeListener);
            principals.add(connectionPrincipal.getName());
            if (webSockets.indexOf(entry) == 0) {
                ControlMessageRequestFactory requestFactory = new ControlMessageRequestFactory();
                requestFactory.infoLevels.add(InfoLevel.CONNECTOR_INFO);
                final Function<Boolean, Boolean, RuntimeException> success = new Function<Boolean, Boolean, RuntimeException>() {
                    @Override
                    public Boolean apply(Boolean value) throws RuntimeException {
                        receivedConnectorInfo = value;
                        return receivedConnectorInfo;
                    }
                };

                trySubmitRequest(requestFactory).getPromise().then(success, new Function<RuntimeException, Boolean, RuntimeException>() {
                    @Override
                    public Boolean apply(RuntimeException e) throws RuntimeException {
                        logger.ok("Resending initial 'CONNECTOR_INFO' request", e);
                        ControlMessageRequestFactory requestFactory = new ControlMessageRequestFactory();
                        requestFactory.infoLevels.add(InfoLevel.CONNECTOR_INFO);
                        trySubmitRequest(requestFactory).getPromise().then(success);
                        return receivedConnectorInfo;
                    }
                });
            }
        }
        return operationContext;
    }

    public void principalIsShuttingDown(final Principal connectionPrincipal) {
        final String name = connectionPrincipal.getName();
        if (principals.remove(name)) {
            shutdown();
            for (Pair<String, WebSocketConnectionHolder> e : webSockets) {
                if (name.equalsIgnoreCase(e.getFirst())) {
                    webSockets.remove(e);
                }
            }
        }
    }

    protected void shutdown() {
        if (principals.isEmpty()) {
            // Gracefully close all request and shut down this group.
            for (LocalRequest<?, ?, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> local : localRequests
                    .values()) {
                local.cancel();
            }
            for (RemoteRequest<?, ?, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remote : remoteRequests
                    .values()) {
                remote.getPromise().cancel(true);
            }
            delegate.close();
        }
    }

    public <M extends MessageLite> boolean trySendMessage(final M message) {
        final byte[] messageBytes = message.toByteArray();
        return Boolean.TRUE
                .equals(trySendMessage(new Function<WebSocketConnectionHolder, Boolean, Exception>() {
                    public Boolean apply(WebSocketConnectionHolder value) throws Exception {
                        value.sendBytes(messageBytes).get();
                        return Boolean.TRUE;
                    }
                }));
    }

    protected RemoteOperationContext getRemoteConnectionContext() {
        return operationContext;
    }

    public boolean isOperational() {
        for (Pair<String, WebSocketConnectionHolder> e : webSockets) {
            if (e.getSecond().isOperational()) {
                return true;
            }
        }
        return false;
    }

    public Encryptor getEncryptor() {
        return encryptor;
    }

    // --- Closeable implementation ---

    protected void doClose(){
    }

    public boolean isRunning() {
        return isRunning.get() && delegate.isRunning();
    }

    public final void close() {
        if (canCloseNow()) {
            try {
                principals.clear();
                shutdown();
                final List<Pair<String, WebSocketConnectionHolder>> tmp =
                        new ArrayList<Pair<String, WebSocketConnectionHolder>>(webSockets);
                for (Pair<String, WebSocketConnectionHolder> p : tmp) {
                    p.getSecond().close();
                }
                doClose();
            } catch (Throwable t) {
                logger.ok(t, "Failed to close {0}", this);
            }
            // Notify CloseListeners
            CloseListener<WebSocketConnectionGroup> closeListener;
            while ((closeListener = closeListeners.poll()) != null) {
                invokeCloseListener(closeListener);
            }
        }
    }

    protected boolean canCloseNow() {
        return isRunning.compareAndSet(Boolean.TRUE, Boolean.FALSE);
    }

    private final Queue<CloseListener<WebSocketConnectionGroup>> closeListeners =
            new ConcurrentLinkedQueue<CloseListener<WebSocketConnectionGroup>>();

    public void addCloseListener(CloseListener<WebSocketConnectionGroup> closeListener) {
        // check if this is still running
        if (isRunning.get()) {
            // add close listener
            closeListeners.add(closeListener);
            // check the its state again
            if (!isRunning.get() && closeListeners.remove(closeListener)) {
                // if this was closed during the method call - notify the
                // listener
                invokeCloseListener(closeListener);
            }
        } else { // if this is closed - notify the listener
            invokeCloseListener(closeListener);
        }
    }

    public void removeCloseListener(
            CloseListener<WebSocketConnectionGroup> closeListener) {
        closeListeners.remove(closeListener);
    }

    protected void invokeCloseListener(CloseListener<WebSocketConnectionGroup> closeListener) {
        try {
            closeListener.onClosed(this);
        } catch (Exception ignored) {
            logger.ok(ignored, "CloseListener failed");
        }
    }

    // --- AsyncConnectorInfoManager implementation ---

    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(final ConnectorKey key) {
        return delegate.findConnectorInfoAsync(key);
    }

    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(
            final ConnectorKeyRange keyRange) {
        return delegate.findConnectorInfoAsync(keyRange);
    }

    public List<ConnectorInfo> getConnectorInfos() {
        return delegate.getConnectorInfos();
    }

    public ConnectorInfo findConnectorInfo(final ConnectorKey key) {
        return delegate.findConnectorInfo(key);
    }

    // --- AsyncConnectorInfoManager implementation ---


    public void addConfigurationChangeListener(String key,
                                               ConfigurationPropertyChangeListener listener) {
        if (null != key) {
            if (null != listener) {
                configurationChangeListenerMap.put(key, listener);
            } else {
                configurationChangeListenerMap.remove(key);
            }
        }
    }

    public void notifyConfigurationChangeListener(String key, List<ConfigurationProperty> change) {
        if (null != key && null != change) {
            ConfigurationPropertyChangeListener listener = configurationChangeListenerMap.get(key);
            if (null != listener) {
                try {
                    listener.configurationPropertyChange(change);
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }
    }
    
    public boolean checkIsActive() {
        boolean operational = isOperational();
        if (System.currentTimeMillis() - lastActivity > TimeUnit.MINUTES.toMillis(12) && !operational) {
            // (2 * (Control request interval)) + 2 hour inactivity -> Shutdown
            close();
        } else {

            for (LocalRequest<?, ?, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> local : localRequests
                    .values()) {
                local.check();
            }
            for (RemoteRequest<?, ?, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remote : remoteRequests
                    .values()) {
                remote.check();
            }

            if (operational) {
                ControlMessageRequestFactory requestFactory = new ControlMessageRequestFactory();
                if (!receivedConnectorInfo) {
                    requestFactory.infoLevels.add(InfoLevel.CONNECTOR_INFO);
                }
                trySubmitRequest(requestFactory);
            }
        }
        return operational || !remoteRequests.isEmpty() || !localRequests.isEmpty();
    }
    
    public void processControlRequest(ControlRequest message){
        lastActivity = System.currentTimeMillis();
        for (Map.Entry<Long, RemoteRequest<?, ?, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>> entry : remoteRequests.entrySet()){
            if (!message.getLocalRequestIdList().contains(entry.getKey())){
                //Remote request is exists locally but remotely nothing match. 
                // 1. ControlRequest was sent before LocalRequest was created so it normal.
                // 2. Request on remote side is completed so we must Complete the RemoteRequest(Fail) unless the local request is still processing.
                entry.getValue().inconsistent();
            }
        }
        for (Map.Entry<Long, LocalRequest<?, ?, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>> entry: localRequests.entrySet()){
            if (!message.getRemoteRequestIdList().contains(entry.getKey())){
                //Local request exists locally but remotely nothing match. 
                // 1. ControlRequest was sent before RemoteRequest was created so it normal.
                // 2. Request on remote side is Cancelled/Terminated so it can safely Cancel here. 
                entry.getValue().inconsistent();
            }
        }
    }
    
    // -- Static Classes

    private class RemoteConnectorInfoManager extends
            ManagedAsyncConnectorInfoManager<RemoteConnectorInfoImpl, RemoteConnectorInfoManager> {
        void addAll(List<? extends AbstractConnectorInfo> connectorInfos) {
            for (AbstractConnectorInfo connectorInfo : connectorInfos) {
                addConnectorInfo(new RemoteConnectorInfoImpl(WebSocketConnectionGroup.this,
                        connectorInfo));
            }
        }
    }

    private static class ControlMessageRequestFactory
            implements
            RemoteRequestFactory<ControlMessageRequest, Boolean, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

        public final EnumSet<InfoLevel> infoLevels = EnumSet.noneOf(InfoLevel.class);

        public ControlMessageRequest createRemoteRequest(
                RemoteOperationContext context,
                long requestId,
                CompletionCallback<Boolean, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {
            return new ControlMessageRequest(context, requestId, completionCallback, infoLevels);
        }
    }

    private static class ControlMessageRequest extends RemoteOperationRequest<Boolean> {

        private final EnumSet<InfoLevel> infoLevels;

        public ControlMessageRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Boolean, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final EnumSet<InfoLevel> infoLevels) {
            super(context, requestId, completionCallback);
            this.infoLevels = infoLevels;
        }

        protected RPCMessages.RPCRequest.Builder createOperationRequest(
                RemoteOperationContext remoteContext) {

            ControlRequest.Builder builder = ControlRequest.newBuilder();
            for (InfoLevel infoLevel : infoLevels) {
                builder.addInfoLevel(infoLevel);
            }
            builder.addAllLocalRequestId(remoteContext.getRemoteConnectionGroup().localRequests.keySet());
            builder.addAllRemoteRequestId(remoteContext.getRemoteConnectionGroup().remoteRequests.keySet());
            return RPCMessages.RPCRequest.newBuilder().setControlRequest(builder);
        }

        protected boolean handleResponseMessage(WebSocketConnectionHolder sourceConnection,
                MessageLite message) {
            if (message instanceof ControlResponse) {
                final ControlResponse response = (ControlResponse) message;

                if (!response.getConnectorInfos().isEmpty()) {
                    List<org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl> connectorInfos =
                            MessagesUtil.deserializeLegacy(((ControlResponse) message)
                                    .getConnectorInfos());
                    if (null != connectorInfos && !connectorInfos.isEmpty()) {
                        sourceConnection.getRemoteConnectionContext().getRemoteConnectionGroup().delegate
                                .addAll(connectorInfos);
                    }
                }

                getResultHandler().handleResult(Boolean.TRUE);

            } else {
                return false;
            }
            return true;
        }
    }

}
