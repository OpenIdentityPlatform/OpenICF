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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.connectors.scriptedcrest;

import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * Keeps an idle pooled connection for as long as the server's {@code Keep-Alive: timeout}
 * allows, and never longer than {@link #MAX_IDLE_MILLIS}.
 * <p>
 * A server that announces no idle timeout may still close idle connections, and a request sent
 * over a connection the server has just closed fails. Ten seconds is below the idle timeouts of
 * common servers (Jetty 30 s, Tomcat 20 s), so the pool drops such a connection first; it closes
 * an expired connection when it would otherwise lease it.
 */
public final class IdleKeepAliveStrategy implements ConnectionKeepAliveStrategy {

    /** The longest time an idle connection is kept for reuse, in milliseconds. */
    public static final long MAX_IDLE_MILLIS = 10000L;

    public static final IdleKeepAliveStrategy INSTANCE = new IdleKeepAliveStrategy();

    private IdleKeepAliveStrategy() {
    }

    @Override
    public long getKeepAliveDuration(final HttpResponse response, final HttpContext context) {
        final long announced =
                DefaultConnectionKeepAliveStrategy.INSTANCE.getKeepAliveDuration(response, context);
        // The pool keeps a connection with a duration of 0 or less forever, so "timeout=0", which
        // allows no idle time, expires the entry at once instead.
        if (announced == 0) {
            return 1L;
        }
        return announced < 0 || announced > MAX_IDLE_MILLIS ? MAX_IDLE_MILLIS : announced;
    }
}
