/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.connectors.xml;

import static org.forgerock.openicf.connectors.xml.XMLHandlerIndexTests.describe;
import static org.forgerock.openicf.connectors.xml.XMLHandlerIndexTests.equalsQuery;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.forgerock.openicf.connectors.xml.query.QueryBuilder;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import org.w3c.dom.NodeList;

/** Update, delete and authenticate use an entry's own child elements, whatever their prefix, and no others (#182). */
public class XMLHandlerOwnElementsTests {

    /** Entries below another element and entries inside another entry's value: XSD-invalid hand edits. */
    private static final String[] NESTED = {
            "<ri:__ACCOUNT__><icf:__UID__>u1</icf:__UID__><icf:__NAME__>nopass</icf:__NAME__>"
                    + "<ri:lastname>L1</ri:lastname></ri:__ACCOUNT__>",
            "<ri:wrapper><ri:__ACCOUNT__><icf:__UID__>u8</icf:__UID__><icf:__NAME__>nested</icf:__NAME__>"
                    + "<icf:__PASSWORD__>p8</icf:__PASSWORD__><ri:lastname>L8</ri:lastname></ri:__ACCOUNT__></ri:wrapper>",
            "<ri:__ACCOUNT__><icf:__UID__>u11</icf:__UID__><icf:__NAME__>holder</icf:__NAME__><ri:lastname>"
                    + "<ri:__ACCOUNT__><icf:__UID__>u12</icf:__UID__><icf:__NAME__>inner</icf:__NAME__>"
                    + "<icf:__PASSWORD__>p12</icf:__PASSWORD__></ri:__ACCOUNT__></ri:lastname></ri:__ACCOUNT__>",
            "<ri:__ACCOUNT__><ri:lastname><ri:__ACCOUNT__><icf:__UID__>u13</icf:__UID__>"
                    + "<icf:__NAME__>inner-uid</icf:__NAME__></ri:__ACCOUNT__></ri:lastname>"
                    + "<icf:__NAME__>no-own-uid</icf:__NAME__><icf:__PASSWORD__>p14</icf:__PASSWORD__></ri:__ACCOUNT__>",
            "<ri:__ACCOUNT__><ri:lastname><ri:__ACCOUNT__><icf:__NAME__>inner-name</icf:__NAME__></ri:__ACCOUNT__>"
                    + "</ri:lastname><icf:__NAME__>outer-name</icf:__NAME__><icf:__PASSWORD__>p15</icf:__PASSWORD__>"
                    + "</ri:__ACCOUNT__>",
    };

    /** The ICF namespace under the prefix x, and a lastname in no namespace. */
    private static final String[] PREFIXED = {
            "<ri:__ACCOUNT__><x:__UID__>u20</x:__UID__><x:__NAME__>pfx</x:__NAME__><lastname>L20</lastname>"
                    + "<x:__PASSWORD__>p20</x:__PASSWORD__></ri:__ACCOUNT__>",
            "<ri:__ACCOUNT__><x:__NAME__>pfx-no-uid</x:__NAME__><x:__PASSWORD__>p21</x:__PASSWORD__></ri:__ACCOUNT__>",
    };

    private File file;
    private XMLHandlerImpl handler;

