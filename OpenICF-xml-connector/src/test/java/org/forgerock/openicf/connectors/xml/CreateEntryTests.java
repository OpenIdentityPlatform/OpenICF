/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS. All Rights Reserved
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
 */
package org.forgerock.openicf.connectors.xml;

import java.net.URISyntaxException;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import org.testng.Assert;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

public class CreateEntryTests {

    // Test filepaths
    //private static final String XML_FILEPATH = "test/xml_store/test.xml";
    private static XMLHandler handler;

    @BeforeMethod
    public void init() throws URISyntaxException {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(getRandomXMLFile());
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        config.setCreateFileIfNotExists(true);
        config.validate();
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        handler = new ConcurrentXMLHandler(config, parser.parseSchema(), parser.getXsdSchema());
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
    public void withoutNameAttributeDefinedShouldThrowException() {
        final String expectedErrorMessage = Name.NAME + " must be defined.";

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(ObjectClass.ACCOUNT, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void containingAttributesFlaggedAsNonCreateableShouldThrowException() {
        final String expectedErrorMessage = ATTR_ACCOUNT_IS_DELETED + " is not a creatable field.";

        Set<Attribute> attrSet = getRequiredAccountAttributes();
        attrSet.add(AttributeBuilder.build(ATTR_ACCOUNT_IS_DELETED, true));

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(ObjectClass.ACCOUNT, attrSet);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withMissingRequiredFieldShouldThrowException() {
        final String expectedErrorMessage = "Missing required field: " + ATTR_PASSWORD;

        Map<String, Attribute> requiredMap = convertToAttributeMap(getRequiredAccountAttributes());
        requiredMap.remove(ATTR_PASSWORD);

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(ObjectClass.ACCOUNT, convertToAttributeSet(requiredMap));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withBlankRequiredFieldShouldThrowException() {
        final String expectedErrorMessage = "Parameter '" + ATTR_PASSWORD + "' must not be blank.";

        Map<String, Attribute> requiredMap = convertToAttributeMap(getRequiredAccountAttributes());
        requiredMap.remove(ATTR_PASSWORD);
        requiredMap.put(ATTR_PASSWORD, AttributeBuilder.build(ATTR_PASSWORD, new GuardedString(new String("").toCharArray())));

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(ObjectClass.ACCOUNT, convertToAttributeSet(requiredMap));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void withIllegalAttributeTypeShouldThrowException() {
        final String expectedErrorMessage = ATTR_ACCOUNT_FIRST_NAME + " contains invalid type. Value(s) should be of type java.lang.String";

        Set<Attribute> attrSet = getRequiredAccountAttributes();

        // Expected type is String
        attrSet.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, 20.0));

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(ObjectClass.ACCOUNT, attrSet);
    }

    @Test
    public void shouldReturnUid() {
        Uid insertedUid = handler.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes());
        Assert.assertNotNull(insertedUid.getUidValue());
    }

    @Test
    public void shouldReturnNameAsUidWhenUidIsNotImplementedInSchema() {
        Set<Attribute> attrSet = new HashSet<Attribute>();

        attrSet.add(AttributeBuilder.build(ATTR_NAME, ATTR_GROUP_VALUE_NAME));
        attrSet.add(AttributeBuilder.build(ATTR_DESCRIPTION, ATTR_GROUP_VALUE_DESCRIPTION));
        attrSet.add(AttributeBuilder.build(ATTR_SHORT_NAME, ATTR_GROUP_VALUE_SHORT_NAME));

        Uid insertedUid = handler.create(ObjectClass.GROUP, attrSet);
        AssertJUnit.assertEquals(insertedUid.getUidValue(), AttributeUtil.getNameFromAttributes(attrSet).getNameValue());
    }

    @Test
    public void shouldReturnRandomGeneratedUidWhenUidIsImplementedInSchema() {
        Name name = AttributeUtil.getNameFromAttributes(getRequiredAccountAttributes());
        Set<Attribute> attrSet = getRequiredAccountAttributes();

        Uid uid = handler.create(ObjectClass.ACCOUNT, attrSet);
        AssertJUnit.assertNotSame(uid, name.getNameValue());
    }

    @Test(expectedExceptions = AlreadyExistsException.class)
    public void withExistingIdShouldThrowException() {
        final String uid = AttributeUtil.getNameFromAttributes(getRequiredAccountAttributes()).getNameValue();
        final String expectedErrorMessage = "Could not create entry. An entry with the " + Uid.NAME + " of " + uid + " already exists.";

        handler.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes());

        //thrown.expectMessage(expectedErrorMessage);

        handler.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes());
    }
}
