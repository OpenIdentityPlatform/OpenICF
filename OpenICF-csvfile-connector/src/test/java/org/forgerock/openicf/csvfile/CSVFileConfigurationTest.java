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
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.Test;

/**
 *
 * @author lazyman
 */
public class CSVFileConfigurationTest {

    @Test
    public void validateGoodConfiguration() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("config.csv"));
        config.setPasswordAttribute("password");
        config.setUniqueAttribute("uid");

        config.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void validateFilePath() {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setPasswordAttribute("password");
        config.setUniqueAttribute("uid");

        config.validate();
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void validateEncoding() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("bad-encoding");
        config.setFilePath(TestUtils.getTestFile("config.csv"));
        config.setPasswordAttribute("password");
        config.setUniqueAttribute("uid");

        config.validate();
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void validateUniqueAttribute() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("config.csv"));
        config.setPasswordAttribute("password");

        config.validate();
    }
}
