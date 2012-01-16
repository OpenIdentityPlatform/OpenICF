/*
 *
 * Copyright (c) 2010-2012 ForgeRock Inc. All Rights Reserved
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
import com.forgerock.openicf.xml.XMLHandler;
import com.forgerock.openicf.xml.XMLHandlerImpl;
import com.forgerock.openicf.xml.query.QueryBuilder;
import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.xsdparser.SchemaParser;
import static com.forgerock.openicf.xml.tests.XmlConnectorTestUtil.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.Assert;

public class UpdateEntryTests {

    //private static final String XML_FILEPATH = "test/xml_store/test.xml";
    private static XMLHandler handler;

    @BeforeMethod
    public void init() {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(getRandomXMLFile());
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        config.setCreateFileIfNotExists(true);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        handler = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
        handler.init();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withNonSupportedObjectTypeShouldThrowException() {
        final String objectType = "NonExistingObject";
        final String expectedErrorMessage = objectType + " is not supported.";

        ObjectClass objClass = new ObjectClass(objectType);

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(objClass, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withNotSupportedAttributeShouldThrowException() {
        final String notSupportedAttribute = "notSupported";
        final String expectedErrorMessage = "Data field: " + notSupportedAttribute + " is not supported.";

        // Setup account
        Uid insertedUid = createTestAccount();

        //thrown.expectMessage(expectedErrorMessage);

        Set<Attribute> newAttributes = new HashSet();
        newAttributes.add(AttributeBuilder.build(notSupportedAttribute, "arg"));

        handler.update(ObjectClass.ACCOUNT, insertedUid, newAttributes);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void containingAttributesFlaggedAsNonUpdateableShouldThrowException() {
        final String expectedErrorMessage = ATTR_ACCOUNT_IS_DELETED + " is not updatable.";

        // Setup account
        Uid insertedUid = createTestAccount();

        //thrown.expectMessage(expectedErrorMessage);

        Set<Attribute> newAttributes = new HashSet();
        newAttributes.add(AttributeBuilder.build(ATTR_ACCOUNT_IS_DELETED, "true"));

        handler.update(ObjectClass.ACCOUNT, insertedUid, newAttributes);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withBlankValueForRequiredFieldShouldThrowException() {
        final String expectedErrorMessage = "Parameter '" + ATTR_ACCOUNT_LAST_NAME + "' must not be blank.";

        // Setup account
        Uid insertedUid = createTestAccount();

        //thrown.expectMessage(expectedErrorMessage);

        Set<Attribute> newAttributes = new HashSet();
        newAttributes.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, ""));

        handler.update(ObjectClass.ACCOUNT, insertedUid, newAttributes);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withNullValueForRequiredFieldShouldThrowException() {
        final String expectedErrorMessage = "No values provided for required attribute: " + ATTR_ACCOUNT_LAST_NAME;

        // Setup account
        Uid insertedUid = createTestAccount();

        //thrown.expectMessage(expectedErrorMessage);

        Set<Attribute> newAttributes = new HashSet();
        newAttributes.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME));
        handler.update(ObjectClass.ACCOUNT, insertedUid, newAttributes);
    }

    @Test
    public void withExistingAttributeContainingNoValuesShouldNotThrowException() {

        // Setup account
        Uid insertedUid = createTestAccount();

        Set<Attribute> attrWithoutValue = new HashSet();
        attrWithoutValue.add(AttributeBuilder.build(ATTR_ACCOUNT_EMPLOYEE_TYPE));
        handler.update(ObjectClass.ACCOUNT, insertedUid, attrWithoutValue);

        EqualsFilter equalsFilter = new EqualsFilter(AttributeBuilder.build(ATTR_NAME, ATTR_ACCOUNT_VALUE_NAME));
        XMLFilterTranslator filterTranslator = new XMLFilterTranslator(true);

        Query equalsQuery = filterTranslator.createEqualsExpression(equalsFilter, false);
        QueryBuilder queryBuilder = new QueryBuilder(equalsQuery, ObjectClass.ACCOUNT);

        List<ConnectorObject> results = (List) handler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);
        ConnectorObject connectorObject = results.get(0);

        Assert.assertNull(connectorObject.getAttributeByName(ATTR_ACCOUNT_EMPLOYEE_TYPE));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withAttributeContainingValuesOfIllegalTypeShouldThrowException() {
        final String expectedErrorMessage = ATTR_ACCOUNT_MS_EMPLOYED + " contains values of illegal type";

        // Setup account
        Uid insertedUid = createTestAccount();

        //thrown.expectMessage(expectedErrorMessage);

        Set<Attribute> newAttributes = new HashSet();
        newAttributes.add(AttributeBuilder.build(ATTR_ACCOUNT_MS_EMPLOYED, "1234"));

        handler.update(ObjectClass.ACCOUNT, insertedUid, newAttributes);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void withNonExistingUidShouldThrowException() {
        final ObjectClass objClass = ObjectClass.ACCOUNT;
        final String uid = "nonexisting";
        final String expectedErrorMessage = "Could not update entry. No entry of type "
                + objClass.getObjectClassValue() + " with the id " + uid + " found.";

        //thrown.expectMessage(expectedErrorMessage);

        Set<Attribute> newAttributes = new HashSet();
        handler.update(objClass, new Uid("nonexisting"), newAttributes);
    }

    @Test
    public void shouldReturnUid() {
        // Setup account
        Uid insertedUid = createTestAccount();

        Set<Attribute> attrSet = new HashSet<Attribute>();
        attrSet.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "James"));

        Uid updatedUid = handler.update(ObjectClass.ACCOUNT, insertedUid, attrSet);

        AssertJUnit.assertEquals(insertedUid.getUidValue(), updatedUid.getUidValue());
    }

    @Test
    public void shouldUpdateFieldsInDocument() {
        final String firstName = "James";
        final String lastName = "Bond";

        ObjectClass objClass = ObjectClass.ACCOUNT;

        // Setup account
        Uid insertedUid = createTestAccount();

        Set<Attribute> attrSet = new HashSet<Attribute>();
        attrSet.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, firstName));
        attrSet.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, lastName));

        // Update account
        handler.update(objClass, insertedUid, attrSet);

        // Create search query
        XMLFilterTranslator translator = new XMLFilterTranslator(true);
        AttributeBuilder builder = new AttributeBuilder();
        builder.setName(Uid.NAME);
        builder.addValue(insertedUid.getUidValue());

        EqualsFilter equals = new EqualsFilter(builder.build());
        Query query = translator.createEqualsExpression(equals, false);
        QueryBuilder queryBuilder = new QueryBuilder(query, objClass);

        // Search for account
        List<ConnectorObject> results = new ArrayList<ConnectorObject>(
                handler.search(queryBuilder.toString(), objClass));

        // Check account values
        ConnectorObject connObjAccount = results.get(0);

        AssertJUnit.assertEquals(firstName, AttributeUtil.getStringValue(connObjAccount.getAttributeByName(ATTR_ACCOUNT_FIRST_NAME)));
        AssertJUnit.assertEquals(lastName, AttributeUtil.getStringValue(connObjAccount.getAttributeByName(ATTR_ACCOUNT_LAST_NAME)));
    }

    private Uid createTestAccount() {
        return handler.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes());
    }
}
