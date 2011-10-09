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
import java.io.File;
import java.net.URL;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.Test;

/**
 *
 * @author Viliam Repan (lazyman)
 */
public class TestOpTest {

    private CSVFileConnector connector;

    @AfterMethod
	public void after() {
        connector.dispose();
        connector = null;
    }

    @Test
    public void testGoodConfiguration() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        URL testFile = UtilsTest.class.getResource("/files/authenticate.csv");
        config.setFilePath(new File(testFile.toURI()));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        connector.test();
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void badHeader() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        URL testFile = UtilsTest.class.getResource("/files/test-bad.csv");
        config.setFilePath(new File(testFile.toURI()));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        connector.test();
    }
}
