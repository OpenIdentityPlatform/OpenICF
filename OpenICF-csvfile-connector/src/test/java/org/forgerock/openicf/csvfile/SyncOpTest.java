/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010-2015 ForgeRock
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * Portions Copyrighted 2011 Viliam Repan (lazyman)
 *
 */
package org.forgerock.openicf.csvfile;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.Base64;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class SyncOpTest {

    private CSVFileConnector connector;

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void syncNullHandler() throws Exception {
        initConnector("sync.csv");

        connector.sync(ObjectClass.ACCOUNT, null, null, null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badHeaders() throws Exception {
        initConnector("sync-bad.csv");

        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertEquals("1300734815289", String.valueOf(oldToken.getValue()));
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncResultsHandler() {

            public boolean handle(SyncDelta sd) {
                Assert.fail("This test should fail on headers check.");
                return false;
            }
        }, null);

        //test cleanup
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        if (!oldToken.getValue().equals(token.getValue())) {
            CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
            File syncFile = new File(config.getCsvFile() + "." + token.getValue());
            syncFile.delete();
        }

        Assert.fail("This test should fail on headers check.");
    }

    @Test
    public void syncTest() throws Exception {
        initConnector(TestUtils.class.getResource("/files/").getFile() + "sync.csv");
        TestUtils.remove(new File(TestUtils.class.getResource("/files/").getFile()), "sync.csv[.].*");
        TestUtils.copyAndReplace(
                new File(TestUtils.class.getResource("/files/").getFile() + "sync.backup.csv.1300734815289"),
                new File(TestUtils.class.getResource("/files/").getFile() + "sync.csv.1300734815289"));

        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertEquals(String.valueOf(oldToken.getValue()), "1300734815289");
        final List<SyncDelta> deltas = new ArrayList<SyncDelta>();
        final AtomicReference<SyncToken> newToken = new AtomicReference<SyncToken>();
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncTokenResultsHandler() {

            public boolean handle(SyncDelta sd) {
                deltas.add(sd);
                return true;
            }

            public void handleResult(SyncToken syncToken) {
                newToken.set(syncToken);
            }
        }, null);

        //test cleanup
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        if (!oldToken.getValue().equals(token.getValue())) {
            assertEquals(token.getValue(), newToken.get().getValue());
            CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
            File syncFile = new File(config.getCsvFile() + "." + token.getValue());
            syncFile.delete();
        }

        Map<String, SyncDelta> deltaMap = createSyncDeltaTestMap(token);
        for (SyncDelta delta : deltas) {
            SyncDelta syncDelta = deltaMap.get(delta.getUid().getUidValue());
            deltaMap.remove(delta.getUid().getUidValue());
        }
        assertTrue(deltaMap.isEmpty(), "deltas didn't match");
    }

    @Test
    public void syncTestSuccessive() throws Exception {
        initConnector(TestUtils.class.getResource("/files/").getFile() + "sync-successive.csv");
        TestUtils.remove(new File(TestUtils.class.getResource("/files/").getFile()), "sync-successive.csv[.].*");
        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        final AtomicReference<SyncToken> newToken = new AtomicReference<SyncToken>();
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncTokenResultsHandler() {
            public boolean handle(SyncDelta sd) {
                return true;
            }

            public void handleResult(SyncToken syncToken) {
                newToken.set(syncToken);
            }
        }, null);

        oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(AttributeBuilder.build("firstName", "Barbara"));
        attributes.add(AttributeBuilder.build("lastName", "Jensen"));
        attributes.add(AttributeBuilder.build("uid", "uid=bjensen,dc=example,dc=com"));
        attributes.add(AttributeBuilder.buildPassword(new GuardedString(Base64.encode("asdf".getBytes()).toCharArray())));
        Uid uid = connector.create(ObjectClass.ACCOUNT, attributes, null);
        assertNotNull(uid);

        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncTokenResultsHandler() {
            public boolean handle(SyncDelta sd) {
                return true;
            }

            public void handleResult(SyncToken syncToken) {
                newToken.set(syncToken);
            }
        }, null);

        newToken.set(connector.getLatestSyncToken(ObjectClass.ACCOUNT));
        assertFalse(oldToken.getValue().equals(newToken.get().getValue()));
    }

    @Test
    public void syncTestHandlerStopped() throws Exception {
        initConnector(TestUtils.class.getResource("/files/").getFile() + "sync.csv");
        TestUtils.remove(new File(TestUtils.class.getResource("/files/").getFile()), "sync.csv[.].*");
        TestUtils.copyAndReplace(
                new File(TestUtils.class.getResource("/files/").getFile() + "sync.backup.csv.1300734815289"),
                new File(TestUtils.class.getResource("/files/").getFile() + "sync.csv.1300734815289"));

        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertEquals(String.valueOf(oldToken.getValue()), "1300734815289");
        final List<SyncDelta> deltas = new ArrayList<SyncDelta>();
        final AtomicReference<SyncToken> newToken = new AtomicReference<SyncToken>();
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncTokenResultsHandler() {

            public boolean handle(SyncDelta sd) {
                deltas.add(sd);
                return false;
            }

            public void handleResult(SyncToken syncToken) {
                    newToken.set(syncToken);
            }
        }, null);

        //test cleanup
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        if (!oldToken.getValue().equals(token.getValue())) {
            assertEquals(token.getValue(), newToken.get().getValue());
            CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
            File syncFile = new File(config.getCsvFile() + "." + token.getValue());
            syncFile.delete();
        }

        Map<String, SyncDelta> deltaMap = createSyncDeltaTestMap(token);
        for (SyncDelta delta : deltas) {
            SyncDelta syncDelta = deltaMap.get(delta.getUid().getUidValue());
            deltaMap.remove(delta.getUid().getUidValue());
        }
        assertEquals(deltaMap.size(), 1);
    }

    private Map<String, SyncDelta> createSyncDeltaTestMap(SyncToken token) {
        Map<String, SyncDelta> map = new HashMap<String, SyncDelta>();

        SyncDeltaBuilder builder = new SyncDeltaBuilder();
        builder.setDeltaType(SyncDeltaType.DELETE);
        builder.setToken(token);
        builder.setUid(new Uid("vilo"));
        builder.setObject(null);
        map.put("vilo", builder.build());

        builder = new SyncDeltaBuilder();
        builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
        builder.setToken(token);
        builder.setUid(new Uid("miso"));
        ConnectorObjectBuilder cBuilder = new ConnectorObjectBuilder();
        cBuilder.setName("miso");
        cBuilder.setUid("miso");
        cBuilder.setObjectClass(ObjectClass.ACCOUNT);
        cBuilder.addAttribute("uid", "miso"); 
        cBuilder.addAttribute("firstName", "michal");
        cBuilder.addAttribute("lastName", "LastnameChange");
        cBuilder.addAttribute("__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        builder.setObject(cBuilder.build());
        map.put("miso", builder.build());

        builder = new SyncDeltaBuilder();
        builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
        builder.setToken(token);
        builder.setUid(new Uid("fanfi"));
        cBuilder = new ConnectorObjectBuilder();
        cBuilder.setName("fanfi");
        cBuilder.setUid("fanfi");
        cBuilder.setObjectClass(ObjectClass.ACCOUNT);
        cBuilder.addAttribute("uid", "fanfi"); 
        cBuilder.addAttribute("firstName", "igor");
        cBuilder.addAttribute("lastName", "farinicNewRecord");
        cBuilder.addAttribute("__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        builder.setObject(cBuilder.build());
        map.put("fanfi", builder.build());

        return map;
    }

    private void initConnector(String fileName) throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile(fileName));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        connector = new CSVFileConnector();
        connector.init(config);
    }
}
