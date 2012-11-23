/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
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
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.forgerock.openicf.csvfile.util.Utils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 *
 * @author Viliam Repan (lazyman)
 */
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
        assertEquals("1300734815289", oldToken.getValue());
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncResultsHandler() {

            @Override
            public boolean handle(SyncDelta sd) {
                Assert.fail("This test should fail on headers check.");
                return false;
            }
        }, null);

        //test cleanup
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        if (!oldToken.getValue().equals(token.getValue())) {
            CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
            File syncFile = new File(config.getFilePath() + "." + token.getValue());
            syncFile.delete();
        }

        Assert.fail("This test should fail on headers check.");
    }

    @Test
    public void syncTest() throws Exception {
        initConnector("../../../src/test/resources/files/sync.csv");
        Utils.copyAndReplace(new File("./src/test/resources/files/sync.csv.1300734815289.backup"),
                new File("./src/test/resources/files/sync.csv.1300734815289"));

        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertEquals("1300734815289", oldToken.getValue());
        final List<SyncDelta> deltas = new ArrayList<SyncDelta>();
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncResultsHandler() {

            @Override
            public boolean handle(SyncDelta sd) {
                deltas.add(sd);
                return true;
            }
        }, null);

        //test cleanup
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        if (!oldToken.getValue().equals(token.getValue())) {
            CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
            File syncFile = new File(config.getFilePath() + "." + token.getValue());
            syncFile.delete();
        }

        Map<String, SyncDelta> deltaMap = createSyncDeltaTestMap(token);
        for (SyncDelta delta : deltas) {
            SyncDelta syncDelta = deltaMap.get(delta.getUid().getUidValue());
            deltaMap.remove(delta.getUid().getUidValue());
            assertEquals(syncDelta, delta);
        }
        assertTrue(deltaMap.isEmpty(), "deltas didn't match");
    }

    @Test
    public void syncTestHandlerStopped() throws Exception {
        initConnector("../../../src/test/resources/files/sync.csv");
        File file = new File("./src/test/resources/files/sync.csv.1300734815289");
        Utils.copyAndReplace(new File("./src/test/resources/files/sync.csv.1300734815289.backup"),
                file);

        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertEquals(oldToken.getValue(), "1300734815289");
        final List<SyncDelta> deltas = new ArrayList<SyncDelta>();
        connector.sync(ObjectClass.ACCOUNT, oldToken, new SyncResultsHandler() {

            @Override
            public boolean handle(SyncDelta sd) {
                deltas.add(sd);
                return false;
            }
        }, null);

        //test cleanup
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        if (!oldToken.getValue().equals(token.getValue())) {
            CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
            File syncFile = new File(config.getFilePath() + "." + token.getValue());
            syncFile.delete();
        }

        Map<String, SyncDelta> deltaMap = createSyncDeltaTestMap(token);
        for (SyncDelta delta : deltas) {
            SyncDelta syncDelta = deltaMap.get(delta.getUid().getUidValue());
            deltaMap.remove(delta.getUid().getUidValue());
            assertEquals(syncDelta, delta);
        }
        assertEquals(deltaMap.size(), 2);

        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void noSyncTokenAvailable() throws Exception {
        assertTrue(new File("./src/test/resources/files/sync.csv").exists());
        initConnector("../../../src/test/resources/files/sync.csv");

        SyncToken oldToken = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertEquals(oldToken.getValue(),
                Long.toString(new File("./src/test/resources/files/sync.csv").lastModified()));

        CSVFileConfiguration config = (CSVFileConfiguration)connector.getConfiguration();
        File syncFile = config.getFilePath();
        File file = new File(syncFile.getParent(), syncFile.getName() + "." + oldToken.getValue());
        file.delete();
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
//        cBuilder.addAttribute("uid", "miso"); //we're not returning unique attribute, it's already there as __UID__
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
//        cBuilder.addAttribute("uid", "fanfi"); //we're not returning unique attribute, it's already there as __UID__
        cBuilder.addAttribute("firstName", "igor");
        cBuilder.addAttribute("lastName", "farinicNewRecord");
        cBuilder.addAttribute("__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        builder.setObject(cBuilder.build());
        map.put("fanfi", builder.build());

        return map;
    }

    private void initConnector(String fileName) throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile(fileName));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
    }
}
