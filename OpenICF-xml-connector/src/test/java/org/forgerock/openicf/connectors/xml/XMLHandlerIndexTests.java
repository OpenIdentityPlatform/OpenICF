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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
