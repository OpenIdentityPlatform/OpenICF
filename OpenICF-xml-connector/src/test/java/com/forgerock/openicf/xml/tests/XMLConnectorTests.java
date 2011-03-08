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

import com.forgerock.openicf.xml.XMLConfiguration;
import com.forgerock.openicf.xml.XMLConnector;
import com.forgerock.openicf.xml.XMLFilterTranslator;
import static com.forgerock.openicf.xml.tests.XmlConnectorTestUtil.*;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.junit.*;
import org.junit.rules.ExpectedException;
import static org.junit.Assert.*;


public class XMLConnectorTests {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private XMLConnector connector;
    private XMLConfiguration config;

    //private final static String XML_FILEPATH = "test/xml_store/test.xml";

    @Before
    public void init() {
        config = new XMLConfiguration();
        config.setXmlFilePath(XML_FILEPATH);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        
        connector = new XMLConnector();
        connector.init(config);
    }

    @After
    public void destroy() {
        File xmlFile = new File(XML_FILEPATH);

        if(xmlFile.exists()){
            xmlFile.delete();
        }
    }
    
    @Test(expected=NullPointerException.class)
    public void initMethodShouldCastNullPointerExceptionWhenInitializedWithNull(){
        XMLConnector xMLConnector = new XMLConnector();
        xMLConnector.init(null);
    }

    @Test(expected = NullPointerException.class)
    public void testMethodShouldThrowExceptionWhenMissingRequiredFields() {
        XMLConnector xmlCon = new XMLConnector();
        xmlCon.test();
    }

    @Test
    public void testMethodShouldThrowExceptionWhenGivenInvalidXsdFilePaths() {
        final String icfSchemaLocation = "target/test-classes/test/xml_store/404.xsd";
        final String expectedErrorMessage = "File does not exist at filepath " + icfSchemaLocation;

        XMLConfiguration conf = new XMLConfiguration();

        conf.setXmlFilePath(XML_FILEPATH);
        conf.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        conf.setXsdIcfFilePath(icfSchemaLocation);

        connector.init(conf);
        
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(expectedErrorMessage);

        connector.test();
    }

    @Test
    public void testMethodShouldNotThrowExceptionWithValidConfiguration() {
        connector.test();
    }

    @Test
    public void schemaMethodShouldReturnFrameworkSchemaObject() {
        Schema schema = connector.schema();
        assertNotNull(schema);
    }

    @Test
    public void frameworkSchemaObjectShouldIncludeAccountObjectInformation() {
        Schema schema = connector.schema();
        assertNotNull(schema.findObjectClassInfo(ACCOUNT_TYPE));
    }

    @Test
    public void createFilterTranslatorShouldReturnInitializedFilterTranslatorWhenGivenValidParameters() {
        assertNotNull(connector.createFilterTranslator(ObjectClass.ACCOUNT, null));
    }

    @Test
    public void getConfigurationShouldReturnInitializedConfigurationObject() {
        assertNotNull(connector.getConfiguration());
    }

    @Test
    public void executeQueryAgainstDocumentContainingTwoAccountsWithNullAsQueryStringShouldReturnTwoAccounts() {
        Set<Attribute> attrSetOne = getRequiredAccountAttributes();
        Set<Attribute> attrSetTwo = new HashSet<Attribute>();
        attrSetTwo.add(AttributeBuilder.build(ATTR_NAME, "BondUid"));
        attrSetTwo.add(AttributeBuilder.buildPassword(new String(ATTR_ACCOUNT_VALUE_PASSWORD).toCharArray()));
        attrSetTwo.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Bond"));
        
        connector.create(ObjectClass.ACCOUNT, attrSetOne, null);
        connector.create(ObjectClass.ACCOUNT, attrSetTwo, null);
        
        TestResultsHandler resultsHandler = new TestResultsHandler();
        connector.executeQuery(ObjectClass.ACCOUNT, null, resultsHandler, null);
        assertEquals(2, resultsHandler.getResultSize());
    }

