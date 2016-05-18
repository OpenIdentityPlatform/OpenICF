/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for
 * the specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file
 * and include the License file at legal/CDDLv1.0.txt. If applicable, add the following
 * below the CDDL Header, with the fields enclosed by brackets [] replaced by your
 * own identifying information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2010-2016 ForgeRock AS.
 * Portions Copyrighted 2011 Viliam Repan (lazyman)
 */
package org.forgerock.openicf.csvfile;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.impl.api.local.operations.FilteredResultsHandler;
import org.identityconnectors.framework.spi.SearchResultsHandler;
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
        config.setCsvFile(TestUtils.getTestFile("four-rows.csv"));
    }

    /**
     * Tests that an extra delimiter at the end of the header line will be captured as a ConfigurationException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testEmptyHeader() throws Exception {
        File dest = TestUtils.getTestFile("empty-header.csv");
        TestUtils.copyAndReplace(config.getCsvFile(), dest);
        config.setCsvFile(dest);
        config.setFieldDelimiter("*");
        config.setHeaderUid("uid");
        config.setHeaderPassword(OperationalAttributes.PASSWORD_NAME);

        try {
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
        } finally {
            config.getCsvFile().delete();
        }
    }

    @Test
    public void testPaging() throws Exception {
        QueryRunner queryRunner = new QueryRunner();
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile("thirteen-rows.csv"));
        CSVFileConnector csvFileConnector = new CSVFileConnector();
        csvFileConnector.init(config);
        // set page size to 5 records.
        int pageSize = 5;
        OperationOptionsBuilder options = new OperationOptionsBuilder().setPageSize(pageSize);

        // Call the query for the first time getting the first page of objects.
        queryRunner.executeQuery(csvFileConnector, options);
        assertEquals(queryRunner.result.getRemainingPagedResults(), 13 - pageSize, "remaining rows not right");
        assertEquals(queryRunner.result.getPagedResultsCookie(), Integer.toString(pageSize),
                "First page index offset should be equal to the pageSize which is " + pageSize);
        assertEquals(queryRunner.result.getTotalPagedResults(), 13, "Expected total record count should be 13");

        // Call the query again to get the second page of data.
        options.setPagedResultsCookie(queryRunner.result.getPagedResultsCookie());
        queryRunner.executeQuery(csvFileConnector, options);
        assertEquals(queryRunner.result.getRemainingPagedResults(), 13 - pageSize * 2, "remaining rows not right");
        assertEquals(queryRunner.result.getPagedResultsCookie(), "" + pageSize * 2,
                "Second page index offset should now be twice pageSize");
        assertEquals(queryRunner.result.getTotalPagedResults(), 13, "Expected total record count should be 13");

        // Call the query again to get the third and final partial set.
        options.setPagedResultsCookie(queryRunner.result.getPagedResultsCookie());
        queryRunner.executeQuery(csvFileConnector, options);
        assertEquals(queryRunner.records.size(),3,"third partial page should have 3 records.");
        assertEquals(queryRunner.result.getRemainingPagedResults(), 0,
                "After third query, Expected remain row count should be 0");
        assertEquals(queryRunner.result.getPagedResultsCookie(), null,
                "Third page index offset should now be null as it is the last page");
        assertEquals(queryRunner.result.getTotalPagedResults(), 13, "Expected total record count should be 13");
    }

    private class QueryRunner {
        SearchResult result;
        Set<ConnectorObject> records = new HashSet<ConnectorObject>();

        private void executeQuery(CSVFileConnector csvFileConnector, OperationOptionsBuilder options) {
            records.clear();
            csvFileConnector.executeQuery(ObjectClass.ACCOUNT, new FilteredResultsHandler.PassThroughFilter(),
                    new SearchResultsHandler() {
                        public void handleResult(SearchResult result) {
                            QueryRunner.this.result = result;
                        }

                        public boolean handle(ConnectorObject connectorObject) {
                            records.add(connectorObject);
                            return true;
                        }
                    }, options.build());
        }
    }
}
