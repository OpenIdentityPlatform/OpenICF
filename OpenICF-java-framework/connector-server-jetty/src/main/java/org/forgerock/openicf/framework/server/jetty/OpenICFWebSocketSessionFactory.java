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

import org.eclipse.jetty.websocket.common.WebSocketSessionFactory;
import org.eclipse.jetty.websocket.common.events.EventDriver;
import org.eclipse.jetty.websocket.common.events.JettyAnnotatedEventDriver;
import org.eclipse.jetty.websocket.common.events.JettyListenerEventDriver;
import org.eclipse.jetty.websocket.common.scopes.WebSocketContainerScope;

public class OpenICFWebSocketSessionFactory extends WebSocketSessionFactory {
    public OpenICFWebSocketSessionFactory(WebSocketContainerScope containerScope) {
        super(containerScope);
    }

    /*public OpenICFWebSocketSessionFactory(SessionListener... sessionListeners) {
        super(sessionListeners);
    }*/

    @Override
    public boolean supports(final EventDriver websocket) {
        return (websocket instanceof JettyAnnotatedEventDriver)
                || (websocket instanceof JettyListenerEventDriver)
                || (websocket instanceof WebSocketListenerBridge);
    }
}
