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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.Factory;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;

import com.google.protobuf.MessageLite;

public abstract class LoadBalancingAlgorithmFactory
        implements
        Factory<RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>, Iterable<RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>>> {

    private final List<AsyncRemoteConnectorInfoManager> remoteConnectorInfoManagers =
            new LinkedList<AsyncRemoteConnectorInfoManager>();

    public boolean addAsyncRemoteConnectorInfoManager(
            final AsyncRemoteConnectorInfoManager remoteConnectorInfoManager) {
        return null != remoteConnectorInfoManager
                && remoteConnectorInfoManagers.add(remoteConnectorInfoManager);
    }

    public Collection<AsyncRemoteConnectorInfoManager> getAsyncRemoteConnectorInfoManager() {
        return remoteConnectorInfoManagers;
    }

    public RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> newInstance(
            final Iterable<RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>> parameter) {

        List<RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>> delegates =
                new LinkedList<RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>>();
        for (RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> dist : parameter) {
            if (null != dist) {
                delegates.add(dist);
            }
        }
        if (delegates.isEmpty()) {
            throw new IllegalArgumentException("The LoadBalancing delegates is empty");
        }
        return createLoadBalancer(delegates);
    }

    protected abstract RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> createLoadBalancer(
            List<RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>> delegates);

}
