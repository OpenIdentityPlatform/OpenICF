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

import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.openicf.connectors.xml.query.ComparisonQuery;
import org.forgerock.openicf.connectors.xml.query.QueryBuilder;
import org.forgerock.openicf.connectors.xml.query.QueryImpl;
import org.forgerock.openicf.connectors.xml.query.XQueryHandler;
import org.forgerock.openicf.connectors.xml.query.abstracts.Query;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.xquery.XQResultSequence;

public class XMLHandlerIndexTests {

    private XMLHandlerImpl handler;

    @BeforeMethod
    public void setUp() throws Exception {
        File file = getRandomXMLFile();
        Files.copy(getTestFile("index-fixture.xml").toPath(), file.toPath());
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(file);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        handler = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
        handler.init();
    }

    @Test
    public void deleteRemovesTheFirstDuplicateAndExposesTheNext() {
        handler.delete(ObjectClass.ACCOUNT, new Uid("u1"));
        assertEquals(byXQuery(ObjectClass.ACCOUNT, Name.NAME, "dup"), Collections.singletonList("u2|dup"));
        expectThrows(AlreadyExistsException.class, () -> handler.create(ObjectClass.ACCOUNT, account("dup")));

        handler.delete(ObjectClass.ACCOUNT, new Uid("u2"));
        handler.create(ObjectClass.ACCOUNT, account("dup"));
    }

    @Test
    public void renamedEntryIsFoundByItsNewNameOnly() {
        Uid uid = handler.update(ObjectClass.ACCOUNT, new Uid("u1"),
                Collections.singleton(AttributeBuilder.build(Name.NAME, "renamed")));
        assertEquals(uid.getUidValue(), "u1"); // __ACCOUNT__ has a __UID__: the rename keeps it
        expectThrows(AlreadyExistsException.class, () -> handler.create(ObjectClass.ACCOUNT, account("renamed")));

        handler.update(ObjectClass.ACCOUNT, new Uid("u1"),
                Collections.singleton(AttributeBuilder.build(Name.NAME, "renamed-again")));
        handler.create(ObjectClass.ACCOUNT, account("renamed"));
    }

