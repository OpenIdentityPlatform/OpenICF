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
 * Portions Copyrighted 2026 3A Systems, LLC
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml;

import org.testng.annotations.Test;
import org.testng.AssertJUnit;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import static org.testng.Assert.expectThrows;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.annotations.BeforeMethod;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XMLConnectorTests {

    //private XMLConnector connector;
    private ConnectorFacade facade;
    private File xmlFile;

    //private final static String XML_FILEPATH = "test/xml_store/test.xml";
    @BeforeMethod
    public void init() {        
        XMLConfiguration config = new XMLConfiguration();
        xmlFile = getRandomXMLFile();
        config.setXmlFilePath(xmlFile);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        config.setCreateFileIfNotExists(true);
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
        EqualsFilter equalsFilter = new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, ATTR_ACCOUNT_VALUE_LAST_NAME));
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

    @Test
    public void newFileDeclaresNamespacesAsBefore() throws Exception {
        facade.create(ObjectClass.ACCOUNT, getRequiredAccountAttributes(), null);
        String content = new String(Files.readAllBytes(xmlFile.toPath()), StandardCharsets.UTF_8);
        int icf = content.indexOf("xmlns:icf=");
        int ri = content.indexOf("xmlns:ri=");
        int xsi = content.indexOf("xmlns:xsi=");
        AssertJUnit.assertTrue(content, 0 < icf && icf < ri && ri < xsi);
    }

    @Test
    public void valuesSurviveASaveAndAReload() throws Exception {
        String lastName = "  a>b&c<d \"q\" 'a' \u00e9\u4e2d \r\n\ttab ]]> \ud83d\ude00  ";
        Set<Attribute> attributes = getRequiredAccountAttributes();
        attributes.remove(AttributeUtil.find(ATTR_ACCOUNT_LAST_NAME, attributes));
        attributes.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, lastName));
        Uid uid = facade.create(ObjectClass.ACCOUNT, attributes, null);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document saved = factory.newDocumentBuilder().parse(xmlFile);
        NodeList inFile = saved.getElementsByTagNameNS("*", ATTR_ACCOUNT_LAST_NAME);
        AssertJUnit.assertEquals(1, inFile.getLength());
        AssertJUnit.assertEquals(lastName, inFile.item(0).getTextContent());

        ConnectorObject read = facade.getObject(ObjectClass.ACCOUNT, uid, null);
        AssertJUnit.assertEquals(lastName,
                AttributeUtil.getStringValue(read.getAttributeByName(ATTR_ACCOUNT_LAST_NAME)));
    }

    @Test
    public void disposeNeverUsesNodeLists() throws Exception {
        String icf = "http://openidm.forgerock.com/xml/ns/public/resource/openicf/resource-schema-1.xsd";
        String ri = "http://openidm.forgerock.com/xml/ns/public/resource/instances/ef2bc95b-76e0-48e2-86d6-4d4f44d4e4a4";
        Files.write(xmlFile.toPath(), ("<icf:OpenICFContainer xmlns:icf='" + icf + "' xmlns:ri='" + ri + "'>\n"
                + "  <ri:__ACCOUNT__>\n    <icf:__NAME__>a</icf:__NAME__>\n  </ri:__ACCOUNT__>\n"
                + "  <ri:__ACCOUNT__>\n    <icf:__NAME__>b</icf:__NAME__>\n  </ri:__ACCOUNT__>\n"
                + "</icf:OpenICFContainer>\n").getBytes(StandardCharsets.UTF_8));
        XMLHandlerImpl handler = newHandler();
        handler.init();
        handler.dispose();
        AssertJUnit.assertFalse(XercesNodeLists.used(handler.getDocument()));
    }

    @Test
    public void whitespaceOnlyValueIsEmptiedBySave() throws Exception {
        Set<Attribute> attributes = getRequiredAccountAttributes();
        attributes.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, " \t "));
        facade.create(ObjectClass.ACCOUNT, attributes, null);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        NodeList inFile = factory.newDocumentBuilder().parse(xmlFile).getElementsByTagNameNS("*", ATTR_ACCOUNT_FIRST_NAME);
        AssertJUnit.assertEquals(1, inFile.getLength());
        AssertJUnit.assertNull(inFile.item(0).getFirstChild());
    }

    @Test
    public void deletingTheLastEntryLeavesAnEmptyContainer() throws Exception {
        // The removed entry leaves two whitespace-only Text nodes side by side; both go.
        writeAccounts(xmlFile, "alice");
        facade.delete(ObjectClass.ACCOUNT, new Uid("uid-alice"), null);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        AssertJUnit.assertNull(factory.newDocumentBuilder().parse(xmlFile).getDocumentElement().getFirstChild());
    }

    @Test
    public void failedSaveIsLoggedAndThrown() throws Exception {
        XMLHandlerImpl handler = newHandler();
        handler.init(); // no file yet: a new document in memory
        AssertJUnit.assertTrue(xmlFile.mkdir()); // a directory cannot be written, whoever runs the test
        try {
            String errors = captureStdErr(() -> expectThrows(ConnectorException.class, handler::dispose));
            AssertJUnit.assertTrue(errors, errors.contains("Failed saving changes to xml file: java.io.FileNotFoundException"));
        } finally {
            AssertJUnit.assertTrue(xmlFile.delete());
        }
    }

    @Test
    public void successfulSaveLogsItsExit() throws Exception {
        XMLHandlerImpl handler = newHandler();
        handler.init(); // no file yet: a new document in memory
        String out = captureStdOut(handler::dispose);
        AssertJUnit.assertTrue(out, out.contains("Exit serialize" + System.lineSeparator()));
    }

    private XMLHandlerImpl newHandler() {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(xmlFile);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        config.setCreateFileIfNotExists(true);
        SchemaParser parser = new SchemaParser(XMLConnector.class, XSD_SCHEMA_FILEPATH);
        return new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
    }

    private static String captureStdOut(Runnable action) throws UnsupportedEncodingException {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8.name()));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static String captureStdErr(Runnable action) throws UnsupportedEncodingException {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8.name()));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }
}
