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

package org.forgerock.openicf.framework.remote;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.identityconnectors.common.event.ConnectorEvent;
import org.identityconnectors.common.event.ConnectorEventHandler;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl;
import org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoManagerImpl;

/**
 * @since 1.5
 */
public class AsyncRemoteLegacyConnectorInfoManager
        extends
        ManagedAsyncConnectorInfoManager<RemoteConnectorInfoImpl, AsyncRemoteLegacyConnectorInfoManager> {

    private static final Log logger = Log.getLog(AsyncRemoteLegacyConnectorInfoManager.class);

    protected final ConnectorEventHandler handler = new ConnectorEventHandler() {
        public void handleEvent(final ConnectorEvent event) {
            if (ConnectorEvent.CONNECTOR_REGISTERED.equals(event.getTopic())) {
                ConnectorInfo connectorInfo =
                        delegate.findConnectorInfo((ConnectorKey) event.getSource());
                addConnectorInfo((RemoteConnectorInfoImpl) connectorInfo);
            }
        }
    };
    private final RemoteConnectorInfoManagerImpl delegate;
    private final ScheduledFuture<?> future;

    public AsyncRemoteLegacyConnectorInfoManager(final RemoteFrameworkConnectionInfo info,
            final ScheduledExecutorService scheduler) {
        this.delegate = new RemoteConnectorInfoManagerImpl(info, false);
        delegate.addConnectorEventHandler(handler);
        long heartbeatInterval = info.getHeartbeatInterval();
        if (heartbeatInterval <= 0) {
            heartbeatInterval = 60L;
        }
        try {
            future =
                    scheduler.scheduleAtFixedRate(delegate, 0, heartbeatInterval, TimeUnit.SECONDS);
            logger.info("Legacy ConnectorServer Heartbeat scheduled to {0} by {1} seconds", info,
                    heartbeatInterval);
        } catch (RejectedExecutionException e) {
            throw new ConnectorException(e.getMessage(), e);
        }
    }

    protected void doClose() {
        future.cancel(true);
        delegate.deleteConnectorEventHandler(handler);
        super.doClose();
    }

    public List<ConnectorInfo> getConnectorInfos() {
        return delegate.getConnectorInfos();
    }

    public ConnectorInfo findConnectorInfo(final ConnectorKey key) {
        return delegate.findConnectorInfo(key);
    }

}
