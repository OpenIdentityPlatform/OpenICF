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

package org.forgerock.openicf.framework.async;

import java.io.Closeable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.framework.CloseListener;
import org.identityconnectors.common.logging.Log;

/**
 * @since 1.5
 */
public abstract class CloseableAsyncConnectorInfoManager<T extends CloseableAsyncConnectorInfoManager<T>>
        implements AsyncConnectorInfoManager, Closeable {

    private static final Log logger = Log.getLog(CloseableAsyncConnectorInfoManager.class);

    protected final AtomicBoolean isRunning = new AtomicBoolean(Boolean.TRUE);

    protected abstract void doClose();

    public boolean isRunning() {
        return isRunning.get();
    }

    public final void close() {
        if (canCloseNow()) {
            try {
                doClose();
            } catch (Throwable t) {
                logger.ok(t, "Failed to close {0}", this);
            }
            // Notify CloseListeners
            CloseListener<T> closeListener;
            while ((closeListener = closeListeners.poll()) != null) {
                invokeCloseListener(closeListener);
            }
        }
    }

    protected boolean canCloseNow() {
        return isRunning.compareAndSet(Boolean.TRUE, Boolean.FALSE);
    }

    private final Queue<CloseListener<T>> closeListeners =
            new ConcurrentLinkedQueue<CloseListener<T>>();

    public void addCloseListener(CloseListener<T> closeListener) {
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
            CloseListener<CloseableAsyncConnectorInfoManager<T>> closeListener) {
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
