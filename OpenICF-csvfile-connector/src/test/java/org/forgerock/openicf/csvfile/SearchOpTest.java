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
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import java.util.List;
import java.util.ArrayList;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 *
 * @author lazyman
 */
public class SearchOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("search.csv"));
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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClass() {
        connector.executeQuery(null, "", new ResultsHandler() {

            @Override
            public boolean handle(ConnectorObject co) {
                throw new UnsupportedOperationException("Not implemented.");
            }
        }, null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badObjectClass() {
        connector.executeQuery(ObjectClass.GROUP, "", new ResultsHandler() {

            @Override
            public boolean handle(ConnectorObject co) {
                throw new UnsupportedOperationException("Not implemented.");
            }
        }, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClassFilter() {
        connector.createFilterTranslator(null, null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badObjectClassFilter() {
        connector.createFilterTranslator(ObjectClass.GROUP, null);
    }

    @Test
    public void createFilterTranslator() {
        FilterTranslator<String> filter = connector.createFilterTranslator(ObjectClass.ACCOUNT, null);
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

            @Override
            public boolean handle(ConnectorObject co) {
                results.add(co);
                return false;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);

        assertEquals(1, results.size());
        testEntryOne(results.get(0));
    }

    @Test
    public void correctAllQuery() {
        final List<ConnectorObject> results = new ArrayList<ConnectorObject>();
        ResultsHandler handler = new ResultsHandler() {

            @Override
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
        System.out.println(object);
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
}
