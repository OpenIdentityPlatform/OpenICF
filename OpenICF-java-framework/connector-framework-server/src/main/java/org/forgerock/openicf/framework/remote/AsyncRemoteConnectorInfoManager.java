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

import java.io.Closeable;
import java.io.IOException;

import org.forgerock.guava.common.base.Function;
import org.forgerock.guava.common.collect.FluentIterable;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.DelegatingAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteConnectorInfoManager;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.identityconnectors.common.logging.Log;


/**
 * A AsyncRemoteConnectorInfoManager.
 *
 * @since 1.5
 */
public class AsyncRemoteConnectorInfoManager extends DelegatingAsyncConnectorInfoManager {

    private static final Log logger = Log.getLog(AsyncRemoteConnectorInfoManager.class);
    protected final Closeable remoteCloseable;
    protected final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> messageDistributor;

    public AsyncRemoteConnectorInfoManager(
            final Closeable remoteCloseable,
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> messageDistributor) {
        super(false);
        this.remoteCloseable = remoteCloseable;
        this.messageDistributor = messageDistributor;
    }

    public AsyncRemoteConnectorInfoManager(final RemoteConnectorInfoManager loadBalancingAlgorithm) {
        this(loadBalancingAlgorithm, loadBalancingAlgorithm.getRequestDistributor());
        addAsyncConnectorInfoManager(loadBalancingAlgorithm.getAsyncConnectorInfoManager());
    }

    protected AsyncRemoteConnectorInfoManager(
            final LoadBalancingAlgorithmFactory loadBalancingAlgorithmFactory) {
        this(
                null,
                loadBalancingAlgorithmFactory
                        .newInstance(FluentIterable
                                .from(loadBalancingAlgorithmFactory.getAsyncRemoteConnectorInfoManager())
                                .transform(
                                        new Function<AsyncRemoteConnectorInfoManager, RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>>() {
                                            public RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> apply(AsyncRemoteConnectorInfoManager value) {
                                                return value.getMessageDistributor();
                                            }
                                        })));

        for (AsyncRemoteConnectorInfoManager delegate : loadBalancingAlgorithmFactory
                .getAsyncRemoteConnectorInfoManager()) {
            for (AsyncConnectorInfoManager am : delegate.getDelegates()) {
                if (!addAsyncConnectorInfoManager(am)) {
                    throw new IllegalArgumentException(
                            "Possible circular or repeated remote in LoadBalancing tree");
                }
            }
        }
    }

    protected RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getMessageDistributor() {
        return messageDistributor;
    }

    protected void doClose() {
        if (null != remoteCloseable) {
            try {
                remoteCloseable.close();
            } catch (IOException e) {
                logger.ok(e, "Failed to close underlying remote connection");
            }
        }
    }
}
