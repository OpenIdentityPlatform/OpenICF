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

package org.forgerock.openicf.common.rpc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.SuccessHandler;

/**
 * A RemoteRequest represents a locally requested procedure call executed
 * remotely.
 * <p/>
 * The RemoteRequest and {@link LocalRequest} are the representation of the same
 * call on caller and receiver side.
 *
 */
public abstract class RemoteRequest<M, V, E extends Exception, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>> {

    private final P context;
    private final long requestId;
    private final RemoteRequestFactory.CompletionCallback<M, V, E, G, H, P> completionCallback;

    private Long requestTime = null;
    private PromiseImpl<V, E> promise = null;
    private final ReentrantLock lock = new ReentrantLock();

    public RemoteRequest(P context, long requestId,
            RemoteRequestFactory.CompletionCallback<M, V, E, G, H, P> completionCallback) {
        this.context = context;
        this.requestId = requestId;
        this.completionCallback = completionCallback;

    }

    public abstract void handleIncomingMessage(final H sourceConnection, final M message);

    protected abstract MessageElement createMessageElement(P remoteContext, long requestId);

    protected abstract void tryCancelRemote(P remoteContext, long requestId);

    protected abstract E createCancellationException(Throwable cancellationException);

    public long getRequestId() {
        return requestId;
    }

    public Long getRequestTime() {
        return requestTime;
    }

    public Promise<V, E> getPromise() {
        return promise;
    }

    protected SuccessHandler<V> getSuccessHandler() {
        return promise;
    }

    protected FailureHandler<E> getFailureHandler() {
        return promise;
    }

    protected P getConnectionContext() {
        return context;
    }

    protected boolean cancel() {
        return promise.cancel(false);
    }

    public Function<H, Promise<V, E>, Exception> getSendFunction() {
        final Promise<V, E> resultPromise = promise;
        if (null == resultPromise) {
            final MessageElement message = createMessageElement(context, requestId);
            if (message == null || !(message.isString() || message.isByte())) {
                throw new IllegalStateException("RemoteRequest has empty message");
            }
            return new Function<H, Promise<V, E>, Exception>() {

                public Promise<V, E> apply(H remoteConnectionHolder) throws Exception {
                    if (null == promise) {
                        // Single thread should process it so it should not
                        // return false
                        if (lock.tryLock(1, TimeUnit.MINUTES)) {
                            try {
                                if (null == promise) {

                                    promise = new PromiseImpl<V, E>() {

                                        protected E tryCancel(boolean mayInterruptIfRunning) {
                                            if (mayInterruptIfRunning) {
                                                try {
                                                    tryCancelRemote(context, requestId);
                                                } catch (final Throwable t) {
                                                    return createCancellationException(t);
                                                }
                                            }
                                            return createCancellationException(null);
                                        }

                                    };

                                    promise.onSuccessOrFailure(new Runnable() {
                                        public void run() {
                                            completionCallback.complete(RemoteRequest.this);
                                        }
                                    });

                                    try {
                                        if (message.isByte()) {
                                            remoteConnectionHolder.sendBytes(message.byteMessage)
                                                    .get();
                                        } else if (message.isString()) {
                                            remoteConnectionHolder
                                                    .sendString(message.stringMessage).get();
                                        }
                                    } catch (final Exception e) {
                                        promise = null;
                                        throw e;
                                    } catch (final Throwable t) {
                                        promise = null;
                                        throw new Exception(t);
                                    }
                                    // Message has been delivered - Report
                                    // success
                                    requestTime = System.currentTimeMillis();
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                    return promise;
                }
            };
        } else {
            return new Function<H, Promise<V, E>, Exception>() {

                public Promise<V, E> apply(H value) throws Exception {
                    return resultPromise;
                }
            };
        }
    }

    // --- inner Classes

    public final static class MessageElement {
        private final String stringMessage;
        private final byte[] byteMessage;

        private MessageElement(String stringMessage, byte[] byteMessage) {
            this.stringMessage = stringMessage;
            this.byteMessage = byteMessage;
        }

        public boolean isString() {
            return null != stringMessage;
        }

        public boolean isByte() {
            return null != byteMessage;
        }

        public static MessageElement createStringMessage(String message) {
            return new MessageElement(message, null);
        }

        public static MessageElement createByteMessage(byte[] message) {
            return new MessageElement(null, message);
        }
    }
}