    @Test
    public void executeQueryOnAccountsWhereLastNameEqualsVaderShouldReturnOneResult() {

        // Create account
        connector.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        // Build query string
        XMLFilterTranslator filterTranslator = (XMLFilterTranslator) connector.createFilterTranslator(ObjectClass.ACCOUNT, null);
        EqualsFilter equalsFilter = new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Vader"));
        TestResultsHandler resultsHandler = new TestResultsHandler();

        connector.executeQuery(
                ObjectClass.ACCOUNT, filterTranslator.createEqualsExpression(equalsFilter, false), resultsHandler, null);

        assertEquals(1, resultsHandler.getResultSize());
    }

    @Test(expected=NullPointerException.class)
    public void executeQueryWithNullAsObjectTypeShouldThrowException(){
        connector.executeQuery(null, null, null, null);
    }

    @Test
    public void createShouldReturnUidWhenGivenValidParameters() {
        Uid uid = connector.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        assertNotNull(uid);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullAsObjectTypeShouldThrowException() {
        connector.create(null, getRequiredAccountAttributes(), null);
    }

    @Test(expected = NullPointerException.class)
    public void createWithAttributesSetToNullShouldThrowException() {
        connector.create(ObjectClass.ACCOUNT, null, null);
    }

    @Test
    public void updateShouldReturnUidWhenGivenValidParameters() {
        Uid insertedUid = connector.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        Set<Attribute> attributes = getRequiredAccountAttributes();

        attributes.add(AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, "mailadress1@company.org","mailadress2@company.org","mailadress3@company.org"));

        Uid updatedUid = connector.update(ObjectClass.ACCOUNT, insertedUid, attributes, null);

        assertEquals(insertedUid.getUidValue(), updatedUid.getUidValue());
    }

    @Test(expected = NullPointerException.class)
    public void updateWithNullAsObjectTypeShouldThrowException() {
        connector.update(null, null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void updateWithAttributesSetToNullShouldThrowException() {
        connector.update(ObjectClass.ACCOUNT, null, null, null);
    }

    @Test
    public void deleteAccountFromDocumentContainingOneAccountShouldReturnResultSizeOfZero() {
        Uid insertedUid = connector.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);

        connector.delete(ObjectClass.ACCOUNT, insertedUid, null);

        TestResultsHandler resultsHandler = new TestResultsHandler();
        connector.executeQuery(ObjectClass.ACCOUNT, null, resultsHandler, null);
        assertEquals(0, resultsHandler.getResultSize());
    }

    @Test(expected = NullPointerException.class)
    public void deleteWithNullAsObjectTypeShouldThrowException() {
        connector.delete(null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void deleteWithNullAsUidShouldThrowException() {
        connector.delete(ObjectClass.ACCOUNT, null, null);
    }

    @Test
    public void authenticateShouldReturnUidWhenGivenValidAccountDetails() {
        Uid insertedUid = connector.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);
        System.out.println("UID: " + insertedUid.getUidValue());

        Uid authenticatedUid = connector.authenticate(ObjectClass.ACCOUNT, ATTR_ACCOUNT_VALUE_NAME ,
                new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);

        assertEquals(insertedUid.getUidValue(), authenticatedUid.getUidValue());
    }

    @Test
    public void authenticateShouldThrowExceptionWhenObjectClassIsNotOfTypeAccount() {
        final String expectedErrorMessage = "Authentication failed. Can only authenticate against " + ObjectClass.ACCOUNT_NAME + " resources.";
        
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(expectedErrorMessage);

        connector.authenticate(
                ObjectClass.GROUP, "username", new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);
    }
    
    @Test(expected=NullPointerException.class)
    public void authenticateShouldThrowExceptionWhenUsernameIsNull() {
        connector.authenticate(
                ObjectClass.ACCOUNT, null, new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void authenticateShouldThrowExceptionWhenUsernameIsBlank() {
        connector.authenticate(
                ObjectClass.ACCOUNT, "", new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()), null);
    }
}