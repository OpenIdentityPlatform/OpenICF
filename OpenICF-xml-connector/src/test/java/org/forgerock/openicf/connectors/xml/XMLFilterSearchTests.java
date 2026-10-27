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

import static java.util.Arrays.asList;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.contains;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.containsAllValues;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.endsWith;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.equalTo;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.greaterThan;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.greaterThanOrEqualTo;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.lessThan;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.lessThanOrEqualTo;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.not;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.startsWith;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilteredResultsHandlerVisitor;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Searches through the facade with the framework's own filtering switched off, so every entry the
 * generated XQuery misses is visible. The framework filter is the oracle: an entry it selects
 * must be returned.
 */
public class XMLFilterSearchTests {

    private File file;
    private ConnectorFacade facade;

    @BeforeClass
    public void setUp() throws IOException {
        file = getRandomXMLFile();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<icf:OpenICFContainer xmlns:icf=\"").append(ICF_NAMESPACE)
                .append("\" xmlns:ri=\"").append(RI_NAMESPACE).append("\">\n")
                // The first names are AT&T, AT&amp;T and O'B.
                .append(account("a1", element(ATTR_ACCOUNT_FIRST_NAME, "AT&amp;T"),
                        element(ATTR_ACCOUNT_LAST_NAME, "A"), element(ATTR_ACCOUNT_EMAIL, "x1"),
                        element(ATTR_ACCOUNT_AGE, "10"),
                        // 10 in Arabic-Indic digits: Java reads it, XPath reads it as NaN.
                        element(ATTR_ACCOUNT_YEARS_EMPLOYED, "\u0661\u0660"),
                        element(ATTR_ACCOUNT_MS_EMPLOYED, "9007199254740993"),
                        element(ATTR_ACCOUNT_LAST_LOGOFF_DATE, "9007199254740993"),
                        element(ATTR_ACCOUNT_HOURLY_WAGE, "10.0"),
                        element(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, "0.1"),
                        element(ATTR_ACCOUNT_MAX_STORAGE, "1E+3")))
                .append(account("a2", element(ATTR_ACCOUNT_FIRST_NAME, "AT&amp;amp;T"),
                        element(ATTR_ACCOUNT_LAST_NAME, "M"), element(ATTR_ACCOUNT_EMAIL, "x2"),
                        element(ATTR_ACCOUNT_EMAIL, "y2"),
                        element(ATTR_ACCOUNT_AGE, "9"),
                        element(ATTR_ACCOUNT_YEARS_EMPLOYED, "9"),
                        element(ATTR_ACCOUNT_MS_EMPLOYED, "9007199254740992"),
                        element(ATTR_ACCOUNT_LAST_LOGOFF_DATE, "9007199254740992"),
                        element(ATTR_ACCOUNT_HOURLY_WAGE, "5.0"),
                        element(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, "0.2"),
                        element(ATTR_ACCOUNT_MAX_STORAGE, "0.10000000000000000002")))
                .append(account("a3", element(ATTR_ACCOUNT_FIRST_NAME, "O'B"),
                        element(ATTR_ACCOUNT_LAST_NAME, "Z"), element(ATTR_ACCOUNT_EMAIL, "y3"),
                        element(ATTR_ACCOUNT_AGE, "5"),
                        element(ATTR_ACCOUNT_MS_EMPLOYED, "9007199254740991"),
                        element(ATTR_ACCOUNT_LAST_LOGOFF_DATE, "9007199254740991"),
                        element(ATTR_ACCOUNT_HOURLY_WAGE, "Infinity"),
                        element(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, "0.05"),
                        element(ATTR_ACCOUNT_MAX_STORAGE, "0.1")))
                // Empty elements, as create writes them for unset attributes. The office-square-feet
                // of a4 and a5 read as 0.1f, but are not the text Float.toString writes for it.
                .append(account("a4", element(ATTR_ACCOUNT_LAST_NAME, "B"), emptyElement(ATTR_ACCOUNT_AGE),
                        element(ATTR_ACCOUNT_MS_EMPLOYED, "-9007199254740993"),
                        element(ATTR_ACCOUNT_LAST_LOGOFF_DATE, "-9007199254740993"),
                        emptyElement(ATTR_ACCOUNT_HOURLY_WAGE),
                        element(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, "0.1000000025")))
                .append(account("a5", element(ATTR_ACCOUNT_LAST_NAME, "C"),
                        element(ATTR_ACCOUNT_MS_EMPLOYED, "-9223372036854775807"),
                        element(ATTR_ACCOUNT_HOURLY_WAGE, "0.0"),
                        element(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, "0.099999999")))
                .append("</icf:OpenICFContainer>\n");
        Files.write(file.toPath(), xml.toString().getBytes(StandardCharsets.UTF_8));

        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(file);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        APIConfiguration api = TestHelpers.createTestConfiguration(XMLConnector.class, config);
        // The framework would drop wrong matches but cannot add missing ones; switched off, both show.
        api.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        facade = ConnectorFacadeFactory.getInstance().newInstance(api);
    }

    @AfterClass
    public void tearDown() throws IOException {
        // The handler cache is static: without this, the DOM and the index stay for the rest of the run.
        synchronized (XMLConnector.class) {
            XMLConnector.XMLHandlerCache.remove(file.getCanonicalPath());
        }
        file.delete();
    }

    @DataProvider
    public Object[][] filters() {
        return new Object[][] {
                // A negated > or < keeps the bound: M is not greater (less) than M.
                {not(greaterThan(attribute(ATTR_ACCOUNT_LAST_NAME, "M"))), asList("a1", "a2", "a4", "a5")},
                {not(lessThan(attribute(ATTR_ACCOUNT_LAST_NAME, "M"))), asList("a2", "a3")},
                {not(greaterThanOrEqualTo(attribute(ATTR_ACCOUNT_LAST_NAME, "M"))), asList("a1", "a4", "a5")},
                {not(lessThanOrEqualTo(attribute(ATTR_ACCOUNT_LAST_NAME, "M"))), asList("a3")},
                // startsWith and endsWith test each value: a2 has two emails. The framework never
                // matches a multi-valued attribute here, so a2 is an extra match it drops.
                {startsWith(attribute(ATTR_ACCOUNT_EMAIL, "y")), asList("a2", "a3")},
                {not(startsWith(attribute(ATTR_ACCOUNT_EMAIL, "y"))), asList("a1", "a4", "a5")},
                {endsWith(attribute(ATTR_ACCOUNT_EMAIL, "3")), asList("a3")},
                {not(endsWith(attribute(ATTR_ACCOUNT_EMAIL, "3"))), asList("a1", "a2", "a4", "a5")},
                // Numbers are compared as numbers: as strings, '10' < '9'. a4 has an empty age.
                {greaterThan(attribute(ATTR_ACCOUNT_AGE, 9)), asList("a1")},
                {greaterThanOrEqualTo(attribute(ATTR_ACCOUNT_AGE, 9)), asList("a1", "a2")},
                {lessThan(attribute(ATTR_ACCOUNT_AGE, 10)), asList("a2", "a3")},
                {lessThanOrEqualTo(attribute(ATTR_ACCOUNT_AGE, 10)), asList("a1", "a2", "a3")},
                {not(greaterThan(attribute(ATTR_ACCOUNT_AGE, 10))), asList("a1", "a2", "a3")},
                {not(greaterThanOrEqualTo(attribute(ATTR_ACCOUNT_AGE, 10))), asList("a2", "a3")},
                {not(lessThan(attribute(ATTR_ACCOUNT_AGE, 9))), asList("a1", "a2")},
                {not(lessThanOrEqualTo(attribute(ATTR_ACCOUNT_AGE, 9))), asList("a1")},
                // Text XPath reads as NaN is kept: a1's years-employed is 10.
                {greaterThan(attribute(ATTR_ACCOUNT_YEARS_EMPLOYED, 9)), asList("a1")},
                {lessThan(attribute(ATTR_ACCOUNT_YEARS_EMPLOYED, 11)), asList("a1", "a2")},
                // Integers are compared exactly only inside (-2^53, 2^53); 2^53 + 1 reads as 2^53.
                {greaterThan(attribute(ATTR_ACCOUNT_MS_EMPLOYED, 9007199254740992L)), asList("a1", "a2")},
                {greaterThan(attribute(ATTR_ACCOUNT_MS_EMPLOYED, 9007199254740991L)), asList("a1", "a2")},
                {lessThan(attribute(ATTR_ACCOUNT_MS_EMPLOYED, -9007199254740992L)), asList("a4", "a5")},
                {greaterThan(attribute(ATTR_ACCOUNT_MS_EMPLOYED, Long.MIN_VALUE)),
                        asList("a1", "a2", "a3", "a4", "a5")},
                {lessThan(attribute(ATTR_ACCOUNT_LAST_LOGOFF_DATE, new BigInteger("-9007199254740992"))),
                        asList("a4")},
                {greaterThan(attribute(ATTR_ACCOUNT_LAST_LOGOFF_DATE, new BigInteger("9007199254740991"))),
                        asList("a1", "a2")},
                // Other numbers keep every value that is not on the wrong side of the bound.
                {greaterThan(attribute(ATTR_ACCOUNT_HOURLY_WAGE, 5.0)), asList("a1", "a2", "a3")},
                {lessThan(attribute(ATTR_ACCOUNT_HOURLY_WAGE, Double.POSITIVE_INFINITY)),
                        asList("a1", "a2", "a3", "a5")},
                {greaterThan(attribute(ATTR_ACCOUNT_HOURLY_WAGE, -0.0)), asList("a1", "a2", "a3", "a5")},
                {lessThanOrEqualTo(attribute(ATTR_ACCOUNT_HOURLY_WAGE, 5.0)), asList("a2", "a3", "a5")},
                {not(greaterThan(attribute(ATTR_ACCOUNT_HOURLY_WAGE, 5.0))), asList("a2", "a3", "a5")},
                // A float bound covers every decimal Java reads as that float.
                {greaterThanOrEqualTo(attribute(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, 0.1f)),
                        asList("a1", "a2", "a4", "a5")},
                {lessThanOrEqualTo(attribute(ATTR_ACCOUNT_OFFICE_SQUARE_FEET, 0.1f)),
                        asList("a1", "a3", "a4", "a5")},
                {greaterThan(attribute(ATTR_ACCOUNT_MAX_STORAGE, new BigDecimal("0.10000000000000000001"))),
                        asList("a1", "a2", "a3")},
                // An & in the value is text, not the start of an entity reference.
                {startsWith(attribute(ATTR_ACCOUNT_FIRST_NAME, "AT&")), asList("a1", "a2")},
                {equalTo(attribute(ATTR_ACCOUNT_FIRST_NAME, "AT&T")), asList("a1")},
                {equalTo(attribute(ATTR_ACCOUNT_FIRST_NAME, "AT&amp;T")), asList("a2")},
                {not(equalTo(attribute(ATTR_ACCOUNT_FIRST_NAME, "AT&amp;T"))), asList("a1", "a3")},
                {greaterThan(attribute(ATTR_ACCOUNT_FIRST_NAME, "AT&T")), asList("a2", "a3")},
                // contains escapes its value like the other filters.
                {contains(attribute(ATTR_ACCOUNT_FIRST_NAME, "'")), asList("a3")},
                {contains(attribute(ATTR_ACCOUNT_FIRST_NAME, "&amp;")), asList("a2")},
                {not(contains(attribute(ATTR_ACCOUNT_FIRST_NAME, "&T"))), asList("a2", "a3", "a4", "a5")},
                // containsAllValues looks for equal values: x1 does not contain x. A null value is
                // left to the framework, so every entry is returned.
                {containsAllValues(attribute(ATTR_ACCOUNT_EMAIL, "x")), asList()},
                {not(containsAllValues(attribute(ATTR_ACCOUNT_EMAIL, "x"))), asList("a1", "a2", "a3")},
                {containsAllValues(attribute(ATTR_ACCOUNT_AGE, 9)), asList("a2")},
                {containsAllValues(attribute(ATTR_ACCOUNT_EMAIL, null)), asList("a1", "a2", "a3", "a4", "a5")},
                {containsAllValues(AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, "x1", null)),
                        asList("a1", "a2", "a3", "a4", "a5")},
        };
    }

    @Test(dataProvider = "filters")
    public void searchLosesNoMatchOfTheFrameworkFilter(Filter filter, List<String> expected) {
        Filter framework = FilteredResultsHandlerVisitor.wrapFilter(filter, false);
        List<String> selectedByFramework = new ArrayList<String>();
        facade.search(ObjectClass.ACCOUNT, null, o -> {
            if (framework.accept(o)) {
                selectedByFramework.add(o.getName().getNameValue());
            }
            return true;
        }, null);

        List<String> returned = names(filter);
        assertTrue(returned.containsAll(selectedByFramework), returned + " lost " + selectedByFramework);
        assertEquals(returned, expected);
    }

    /** The sorted __NAME__ values the connector returns for the filter. */
    private List<String> names(Filter filter) {
        List<String> names = new ArrayList<String>();
        facade.search(ObjectClass.ACCOUNT, filter, o -> names.add(o.getName().getNameValue()), null);
        Collections.sort(names);
        return names;
    }

    private static Attribute attribute(String name, Object value) {
        return AttributeBuilder.build(name, value);
    }

    private static String account(String name, String... elements) {
        StringBuilder xml = new StringBuilder();
        xml.append("  <ri:__ACCOUNT__>\n")
                .append("    <icf:__UID__>uid-").append(name).append("</icf:__UID__>\n")
                .append("    <icf:__NAME__>").append(name).append("</icf:__NAME__>\n");
        for (String element : elements) {
            xml.append("    ").append(element).append('\n');
        }
        return xml.append("  </ri:__ACCOUNT__>\n").toString();
    }

    private static String element(String name, String text) {
        return "<ri:" + name + ">" + text + "</ri:" + name + ">";
    }

    private static String emptyElement(String name) {
        return "<ri:" + name + "/>";
    }
}
