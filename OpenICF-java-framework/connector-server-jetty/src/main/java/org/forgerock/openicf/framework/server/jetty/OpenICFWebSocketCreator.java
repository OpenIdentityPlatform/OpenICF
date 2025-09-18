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
 * Portions copyright 2025 3A Systems LLC.
 */

package org.forgerock.openicf.framework.server.jetty;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.security.auth.callback.NameCallback;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.OpenICFServerAdapter;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;

public class OpenICFWebSocketCreator implements JettyWebSocketCreator {

    private static final Logger logger = Log.getLogger(OpenICFWebSocketCreator.class);

    protected final ConcurrentMap<String, WebSocketConnectionGroup> globalConnectionGroups =
            new ConcurrentHashMap<String, WebSocketConnectionGroup>();
    protected final ConnectorFramework connectorFramework;
    protected final OperationMessageListener listener;
    protected ConcurrentMap<String, ConnectionPrincipal<?>> principalCache = new ConcurrentHashMap<String, ConnectionPrincipal<?>>();


    private Authenticator authenticator;


    public OpenICFWebSocketCreator(final ConnectorFramework connectorFramework,
                                   final ScheduledExecutorService executorService) {
        this(connectorFramework, null, null, executorService);
    }


    public OpenICFWebSocketCreator(final ConnectorFramework connectorFramework,
                                   final OperationMessageListener listener,
                                   final Authenticator authenticator,
                                   final ScheduledExecutorService executorService) {
        this.connectorFramework = connectorFramework;
        if (null == listener) {
            this.listener
                    = new OpenICFServerAdapter(this.connectorFramework, connectorFramework.getLocalManager(), false);
        } else {
            this.listener = listener;
        }


        if (null == authenticator) {
            logger.info("Creating single 'anonymous' authenticator");
            this.authenticator = new Authenticator() {
                @Override
                public void authenticate(JettyServerUpgradeRequest request, JettyServerUpgradeResponse response, NameCallback callback) {
                    callback.setName(ConnectionPrincipal.DEFAULT_NAME);
                }
            };
        } else {
            this.authenticator = authenticator;
        }

        //Will be cancelled when executorService is shut down
        executorService.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                for (WebSocketConnectionGroup e : globalConnectionGroups.values()) {
                    if (!e.checkIsActive()) {
                        globalConnectionGroups.remove(e.getRemoteSessionId());
                    }
                }
            }
        }, 1, 4, TimeUnit.MINUTES);
    }

    @Override
    public Object createWebSocket(JettyServerUpgradeRequest request, JettyServerUpgradeResponse response) {

        if (request.getSubProtocols().contains(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL)) {
            response.setAcceptedSubProtocol(RemoteWSFrameworkConnectionInfo.OPENICF_PROTOCOL);
        }

        ConnectionPrincipal<?> connectionPrincipal = authenticate(request, response);
        if (null != connectionPrincipal) {
            return connectionPrincipal;
        } else if (!response.isCommitted()) {
            unauthorized(response, "Unknown Principal");
        }
        return null;
    }

    protected void unauthorized(JettyServerUpgradeResponse response, String message) {
        try {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "A client certificate is required for accessing OpenICF application but the server's listener is not configured for mutual authentication (or the client did not provide a certificate).");
        } catch (IOException e) {
            //
        }
    }

    public ConnectionPrincipal<?> authenticate(JettyServerUpgradeRequest request, JettyServerUpgradeResponse response) {
        NameCallback callback = new NameCallback("OpenICF user:>");
        authenticator.authenticate(request, response, callback);
        if (StringUtil.isNotBlank(callback.getName())) {
            ConnectionPrincipal<?> connectionPrincipal = principalCache.get(callback.getName());
            if (connectionPrincipal == null) {
                principalCache.putIfAbsent(callback.getName(), new SinglePrincipal(callback.getName(), listener,
                        connectorFramework, globalConnectionGroups));
                return principalCache.get(callback.getName());
            } else {
                return connectionPrincipal;
            }
        }
        return null;
    }

}
