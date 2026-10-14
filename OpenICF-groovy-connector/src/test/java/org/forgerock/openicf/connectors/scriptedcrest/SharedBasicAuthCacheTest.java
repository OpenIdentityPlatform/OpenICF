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
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SharedBasicAuthCacheTest {

    private static final HttpHost HOST = new HttpHost("localhost", 8080, "http");

    @Test
    public void testKeepsABasicScheme() {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        cache.put(HOST, new BasicScheme());
        Assert.assertTrue(cache.get(HOST) instanceof BasicScheme, String.valueOf(cache.get(HOST)));
    }

    @Test
    public void testDropsADigestScheme() {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        cache.put(HOST, new DigestScheme());
        Assert.assertNull(cache.get(HOST));
    }

    @Test
    public void testRemoveAndClearForgetTheScheme() {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        cache.put(HOST, new BasicScheme());
        cache.remove(HOST);
        Assert.assertNull(cache.get(HOST));
        cache.put(HOST, new BasicScheme());
        cache.clear();
        Assert.assertNull(cache.get(HOST));
    }
}
