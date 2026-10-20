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

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
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

    @Test
    public void testGivesEachRequestItsOwnScheme() {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        final BasicScheme cached = new BasicScheme();
        cache.put(HOST, cached);
        final Object first = cache.get(HOST);
        final Object second = cache.get(HOST);
        Assert.assertNotSame(first, second);
        Assert.assertNotSame(first, cached);
        Assert.assertNotSame(second, cached);
    }

    @Test
    public void testCopyAuthenticatesLikeTheCachedScheme() throws Exception {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        final BasicScheme cached = new BasicScheme();
        cached.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"r\""));
        cache.put(HOST, cached);
        final UsernamePasswordCredentials credentials =
                new UsernamePasswordCredentials("admin", "Passw0rd");
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Header expected = cached.authenticate(credentials, request, new BasicHttpContext());
        final Header actual =
                ((BasicScheme) cache.get(HOST)).authenticate(credentials, request,
                        new BasicHttpContext());
        Assert.assertEquals(actual.getName(), expected.getName());
        Assert.assertEquals(actual.getValue(), expected.getValue());
    }

    @Test
    public void testCopyKeepsTheProxyState() throws Exception {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        final BasicScheme cached = new BasicScheme();
        cached.processChallenge(new BasicHeader(AUTH.PROXY_AUTH, "Basic realm=\"r\""));
        cache.put(HOST, cached);
        final BasicScheme copy = (BasicScheme) cache.get(HOST);
        Assert.assertTrue(copy.isProxy());
        final Header header = copy.authenticate(new UsernamePasswordCredentials("admin", "Passw0rd"),
                new BasicHttpRequest("GET", "/"), new BasicHttpContext());
        Assert.assertEquals(header.getName(), AUTH.PROXY_AUTH_RESP);
    }

    @Test
    public void testCopyKeepsTheCredentialsCharset() {
        final SharedBasicAuthCache cache = new SharedBasicAuthCache();
        cache.put(HOST, new BasicScheme(Consts.UTF_8));
        final BasicScheme copy = (BasicScheme) cache.get(HOST);
        Assert.assertEquals(copy.getCredentialsCharset(), Consts.UTF_8);
    }
}
