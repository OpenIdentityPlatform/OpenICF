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

package org.forgerock.openicf.common.rpc.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.common.rpc.MessageListener;
import org.forgerock.openicf.common.rpc.RemoteConnectionContext;
import org.forgerock.openicf.common.rpc.RemoteConnectionGroup;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.testng.Reporter;

public class NIOSimulator<M, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>>
        implements Closeable {

    private final MessageListener<M, G, H, P> serverListener;
    private final Future<?> processorFuture;
    private final List<RemoteConnectionHolder<M, G, H, P>> connections =
            new CopyOnWriteArrayList<RemoteConnectionHolder<M, G, H, P>>();

    public NIOSimulator(MessageListener<M, G, H, P> serverListener) {
        this.serverListener = serverListener;
        processorFuture = executorService.submit(new Runnable() {
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        executorService.submit(queue.take());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();

    public RemoteConnectionHolder<M, G, H, P> connect(
            final MessageListener<M, G, H, P> clientListener, P serverContext, P clientContext) {
        RemoteConnectionHolderImpl connection =
                new RemoteConnectionHolderImpl(serverListener, clientListener, serverContext,
                        clientContext);
        connections.add(connection);
        return connection;
    }

    public void close() throws IOException {
        for (RemoteConnectionHolder<M, G, H, P> o : connections) {
            o.close();
        }
        processorFuture.cancel(true);
        queue.clear();
        executorService.shutdownNow();
    }

    public class RemoteConnectionHolderImpl implements RemoteConnectionHolder<M, G, H, P> {

        private final H reverseConnection;
        private final P context;
        private final MessageListener<M, G, H, P> remoteListener;
        private AtomicBoolean active = new AtomicBoolean(true);

        public RemoteConnectionHolderImpl(final MessageListener<M, G, H, P> serverListener,
                final MessageListener<M, G, H, P> clientListener, P serverContext, P clientContext) {
            reverseConnection =
                    (H) new RemoteConnectionHolderImpl((H) this, clientListener, serverContext);
            remoteListener = serverListener;
            context = clientContext;
            remoteListener.onConnect(reverseConnection);
            clientListener.onConnect((H) this);
        }

        public RemoteConnectionHolderImpl(H reverseConnection,
                MessageListener<M, G, H, P> clientListener, P serverContext) {
            this.reverseConnection = reverseConnection;
            remoteListener = clientListener;
            context = serverContext;
        }

        public P getRemoteConnectionContext() {
            return context;
        }

        public Future<?> sendBytes(final byte[] data) {
            return getExecutorService().submit(new Callable<Void>() {
                public Void call() throws Exception {
                    if (active.get()) {
                        queue.offer(new Runnable() {
                            public void run() {
                                getMessageListener().onMessage(getReverseConnection(), data);
                            }
                        });
                    } else {
                        throw new IOException("Simulated Connection is Closed");
                    }
                    return null;
                }
            });
        }

        public Future<?> sendString(final String data) {
            return getExecutorService().submit(new Callable<Void>() {
                public Void call() throws Exception {
                    if (active.get()) {
                        queue.offer(new Runnable() {
                            public void run() {
                                getMessageListener().onMessage(getReverseConnection(), data);
                            }
                        });
                    } else {
                        Reporter.log("Simulated Connection is Closed");
                        throw new IOException("Simulated Connection is Closed");
                    }
                    return null;
                }
            });
        }

        public void sendPing(final byte[] applicationData) throws Exception {
            getExecutorService().submit(new Callable<Void>() {
                public Void call() throws Exception {
                    if (active.get()) {
                        getMessageListener().onPing(getReverseConnection(), applicationData);

                    } else {
                        throw new IOException("Simulated Connection is Closed");
                    }
                    return null;
                }
            }).get();
        }

        public void sendPong(final byte[] applicationData) throws Exception {
            getExecutorService().submit(new Callable<Void>() {
                public Void call() throws Exception {
                    if (active.get()) {
                        getMessageListener().onPong(getReverseConnection(), applicationData);
                    } else {
                        throw new IOException("Simulated Connection is Closed");
                    }
                    return null;
                }
            }).get();
        }

        public void close() {
            if (active.compareAndSet(true, false)) {
                tryClose();
            }
        }

        protected void tryClose() {
            connections.remove(this);
            reverseConnection.close();
            ((RemoteConnectionHolderImpl) reverseConnection).getMessageListener().onClose((H) this,
                    100, "Connection Closed");
        }

        protected H getReverseConnection() {
            return reverseConnection;
        }

        protected MessageListener<M, G, H, P> getMessageListener() {
            return remoteListener;
        }

        protected ExecutorService getExecutorService() {
            return executorService;
        }
    }

}
