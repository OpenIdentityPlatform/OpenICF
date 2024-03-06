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
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class SearchOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile("search.csv"));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

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
        connector.executeQuery(null, null, new ResultsHandler() {

            public boolean handle(ConnectorObject co) {
                throw new UnsupportedOperationException("Not implemented.");
            }
        }, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void badObjectClass() {
        connector.executeQuery(ObjectClass.GROUP, null, new ResultsHandler() {

            public boolean handle(ConnectorObject co) {
                throw new UnsupportedOperationException("Not implemented.");
            }
        }, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClassFilter() {
        connector.createFilterTranslator(null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void badObjectClassFilter() {
        connector.createFilterTranslator(ObjectClass.GROUP, null);
    }

    @Test
    public void createFilterTranslator() {
        FilterTranslator<Filter> filter = connector.createFilterTranslator(ObjectClass.ACCOUNT, null);
        assertNotNull(filter);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullHandler() {
        connector.executeQuery(ObjectClass.ACCOUNT, null, null, null);
    }

    @Test
    public void correctButStoppedHandler() {
        final List<ConnectorObject> results = new ArrayList<ConnectorObject>();
        ResultsHandler handler = new ResultsHandler() {

            public boolean handle(ConnectorObject co) {
                results.add(co);
                return false;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);

        assertEquals(results.size(), 1);
        testEntryOne(results.get(0));
    }

    @Test
    public void correctAllQuery() {
        final List<ConnectorObject> results = new ArrayList<ConnectorObject>();
        ResultsHandler handler = new ResultsHandler() {

            public boolean handle(ConnectorObject co) {
                results.add(co);
                return true;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);

        assertEquals(2, results.size());
        testEntryOne(results.get(0));
        testEntryTwo(results.get(1));
    }

    @Test(enabled = false)
    private void testEntryOne(ConnectorObject object) {
        assertNotNull(object);
        assertNotNull(object.getUid());
        assertEquals("vilo", object.getUid().getUidValue());
        assertEquals(object.getAttributes().size(), 6);
        testAttribute(object, "__NAME__", "vilo");
        testAttribute(object, "__UID__", "vilo");
        testAttribute(object, "uid", "vilo");
        testAttribute(object, "firstName", "viliam");
        testAttribute(object, "lastName", "repan");
        testAttribute(object, "__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
    }

    @Test(enabled = false)
    private void testEntryTwo(ConnectorObject object) {
        assertNotNull(object);
        assertNotNull(object.getUid());
        assertEquals("miso", object.getUid().getUidValue());
        assertEquals(object.getAttributes().size(), 5);
        testAttribute(object, "__NAME__", "miso");
        testAttribute(object, "__UID__", "miso");
        testAttribute(object, "uid", "miso"); 
        testAttribute(object, "firstName", "michal");
        testAttribute(object, "lastName");
        testAttribute(object, "__PASSWORD__", new GuardedString("bad=".toCharArray()));
    }

    @Test(enabled = false)
    private void testAttribute(ConnectorObject object, String name, Object... values) {
        Attribute attribute = object.getAttributeByName(name);
        if (values.length == 0) {
            assertNull(attribute);
        } else {
            assertNotNull(attribute, "Attribute '" + name + "' not found.");
            List<Object> attrValues = attribute.getValue();
            assertNotNull(attrValues);
            assertEquals(attrValues.size(), values.length);
            assertEquals(attrValues.toArray(), values, "Attribute '" + name + "' doesn't have same values.");
        }
    }

    @Test(expectedExceptions = Exception.class)
    private void testMissingColumn() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile("missing-column.csv"));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        ResultsHandler handler = new ResultsHandler() {

            public boolean handle(ConnectorObject co) {
                return true;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);
    }

    @Test(expectedExceptions = ConnectorIOException.class)
    private void nonExistingFile() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(new File("C:\\non-existing-file.csv"));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        ResultsHandler handler = new ResultsHandler() {

            public boolean handle(ConnectorObject co) {
                return true;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);
    }

    @Test
    private void testPaging() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile("thirteen-rows.csv"));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        final List<SearchResult> result = new ArrayList<SearchResult>();

        ResultsHandler handler = new SearchResultsHandler() {

            public boolean handle(ConnectorObject co) {
                return true;
            }

            public void handleResult(SearchResult res) {
                result.add(res);
            }
        };

        OperationOptions options = OperationOptionsBuilder.create().setPageSize(3).build();
        connector.executeQuery(ObjectClass.ACCOUNT, FilterBuilder.present("firstName"), handler, options);
        assertEquals(result.get(0).getTotalPagedResults(), 13);
        assertEquals(result.get(0).getRemainingPagedResults(), 10);
        assertNotNull(result.get(0).getPagedResultsCookie());

        options = OperationOptionsBuilder.create()
                .setPageSize(3)
                .setPagedResultsCookie(result.get(0).getPagedResultsCookie())
                .build();
        connector.executeQuery(ObjectClass.ACCOUNT, FilterBuilder.present("firstName"), handler, options);
        assertEquals(result.get(1).getTotalPagedResults(), 13);
        assertEquals(result.get(1).getRemainingPagedResults(), 7);
        assertNotNull(result.get(1).getPagedResultsCookie());
        assertNotEquals(result.get(0).getPagedResultsCookie(), result.get(1).getPagedResultsCookie());
    }
}
