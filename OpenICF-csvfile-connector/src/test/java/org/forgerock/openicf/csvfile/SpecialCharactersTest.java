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
 * Portions Copyrighted 2012 Viliam Repan (lazyman)
 *
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ResultsHandler;

import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Viliam Repan (lazyman)
 */
public class SpecialCharactersTest {

    private CSVFileConfiguration config;
    private CSVFileConnector connector;

    @Test
    public void testSpecialCharacters() throws Exception {
        final List<ConnectorObject> results = new ArrayList<ConnectorObject>();
        ResultsHandler handler = new ResultsHandler() {

            @Override
            public boolean handle(ConnectorObject co) {
                results.add(co);
                return true;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);
        testResults(results);
    }

    private void testResults(List<ConnectorObject> results) {
        assertEquals(3, results.size());

        testEntryOne(results.get(0));
        testEntryTwo(results.get(1));
        testEntryThree(results.get(2));
        //testEntryThree(results.get(3));  Currently not supported
    }

    @Test(enabled = false)
    private void testEntryOne(ConnectorObject object) {
        assertNotNull(object);
        assertNotNull(object.getUid());
        assertEquals("mmouse", object.getUid().getUidValue());
        assertEquals(object.getAttributes().size(), 7);
        testAttribute(object, "__NAME__", "mmouse");
        testAttribute(object, "__UID__", "mmouse");
        testAttribute(object, "firstName", "Mickey");
        testAttribute(object, "lastName", "Mouse");
        testAttribute(object, "__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        testAttribute(object, "specialCharacter", "embedded\"doublequote");
    }

    @Test(enabled = false)
    private void testEntryTwo(ConnectorObject object) {
        assertNotNull(object);
        assertNotNull(object.getUid());
        assertEquals("dduck", object.getUid().getUidValue());
        assertEquals(object.getAttributes().size(), 7);
        testAttribute(object, "__NAME__", "dduck");
        testAttribute(object, "__UID__", "dduck");
        testAttribute(object, "firstName", "Daffy");
        testAttribute(object, "lastName", "Duck");
        testAttribute(object, "__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        testAttribute(object, "specialCharacter", "embedded,delimiter");
    }

    @Test(enabled = false)
    private void testEntryThree(ConnectorObject object) {
        assertNotNull(object);
        assertNotNull(object.getUid());
        assertEquals("rrabbit", object.getUid().getUidValue());
        assertEquals(object.getAttributes().size(), 7);
        testAttribute(object, "__NAME__", "rrabbit");
        testAttribute(object, "__UID__", "rrabbit");
        testAttribute(object, "firstName", "Roger");
        testAttribute(object, "lastName", "Rabbit");
        testAttribute(object, "__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        testAttribute(object, "specialCharacter", "embedded'singlequote");
    }
    
    @Test(enabled = false)
    private void testEntryFour(ConnectorObject object) {
        assertNotNull(object);
        assertNotNull(object.getUid());
        assertEquals("rrabbit", object.getUid().getUidValue());
        assertEquals(object.getAttributes().size(), 7);
        testAttribute(object, "__NAME__", "rrabbit");
        testAttribute(object, "__UID__", "rrabbit");
        testAttribute(object, "firstName", "Roger");
        testAttribute(object, "lastName", "Rabbit");
        testAttribute(object, "__PASSWORD__", new GuardedString("Z29vZA==".toCharArray()));
        testAttribute(object, "specialCharacter", "embedded\nnewline");
    }
    
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

    @BeforeMethod
    private void before()
            throws IOException, URISyntaxException {

        config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFieldDelimiter(",");
        config.setValueQualifier("\"");
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");
        config.setFilePath(TestUtils.getTestFile("special-characters.csv"));
        
        connector = new CSVFileConnector();
        connector.init(config); 
    }

    @AfterMethod
    private void after() {
        connector.dispose();
        connector = null;
    }
}
