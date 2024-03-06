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

package org.forgerock.openicf.framework.async.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public abstract class AbstractAPIOperation {

    public static final ConnectionFailedException FAILED_EXCEPTION = new ConnectionFailedException(
            "No remote Connector Server is available at this moment");

    static {
        FAILED_EXCEPTION.setStackTrace(new StackTraceElement[0]);
    }

    private final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection;
    private final ConnectorKey connectorKey;
    private final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction;
    private long timeout;

    public AbstractAPIOperation(
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
            long timeout) {
        this.remoteConnection = Assertions.nullChecked(remoteConnection, "remoteConnection");
        this.connectorKey = Assertions.nullChecked(connectorKey, "connectorKey");
        this.facadeKeyFunction = Assertions.nullChecked(facadeKeyFunction, "facadeKeyFunction");
        this.timeout = Math.max(timeout, APIOperation.NO_TIMEOUT);
    }

    public ConnectorKey getConnectorKey() {
        return connectorKey;
    }

    protected long getTimeout() {
        return timeout;
    }

    protected RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getRemoteConnection() {
        return remoteConnection;
    }

    protected Function<RemoteOperationContext, ByteString, RuntimeException> getFacadeKeyFunction() {
        return facadeKeyFunction;
    }

    protected <V, M extends MessageLite, R extends AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<V, M>> Promise<V, RuntimeException> submitRequest(
            AbstractRemoteOperationRequestFactory<V, R> requestFactory) {
        R request = getRemoteConnection().trySubmitRequest(requestFactory);
        if (null != request) {
            return request.getPromise();
        }
        return Promises.<V, RuntimeException> newExceptionPromise(FAILED_EXCEPTION);
    }

    protected <T> T asyncTimeout(Promise<T, RuntimeException> promise) {
        if (APIOperation.NO_TIMEOUT == timeout) {
            return promise.getOrThrowUninterruptibly();
        } else {
            try {
                return promise.getOrThrowUninterruptibly(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                throw new OperationTimeoutException(ex);
            }
        }
    }

    protected static abstract class ResultBuffer<T, R> {

        private static final Object NULL_OBJECT = new Object();
        private final long timeoutMillis;
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();

        private final ConcurrentHashMap<Long, Object> buffer =
                new ConcurrentHashMap<Long, Object>();

        private final AtomicLong nextPermit = new AtomicLong(1);

        private long lastSequenceNumber = -1;

        public ResultBuffer(long timeoutMillis) {

            if (timeoutMillis == APIOperation.NO_TIMEOUT) {
                this.timeoutMillis = Long.MAX_VALUE;
            } else if (timeoutMillis == 0) {
                this.timeoutMillis = 60 * 1000;
            } else {
                this.timeoutMillis = timeoutMillis;
            }
        }

        public boolean isStopped() {
            return stopped.get();
        }

        public boolean hasLast() {
            return lastSequenceNumber > 0;
        }

        public boolean hasAll() {
            return hasLast() && nextPermit.get() > lastSequenceNumber;
        }

        public int getRemaining() {
            return queue.size();
        }

        public void clear() {
            stopped.set(Boolean.TRUE);
            buffer.clear();
            queue.clear();
        }

        public void receiveNext(long sequence, T result) {
            if (null != result) {
                if (nextPermit.get() == sequence) {
                    enqueue(result);
                } else {
                    buffer.put(sequence, result);
                }
            }
            // Feed the queue
            Object o = null;
            while ((o = buffer.remove(nextPermit.get())) != null) {
                enqueue(o);
            }
        }

        public void receiveLast(long resultCount, R result) {
            if (0 == resultCount && null != result) {
                // Empty result set
                enqueue(result);
                lastSequenceNumber = 0;
            } else {
                long idx = resultCount + 1;
                buffer.put(idx, result != null ? result : NULL_OBJECT);
                lastSequenceNumber = idx;
            }

            if (lastSequenceNumber == nextPermit.get()) {
                // Operation finished
                enqueue(result != null ? result : NULL_OBJECT);
            } else {
                receiveNext(nextPermit.get(), null);
            }
        }

        protected void enqueue(Object result) {
            try {
                // Block if queue is full
                queue.put(result);
                // Let the next go through
                nextPermit.incrementAndGet();
            } catch (InterruptedException e) {
                // What to do?
                Thread.currentThread().interrupt();
            }
        }

        protected abstract boolean handle(Object result);

        public void process() {
            while (!stopped.get()) {

                Object obj;
                try {
                    obj = queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    clear();
                    Thread.currentThread().interrupt();
                    throw ConnectorException.wrap(e);
                }

                if (obj == null) {
                    // we timed out
                    receiveNext(-1L, null);
                    if (queue.isEmpty()) {
                        clear();
                        throw new OperationTimeoutException();
                    }
                } else {
                    try {
                        boolean keepGoing = handle(NULL_OBJECT.equals(obj) ? null : obj);
                        if (!keepGoing) {
                            // stop and wait
                            clear();
                        }
                    } catch (RuntimeException e) {
                        // handler threw an exception
                        clear();
                        throw e;
                    } catch (Throwable t) {
                        clear();
                        throw new ConnectorException(t.getMessage(), t);
                    }
                }
            }
        }
    }
}
