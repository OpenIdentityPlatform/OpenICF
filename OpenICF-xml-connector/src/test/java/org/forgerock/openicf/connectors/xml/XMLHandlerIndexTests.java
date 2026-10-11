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
import static org.testng.Assert.expectThrows;

import java.io.File;
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
import org.w3c.dom.Element;

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
        handler.update(ObjectClass.ACCOUNT, new Uid("u1"),
                Collections.singleton(AttributeBuilder.build(Name.NAME, "renamed")));
        expectThrows(AlreadyExistsException.class, () -> handler.create(ObjectClass.ACCOUNT, account("renamed")));

        handler.update(ObjectClass.ACCOUNT, new Uid("u1"),
                Collections.singleton(AttributeBuilder.build(Name.NAME, "dup")));
        handler.create(ObjectClass.ACCOUNT, account("renamed"));
    }

    @Test
    public void failedRenameLeavesNoStaleName() {
        expectThrows(IllegalArgumentException.class,
                () -> handler.update(ObjectClass.ACCOUNT, new Uid("u1"), renameThenFail("a")));
        // The rename was applied before the failure (#158): u1 is no longer "dup" in the document.
        assertEquals(byXQuery(ObjectClass.ACCOUNT, Name.NAME, "a"), Collections.singletonList("u1|a"));
        handler.delete(ObjectClass.ACCOUNT, new Uid("u2"));
        handler.create(ObjectClass.ACCOUNT, account("dup")); // a stale "dup" for u1 in the index would refuse this
    }

    /**
     * Renames to {@code newName}, then fails on an unknown attribute. update() applies the attributes
     * in order, so the rename is in the document when it throws. A two-valued __NAME__ cannot be used:
     * AttributeBuilder.build(Name.NAME, "a", "b") throws before update() runs.
     */
    static Set<Attribute> renameThenFail(String newName) {
        Set<Attribute> attributes = new LinkedHashSet<Attribute>();
        attributes.add(AttributeBuilder.build(Name.NAME, newName));
        attributes.add(AttributeBuilder.build("no-such-attribute", "x"));
        return attributes;
    }

    @Test
    public void authenticateFindsTheEntryByName() {
        handler.create(ObjectClass.ACCOUNT, account("eve"));
        Uid uid = handler.authenticate("eve", new GuardedString("secret-eve".toCharArray()));
        assertEquals(uid.getUidValue() + "|eve", byXQuery(ObjectClass.ACCOUNT, Name.NAME, "eve").get(0));
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
        handler.update(ObjectClass.ACCOUNT, new Uid("u2"), Collections.singleton(AttributeBuilder.build(Name.NAME, "renamed")));
        handler.update(ObjectClass.ACCOUNT, new Uid("u2"), Collections.singleton(AttributeBuilder.build(Name.NAME, "dup")));
        assertIndexAgreesWithXQuery("renamed back to a duplicate");
        handler.delete(ObjectClass.ACCOUNT, new Uid("u1"));
        assertIndexAgreesWithXQuery("first duplicate deleted");
        handler.update(ObjectClass.ACCOUNT, new Uid("u11"), Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "L11")));
        assertIndexAgreesWithXQuery("value holding an entry replaced");
        expectThrows(IllegalArgumentException.class,
                () -> handler.update(ObjectClass.ACCOUNT, new Uid("u4"), renameThenFail("x")));
        assertIndexAgreesWithXQuery("failed rename");
        handler.dispose();
        assertIndexAgreesWithXQuery("saved");
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
