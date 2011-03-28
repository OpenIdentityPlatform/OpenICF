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

package com.forgerock.openicf.xml.tests;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import com.forgerock.openicf.xml.XMLConnector;
import com.forgerock.openicf.xml.xsdparser.SchemaParser;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import static com.forgerock.openicf.xml.tests.XmlConnectorTestUtil.*;

public class SchemaParserTests {
    SchemaParser parser;

    @BeforeMethod
	public void setUp(){
        parser = new SchemaParser(XMLConnector.class, XSD_SCHEMA_FILEPATH);
    }

    @Test
    public void instanceShouldNotBeNull(){
        AssertJUnit.assertNotNull(parser);
    }

    @Test(expectedExceptions= ConnectorIOException.class)
    public void withInvalidFilePathShouldThrowException(){
        SchemaParser parserTest = new SchemaParser(XMLConnector.class, "test/xml_store/404");
    }

    @Test(expectedExceptions= IllegalArgumentException.class)
    public void withEmptyFilePathShouldThrowException(){
        SchemaParser parserTest = new SchemaParser(XMLConnector.class, "");
    }

    @Test(expectedExceptions= NullPointerException.class)
    public void withNullShouldThrowException(){
        SchemaParser parserTest = new SchemaParser(null, null);
    }

    @Test
    public void getXsdSchemaShouldReturnXsdSchemaSet() {
        AssertJUnit.assertNotNull(parser.getXsdSchema());
    }

    @Test
    public void parseSchemaShouldReturnSchema() {
        AssertJUnit.assertNotNull(parser.parseSchema());
    }
}