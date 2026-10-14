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

import java.io.StringReader;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import groovy.lang.Closure;

public class StreamingJsonSlurperTest {

    private static final String THREE_RESULTS =
            "{\"result\":[{\"_id\":\"1\"},{\"_id\":\"2\"},{\"_id\":\"3\"}],\"resultCount\":3}";

    /** Counts the result objects it receives and answers each one with {@code answer}. */
    public static final class CountingHandler extends Closure<Object> {
        private final boolean answer;
        int calls;

        CountingHandler(final boolean answer) {
            super(null);
            this.answer = answer;
        }

        public Object doCall(final Object object) {
            calls++;
            return answer;
        }
    }

    /** Counts how often the parser calls it after the parsed value. */
    public static final class CountingCallback extends Closure<Object> {
        int calls;

        CountingCallback() {
            super(null);
        }

        public Object doCall(final Object lexer, final Object content) {
            calls++;
            return null;
        }
    }

    @Test
    public void testParseStopsAtTheFirstFalse() {
        final StreamingJsonSlurper slurper = new StreamingJsonSlurper();
        final CountingHandler handler = new CountingHandler(false);
        final CountingCallback callback = new CountingCallback();
        slurper.parse(new StringReader(THREE_RESULTS), handler, callback);
        Assert.assertEquals(handler.calls, 1);
        Assert.assertTrue(slurper.isStopped());
        Assert.assertEquals(callback.calls, 0);
    }

    @Test
    public void testParseStopsInsideANestedArray() {
        final StreamingJsonSlurper slurper = new StreamingJsonSlurper();
        final CountingHandler handler = new CountingHandler(false);
        slurper.parse(new StringReader(
                "{\"result\":[[{\"_id\":\"1\"},{\"_id\":\"2\"}],{\"_id\":\"3\"}]}"), handler, null);
        Assert.assertEquals(handler.calls, 1);
        Assert.assertTrue(slurper.isStopped());
    }

    @Test
    public void testParseStopsInATopLevelArray() {
        final StreamingJsonSlurper slurper = new StreamingJsonSlurper();
        final CountingHandler handler = new CountingHandler(false);
        final CountingCallback callback = new CountingCallback();
        slurper.parse(new StringReader("[{\"_id\":\"1\"},{\"_id\":\"2\"}]"), handler, callback);
        Assert.assertEquals(handler.calls, 1);
        Assert.assertTrue(slurper.isStopped());
        Assert.assertEquals(callback.calls, 0);
    }

    @Test
    public void testParseReadsEverythingWhileTheHandlerReturnsTrue() {
        final StreamingJsonSlurper slurper = new StreamingJsonSlurper();
        final CountingHandler handler = new CountingHandler(true);
        final CountingCallback callback = new CountingCallback();
        final Map<?, ?> content =
                (Map<?, ?>) slurper.parse(new StringReader(THREE_RESULTS), handler, callback);
        Assert.assertEquals(handler.calls, 3);
        Assert.assertFalse(slurper.isStopped());
        Assert.assertEquals(callback.calls, 1);
        Assert.assertEquals(String.valueOf(content.get("resultCount")), "3");
    }

    @Test
    public void testParseForgetsTheStopOfAnEarlierParse() {
        final StreamingJsonSlurper slurper = new StreamingJsonSlurper();
        slurper.parse(new StringReader(THREE_RESULTS), new CountingHandler(false), null);
        final CountingHandler handler = new CountingHandler(true);
        slurper.parse(new StringReader(THREE_RESULTS), handler, null);
        Assert.assertEquals(handler.calls, 3);
        Assert.assertFalse(slurper.isStopped());
    }
}
