/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 * Portions copyright 2025-2026 3A Systems LLC.
 */


package org.forgerock.openicf.framework.server.jetty;

import org.eclipse.jetty.util.StringUtil;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;

import java.util.concurrent.ConcurrentMap;

/**
 * The authenticated principal of one or more WebSocket connections.
 * <p>
 * {@link OpenICFWebSocketCreator} caches one instance per principal name and
 * closes it from {@link OpenICFWebSocketCreator#close()}. The per-connection
 * state lives in {@link OpenICFWebSocket}, which is created for every
 * accepted connection.
 */
public class SinglePrincipal extends ConnectionPrincipal<SinglePrincipal> {

    final String name;
    final ConnectorFramework connectorFramework;

    public SinglePrincipal(final String name,
                           final OperationMessageListener listener,
                           final ConnectorFramework connectorFramework,
                           final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups) {
        super(listener, globalConnectionGroups);
        this.name = name;
        this.connectorFramework = connectorFramework;
    }

    @Override
    public String getName() {
        return StringUtil.isBlank(name) ? super.getName() : name;
    }

    public RemoteOperationContext handshake(
            final WebSocketConnectionHolder webSocketConnection,
            final RPCMessages.HandshakeMessage message) {
        return super.handshake(webSocketConnection, message);
    }

    protected void doClose() {
        // Per-connection resources are released by OpenICFWebSocket when the
        // connection closes; the principal itself holds none.
    }

    @Override
    protected void onNewWebSocketConnectionGroup(final WebSocketConnectionGroup connectionGroup) {
        connectorFramework.getServerManager(getName()).addWebSocketConnectionGroup(connectionGroup);
    }
}
