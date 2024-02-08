/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for
 * the specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file
 * and include the License file at legal/CDDLv1.0.txt. If applicable, add the following
 * below the CDDL Header, with the fields enclosed by brackets [] replaced by your
 * own identifying information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openicf.connectors;

import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.routing.RoutingMode;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.routing.Router;
import org.forgerock.util.Factory;

/**
 * Http Application implementation to demonstrate integration with the Commons HTTP Framework.
 */
public class TestHttpApplication implements HttpApplication {

    @Override
    public Handler start() throws HttpApplicationException {
        Router router = new Router();
        router.addRoute(requestUriMatcher(RoutingMode.STARTS_WITH, "/rest/users"), MemoryBackendHandler.getHandler());
        router.addRoute(requestUriMatcher(RoutingMode.STARTS_WITH, "/rest/groups"), MemoryBackendHandler.getHandler());
        router.addRoute(requestUriMatcher(RoutingMode.STARTS_WITH, "/crest/users"), MemoryBackendHandler.getHandler());
        router.addRoute(requestUriMatcher(RoutingMode.STARTS_WITH, "/crest/groups"), MemoryBackendHandler.getHandler());
        return router;
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        return null;
    }

    @Override
    public void stop() {

    }
}