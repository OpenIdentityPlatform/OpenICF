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

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.forgerock.openicf.csvfile.util.Utils;
import java.io.File;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 *
 * @author Viliam Repan (lazyman)
 */
public class DeleteOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        File file = TestUtils.getTestFile("delete.csv");
        File backup = TestUtils.getTestFile("delete-backup.csv");
        Utils.copyAndReplace(backup, file);

        CSVFileConfiguration config = new CSVFileConfiguration();
//        URL testFile = UtilsTest.class.getResource("/files/update-attribute.csv");
//        config.setFilePath(new File(testFile.toURI()));
        config.setFilePath(TestUtils.getTestFile("delete.csv"));
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

    @AfterClass
    public static void afterClass() throws Exception {
        File file = TestUtils.getTestFile("delete.csv");
        file.delete();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClass() {
        connector.delete(null, new Uid("vilo"), null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badObjectClass() {
        connector.delete(ObjectClass.GROUP, new Uid("vilo"), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullUid() {
        connector.delete(ObjectClass.ACCOUNT, null, null);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void notExistingUid() {
        connector.delete(ObjectClass.ACCOUNT, new Uid("unknown"), null);
    }

    @Test
    public void correctDelete() throws Exception {
        connector.delete(ObjectClass.ACCOUNT, new Uid("vilo"), null);
        CSVFileConfiguration config = (CSVFileConfiguration) connector.getConfiguration();
        String result = TestUtils.compareFiles(config.getFilePath(),
                TestUtils.getTestFile("delete-result.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }
}
