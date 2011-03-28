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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import com.forgerock.openicf.xml.XMLConfiguration;
import com.forgerock.openicf.xml.XMLConnector;
import com.forgerock.openicf.xml.XMLFilterTranslator;
import com.forgerock.openicf.xml.XMLHandlerImpl;
import com.forgerock.openicf.xml.query.QueryBuilder;
import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.xsdparser.SchemaParser;
import static com.forgerock.openicf.xml.tests.XmlConnectorTestUtil.*;
import java.io.File;
import java.util.Collection;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class DeleteEntryTests {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    // Test filepaths
    //private static final String XML_FILEPATH = "test/xml_store/test.xml";

    private static XMLHandlerImpl handler;

    @BeforeMethod
	public void init() {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(XML_FILEPATH);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);

        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        handler = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
    }

    @AfterMethod
	public void destroy() {
        File xmlFile = new File(XML_FILEPATH);

        if (xmlFile.exists())
            xmlFile.delete();
    }

    @Test
    public void withNonExistingUidShouldThrowException() {
        final String uid = "nonexistingUID";
        final String expectedErrorMessage = "Deleting entry failed. Could not find an entry of type " + ObjectClass.ACCOUNT_NAME + " with the uid " + uid;

        thrown.expect(UnknownUidException.class);
        thrown.expectMessage(expectedErrorMessage);

        handler.delete(ObjectClass.ACCOUNT, new Uid(uid));
    }

    @Test
    public void withNotSupportedObjectTypeShouldThrowException() {
        ObjectClass objClass = new ObjectClass("nonExistingObject");
        Uid uid = new Uid("nonexistingUID");
        final String expectedErrorMessage = "Object type: " + objClass.getObjectClassValue() + " is not supported.";

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(expectedErrorMessage);

        handler.delete(objClass, uid);
    }

    @Test
    public void withExistingUidShouldRemoveEntryFromDocument() {

        // Create new account object
        ObjectClass objClass = ObjectClass.ACCOUNT;
        Uid insertedUid = handler.create(objClass, XmlConnectorTestUtil.getRequiredAccountAttributes());

        // Create search query
        Collection<ConnectorObject> searchResults = null;
        XMLFilterTranslator translator = new XMLFilterTranslator();
        AttributeBuilder builder = new AttributeBuilder();
        builder.setName(Uid.NAME);
        builder.addValue(insertedUid.getUidValue());

        EqualsFilter equals = new EqualsFilter(builder.build());
        Query query = translator.createEqualsExpression(equals, false);
        QueryBuilder queryBuilder = new QueryBuilder(query, objClass);
        XmlConnectorTestUtil.TestResultsHandler resultsHandler = new XmlConnectorTestUtil.TestResultsHandler();

        searchResults = handler.search(queryBuilder.toString(), objClass);
        resultsHandler.setResults(searchResults);

        // Check if the account exist in the document
        AssertJUnit.assertEquals(1, resultsHandler.getResultSize());

        // Delete account
        handler.delete(objClass, insertedUid);

        searchResults = handler.search(queryBuilder.toString(), objClass);
        resultsHandler.setResults(searchResults);

        // Check if account got deleted
        AssertJUnit.assertEquals(0, resultsHandler.getResultSize());
    }
}