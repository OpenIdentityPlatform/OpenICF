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

package org.forgerock.openicf.framework.remote.protobuf;

import static org.forgerock.openicf.framework.remote.MessagesUtil.deserializeMessage;
import static org.forgerock.openicf.framework.remote.MessagesUtil.serializeMessage;
import static org.testng.Assert.assertEquals;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MessagesUtilTest {

    @Test
    public void testUidSerialize() throws Exception {
        Uid source = new Uid("1");
        CommonObjectMessages.Uid message = serializeMessage(source, CommonObjectMessages.Uid.class);
        Assert.assertEquals(deserializeMessage(message, Uid.class), source);
        source = new Uid("2", "1");
        message = serializeMessage(source, CommonObjectMessages.Uid.class);
        Assert.assertEquals(deserializeMessage(message, Uid.class), source);
    }

    @Test
    public void testConnectorKeySerialize() throws Exception {
        ConnectorKey source = new ConnectorKey("bundle", "1.5.0.0", "Connector");
        CommonObjectMessages.ConnectorKey message =
                serializeMessage(source, CommonObjectMessages.ConnectorKey.class);
        Assert.assertEquals(deserializeMessage(message, ConnectorKey.class), source);
    }

    @Test
    public void testScriptContextSerialize() throws Exception {
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("language");
        builder.setScriptText("text");
        builder.addScriptArgument("foo", "bar");
        builder.addScriptArgument("foo2", "bar2");
        CommonObjectMessages.ScriptContext message =
                serializeMessage(builder.build(), CommonObjectMessages.ScriptContext.class);
        ScriptContext v2 = deserializeMessage(message, ScriptContext.class);
        assertEquals(2, v2.getScriptArguments().size());
        assertEquals("bar", v2.getScriptArguments().get("foo"));
        assertEquals("bar2", v2.getScriptArguments().get("foo2"));
        assertEquals("language", v2.getScriptLanguage());
        assertEquals("text", v2.getScriptText());

    }

    @Test
    public void testSearchResultSerialize() throws Exception {
        SearchResult source = new SearchResult();
        CommonObjectMessages.SearchResult message =
                serializeMessage(source, CommonObjectMessages.SearchResult.class);
        Assert.assertEquals(deserializeMessage(message, SearchResult.class), source);
    }

    @Test
    public void testSyncDeltaSerialize() throws Exception {
        ConnectorObjectBuilder coBuilder = new ConnectorObjectBuilder().setUid("0").setName("0");
        SyncDelta source =
                new SyncDeltaBuilder().setDeltaType(SyncDeltaType.CREATE)
                        .setToken(new SyncToken(1)).setObject(coBuilder.build()).build();
        CommonObjectMessages.SyncDelta message =
                serializeMessage(source, CommonObjectMessages.SyncDelta.class);
        Assert.assertEquals(deserializeMessage(message, SyncDelta.class), source);
    }

}
