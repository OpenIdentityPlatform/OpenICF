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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;

/**
 * An {@link AuthCache} that all requests of one HTTP client share, so that only the first
 * request takes the server's BASIC challenge and the following ones authenticate preemptively.
 * <p>
 * It keeps only {@link BasicScheme}s: a Digest scheme carries a nonce count that concurrent
 * requests must not share. Its methods are synchronized because {@link BasicAuthCache} is not
 * thread-safe before HttpClient 4.4.
 */
public final class SharedBasicAuthCache implements AuthCache {

    private final BasicAuthCache cache = new BasicAuthCache();

    @Override
    public synchronized void put(final HttpHost host, final AuthScheme authScheme) {
        if (authScheme instanceof BasicScheme) {
            cache.put(host, authScheme);
        }
    }

    @Override
    public synchronized AuthScheme get(final HttpHost host) {
        return cache.get(host);
    }

    @Override
    public synchronized void remove(final HttpHost host) {
        cache.remove(host);
    }

    @Override
    public synchronized void clear() {
        cache.clear();
    }
}
