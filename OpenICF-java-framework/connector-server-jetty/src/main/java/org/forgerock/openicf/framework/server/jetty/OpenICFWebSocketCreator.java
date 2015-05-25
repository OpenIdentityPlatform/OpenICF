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

package org.forgerock.openicf.framework.server.jetty;

import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.OpenICFServerAdapter;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;

public class OpenICFWebSocketCreator implements WebSocketCreator {

    private static final Logger logger = Log.getLogger(WebSocketListenerBridge.class);

    protected final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups =
            new ConcurrentHashMap<String, WebSocketConnectionGroup>();

    private final SinglePrincipal singleTenant;

    public OpenICFWebSocketCreator(final ConnectorFramework connectorFramework) {
        singleTenant =
                new SinglePrincipal(new OpenICFServerAdapter(connectorFramework, connectorFramework
                        .getLocalManager(), false), globalConnectionGroups);
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest request, ServletUpgradeResponse response) {
        Principal principal = request.getUserPrincipal();

        if (request.getSubProtocols().contains(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL)) {
            response.setAcceptedSubProtocol(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL);
        }

        if (null != principal) {

            ConnectionPrincipal<?> connectionPrincipal = authenticate(principal);
            if (null != connectionPrincipal) {
                return connectionPrincipal;
            } else {
                unauthorized(response, "Unknown Principal" + principal.getName());
            }
        } else {
            unauthorized(response, "Authentication Required");
        }

        return null;
    }

    protected void unauthorized(ServletUpgradeResponse response, String message) {
        try {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "A client certificate is required for accessing OpenICF application but the server's listener is not configured for mutual authentication (or the client did not provide a certificate).");
        } catch (IOException e) {
            //
        }
    }

    public ConnectionPrincipal authenticate(Principal principal) {
        return singleTenant;
    }

    public static class SinglePrincipal extends ConnectionPrincipal<SinglePrincipal> {

        public SinglePrincipal(final OperationMessageListener listener,
                final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups) {
            super(listener, globalConnectionGroups);
        }

        public RemoteOperationContext handshake(
                final WebSocketConnectionHolder webSocketConnection,
                final RPCMessages.HandshakeMessage message) {
            return super.handshake(webSocketConnection, message);
        }

        protected void doClose() {

        }

    }
}
