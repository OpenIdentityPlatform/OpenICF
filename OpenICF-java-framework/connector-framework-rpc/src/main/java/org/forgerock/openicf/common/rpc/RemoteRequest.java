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
 *
 * Portions Copyrighted 2026 3A Systems, LLC
 */

package org.forgerock.openicf.common.rpc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.util.Function;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;

/**
 * A RemoteRequest represents a locally requested procedure call executed
 * remotely.
 * <p>
 * The RemoteRequest and {@link LocalRequest} are the representation of the same
 * call on caller and receiver side.
 *
 */
public abstract class RemoteRequest<V, E extends Exception, G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>> {

    private final P context;
    private final long requestId;
    private final RemoteRequestFactory.CompletionCallback<V, E, G, H, P> completionCallback;

    // The request is registered in RemoteConnectionGroup#remoteRequests
    // before it is sent, so it can be cancelled from another thread while
    // the send is still in progress. The promise therefore exists from
    // construction on, and requestTime records whether the message has left
    // (read by tryCancel on the cancelling thread).
    private volatile Long requestTime = null;
    private final PromiseImpl<V, E> promise;
    private final ReentrantLock lock = new ReentrantLock();

    // tryCancel(true) sets this before it reads requestTime; the send
    // function sets requestTime before it reads this. Whichever runs second
    // sees the other's write, so a cancel racing with the send never leaves
    // the remote side uninformed - and remoteCancelSent keeps it to one
    // cancel message when both do.
    private volatile boolean remoteCancelRequested = false;
    private final AtomicBoolean remoteCancelSent = new AtomicBoolean(false);

    public RemoteRequest(P context, long requestId,
            RemoteRequestFactory.CompletionCallback<V, E, G, H, P> completionCallback) {
        this.context = context;
        this.requestId = requestId;
        this.completionCallback = completionCallback;
        this.promise = new PromiseImpl<V, E>() {

            protected E tryCancel(boolean mayInterruptIfRunning) {
                if (mayInterruptIfRunning) {
                    remoteCancelRequested = true;
                    // Nothing to cancel remotely while the message has not
                    // been delivered: the send function does not send a
                    // cancelled request.
                    if (isSent()) {
                        try {
                            notifyRemoteCancelOnce();
                        } catch (final Throwable t) {
                            return createCancellationException(t);
                        }
                    }
                }
                return createCancellationException(null);
            }

        };
        this.promise.thenOnResultOrException(new Runnable() {
            public void run() {
                RemoteRequest.this.completionCallback.complete(RemoteRequest.this);
            }
        });
    }

    /**
     * Check if this object was marked {@link #inconsistent() inconsistent} and don't dispose.
     *
     * @return 'true' when object is still active or 'false' when this can be
     *         disposed.
     */
    public abstract boolean check();

    /**
     * Signs that the object state is inconsistent.
     */
    public abstract void inconsistent();
    
    public abstract void handleIncomingMessage(final H sourceConnection, final Object message);

    protected abstract MessageElement createMessageElement(P remoteContext, long requestId);

    protected abstract void tryCancelRemote(P remoteContext, long requestId);

    protected abstract E createCancellationException(Throwable cancellationException);

    public long getRequestId() {
        return requestId;
    }

    public Long getRequestTime() {
        return requestTime;
    }

    /**
     * Returns the promise of this request. It exists from construction on,
     * so a request that is registered but not yet sent can be cancelled; the
     * result arrives only once the message has been sent and answered.
     *
     * @return the promise, never {@code null}.
     */
    public Promise<V, E> getPromise() {
        return promise;
    }

    protected ResultHandler<V> getResultHandler() {
        return promise;
    }

    protected ExceptionHandler<E> getExceptionHandler() {
        return promise;
    }

    protected P getConnectionContext() {
        return context;
    }

    protected boolean cancel() {
        return promise.cancel(false);
    }

    private boolean isSent() {
        return null != requestTime;
    }

    private void notifyRemoteCancelOnce() {
        if (remoteCancelSent.compareAndSet(false, true)) {
            tryCancelRemote(context, requestId);
        }
    }

    public Function<H, Promise<V, E>, Exception> getSendFunction() {
        if (isSent()) {
            return new Function<H, Promise<V, E>, Exception>() {

                public Promise<V, E> apply(H value) throws Exception {
                    return promise;
                }
            };
        }
        final MessageElement message = createMessageElement(context, requestId);
        if (message == null || !(message.isString() || message.isByte())) {
            throw new IllegalStateException("RemoteRequest has empty message");
        }
        return new Function<H, Promise<V, E>, Exception>() {

            public Promise<V, E> apply(H remoteConnectionHolder) throws Exception {
                // A request cancelled before it was sent stays unsent: the
                // caller gets the cancelled promise back instead of waiting
                // for an answer that can never arrive.
                if (isSent() || promise.isDone()) {
                    return promise;
                }
                // Single thread should process it so it should not
                // return false
                if (!lock.tryLock(1, TimeUnit.MINUTES)) {
                    throw new IllegalStateException("RemoteRequest " + requestId
                            + " is still being sent by another thread");
                }
                try {
                    if (!isSent() && !promise.isDone()) {
                        // A failed send propagates to the group, which
                        // retries on its next connection with this same
                        // promise.
                        if (message.isByte()) {
                            remoteConnectionHolder.sendBytes(message.byteMessage).get();
                        } else if (message.isString()) {
                            remoteConnectionHolder.sendString(message.stringMessage).get();
                        }
                        // Message has been delivered - Report success
                        requestTime = System.currentTimeMillis();
                        if (remoteCancelRequested) {
                            // Cancelled while the message was on its way:
                            // tryCancel saw it unsent.
                            notifyRemoteCancelOnce();
                        }
                    }
                } finally {
                    lock.unlock();
                }
                return promise;
            }
        };
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