    @Test
    public void renameFailingMidwayLeavesNoStaleName() {
        nestLastNameInto("u1");
        expectThrows(DOMException.class,
                () -> handler.update(ObjectClass.ACCOUNT, new Uid("u1"), renameThenFailMidway("a")));
        // The DOM fails midway (#182), after the rename: u1 is no longer "dup" in the document.
        assertEquals(byXQuery(ObjectClass.ACCOUNT, Name.NAME, "a"), Collections.singletonList("u1|a"));
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "a"), ObjectClass.ACCOUNT)),
                Collections.singletonList("u1|a")); // the index has the new name too
        handler.delete(ObjectClass.ACCOUNT, new Uid("u2"));
        handler.create(ObjectClass.ACCOUNT, account("dup")); // a stale "dup" for u1 in the index would refuse this
    }

    @Test
    public void rejectedRenameKeepsTheOldName() {
        expectThrows(IllegalArgumentException.class,
                () -> handler.update(ObjectClass.ACCOUNT, new Uid("u1"), renameThenFail("a")));
        assertEquals(byXQuery(ObjectClass.ACCOUNT, Name.NAME, "a"), Collections.emptyList());
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "a"), ObjectClass.ACCOUNT)), Collections.emptyList());
        assertEquals(byXQuery(ObjectClass.ACCOUNT, Name.NAME, "dup"), Arrays.asList("u1|dup", "u2|dup"));
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "dup"), ObjectClass.ACCOUNT)),
                Arrays.asList("u1|dup", "u2|dup"));
    }

    @Test
    public void renameIntoANameAnotherEntryHasIsRefused() {
        Uid fresh = handler.create(ObjectClass.ACCOUNT, account("fresh"));
        AlreadyExistsException e = expectThrows(AlreadyExistsException.class, () -> handler.update(ObjectClass.ACCOUNT,
                fresh, Collections.singleton(AttributeBuilder.build(Name.NAME, "dup"))));
        assertEquals(e.getMessage(), "Could not update entry. An entry with the __NAME__ of dup already exists.");

        // Only a GROUP has the name The Empire.
        handler.create(ObjectClass.GROUP, getRequiredGroupAttributes());
        expectThrows(AlreadyExistsException.class, () -> handler.update(ObjectClass.GROUP,
                new Uid("dup"), Collections.singleton(AttributeBuilder.build(Name.NAME, ATTR_GROUP_VALUE_NAME))));

        // u10's name is "" in the index: a blank name is refused as blank, not as taken.
        IllegalArgumentException blank = expectThrows(IllegalArgumentException.class, () -> handler.update(
                ObjectClass.ACCOUNT, new Uid("u1"), Collections.singleton(AttributeBuilder.build(Name.NAME, ""))));
        assertEquals(blank.getMessage(), "Parameter '__NAME__' must not be blank.");
    }

    @Test
    public void keepingANameAnotherEntryAlsoHasIsAllowed() {
        Set<Attribute> keepName = new LinkedHashSet<Attribute>();
        keepName.add(AttributeBuilder.build(Name.NAME, "dup"));
        keepName.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "L2b"));
        handler.update(ObjectClass.ACCOUNT, new Uid("u2"), keepName);
        assertEquals(describe(handler.search(equalsQuery(Name.NAME, "dup"), ObjectClass.ACCOUNT)),
                Arrays.asList("u1|dup", "u2|dup"));

        // Only __NAME__ is checked: another attribute may have a value that is someone's name.
        handler.update(ObjectClass.ACCOUNT, new Uid("u4"),
                Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "dup")));

        // Only an ACCOUNT has the name no-uid. __GROUP__ has no __UID__: its uid is the name, so the rename changes it.
        Uid renamed = handler.update(ObjectClass.GROUP, new Uid("dup"),
                Collections.singleton(AttributeBuilder.build(Name.NAME, "no-uid")));
        assertEquals(renamed.getUidValue(), "no-uid");
        Uid kept = handler.update(ObjectClass.GROUP, new Uid("no-uid"),
                Collections.singleton(AttributeBuilder.build(ATTR_DESCRIPTION, "D2")));
        assertEquals(kept.getUidValue(), "no-uid");
    }

    @Test
    public void clearingAnOptionalNameIsRefused() throws Exception {
        // Next to the original, so that its import of resource-schema-1.xsd resolves.
        File optionalNameXsd = getTestFile("optional-name.xsd");
        String xsd = new String(Files.readAllBytes(XSD_SCHEMA_FILEPATH.toPath()), StandardCharsets.UTF_8);
        // The first __NAME__ reference is __ACCOUNT__'s.
        Files.write(optionalNameXsd.toPath(), xsd.replaceFirst("<xsd:element ref=\"icf:__NAME__\"/>",
                "<xsd:element ref=\"icf:__NAME__\" minOccurs=\"0\"/>").getBytes(StandardCharsets.UTF_8));
        try {
            File file = getRandomXMLFile();
            writeAccounts(file, "alice", "bob");
            XMLConfiguration config = new XMLConfiguration();
            config.setXmlFilePath(file);
            config.setXsdFilePath(optionalNameXsd);
            SchemaParser parser = new SchemaParser(XMLConnector.class, optionalNameXsd);
            XMLHandlerImpl optional = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
            optional.init();

            // create() requires a name whatever the XSD says, and an object without one cannot be built:
            // a cleared entry would fail every search of the class.
            for (String blank : Arrays.asList("", "   ")) {
                IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> optional.update(
                        ObjectClass.ACCOUNT, new Uid("uid-alice"), Collections.singleton(AttributeBuilder.build(Name.NAME, blank))));
                assertEquals(e.getMessage(), "Parameter '__NAME__' must not be blank.");
            }
            assertEquals(describe(optional.search((Query) null, ObjectClass.ACCOUNT)),
                    Arrays.asList("uid-alice|alice", "uid-bob|bob"));
        } finally {
            Files.delete(optionalNameXsd.toPath());
        }
    }

    @Test
    public void clearingTheOptionalNameOfAnEntryWithoutUidIsRefused() throws Exception {
        File optionalNameXsd = getTestFile("optional-group-name.xsd");
        String xsd = new String(Files.readAllBytes(XSD_SCHEMA_FILEPATH.toPath()), StandardCharsets.UTF_8);
        String ref = "<xsd:element ref=\"icf:__NAME__\"/>";
        // The second __NAME__ reference is __GROUP__'s, a class without __UID__.
        int group = xsd.indexOf(ref, xsd.indexOf(ref) + 1);
        Files.write(optionalNameXsd.toPath(), (xsd.substring(0, group) + "<xsd:element ref=\"icf:__NAME__\" minOccurs=\"0\"/>"
                + xsd.substring(group + ref.length())).getBytes(StandardCharsets.UTF_8));
        try {
            File file = getRandomXMLFile();
            Files.copy(getTestFile("index-fixture.xml").toPath(), file.toPath());
            XMLConfiguration config = new XMLConfiguration();
            config.setXmlFilePath(file);
            config.setXsdFilePath(optionalNameXsd);
            SchemaParser parser = new SchemaParser(XMLConnector.class, optionalNameXsd);
            XMLHandlerImpl optional = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
            optional.init();

            // The uid is the name, so a cleared name would leave an entry that no search could read back.
            for (String blank : Arrays.asList("", "   ")) {
                IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> optional.update(
                        ObjectClass.GROUP, new Uid("dup"), Collections.singleton(AttributeBuilder.build(Name.NAME, blank))));
                assertEquals(e.getMessage(), "Parameter '__NAME__' must not be blank.");
            }
            assertEquals(describe(optional.search(equalsQuery(Name.NAME, "dup"), ObjectClass.GROUP)),
                    Collections.singletonList("dup|dup"));
        } finally {
            Files.delete(optionalNameXsd.toPath());
        }
    }

    /**
     * Renames to {@code newName} along with an unknown attribute. update() rejects the unknown attribute
     * before it changes the entry, so the rename is not applied. A two-valued __NAME__ cannot be used:
     * AttributeBuilder.build(Name.NAME, "a", "b") throws before update() runs.
     */
    static Set<Attribute> renameThenFail(String newName) {
        Set<Attribute> attributes = new LinkedHashSet<Attribute>();
        attributes.add(AttributeBuilder.build(Name.NAME, newName));
        attributes.add(AttributeBuilder.build("no-such-attribute", "x"));
        return attributes;
    }

    /** Renames to {@code newName}, then replaces the lastname: on an entry given {@link #nestLastNameInto}, the DOM fails there. */
    static Set<Attribute> renameThenFailMidway(String newName) {
        Set<Attribute> attributes = new LinkedHashSet<Attribute>();
        attributes.add(AttributeBuilder.build(Name.NAME, newName));
        attributes.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "NEW"));
        return attributes;
    }

    /**
     * An XSD-invalid hand edit (#182): update's descendant lookup finds the inner lastname, removeChild rejects it.
     * Once #182 is fixed, the tests that use it need another way to fail midway.
     */
    private void nestLastNameInto(String uid) {
        Element entry = handler.indexedEntries(equalsQuery(Uid.NAME, uid), ObjectClass.ACCOUNT).get(0);
        Element wrapper = entry.getOwnerDocument().createElementNS(RI_NAMESPACE, "ri:wrapper");
        Element inner = entry.getOwnerDocument().createElementNS(RI_NAMESPACE, "ri:" + ATTR_ACCOUNT_LAST_NAME);
        inner.setTextContent("inner");
        wrapper.appendChild(inner);
        entry.appendChild(wrapper); // after the entry's own lastname, which update removes first
    }

    @Test
    public void authenticateFindsTheEntryByName() {
        handler.create(ObjectClass.ACCOUNT, account("eve"));
        Uid uid = handler.authenticate("eve", new GuardedString("secret-eve".toCharArray()));
        assertEquals(uid.getUidValue() + "|eve", byXQuery(ObjectClass.ACCOUNT, Name.NAME, "eve").get(0));
    }

    @Test
    public void identifierWithAnAmpersandIsFound() {
        // The XQuery literal 'a&b' is not valid: create's duplicate check failed on it before the index.
        Uid uid = handler.create(ObjectClass.ACCOUNT, account("a&b"));
        Query byName = new XMLFilterTranslator(true).createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(Name.NAME, "a&b")), false);
        assertEquals(describe(handler.search(byName, ObjectClass.ACCOUNT)),
                Collections.singletonList(uid.getUidValue() + "|a&b"));
        assertEquals(handler.authenticate("a&b", new GuardedString("secret-a&b".toCharArray())), uid);
    }

    @Test
    public void identifierLookupsUseTheHandlersOwnNamespaces() throws Exception {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(getRandomXMLFile());
        Files.copy(getTestFile("index-fixture.xml").toPath(), config.getXmlFilePath().toPath());
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        SchemaParser parser = new SchemaParser(XMLConnector.class, XSD_SCHEMA_FILEPATH);
        XMLHandlerImpl own = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());

        // Next to the original, so that its import of resource-schema-1.xsd resolves.
        File otherXsd = getTestFile("other-ri-namespace.xsd");
        String xsd = new String(Files.readAllBytes(XSD_SCHEMA_FILEPATH.toPath()), StandardCharsets.UTF_8);
        Files.write(otherXsd.toPath(), xsd.replace("instances/ef2bc95b-76e0-48e2-86d6-4d4f44d4e4a4", "instances/other")
                .getBytes(StandardCharsets.UTF_8));
        try {
            XMLConfiguration otherConfig = new XMLConfiguration();
            otherConfig.setXmlFilePath(getRandomXMLFile());
            otherConfig.setXsdFilePath(otherXsd);
            SchemaParser otherParser = new SchemaParser(XMLConnector.class, otherXsd);
            // Sets the JVM-wide namespaces to its own: it is now the last configured handler.
            new XMLHandlerImpl(otherConfig, otherParser.parseSchema(), otherParser.getXsdSchema());
            own.init();
            assertEquals(describe(own.search(equalsQuery(Name.NAME, "dup"), ObjectClass.ACCOUNT)),
                    Arrays.asList("u1|dup", "u2|dup"));
        } finally {
            Files.delete(otherXsd.toPath());
            // Puts the JVM-wide namespaces back for the tests that run after this one.
            new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
        }
    }

    /** {@code $x/icf:FIELD = 'VALUE'} exactly as XMLFilterTranslator builds it, without the framework's value checks. */
    static Query equalsQuery(String field, String value) {
        Query query = new QueryImpl();
        query.set(new ComparisonQuery("$x/icf:" + field, "=", value, "'" + value.replaceAll("'", "''") + "'"));
        return query;
    }

    List<String> byXQuery(ObjectClass objectClass, String field, String value) {
        return describe(handler.search(new QueryBuilder(equalsQuery(field, value), objectClass).toString(), objectClass));
    }

    static List<String> describe(Collection<ConnectorObject> objects) {
        List<String> result = new ArrayList<String>();
        for (ConnectorObject object : objects) {
            result.add(object.getUid().getUidValue() + "|" + (object.getName() == null ? null : object.getName().getNameValue()));
        }
        return result;
    }

    private static final String[][] PROBES = {
        {"__ACCOUNT__", "__NAME__", "dup"}, {"__ACCOUNT__", "__NAME__", "no-uid"},
        {"__ACCOUNT__", "__NAME__", "two-a"}, {"__ACCOUNT__", "__NAME__", "two-b"},
        {"__ACCOUNT__", "__NAME__", "  padded  "}, {"__ACCOUNT__", "__NAME__", "padded"},
        {"__ACCOUNT__", "__NAME__", "cdata"}, {"__ACCOUNT__", "__NAME__", "foreign"},
        {"__ACCOUNT__", "__NAME__", "nested"}, {"__ACCOUNT__", "__NAME__", "late"},
        {"__ACCOUNT__", "__NAME__", "  "}, {"__ACCOUNT__", "__NAME__", ""},
        {"__ACCOUNT__", "__NAME__", "renamed"}, {"__ACCOUNT__", "__NAME__", "missing"},
        {"__ACCOUNT__", "__NAME__", "inner"}, {"__ACCOUNT__", "__NAME__", "holder"},
        {"__ACCOUNT__", "__NAME__", "foreign-name"},
        {"__ACCOUNT__", "__UID__", "u1"}, {"__ACCOUNT__", "__UID__", "u7"},
        {"__ACCOUNT__", "__UID__", "u8"}, {"__ACCOUNT__", "__UID__", "u12"},
        {"__ACCOUNT__", "__UID__", ""}, {"__ACCOUNT__", "__UID__", "missing"},
        {"__GROUP__", "__NAME__", "dup"},
    };

    private void assertIndexAgreesWithXQuery(String step) throws Exception {
        for (String[] probe : PROBES) {
            ObjectClass objectClass = new ObjectClass(probe[0]);
            Query query = equalsQuery(probe[1], probe[2]);
            assertSameEntries(handler.indexedEntries(query, objectClass), xqueryEntries(query, objectClass),
                    step + " " + Arrays.toString(probe));
        }
        for (ObjectClass objectClass : Arrays.asList(ObjectClass.ACCOUNT, ObjectClass.GROUP)) {
            assertSameEntries(handler.indexedEntries(null, objectClass), xqueryEntries(null, objectClass),
                    step + " all " + objectClass);
            assertSameEntries(handler.indexedEntries(new QueryImpl(), objectClass), xqueryEntries(new QueryImpl(), objectClass),
                    step + " empty query " + objectClass);
        }
    }

    private List<Element> xqueryEntries(Query query, ObjectClass objectClass) throws Exception {
        XQueryHandler xquery = new XQueryHandler(new QueryBuilder(query, objectClass).toString(), handler.getDocument());
        try {
            List<Element> entries = new ArrayList<Element>();
            XQResultSequence sequence = xquery.getResultSequence();
            while (sequence.next()) {
                entries.add((Element) sequence.getItem().getNode());
            }
            return entries;
        } finally {
            xquery.close();
        }
    }

    private static void assertSameEntries(List<Element> actual, List<Element> expected, String message) {
        assertNotNull(actual, message + ": the index did not answer");
        assertEquals(actual.size(), expected.size(), message);
        for (int i = 0; i < expected.size(); i++) {
            assertSame(actual.get(i), expected.get(i), message + " #" + i);
        }
    }

    @Test
    public void indexAgreesWithXQueryAfterChanges() throws Exception {
        assertIndexAgreesWithXQuery("loaded");
        handler.create(ObjectClass.ACCOUNT, account("dup-new"));
        assertIndexAgreesWithXQuery("created");
        handler.update(ObjectClass.ACCOUNT, new Uid("u1"), Collections.singleton(AttributeBuilder.build(Name.NAME, "dup")));
        assertIndexAgreesWithXQuery("first duplicate re-keyed");
        handler.delete(ObjectClass.ACCOUNT, new Uid("u1"));
        assertIndexAgreesWithXQuery("first duplicate deleted");
        handler.update(ObjectClass.ACCOUNT, new Uid("u11"), Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "L11")));
        assertIndexAgreesWithXQuery("value holding an entry replaced");
        nestLastNameInto("u4");
        expectThrows(DOMException.class,
                () -> handler.update(ObjectClass.ACCOUNT, new Uid("u4"), renameThenFailMidway("x")));
        assertIndexAgreesWithXQuery("failed rename midway");
        handler.dispose();
        assertIndexAgreesWithXQuery("saved");
    }

    @Test
    public void saveRebuildsTheIndexWhenItEmptiesAnIdentifier() throws Exception {
        // Stands in for a schema with an optional __NAME__, where update can store a whitespace-only name
        // that the save empties; the handler would not accept the value, so the DOM is edited directly.
        Element u1 = handler.indexedEntries(equalsQuery("__UID__", "u1"), ObjectClass.ACCOUNT).get(0);
        for (Node child = u1.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && Name.NAME.equals(child.getLocalName())) {
                child.setTextContent("  ");
            }
        }
        handler.create(ObjectClass.ACCOUNT, account("x"));
        handler.dispose();
        assertIndexAgreesWithXQuery("saved after a name became blank");
    }

    @Test
    public void queryOnAnotherAttributeFallsBackToXQuery() {
        Query query = new XMLFilterTranslator(true).createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "L2")), false);
        assertNull(handler.indexedEntries(query, ObjectClass.ACCOUNT));
        assertEquals(describe(handler.search(query, ObjectClass.ACCOUNT)), Arrays.asList("u2|dup"));
    }

    @Test
    public void searchWithoutADocumentFailsAsBefore() throws Exception {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(getRandomXMLFile());
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        XMLHandlerImpl uninitialized = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
        ConnectorException byIdentifier = expectThrows(ConnectorException.class,
                () -> uninitialized.search(equalsQuery("__NAME__", "dup"), ObjectClass.ACCOUNT));
        assertTrue(byIdentifier.getMessage().startsWith("Data file does not exists: "), byIdentifier.getMessage());
        ConnectorException all = expectThrows(ConnectorException.class,
                () -> uninitialized.search(new QueryImpl(), ObjectClass.ACCOUNT));
        assertTrue(all.getMessage().startsWith("Data file does not exists: "), all.getMessage());
    }

    @Test
    public void onlySingleEqualityOnAnIdentifierGoesToTheIndex() {
        XMLFilterTranslator translator = new XMLFilterTranslator(true);
        Query byName = translator.createEqualsExpression(new EqualsFilter(AttributeBuilder.build(Name.NAME, "x")), false);
        Query byUid = translator.createEqualsExpression(new EqualsFilter(AttributeBuilder.build(Uid.NAME, "x")), false);
        assertEquals(XMLHandlerImpl.identifierField(byName), Name.NAME);
        assertEquals(XMLHandlerImpl.identifierField(byUid), Uid.NAME);
        assertEquals(XMLHandlerImpl.identifierField(new XMLFilterTranslator(false).createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(Uid.NAME, "x")), false)), Name.NAME);

        assertNull(XMLHandlerImpl.identifierField(
                translator.createEqualsExpression(new EqualsFilter(AttributeBuilder.build(Name.NAME, "x")), true)));
        assertNull(XMLHandlerImpl.identifierField(
                translator.createEqualsExpression(new EqualsFilter(AttributeBuilder.build("__name__", "x")), false)));
        assertNull(XMLHandlerImpl.identifierField(
                translator.createEqualsExpression(new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "x")), false)));
        assertNull(XMLHandlerImpl.identifierField(translator.createAndExpression(byName,
                translator.createEqualsExpression(new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "y")), false))));
        assertNull(XMLHandlerImpl.identifierField(translator.createStartsWithExpression(
                new StartsWithFilter(AttributeBuilder.build(Name.NAME, "x")), false)));
        assertNull(XMLHandlerImpl.identifierField(translator.createGreaterThanOrEqualExpression(
                new GreaterThanOrEqualFilter(AttributeBuilder.build(Name.NAME, "x")), false)));
    }

    @Test
    public void lookupsAndSavesNeverUseNodeLists() throws Exception {
        File file = getRandomXMLFile();
        writeAccounts(file, "alice", "bob", "carol");
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(file);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        XMLHandlerImpl fresh = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());
        fresh.init();

        fresh.search(equalsQuery(Name.NAME, "bob"), ObjectClass.ACCOUNT);
        fresh.search(equalsQuery(Uid.NAME, "uid-carol"), ObjectClass.ACCOUNT);
        fresh.search((Query) null, ObjectClass.ACCOUNT);
        fresh.authenticate("alice", new GuardedString("secret-alice".toCharArray()));
        fresh.create(ObjectClass.ACCOUNT, account("dave"));
        fresh.update(ObjectClass.ACCOUNT, new Uid("uid-bob"),
                Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Changed")));
        fresh.delete(ObjectClass.ACCOUNT, new Uid("uid-carol"));
        fresh.dispose();

        org.testng.Assert.assertFalse(XercesNodeLists.used(fresh.getDocument()));
    }

    @Test
    public void replacedValueTakesTheEntriesInsideItAlong() {
        handler.update(ObjectClass.ACCOUNT, new Uid("u11"),
                Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "L11")));
        handler.create(ObjectClass.ACCOUNT, account("inner")); // a stale "inner" for u12 in the index would refuse this
    }

    @Test
    public void classWithoutUidIsFoundByNameWhenAskedByUid() {
        // __GROUP__ has no __UID__ element: its Uid is the name.
        handler.delete(ObjectClass.GROUP, new Uid("dup"));
        expectThrows(UnknownUidException.class, () -> handler.delete(ObjectClass.GROUP, new Uid("dup")));
    }
}
