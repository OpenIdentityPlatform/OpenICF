/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 ForgeRock AS. All rights reserved.
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

import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.CloseListener;
import org.forgerock.openicf.framework.DelegatingAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.identityconnectors.common.logging.Log;

/**
 * A RemoteServerConnectorInfoManager
 *
 * @since 1.5
 */
public class RemoteServerConnectorInfoManager extends DelegatingAsyncConnectorInfoManager {

    private static final Log logger = Log.getLog(RemoteServerConnectorInfoManager.class);
    private final CloseListener<WebSocketConnectionGroup> closeListener;
    protected final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> messageDistributor;


    public RemoteServerConnectorInfoManager() {
        super(true);
        this.messageDistributor = new RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>() {
            @Override
            public <R extends RemoteRequest<V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext>, V, E extends Exception> R trySubmitRequest(RemoteRequestFactory<R, V, E, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> requestFactory) {

                for (AsyncConnectorInfoManager next : getDelegates()) {
                    if (next instanceof WebSocketConnectionGroup) {
                        R result = ((WebSocketConnectionGroup) next).trySubmitRequest(requestFactory);
                        if (null != result) {
                            return result;
                        }
                    } else {
                        logger.ok("Server delegate must be RequestDistributor");
                    }
                }
                /*
                 * All factories are offline so give up.
                 */
                return null;
            }

            @Override
            public boolean isOperational() {
                for (AsyncConnectorInfoManager next : getDelegates()) {
                    if (next instanceof WebSocketConnectionGroup && ((WebSocketConnectionGroup) next).isOperational()) {
                        return true;
                    }
                }
                return false;
            }
        };
        this.closeListener = new CloseListener<WebSocketConnectionGroup>() {
            @Override
            public void onClosed(final WebSocketConnectionGroup source) {
                if (removeAsyncConnectorInfoManager(source)) {
                    source.removeCloseListener(closeListener);
                }
            }
        };
    }


    public void addWebSocketConnectionGroup(WebSocketConnectionGroup connectionGroup) {
        addAsyncConnectorInfoManager(connectionGroup);
        connectionGroup.addCloseListener(closeListener);
    }

    protected RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getMessageDistributor() {
        return messageDistributor;
    }

    protected void doClose() {
        for (AsyncConnectorInfoManager next : getDelegates()) {
            if (next instanceof WebSocketConnectionGroup) {
                ((WebSocketConnectionGroup) next).close();
            }
        }
        logger.ok("Closing Remote Server Connector Info Manager");
    }
}