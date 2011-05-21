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
import org.testng.AssertJUnit;
import com.forgerock.openicf.xml.XMLConfiguration;
import com.forgerock.openicf.xml.XMLConnector;
import static com.forgerock.openicf.xml.tests.XmlConnectorTestUtil.*;
import java.util.HashSet;
import java.util.Set;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.annotations.BeforeMethod;

public class XMLConnectorTests {

    //private XMLConnector connector;
    private ConnectorFacade facade;

    //private final static String XML_FILEPATH = "test/xml_store/test.xml";
    @BeforeMethod
    public void init() {        
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(getRandomXMLFile());
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        APIConfiguration impl = TestHelpers.createTestConfiguration(XMLConnector.class, config);
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        facade = factory.newInstance(impl);
    }

  

    @Test(expectedExceptions = NullPointerException.class)
    public void initMethodShouldCastNullPointerExceptionWhenInitializedWithNull() {
        XMLConnector xMLConnector = new XMLConnector();
        xMLConnector.init(null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testMethodShouldThrowExceptionWhenMissingRequiredFields() {
        XMLConnector xmlCon = new XMLConnector();
        xmlCon.test();
    }

    //@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "File does not exist at filepath target/test-classes/test/xml_store/404.xsd")
//    public void testMethodShouldThrowExceptionWhenGivenInvalidXsdFilePaths() {
//        final String icfSchemaLocation = "target/test-classes/test/xml_store/404.xsd";
//        final String expectedErrorMessage = "File does not exist at filepath " + icfSchemaLocation;
//
//        XMLConfiguration conf = new XMLConfiguration();
//
//        conf.setXmlFilePath(getRandomXMLFile());
//        conf.setXsdFilePath(XSD_SCHEMA_FILEPATH);
//        //conf.setXsdIcfFilePath(new File(icfSchemaLocation));
//
//        connector.init(conf);
//        connector.test();
//    }

    @Test
    public void testMethodShouldNotThrowExceptionWithValidConfiguration() {
        facade.test();
    }

    @Test
    public void schemaMethodShouldReturnFrameworkSchemaObject() {
        Schema schema = facade.schema();
        AssertJUnit.assertNotNull(schema);
    }

    @Test
    public void frameworkSchemaObjectShouldIncludeAccountObjectInformation() {
        Schema schema = facade.schema();
        AssertJUnit.assertNotNull(schema.findObjectClassInfo(ACCOUNT_TYPE));
    }

    @Test
    public void executeQueryAgainstDocumentContainingTwoAccountsWithNullAsQueryStringShouldReturnTwoAccounts() {
        Set<Attribute> attrSetOne = getRequiredAccountAttributes();
        Set<Attribute> attrSetTwo = new HashSet<Attribute>();
        attrSetTwo.add(AttributeBuilder.build(ATTR_NAME, "BondUid"));
        attrSetTwo.add(AttributeBuilder.buildPassword(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()));
        attrSetTwo.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Bond"));

        facade.create(ObjectClass.ACCOUNT, attrSetOne, null);
        facade.create(ObjectClass.ACCOUNT, attrSetTwo, null);

        TestResultsHandler resultsHandler = new TestResultsHandler();
        facade.search(ObjectClass.ACCOUNT, null, resultsHandler, null);
        AssertJUnit.assertEquals(2, resultsHandler.getResultSize());
    }

    @Test
    public void executeQueryOnAccountsWhereLastNameEqualsVaderShouldReturnOneResult() {

        // Create account
        facade.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        // Build query string
        //XMLFilterTranslator filterTranslator = (XMLFilterTranslator) connector.createFilterTranslator(ObjectClass.ACCOUNT, null);
        EqualsFilter equalsFilter = new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Vader"));
        TestResultsHandler resultsHandler = new TestResultsHandler();

        facade.search(
                ObjectClass.ACCOUNT, equalsFilter, resultsHandler, null);

        AssertJUnit.assertEquals(1, resultsHandler.getResultSize());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void executeQueryWithNullAsObjectTypeShouldThrowException() {
        facade.search(ObjectClass.GROUP, null, null, null);
    }

    @Test
    public void createShouldReturnUidWhenGivenValidParameters() {
        Uid uid = facade.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        AssertJUnit.assertNotNull(uid);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void createWithNullAsObjectTypeShouldThrowException() {
        facade.create(null, getRequiredAccountAttributes(), null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void createWithAttributesSetToNullShouldThrowException() {
        facade.create(ObjectClass.ACCOUNT, null, null);
    }

    @Test
    public void updateShouldReturnUidWhenGivenValidParameters() {
        Uid insertedUid = facade.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        Set<Attribute> attributes = getRequiredAccountAttributes();

        attributes.add(AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, "mailadress1@company.org", "mailadress2@company.org", "mailadress3@company.org"));

        Uid updatedUid = facade.update(ObjectClass.ACCOUNT, insertedUid, attributes, null);

        AssertJUnit.assertEquals(insertedUid.getUidValue(), updatedUid.getUidValue());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void updateWithNullAsObjectTypeShouldThrowException() {
        facade.update(null, null, null, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void updateWithAttributesSetToNullShouldThrowException() {
        facade.update(ObjectClass.ACCOUNT, null, null, null);
    }

    @Test
    public void deleteAccountFromDocumentContainingOneAccountShouldReturnResultSizeOfZero() {
        Uid insertedUid = facade.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        facade.delete(ObjectClass.ACCOUNT, insertedUid, null);

        TestResultsHandler resultsHandler = new TestResultsHandler();

        facade.search(ObjectClass.ACCOUNT, null, resultsHandler, null);
        AssertJUnit.assertEquals(0, resultsHandler.getResultSize());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteWithNullAsObjectTypeShouldThrowException() {
        facade.delete(null, null, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteWithNullAsUidShouldThrowException() {
        facade.delete(ObjectClass.ACCOUNT, null, null);
    }

    @Test
    public void authenticateShouldReturnUidWhenGivenValidAccountDetails() {
        Uid insertedUid = facade.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);
        System.out.println("UID: " + insertedUid.getUidValue());

        Uid authenticatedUid = facade.authenticate(ObjectClass.ACCOUNT, ATTR_ACCOUNT_VALUE_NAME,
                new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);

        AssertJUnit.assertEquals(insertedUid.getUidValue(), authenticatedUid.getUidValue());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void authenticateShouldThrowExceptionWhenObjectClassIsNotOfTypeAccount() {
        final String expectedErrorMessage = "Authentication failed. Can only authenticate against " + ObjectClass.ACCOUNT_NAME + " resources.";

        //thrown.expectMessage(expectedErrorMessage);

        facade.authenticate(ObjectClass.GROUP, "username", new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void authenticateShouldThrowExceptionWhenUsernameIsNull() {
        facade.authenticate(ObjectClass.ACCOUNT, null, new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void authenticateShouldThrowExceptionWhenUsernameIsBlank() {
        facade.authenticate(ObjectClass.ACCOUNT, "", new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);
    }
}
