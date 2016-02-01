/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011-2016 ForgeRock
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
 * 
 */
package org.forgerock.openicf.csvfile;

import java.io.FileWriter;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.impl.api.local.operations.FilteredResultsHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Attempts to test the {@link CSVFileConnector} with the framework.
 * 
 */
public class CSVFileConnectorTests {

    private CSVFileConfiguration config;

    /**
     * Setup the tests with a csv file to work with, and a CSVFileConfiguration.
     *
     * @throws Exception
     */
    @BeforeMethod
    public void setup() throws Exception {
        config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile("connector-csv-test.csv"));
        config.setFieldDelimiter("*");
        config.setHeaderUid("uid");
        config.setHeaderName("uid");
        config.setHeaderPassword(OperationalAttributes.PASSWORD_NAME);
    }

    /**
     * Delete any files created during the test.
     *
     * @throws Exception
     */
    @AfterMethod
    public void tearDown() throws Exception {
        if (null != config.getCsvFile()) {
            config.getCsvFile().delete();
        }
    }

    /**
     * Tests that an extra delimiter at the end of the header line will be captured as a ConfigurationException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testEmptyHeader() throws Exception {
        FileWriter f2 = new FileWriter(config.getCsvFile(), false);
        f2.write(new StringBuilder("uid")
                .append(config.getFieldDelimiter())
                .append(OperationalAttributes.PASSWORD_NAME)
                .append(config.getFieldDelimiter())
                .append("fullName")
                .append(config.getFieldDelimiter()).toString());
        // The extra delimiter at the end is an empty header and should cause the ConfigurationException.
        f2.close();

        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);
        // Running a query on the file with the invalid header config should cause the ConfigurationException
        connector.executeQuery(ObjectClass.ACCOUNT, new FilteredResultsHandler.PassThroughFilter(),
                new ResultsHandler() {
                    public boolean handle(ConnectorObject connectorObject) {
                        return true;
                    }
                }, null);
    }
}
