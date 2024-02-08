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

import java.io.Closeable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.framework.CloseListener;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.identityconnectors.common.logging.Log;

public abstract class RemoteConnectionInfoManagerFactory implements Closeable {

    private static final Log logger = Log.getLog(RemoteConnectionInfoManagerFactory.class);

    final OperationMessageListener messageListener;
    final ConnectionManagerConfig managerConfig;

    public RemoteConnectionInfoManagerFactory(final OperationMessageListener messageListener,
            final ConnectionManagerConfig managerConfig) {
        this.messageListener = messageListener;
        this.managerConfig = managerConfig;
    }

    public abstract RemoteConnectorInfoManager connect(RemoteWSFrameworkConnectionInfo info);

    protected abstract void doClose();

    protected OperationMessageListener getMessageListener() {
        return messageListener;
    }

    protected ConnectionManagerConfig getManagerConfig() {
        return managerConfig;
    }

    private final AtomicBoolean isRunning = new AtomicBoolean(Boolean.TRUE);

    public boolean isRunning() {
        return isRunning.get();
    }

    public final void close() {
        if (isRunning.compareAndSet(Boolean.TRUE, Boolean.FALSE)) {
            doClose();
            // Notify CloseListeners
            CloseListener<RemoteConnectionInfoManagerFactory> closeListener;
            while ((closeListener = closeListeners.poll()) != null) {
                invokeCloseListener(closeListener);
            }
        }
    }

    private final Queue<CloseListener<RemoteConnectionInfoManagerFactory>> closeListeners =
            new ConcurrentLinkedQueue<CloseListener<RemoteConnectionInfoManagerFactory>>();

    public void addCloseListener(
            final org.forgerock.openicf.framework.CloseListener<RemoteConnectionInfoManagerFactory> closeListener) {
        if (isRunning.get()) {
            closeListeners.add(closeListener);

            if (!isRunning.get() && closeListeners.remove(closeListener)) {
                invokeCloseListener(closeListener);
            }
        } else {
            invokeCloseListener(closeListener);
        }
    }

    public void removeCloseListener(
            final org.forgerock.openicf.framework.CloseListener<RemoteConnectionInfoManagerFactory> closeListener) {
        closeListeners.remove(closeListener);
    }

    protected void invokeCloseListener(
            org.forgerock.openicf.framework.CloseListener<RemoteConnectionInfoManagerFactory> closeListener) {
        try {
            closeListener.onClosed(this);
        } catch (Exception ignored) {
            logger.ok(ignored, "CloseListener failed");
        }
    }
}
