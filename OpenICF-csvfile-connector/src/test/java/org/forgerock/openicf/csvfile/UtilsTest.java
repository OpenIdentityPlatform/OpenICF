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

import org.testng.annotations.Test;
import java.net.URL;
import java.io.File;
import org.forgerock.openicf.csvfile.util.Utils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.testng.annotations.AfterMethod;

import static org.testng.Assert.*;

/**
 *
 * @author lazyman
 */
public class UtilsTest {

    private CSVFileConnector connector;

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    private void initConnector(String valueQualifier, String multivalueDelimiter, String fieldDelimiter) throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        URL testFile = UtilsTest.class.getResource("/files/authenticate.csv");
        config.setFilePath(new File(testFile.toURI()));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        config.setValueQualifier(valueQualifier);
        config.setMultivalueDelimiter(multivalueDelimiter);
        config.setFieldDelimiter(fieldDelimiter);

        connector = new CSVFileConnector();
        connector.init(config);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void parseValuesSimpleTest() throws Exception {
        initConnector("\"", "|", ",");


        Map<String, String> testCases = new HashMap<String, String>();
        testCases.put("", "[]");
        testCases.put(null, "[]");
        testCases.put(" ,\"a\",\"b\",\"c\",\"asdf\"", "[, a, b, c, asdf]");
        testCases.put("\t ,\"a\",\"b\",\"c\",\"asdf\"", "[, a, b, c, asdf]");
        testCases.put(",\"a\",\"b\",\"c\",\"asdf\"", "[, a, b, c, asdf]");
        testCases.put("\"a\",,\"b\",\"c\",\"asdf\"", "[a, , b, c, asdf]");
        testCases.put("\"a\",\"b\",\"c\",\"asdf\",", "[a, b, c, asdf, ]");

        Set<Entry<String, String>> set = testCases.entrySet();
        for (Entry<String, String> entry : set) {
            assertEquals(entry.getValue(), Arrays.toString(Utils.parseValues(entry.getKey(),
                    connector.getLinePattern(), (CSVFileConfiguration) connector.getConfiguration()).toArray()));
        }
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void parseValuesSpecialTest() throws Exception {
        initConnector("\"", ".", "\\|");

        Map<String, String> testCases = new HashMap<String, String>();
        testCases.put("", "[]");
        testCases.put(null, "[]");
        testCases.put(" |\"a\"|\"b\"|\"c\"|\"asdf\"", "[, a, b, c, asdf]");
        testCases.put("\t |\"a\"|\"b\"|\"c\"|\"asdf\"", "[, a, b, c, asdf]");
        testCases.put("|\"a\"|\"b\"|\"c\"|\"asdf\"", "[, a, b, c, asdf]");
        testCases.put("\"a\"||\"b\"|\"c\"|\"asdf\"", "[a, , b, c, asdf]");
        testCases.put("\"a\"|\"b\"|\"c\"|\"asdf\"|", "[a, b, c, asdf, ]");

        Set<Entry<String, String>> set = testCases.entrySet();
        for (Entry<String, String> entry : set) {
            assertEquals(entry.getValue(), Arrays.toString(Utils.parseValues(entry.getKey(),
                    connector.getLinePattern(), (CSVFileConfiguration) connector.getConfiguration()).toArray()),
                    "Testing: '" + entry.getKey() + "'");
        }
    }
}
