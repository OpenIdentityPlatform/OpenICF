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

import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.ResultHandler;

/**
 * A LocalRequest represents a remotely requested procedure call locally.
 * <p>
 * The {@link RemoteRequest} and LocalRequest are the representation of the same
 * call on caller and receiver side.
 *
 */
public abstract class LocalRequest<V, E extends Exception, G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>>
        implements ResultHandler<V>, ExceptionHandler<E> {

    private final long requestId;

    private final P remoteConnectionContext;

    private final AtomicBoolean cancelled = new AtomicBoolean(Boolean.FALSE);

    /**
     * Creates the request without registering it. Historically the
     * constructor registered {@code this} in the
     * {@link RemoteConnectionGroup}; subclasses relying on that must now call
     * {@link #register()} after construction, or the request will never
     * receive responses or cancel messages.
     */
    protected LocalRequest(final long requestId, final H socket) {
        this.requestId = requestId;
        remoteConnectionContext = socket.getRemoteConnectionContext();
    }

    /**
     * Registers this request in the
     * {@link RemoteConnectionGroup} so responses and cancel messages can be
     * dispatched to it. Must be called once the request is fully constructed
     * and before it is executed: registration publishes this instance to
     * other threads, and a registration racing with the constructor would let
     * a concurrent cancel observe a partially initialized subclass.
     *
     * @return {@code true} when the request is registered and live,
     *         {@code false} when a cancel for this request id was already
     *         received - the request is cancelled and must not be executed.
     */
    public final boolean register() {
        return null != remoteConnectionContext.getRemoteConnectionGroup().receiveRequest(this);
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
    
    protected abstract boolean tryHandleResult(V result);

    protected abstract boolean tryHandleError(E error);

    protected abstract boolean tryCancel();

    public long getRequestId() {
        return requestId;
    }

    public P getRemoteConnectionContext() {
        return remoteConnectionContext;
    }

    /**
     * Returns {@code true} once a {@link #cancel()} has been delivered to
     * this request, whether directly or as a pending cancel applied during
     * {@link #register()}.
     */
    public final boolean isCancelled() {
        return cancelled.get();
    }

    public final boolean cancel() {
        remoteConnectionContext.getRemoteConnectionGroup().removeRequest(getRequestId());
        // A cancel may be delivered twice when a direct cancel races with a
        // pending cancel applied during register() - deliver tryCancel() once.
        if (cancelled.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            return tryCancel();
        }
        return false;
    }

    public final void handleResult(final V result) {
        remoteConnectionContext.getRemoteConnectionGroup().removeRequest(getRequestId());
        tryHandleResult(result);
    }

    public final void handleException(final E error) {
        remoteConnectionContext.getRemoteConnectionGroup().removeRequest(getRequestId());
        tryHandleError(error);

    }

    public void handleIncomingMessage(final H sourceConnection, final Object message) {
        throw new UnsupportedOperationException("This request does not supports");
    }

}
