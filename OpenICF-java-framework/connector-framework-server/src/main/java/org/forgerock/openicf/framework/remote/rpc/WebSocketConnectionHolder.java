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

package org.forgerock.openicf.framework.remote.rpc;

import java.io.Closeable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.forgerock.openicf.framework.CloseListener;
import org.identityconnectors.common.logging.Log;

import com.google.protobuf.MessageLite;

public abstract class WebSocketConnectionHolder
        implements
        Closeable,
        RemoteConnectionHolder<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

    private static final Log logger = Log.getLog(WebSocketConnectionHolder.class);

    protected final Queue<CloseListener<WebSocketConnectionHolder>> listeners =
            new ConcurrentLinkedQueue<CloseListener<WebSocketConnectionHolder>>();

    private final Queue<Runnable> dispatchQueue = new ConcurrentLinkedQueue<Runnable>();
    private final AtomicBoolean dispatchScheduled = new AtomicBoolean(false);

    /**
     * Runs {@code task} on {@code executor} after every task previously
     * submitted to this holder has completed, preserving the submission order.
     * At most one task of this holder is in flight at any time, so messages of
     * one socket are processed in arrival order while different sockets still
     * run concurrently on the shared executor.
     * <p>
     * Tasks must not block waiting for a later message of the same socket:
     * that message cannot be dispatched until the current task returns.
     */
    public void executeSerially(final Executor executor, final Runnable task) {
        dispatchQueue.add(task);
        scheduleDispatch(executor);
    }

    private void scheduleDispatch(final Executor executor) {
        if (dispatchScheduled.compareAndSet(false, true)) {
            try {
                executor.execute(new Runnable() {
                    public void run() {
                        drainDispatchQueue(executor);
                    }
                });
            } catch (RuntimeException e) {
                // Typically RejectedExecutionException on shutdown - allow a
                // later submission to try again instead of wedging the queue.
                dispatchScheduled.set(false);
                throw e;
            }
        }
    }

    private void drainDispatchQueue(final Executor executor) {
        try {
            Runnable task;
            while ((task = dispatchQueue.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.warn(t, "Failed to process a dispatched message");
                }
            }
        } finally {
            dispatchScheduled.set(false);
            // A task may have been added after poll() returned null but before
            // the flag was cleared; whoever wins the CAS reschedules the drain.
            if (!dispatchQueue.isEmpty()) {
                scheduleDispatch(executor);
            }
        }
    }

    public boolean receiveHandshake(HandshakeMessage message) {
        if (null == getRemoteConnectionContext()) {
            handshake(message);
            final RemoteOperationContext context = getRemoteConnectionContext();
            if (null != context) {
                // The context is visible only from this point on, so the
                // initial connector info exchange is started here instead of
                // inside handshake(message) - a response arriving before the
                // context is assigned would otherwise be dropped.
                context.getRemoteConnectionGroup().handshakeComplete();
            }
        }
        return isHandHooked();
    }

    public boolean isHandHooked() {
        return null != getRemoteConnectionContext();
    }

    public final void close() {
        tryClose();
        CloseListener<WebSocketConnectionHolder> listener;
        while ((listener = listeners.poll()) != null) {
            try {
                listener.onClosed(this);
            } catch (Exception ignore) {
                // Ignore this and continue to next
            }
        }
    }

    protected abstract void handshake(HandshakeMessage message);

    protected abstract void tryClose();

    public abstract boolean isOperational();
}
