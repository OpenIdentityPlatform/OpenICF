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

import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.ICF_NAMESPACE;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.RI_NAMESPACE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class EntryIndexTests {

    private static final String ACCOUNT = ObjectClass.ACCOUNT_NAME;
    private static final String GROUP = ObjectClass.GROUP_NAME;
    private static final String NAME = Name.NAME;
    private static final String UID = Uid.NAME;

    @Test
    public void findsEntriesByNameAndUidInDocumentOrder() throws Exception {
        EntryIndex index = index(parse(account("u1", "dup") + account("u2", "x") + account("u3", "dup")));
        assertEquals(uids(index.find(ACCOUNT, NAME, "dup")), Arrays.asList("u1", "u3"));
        assertEquals(uidOf(index.first(ACCOUNT, NAME, "dup")), "u1");
        assertEquals(uidOf(index.first(ACCOUNT, UID, "u2")), "u2");
        assertNull(index.first(ACCOUNT, NAME, "missing"));
        assertTrue(index.find(ACCOUNT, NAME, "missing").isEmpty());
        assertEquals(uids(index.all(ACCOUNT)), Arrays.asList("u1", "u2", "u3"));
    }

    @Test
    public void findsNestedEntriesAndIgnoresEverythingElse() throws Exception {
        EntryIndex index = index(parse("<ri:wrapper>" + account("u1", "a") + "</ri:wrapper>"
                + "<x:__ACCOUNT__><icf:__UID__>u2</icf:__UID__><icf:__NAME__>a</icf:__NAME__></x:__ACCOUNT__>"
                + "<ri:__DEVICE__><icf:__UID__>u3</icf:__UID__><icf:__NAME__>a</icf:__NAME__></ri:__DEVICE__>"
                + "<ri:__account__><icf:__UID__>u5</icf:__UID__><icf:__NAME__>a</icf:__NAME__></ri:__account__>"
                + "<ri:__ACCOUNT__><icf:__UID__>u4</icf:__UID__><ri:x><icf:__NAME__>deep</icf:__NAME__></ri:x></ri:__ACCOUNT__>"));
        assertEquals(uids(index.find(ACCOUNT, NAME, "a")), Arrays.asList("u1"));
        assertNull(index.first(ACCOUNT, NAME, "deep"));
        assertEquals(uids(index.all(ACCOUNT)), Arrays.asList("u1", "u4"));
    }

    @Test
    public void classesAreSeparate() throws Exception {
        EntryIndex index = index(parse(account("u1", "dup")
                + "<ri:__GROUP__><icf:__UID__>g1</icf:__UID__><icf:__NAME__>dup</icf:__NAME__></ri:__GROUP__>"));
        assertEquals(uids(index.find(ACCOUNT, NAME, "dup")), Arrays.asList("u1"));
        assertEquals(uids(index.find(GROUP, NAME, "dup")), Arrays.asList("g1"));
    }

    @Test
    public void everyIdentifierValueFindsTheEntryOnce() throws Exception {
        EntryIndex index = index(parse("<ri:__ACCOUNT__><icf:__UID__>u1</icf:__UID__>"
                + "<icf:__NAME__>a</icf:__NAME__><icf:__NAME__>b</icf:__NAME__><icf:__NAME__>a</icf:__NAME__></ri:__ACCOUNT__>"));
        assertEquals(uids(index.find(ACCOUNT, NAME, "a")), Arrays.asList("u1"));
        assertEquals(uids(index.find(ACCOUNT, NAME, "b")), Arrays.asList("u1"));
    }

    @Test
    public void valuesAreComparedAsIs() throws Exception {
        EntryIndex index = index(parse(account("u1", "  padded  ")
                + "<ri:__ACCOUNT__><icf:__UID__>u2</icf:__UID__><icf:__NAME__><![CDATA[cd]]>ata</icf:__NAME__></ri:__ACCOUNT__>"));
        assertEquals(uids(index.find(ACCOUNT, NAME, "  padded  ")), Arrays.asList("u1"));
        assertTrue(index.find(ACCOUNT, NAME, "padded").isEmpty());
        assertEquals(uids(index.find(ACCOUNT, NAME, "cdata")), Arrays.asList("u2"));
    }

    @Test
    public void rootMustBeTheContainer() throws Exception {
        EntryIndex index = index(parseRaw("<ri:other xmlns:ri='" + RI_NAMESPACE + "' xmlns:icf='" + ICF_NAMESPACE + "'>"
                + account("u1", "a") + "</ri:other>"));
        assertTrue(index.all(ACCOUNT).isEmpty());
    }

    @Test
    public void addedEntryComesLast() throws Exception {
        Document document = parse(account("u1", "dup"));
        EntryIndex index = index(document);
        Element added = (Element) document.importNode(parse(account("u2", "dup")).getDocumentElement().getFirstChild(), true);
        document.getDocumentElement().appendChild(added);
        index.added(added);
        assertEquals(uids(index.find(ACCOUNT, NAME, "dup")), Arrays.asList("u1", "u2"));
        assertEquals(uids(index.all(ACCOUNT)), Arrays.asList("u1", "u2"));
    }

    @Test
    public void removedEntryExposesTheNextDuplicate() throws Exception {
        Document document = parse(account("u1", "dup") + account("u2", "dup"));
        EntryIndex index = index(document);
        Element first = index.first(ACCOUNT, NAME, "dup");
        document.getDocumentElement().removeChild(first);
        index.removed(first);
        assertEquals(uids(index.find(ACCOUNT, NAME, "dup")), Arrays.asList("u2"));
        assertNull(index.first(ACCOUNT, UID, "u1"));
        assertEquals(uids(index.all(ACCOUNT)), Arrays.asList("u2"));
    }

    @Test
    public void removedWrapperTakesItsEntriesAlong() throws Exception {
        Document document = parse("<ri:wrapper>" + account("u1", "a") + "</ri:wrapper>" + account("u2", "b"));
        EntryIndex index = index(document);
        Node wrapper = document.getDocumentElement().getFirstChild();
        document.getDocumentElement().removeChild(wrapper);
        index.removed(wrapper);
        assertNull(index.first(ACCOUNT, NAME, "a"));
        assertEquals(uids(index.all(ACCOUNT)), Arrays.asList("u2"));
    }

    @Test
    public void rekeyedEntryKeepsItsPlaceInDocumentOrder() throws Exception {
        Document document = parse(account("u1", "old") + account("u2", "dup"));
        EntryIndex index = index(document);
        Element first = index.first(ACCOUNT, UID, "u1");
        first.getElementsByTagNameNS(ICF_NAMESPACE, NAME).item(0).setTextContent("dup");
        index.rekeyed(first);
        assertNull(index.first(ACCOUNT, NAME, "old"));
        assertEquals(uids(index.find(ACCOUNT, NAME, "dup")), Arrays.asList("u1", "u2"));
    }

    @Test
    public void rekeyedEntryWithoutANameIsFoundByUidOnly() throws Exception {
        Document document = parse(account("u1", "a"));
        EntryIndex index = index(document);
        Element entry = index.first(ACCOUNT, UID, "u1");
        entry.removeChild(entry.getElementsByTagNameNS(ICF_NAMESPACE, NAME).item(0));
        index.rekeyed(entry);
        assertNull(index.first(ACCOUNT, NAME, "a"));
        assertEquals(uidOf(index.first(ACCOUNT, UID, "u1")), "u1");
    }

    private static EntryIndex index(Document document) {
        EntryIndex index = new EntryIndex(ICF_NAMESPACE, RI_NAMESPACE, new HashSet<String>(Arrays.asList(ACCOUNT, GROUP)));
        index.rebuild(document);
        return index;
    }

    private static String account(String uid, String name) {
        return "<ri:__ACCOUNT__><icf:__UID__>" + uid + "</icf:__UID__><icf:__NAME__>" + name + "</icf:__NAME__></ri:__ACCOUNT__>";
    }

    private static Document parse(String entries) throws Exception {
        return parseRaw("<icf:OpenICFContainer xmlns:icf='" + ICF_NAMESPACE + "' xmlns:ri='" + RI_NAMESPACE
                + "' xmlns:x='urn:other'>" + entries + "</icf:OpenICFContainer>");
    }

    private static Document parseRaw(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String uidOf(Element entry) {
        return entry.getElementsByTagNameNS(ICF_NAMESPACE, UID).item(0).getTextContent();
    }

    private static List<String> uids(Collection<Element> entries) {
        List<String> uids = new ArrayList<String>();
        for (Element entry : entries) {
            uids.add(uidOf(entry));
        }
        return uids;
    }
}
