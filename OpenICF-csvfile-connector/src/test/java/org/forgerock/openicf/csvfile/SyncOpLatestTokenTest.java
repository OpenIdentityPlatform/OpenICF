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
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import static org.testng.Assert.*;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.testng.annotations.Test;

/**
 *
 * @author lazyman
 */
public class SyncOpLatestTokenTest {

    private CSVFileConnector connector;

    @BeforeMethod
	public void before() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("sync-token.csv"));
        config.setUniqueAttribute("uid");

        connector = new CSVFileConnector();
        connector.init(config);
    }

    @AfterMethod
	public void after() {
        connector.dispose();
        connector = null;
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClass() {
        connector.getLatestSyncToken(null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badObjectClass() {
        connector.getLatestSyncToken(ObjectClass.GROUP);
    }

    @Test
    public void correctObjectClass() {
        SyncToken token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertNotNull(token);
        assertEquals("1300734815289", token.getValue());
    }
}
