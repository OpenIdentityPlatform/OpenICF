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
 * Portions Copyrighted 2026 3A Systems, LLC
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

public class SchemaParserTests {

    SchemaParser parser;

    @BeforeMethod
    public void setUp() {
        parser = new SchemaParser(XMLConnector.class, XmlConnectorTestUtil.XSD_SCHEMA_FILEPATH);
    }

    @Test
    public void instanceShouldNotBeNull() {
        AssertJUnit.assertNotNull(parser);
    }

    @Test(expectedExceptions = ConnectorIOException.class)
    public void withInvalidFilePathShouldThrowException() {
        SchemaParser parserTest = new SchemaParser(XMLConnector.class, new File("test/xml_store/404"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void withEmptyFilePathShouldThrowException() {
        SchemaParser parserTest = new SchemaParser(XMLConnector.class, null);

    }

    @Test(expectedExceptions = NullPointerException.class)
    public void withNullShouldThrowException() {
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

    @Test
    public void parseSchemaShouldSkipSimpleTypedElement() throws IOException {
        final File dir = Files.createTempDirectory("xsd").toFile();
        try {
            // the schema imports its neighbour by a relative location
            Files.copy(XmlConnectorTestUtil.ICF_SCHEMA_FILEPATH.toPath(),
                    new File(dir, XmlConnectorTestUtil.ICF_SCHEMA_FILEPATH.getName()).toPath());
            final String xsd = new String(Files.readAllBytes(
                    XmlConnectorTestUtil.XSD_SCHEMA_FILEPATH.toPath()), StandardCharsets.UTF_8);
            AssertJUnit.assertTrue(xsd.contains("</xsd:schema>"));
            final File withSimpleType = new File(dir, "with-simple-type.xsd");
            Files.write(withSimpleType.toPath(), xsd.replace("</xsd:schema>",
                    "<xsd:element name=\"note\" type=\"xsd:string\"/>\n</xsd:schema>")
                    .getBytes(StandardCharsets.UTF_8));

            final Schema schema = new SchemaParser(XMLConnector.class, withSimpleType).parseSchema();

            AssertJUnit.assertNull(schema.findObjectClassInfo("note"));
            AssertJUnit.assertEquals(parser.parseSchema(), schema);
        } finally {
            IOUtil.delete(dir);
        }
    }
}
