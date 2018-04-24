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

package org.forgerock.openicf.framework.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.DelegatingAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.client.ConnectionManager.RemoteConnectionContext;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ICloseType;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.websockets.ClosingFrame;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.SimpleWebSocket;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.WebSocketHolder;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.grizzly.websockets.frametypes.PingFrameType;
import org.glassfish.grizzly.websockets.frametypes.PongFrameType;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

public class ClientRemoteConnectorInfoManager extends
        ConnectionPrincipal<ClientRemoteConnectorInfoManager> implements RemoteConnectorInfoManager {

    private static final Log logger = Log.getLog(ClientRemoteConnectorInfoManager.class);

    protected static final Attribute<PromiseImpl<WebSocketConnectionHolder, RuntimeException>> CONNECT_PROMISE =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("connect-promise");

    private final String name;
    private final TCPNIOConnectorHandler connectionHandler;

    private final ScheduledFuture<?> keepConnectedFuture;

    private final RemoteWSFrameworkConnectionInfo connectionInfo;
    private final SocketAddress remoteAddress;

    private final AtomicInteger availableConnectionPermitCount = new AtomicInteger(0);
    private final AtomicInteger permittedConnectionCount = new AtomicInteger(0);

    private final int maximumConnectionCount;
    private final AtomicReference<Promise<WebSocketConnectionHolder, RuntimeException>> lastConnectionPermit =
            new AtomicReference<Promise<WebSocketConnectionHolder, RuntimeException>>();

    private final List<WebSocketConnectionHolder> privateConnections =
            new CopyOnWriteArrayList<WebSocketConnectionHolder>();

    private final RemoteDelegatingAsyncConnectorInfoManager delegatingAsyncConnectorInfoManager =
            new RemoteDelegatingAsyncConnectorInfoManager();

    @SuppressWarnings("raw")
    public ClientRemoteConnectorInfoManager(
            final TCPNIOTransport clientTransport,
            final RemoteWSFrameworkConnectionInfo parameters,
            final OperationMessageListener messageListener,
            final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduledExecutorReference,
            final ConcurrentMap<String, WebSocketConnectionGroup> connectionGroupsRegistry) {
        super(messageListener, connectionGroupsRegistry);
        name = Assertions.blankChecked(parameters.getPrincipal(), "principal");
        isRunning.set(Boolean.FALSE);
        connectionInfo = Assertions.nullChecked(parameters, "RemoteWSFrameworkConnectionInfo");
        if (parameters.isUseProxy()) {
            remoteAddress =
                    new InetSocketAddress(connectionInfo.getProxyHost(), connectionInfo
                            .getProxyPort());
        } else {
            remoteAddress =
                    new InetSocketAddress(connectionInfo.getRemoteURI().getHost(), connectionInfo
                            .getRemoteURI().getPort());
        }

        permittedConnectionCount.set(Math.max(1, connectionInfo.getExpectedConnectionCount()));
        availableConnectionPermitCount.set(permittedConnectionCount.get());
        maximumConnectionCount = connectionInfo.getMaximumConnectionCount();

        connectionHandler = new TCPNIOConnectorHandler(clientTransport) {
            @Override
            protected void preConfigure(final Connection conn) {
                super.preConfigure(conn);
                final ProtocolHandler protocolHandler =
                        WebSocketEngine.DEFAULT_VERSION.createHandler(true);
                /*
                 * holder.handshake = handshake;
                 */
                protocolHandler.setConnection(conn);
                final ICFWebSocket socket =
                        new ICFWebSocket(protocolHandler, getOperationMessageListener());
                final WebSocketHolder holder = WebSocketHolder.set(conn, protocolHandler, socket);
                final RemoteConnectionContext context =
                        new RemoteConnectionContext(conn, connectionInfo);

                holder.handshake =
                        protocolHandler.createClientHandShake(connectionInfo.getRemoteURI());

                holder.handshake.setSubProtocol(Arrays
                        .asList(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL));
                CONNECT_PROMISE.set(conn, socket.connectPromise);

                conn.addCloseListener(new CloseListener() {
                    public void onClosed(Closeable closeable, ICloseType type) throws IOException {
                        logger.ok("DEBUG = Connection Closed {0}", type);
                        tryReleaseConnectionPermit();

                        if (!socket.connectPromise.isDone()) {
                            socket.connectPromise.handleException(new ConnectorIOException(
                                    "Connection is closed before WebSocket is established"));
                        }
                        // Immediately try to reconnect
                        if (System.currentTimeMillis() - lastConnectithenOnException.get() > 30000) {
                            // Try reconnect only if the last error is older
                            // then 30 sec
                            logger.ok("Try attempt to reconnect after connection is closed");
                            ClientRemoteConnectorInfoManager.this.connect(false);
                        }
                    }
                });
            }
        };

        final Runnable runnable = new Runnable() {
            public void run() {
                for (final WebSocketConnectionHolder ws : privateConnections) {
                    if (!ws.isOperational() || !isRunning.get()) {
                        ws.close();
                    }
                }
                while (isRunning.get() && null != connect(false)) {
                    logger.ok("New connection is created - {0}. Remaining permits:{1}", getName(),
                            availableConnectionPermitCount.get());
                }
            }
        };

        final ScheduledExecutorService scheduler = scheduledExecutorReference.get();
        ScheduledFuture<?> future = null;
        if (null != scheduler) {
            try {
                long heartbeatInterval = parameters.getHeartbeatInterval();
                if (heartbeatInterval <= 0) {
                    heartbeatInterval = 60L;
                }
                isRunning.set(Boolean.TRUE);
                future =
                        scheduler.scheduleAtFixedRate(runnable, 0, heartbeatInterval,
                                TimeUnit.SECONDS);
            } catch (RejectedExecutionException x) {
                // It has been shut down
                logger.ok(x, "Could not schedule action {} to scheduler {}", getName());
            }
        }
        keepConnectedFuture = future;
    }

    public String getName() {
        return name;
    }

    protected void doClose() {
        keepConnectedFuture.cancel(false);
        for (WebSocketConnectionGroup e : connectionGroups.values()) {
            // We should gracefully shut down the Group
            e.principalIsShuttingDown(this);
            if (!e.isOperational()) {
                globalConnectionGroups.remove(e.getRemoteSessionId());
            }
        }
        for (WebSocketConnectionHolder ws : privateConnections) {
            ws.close();
        }
        privateConnections.clear();

        connectionGroups.clear();
    }

    public boolean isSelfManaged() {
        return null != keepConnectedFuture;
    }

    // ---

    public AsyncConnectorInfoManager getAsyncConnectorInfoManager() {
        return delegatingAsyncConnectorInfoManager;
    }

    public RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getRequestDistributor() {
        return requestDistributor;
    }

    private final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> requestDistributor =
            new RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>() {
                public <R extends RemoteRequest<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>, V, E extends Exception> R trySubmitRequest(
                        final RemoteRequestFactory<R, V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> requestFactory) {
                    return ClientRemoteConnectorInfoManager.this.trySubmitRequest(requestFactory);
                }

                public boolean isOperational() {
                    return ClientRemoteConnectorInfoManager.this.isOperational();
                }
            };

    public <R extends RemoteRequest<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>, V, E extends Exception> R trySubmitRequest(
            final RemoteRequestFactory<R, V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> requestFactory) {
        // Try the existing WS Connections first
        int sizeBefore = privateConnections.size();
        if (sizeBefore > 0) {
            for (WebSocketConnectionGroup e : connectionGroups.values()) {
                if (e.isOperational()) {
                    R result = e.trySubmitRequest(requestFactory);
                    if (null != result) {
                        return result;
                    }
                }
            }
        }
        // No new Connection was made meanwhile and it has free slots
        if (sizeBefore >= privateConnections.size() && hasFreeConnectionPermit()) {
            // Connect onDemand and use the new Connection
            try {
                final WebSocketConnectionHolder newSocket =
                        connect().getOrThrowUninterruptibly(30, TimeUnit.SECONDS);
                return newSocket.getRemoteConnectionContext().getRemoteConnectionGroup()
                        .trySubmitRequest(requestFactory);
            } catch (TimeoutException e) {
                logger.info(e, "Failed to acquire new connection onDemand");
            } catch (Throwable t) {
                logger.ok(t, "Unexpected exception");
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

    // ----

    private final AtomicLong lastConnectithenOnException = new AtomicLong(0L);

    public Promise<WebSocketConnectionHolder, RuntimeException> connect() {
        return connect(true);
    }

    private Promise<WebSocketConnectionHolder, RuntimeException> connect(boolean expectPromise) {
        final PromiseImpl<Promise<WebSocketConnectionHolder, RuntimeException>, RuntimeException> outerPromise =
                PromiseImpl.create();

        final CompletionHandler<Connection> callback = new CompletionHandler<Connection>() {

            public void cancelled() {
                outerPromise.cancel(false);
            }

            public void failed(Throwable throwable) {
                lastConnectithenOnException.set(System.currentTimeMillis());
                if (throwable instanceof ConnectException) {
                    // Can not connect to remote server -
                    outerPromise.handleException(new ConnectionBrokenException(throwable
                            .getMessage(), throwable));
                } else if (throwable instanceof RuntimeException) {
                    outerPromise.handleException(((RuntimeException) throwable));
                } else {
                    outerPromise.handleException(new ConnectorIOException(throwable.getMessage(),
                            throwable));
                }
                logger.info(throwable, "Failed establish remote connection");
            }

            public void completed(Connection result) {
                outerPromise.handleResult(CONNECT_PROMISE.get(result));
                logger.ok("TCP Connection is completed");
            }

            public void updated(Connection result) {

            }
        };

        final int index = tryAcquireConnectionPermit();
        Promise<WebSocketConnectionHolder, RuntimeException> promise = null;
        // Successfully acquired one permit
        if (index > -1) {
            if (isRunning.get()) {
                if (connectionInfo.getLocalAddress() != null) {
                    connectionHandler.connect(remoteAddress, new InetSocketAddress(connectionInfo
                            .getLocalAddress(), 0), callback);
                } else {
                    connectionHandler.connect(remoteAddress, callback);
                }
                promise =
                        outerPromise
                                .thenAsync(
                                        new AsyncFunction<Promise<WebSocketConnectionHolder, RuntimeException>, WebSocketConnectionHolder, RuntimeException>() {
                                            public Promise<WebSocketConnectionHolder, RuntimeException> apply(
                                                    final Promise<WebSocketConnectionHolder, RuntimeException> value)
                                                    throws RuntimeException {
                                                return value;
                                            }
                                        }).thenOnResult(
                                        new ResultHandler<WebSocketConnectionHolder>() {
                                            public void handleResult(
                                                    final WebSocketConnectionHolder result) {
                                                if (isRunning.get()) {
                                                    privateConnections.add(result);
                                                } else {
                                                    result.close();
                                                }
                                            }
                                        }).thenOnException(
                                        new ExceptionHandler<RuntimeException>() {
                                            public void handleException(RuntimeException error) {
                                                lastConnectithenOnException.set(System
                                                        .currentTimeMillis());
                                                logger.ok(error,
                                                        "Connection to server failed: {0}", error
                                                                .getMessage());
                                            }
                                        });
                if (index == 0) {
                    lastConnectionPermit.set(promise);
                    synchronized (this) {
                        notifyAll();
                    }
                }
            } else {
                tryReleaseConnectionPermit();
                promise =
                        Promises.<WebSocketConnectionHolder, RuntimeException> newExceptionPromise(new IllegalStateException(
                                "Shut down"));
            }
        } else {
            // No more permit is available return null unless promise is
            // expected
            if (expectPromise) {
                try {
                    synchronized (this) {
                        while ((promise = lastConnectionPermit.get()) == null && isRunning.get()) {
                            // This time should be more then enough for other
                            // thread to set the value
                            wait(5000);
                        }
                    }
                } catch (InterruptedException e) {
                    // Thread.currentThread().interrupt();
                    promise =
                            Promises.<WebSocketConnectionHolder, RuntimeException> newExceptionPromise(new ConnectorException(
                                    e.getMessage(), e));
                }
                if (promise == null) {
                    promise =
                            Promises.<WebSocketConnectionHolder, RuntimeException> newExceptionPromise(new ConnectorException(
                                    "Failed acquire connection. Manager is running:"
                                            + isRunning.get()));
                }
            }
        }
        return promise;

    }

    protected boolean hasFreeConnectionPermit() {
        return availableConnectionPermitCount.get() > 0;
    }

    protected final int tryAcquireConnectionPermit() {
        for (;;) {
            int available = availableConnectionPermitCount.get();
            int remaining = available - 1;
            if (remaining < 0 || availableConnectionPermitCount.compareAndSet(available, remaining))
                return remaining;
        }
    }

    protected final void tryReleaseConnectionPermit() {
        for (;;) {
            int current = availableConnectionPermitCount.get();
            int next = current + 1;
            if (next > permittedConnectionCount.get()
                    || availableConnectionPermitCount.compareAndSet(current, next))
                return;
        }
    }

    protected void increaseConnectionPermits() {
        for (;;) {
            int current = permittedConnectionCount.get();
            int next = current + 1;
            if (next > maximumConnectionCount) {
                return;
            }
            if (permittedConnectionCount.compareAndSet(current, next)) {
                availableConnectionPermitCount.incrementAndGet();
                return;
            }
        }
    }

    protected void decreaseConnectionPermits() {
        for (;;) {
            int current = permittedConnectionCount.get();
            int next = current - 1;
            if (next < 1) {
                return;
            }
            if (permittedConnectionCount.compareAndSet(current, next)) {
                availableConnectionPermitCount.decrementAndGet();
                return;
            }
        }
    }

    private class ICFWebSocket extends SimpleWebSocket {

        protected final PromiseImpl<WebSocketConnectionHolder, RuntimeException> connectPromise =
                PromiseImpl.create();

        protected final Queue<OperationMessageListener> listeners =
                new ConcurrentLinkedQueue<OperationMessageListener>();

        private RemoteOperationContext context = null;

        private final WebSocketConnectionHolder adapter = new WebSocketConnectionHolder() {

            protected void handshake(RPCMessages.HandshakeMessage message) {

                context = ClientRemoteConnectorInfoManager.this.handshake(this, message);

                if (null != context) {
                    connectPromise.handleResult(this);
                } else {
                    connectPromise.handleException(new ConnectorException(
                            "Failed Application HandShake"));
                    tryClose();
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
                ICFWebSocket.this.close();
            }

        };

        ICFWebSocket(final ProtocolHandler protocolHandler,
                final OperationMessageListener messageListener) {
            super(protocolHandler, new WebSocketListener[0]);
            add(messageListener);
        }

        final boolean add(final OperationMessageListener listener) {
            return listeners.add(listener);
        }

        final boolean remove(final OperationMessageListener listener) {
            return listeners.remove(listener);
        }

        public void onClose(DataFrame frame) {
            super.onClose(frame);
            privateConnections.remove(adapter);
            final ClosingFrame closing = (ClosingFrame) frame;
            connectPromise.handleException(new ConnectorException("Connection is closed: #"
                    + closing.getCode() + " - " + closing.getReason()));
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

        SimpleBinaryMessage activeMessage = null;

        public void onFragment(boolean last, byte[] fragment) {
            super.onFragment(last, fragment);
            if (activeMessage == null) {
                activeMessage = new SimpleBinaryMessage(this, fragment);
            }

            activeMessage.appendFrame(fragment);

            if (last) {
                activeMessage.messageComplete();
                activeMessage = null;
            }
        }
    }

    private static class SimpleBinaryMessage {
        private final WebSocket onEvent;
        protected final ByteArrayOutputStream out;
        protected boolean finished;

        public SimpleBinaryMessage(WebSocket onEvent, byte[] fragment) {
            this.onEvent = onEvent;
            this.out =
                    new ByteArrayOutputStream(null != fragment ? 2 * fragment.length : 16 * 1024);
            finished = false;
        }

        public void appendFrame(byte[] fragment) {
            if (fragment == null) {
                return;
            }
            out.write(fragment, 0, fragment.length);
        }

        public void messageComplete() {
            finished = true;
            byte data[] = out.toByteArray();
            onEvent.onMessage(data);
        }
    }

    protected void onNewWebSocketConnectionGroup(final WebSocketConnectionGroup connectionGroup) {
        logger.ok("Activating new ConnectionGroup {0}:{1}", getName(), connectionGroup
                .getRemoteSessionId());
        delegatingAsyncConnectorInfoManager.onAddAsyncConnectorInfoManager(connectionGroup);
    }

    // --- Inner Classes

    private class RemoteDelegatingAsyncConnectorInfoManager extends
            DelegatingAsyncConnectorInfoManager {
        public RemoteDelegatingAsyncConnectorInfoManager() {
            super(true);
        }

        protected RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getMessageDistributor() {
            return ClientRemoteConnectorInfoManager.this.getRequestDistributor();
        }

        protected void doClose() {
            ClientRemoteConnectorInfoManager.this.close();
        }

        protected Collection<? extends AsyncConnectorInfoManager> getDelegates() {
            return connectionGroups.values();
        }

        public void onAddAsyncConnectorInfoManager(AsyncConnectorInfoManager delegate) {
            // Notify deferred listeners
            super.onAddAsyncConnectorInfoManager(delegate);
        }
    }

    public static class Statistic {

        private static Log logger = Log.getLog(Statistic.class);

        private final AtomicLong operationTime = new AtomicLong(0L);
        private final AtomicLong operationCalls = new AtomicLong(0L);

        public <V> V measure(Future<V> future) throws ExecutionException, InterruptedException {
            long start = System.nanoTime();
            try {
                return future.get();
            } finally {
                measure(System.nanoTime() - start);
            }
        }

        public <V> V measure(Future<V> future, long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            long start = System.nanoTime();
            try {
                return future.get(timeout, unit);
            } finally {
                measure(System.nanoTime() - start);
            }
        }

        public void measure(Runnable runnable) {
            long start = System.nanoTime();
            try {
                runnable.run();
            } finally {
                measure(System.nanoTime() - start);
            }
        }

        public <T> T measure(Callable<T> callable) throws Exception {
            long start = System.nanoTime();
            try {
                return callable.call();
            } finally {
                measure(System.nanoTime() - start);
            }
        }

        public void measure(long duration) {
            long x = operationTime.get();
            long r = x + duration;
            // Prevent from long overflow
            if (((x ^ r) & (duration ^ r)) >= 0) {
                operationTime.addAndGet(duration);
                operationCalls.incrementAndGet();
            }
        }

        public enum Action {
            NEUTRAL, INCREMENT, DECREMENT;
        }

        private long timeFirst = -1L;
        private long timeSecond = -1L;

        public Action touch() {
            // Reset the measuring
            long p3 = operationTime.get() / Math.max(1, operationCalls.get());
            operationTime.set(0L);
            operationCalls.set(0L);

            // Shift the values
            long p1 = timeFirst;
            long p2 = timeSecond;
            timeFirst = timeSecond;
            timeSecond = p3;

            Action result = Action.NEUTRAL;
            if (p1 <= 0 && p2 <= 0) {
                result = Action.NEUTRAL;
            } else if (p1 < p2 && p2 <= p3) {
                result = Action.INCREMENT;
            } else if (p1 > p2 && p2 >= p3) {
                result = Action.DECREMENT;
            }
            if (logger.isOk()) {
                logger.ok(new StringBuilder().append("Statistic (p1='").append(p1).append("' p2='")
                        .append(p2).append("' p3='").append(p3).append("') > ").append(
                                result.name()).toString());
            }
            return result;
        }
    }
}
