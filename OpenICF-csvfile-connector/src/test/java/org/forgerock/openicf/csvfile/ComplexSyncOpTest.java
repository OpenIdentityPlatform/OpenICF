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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.identityconnectors.framework.common.objects.Uid;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.testng.annotations.Test;

/**
 *
 * @author Viliam Repan (lazyman)
 */
public class ComplexSyncOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("sync.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
    }

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    /**
     * only for to test sync tmp files handling, test not usable now
     */
    @Test(groups = "broken")
    public void syncTest() {
        final List<SyncDelta> deltas = new ArrayList<SyncDelta>();
        SyncToken token = null;
        for (int i = 0; i < 3; i++) {
            connector.sync(ObjectClass.ACCOUNT, token, new SyncResultsHandler() {

                @Override
                public boolean handle(SyncDelta sd) {
                    deltas.add(sd);
                    return true;
                }
            }, null);
            if (!deltas.isEmpty()) {
                token = deltas.get(0).getToken();
            }

            Map<String, SyncDelta> deltaMap = createSyncDeltaTestMap(token);
            for (SyncDelta delta : deltas) {
                SyncDelta syncDelta = deltaMap.get(delta.getUid().getUidValue());
                deltaMap.remove(delta.getUid().getUidValue());
//                assertEquals(syncDelta, delta);
            }
            deltas.clear();
        }
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
        cBuilder.addAttribute("firstName", "igor");
        cBuilder.addAttribute("lastName", "farinicNewRecord");
        cBuilder.addAttribute("__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        builder.setObject(cBuilder.build());
        map.put("fanfi", builder.build());

        return map;
    }
}