    @Test
    public void updateOfAnEntryHoldingAnotherChangesOnlyItsOwnElements() throws Exception {
        open("icf", NESTED);
        Uid uid = handler.update(ObjectClass.ACCOUNT, new Uid("u11"),
                Collections.singleton(AttributeBuilder.build(Name.NAME, "renamed")));
        assertEquals(uid, new Uid("u11"));
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "renamed"), ObjectClass.ACCOUNT)),
                List.of("u11|renamed"));
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "inner"), ObjectClass.ACCOUNT)),
                List.of("u12|inner"));
        handler.dispose();
        // The new name follows the lastname that holds u12.
        assertEquals(namesInFile(file),
                List.of("nopass", "nested", "inner", "renamed", "inner-uid", "no-own-uid", "inner-name", "outer-name"));
    }

    @Test
    public void deleteOfAnEntryBelowAnotherElementRemovesIt() throws Exception {
        open("icf", NESTED);
        handler.delete(ObjectClass.ACCOUNT, new Uid("u8"));
        handler.delete(ObjectClass.ACCOUNT, new Uid("u12"));
        List<String> left = describe(handler.search(allAccounts(), ObjectClass.ACCOUNT));
        assertFalse(left.contains("u8|nested"), left.toString());
        assertFalse(left.contains("u12|inner"), left.toString());
        assertTrue(left.contains("u11|holder"), left.toString());
        handler.dispose();
        assertEquals(namesInFile(file),
                List.of("nopass", "holder", "inner-uid", "no-own-uid", "inner-name", "outer-name"));
    }

    @Test
    public void updateReplacesIcfElementsUnderAnotherPrefix() throws Exception {
        open("x", PREFIXED);
        handler.update(ObjectClass.ACCOUNT, new Uid("u20"), Collections.singleton(AttributeBuilder.build(Name.NAME, "pfx2")));
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "pfx"), ObjectClass.ACCOUNT)), List.of());
        handler.dispose();
        assertEquals(namesInFile(file), List.of("pfx2", "pfx-no-uid"));
    }

    @Test
    public void updateReplacesAnUnqualifiedAttribute() throws Exception {
        open("x", PREFIXED);
        handler.update(ObjectClass.ACCOUNT, new Uid("u20"),
                Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "NEW")));
        handler.dispose();
        assertEquals(lastNamesInFile(file), List.of("NEW"));
    }

    @Test
    public void authenticateReadsOnlyTheEntrysOwnPassword() throws Exception {
        open("icf", NESTED);
        assertNull(authenticate("holder", "p12")); // p12 is the password of u12, inside holder's lastname
        assertEquals(authenticate("inner", "p12"), new Uid("u12"));
        assertEquals(authenticate("nested", "p8"), new Uid("u8"));
    }

    @Test
    public void authenticateWithoutAPasswordElementFails() throws Exception {
        open("icf", NESTED);
        assertNull(authenticate("nopass", "x"));
    }

    @Test
    public void authenticateReturnsTheEntrysOwnIdentifier() throws Exception {
        open("icf", NESTED);
        // Neither entry has a __UID__ of its own, so the uid is its own __NAME__, not one inside its lastname.
        assertEquals(authenticate("no-own-uid", "p14"), new Uid("no-own-uid"));
        assertEquals(authenticate("outer-name", "p15"), new Uid("outer-name"));
    }

    @Test
    public void authenticateFindsIcfElementsUnderAnotherPrefix() throws Exception {
        open("x", PREFIXED);
        assertEquals(authenticate("pfx", "p20"), new Uid("u20"));
        assertNull(authenticate("pfx", "wrong"));
        assertEquals(authenticate("pfx-no-uid", "p21"), new Uid("pfx-no-uid"));
    }

    private Uid authenticate(String username, String password) {
        return handler.authenticate(username, new GuardedString(password.toCharArray()));
    }

    /** Writes a store with {@code entries} under a container whose ICF prefix is {@code icfPrefix}, and loads it. */
    private void open(String icfPrefix, String... entries) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<").append(icfPrefix).append(":OpenICFContainer xmlns:").append(icfPrefix).append("=\"")
                .append(ICF_NAMESPACE).append("\" xmlns:ri=\"").append(RI_NAMESPACE).append("\">\n");
        for (String entry : entries) {
            xml.append("  ").append(entry).append("\n");
        }
        xml.append("</").append(icfPrefix).append(":OpenICFContainer>\n");
        file = getRandomXMLFile();
        Files.write(file.toPath(), xml.toString().getBytes(StandardCharsets.UTF_8));
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(file);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        handler = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
        handler.init();
    }

    private static String allAccounts() {
        return new QueryBuilder(null, ObjectClass.ACCOUNT).toString();
    }

    /** The lastname values in the file, in any namespace, in document order. */
    private static List<String> lastNamesInFile(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        NodeList lastNames = factory.newDocumentBuilder().parse(file).getElementsByTagNameNS("*", ATTR_ACCOUNT_LAST_NAME);
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < lastNames.getLength(); i++) {
            result.add(lastNames.item(i).getTextContent());
        }
        return result;
    }
}
