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
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IdleKeepAliveStrategyTest {

    private static long durationFor(final String keepAliveHeader) {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        if (null != keepAliveHeader) {
            response.addHeader("Keep-Alive", keepAliveHeader);
        }
        return IdleKeepAliveStrategy.INSTANCE.getKeepAliveDuration(response, new BasicHttpContext());
    }

    @Test
    public void testCapsAConnectionWithoutAnAnnouncedTimeout() {
        Assert.assertEquals(IdleKeepAliveStrategy.MAX_IDLE_MILLIS, 10000L);
        Assert.assertEquals(durationFor(null), 10000L);
    }

    @Test
    public void testHonoursAShorterAnnouncedTimeout() {
        Assert.assertEquals(durationFor("timeout=5"), 5000L);
    }

    @Test
    public void testCapsALongerAnnouncedTimeout() {
        Assert.assertEquals(durationFor("timeout=600"), 10000L);
    }

    @Test
    public void testExpiresAZeroAnnouncedTimeoutAtOnce() {
        Assert.assertEquals(durationFor("timeout=0"), 1L);
    }
}
