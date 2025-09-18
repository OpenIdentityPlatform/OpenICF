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
 *
 * Portions Copyrighted 2025 3A Systems LLC.
 */

package org.forgerock.openicf.framework.server.grizzly;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.glassfish.grizzly.websockets.WebSocketEngine;

public class OpenICFWebSocketServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private OpenICFWebSocketApplication app;

    public OpenICFWebSocketServlet(final ConnectorFramework framework) {
        this.app = new OpenICFWebSocketApplication(framework, "lmA6bMfENJGlIDbfrVtklXFK32s=");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        WebSocketEngine.getEngine().register(config.getServletContext().getContextPath(),
                "/openicf", app);
    }

    @Override
    public void destroy() {
        WebSocketEngine.getEngine().unregister(app);
    }
}
