/*
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
import org.testng.annotations.BeforeClass;
import org.testng.AssertJUnit;
import org.forgerock.openicf.connectors.xml.query.FunctionQuery;
import org.forgerock.openicf.connectors.xml.query.QueryBuilder;
import org.forgerock.openicf.connectors.xml.query.abstracts.Query;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

public class XMLFilterTranslatorTests {

    private static final String LOCAL_ATTR_ACCOUNT_VALUE_LAST_NAME = "Maul";
    private static final String LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME = "Lighter";
    private static XMLHandler xmlHandler;
    private static XMLFilterTranslator filterTranslator;
    private static Query equalsQueryFnVader;
    private static Query equalsQueryFnMaul;
    private static Query equalsQueryLnVader;
    private static Query equalsQueryLnMaul;
    private static Query equalsQueryNonExisting;
    private static Query gtQueryMs;
    private static Query ltQueryMs;
    private static Query gtoreqQueryYearsEmployed;
    private static Query ltoreqQueryYearsEmployed;

    public XMLFilterTranslatorTests() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(getRandomXMLFile());
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        config.setCreateFileIfNotExists(true);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());

        xmlHandler = new ConcurrentXMLHandler(config, parser.parseSchema(), parser.getXsdSchema());
        xmlHandler.init();
        Set<Attribute> attributesFirst = getRequiredAccountAttributes();
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_MS_EMPLOYED, ATTR_ACCOUNT_VALUE_MS_EMPLOYED));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_SIXTH_SENSE, ATTR_ACCOUNT_VALUE_SIXTH_SENSE));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, ATTR_ACCOUNT_VALUE_FIRST_NAME));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, ATTR_ACCOUNT_VALUE_EMAIL_1));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, ATTR_ACCOUNT_VALUE_EMAIL_2));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_SECRET_PIN, new GuardedString(ATTR_ACCOUNT_VALUE_SECRET_PIN.toCharArray())));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_PERMANTENT_EMPLOYEE, ATTR_ACCOUNT_VALUE_PERMANENT_EPLOYEE));
        attributesFirst.add(AttributeBuilder.build(ATTR_ACCOUNT_YEARS_EMPLOYED, ATTR_ACCOUNT_VALUE_YEARS_EPLOYED));

        xmlHandler.create(ObjectClass.ACCOUNT, attributesFirst);

        Set<Attribute> attributesSecond = new HashSet<Attribute>();

        attributesSecond.add(AttributeBuilder.build(ATTR_NAME, "maulUID"));
        attributesSecond.add(AttributeBuilder.build(ATTR_PASSWORD, new GuardedString(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray())));
        attributesSecond.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, LOCAL_ATTR_ACCOUNT_VALUE_LAST_NAME));
        attributesSecond.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME));
        xmlHandler.create(ObjectClass.ACCOUNT, attributesSecond);


        filterTranslator = new XMLFilterTranslator(true);

        AttributeBuilder attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_FIRST_NAME);
        attrBld.addValue(ATTR_ACCOUNT_VALUE_FIRST_NAME);
        EqualsFilter filter = new EqualsFilter(attrBld.build());
        equalsQueryFnVader = filterTranslator.createEqualsExpression(filter, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_LAST_NAME);
        attrBld.addValue(ATTR_ACCOUNT_VALUE_LAST_NAME);
        EqualsFilter filter2 = new EqualsFilter(attrBld.build());
        equalsQueryLnVader = filterTranslator.createEqualsExpression(filter2, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_LAST_NAME);
        attrBld.addValue(LOCAL_ATTR_ACCOUNT_VALUE_LAST_NAME);
        EqualsFilter filter4 = new EqualsFilter(attrBld.build());
        equalsQueryLnMaul = filterTranslator.createEqualsExpression(filter4, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_FIRST_NAME);
        attrBld.addValue(LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME);
        EqualsFilter filter3 = new EqualsFilter(attrBld.build());
        equalsQueryFnMaul = filterTranslator.createEqualsExpression(filter3, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_FIRST_NAME);
        attrBld.addValue("nonexisting");
        EqualsFilter filter5 = new EqualsFilter(attrBld.build());
        equalsQueryNonExisting = filterTranslator.createEqualsExpression(filter5, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_MS_EMPLOYED);
        attrBld.addValue("1");
        GreaterThanFilter gtFilter = new GreaterThanFilter(attrBld.build());
        gtQueryMs = filterTranslator.createGreaterThanExpression(gtFilter, false);


        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_MS_EMPLOYED);
        attrBld.addValue("9999999");
        LessThanFilter ltFilter = new LessThanFilter(attrBld.build());
        ltQueryMs = filterTranslator.createLessThanExpression(ltFilter, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_YEARS_EMPLOYED);
        attrBld.addValue(ATTR_ACCOUNT_VALUE_YEARS_EPLOYED);
        LessThanOrEqualFilter ltoreqFilter = new LessThanOrEqualFilter(attrBld.build());
        ltoreqQueryYearsEmployed = filterTranslator.createLessThanOrEqualExpression(ltoreqFilter, false);

        attrBld = new AttributeBuilder();
        attrBld.setName(ATTR_ACCOUNT_YEARS_EMPLOYED);
        attrBld.addValue(ATTR_ACCOUNT_VALUE_YEARS_EPLOYED);
        GreaterThanOrEqualFilter gtoreqFilter = new GreaterThanOrEqualFilter(attrBld.build());
        gtoreqQueryYearsEmployed = filterTranslator.createGreaterThanOrEqualExpression(gtoreqFilter, false);

    }

    @Test
    public void searchForFirstnameEqualsDarthShouldReturnOneHit() {
        QueryBuilder queryBuilder = new QueryBuilder(equalsQueryFnVader, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsDarthAndLastnameEqualsVaderShouldReturnOneHit() {
        Query andQuery = filterTranslator.createAndExpression(equalsQueryFnVader, equalsQueryLnVader);
        QueryBuilder queryBuilder = new QueryBuilder(andQuery, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsNonexistingShouldReturnZeroHits() {
        QueryBuilder queryBuilder = new QueryBuilder(equalsQueryNonExisting, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        AssertJUnit.assertEquals(0, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsNonexistingANDFirstnameEqualsDarthShouldReturnOneHit() {
        Query andQuery = filterTranslator.createOrExpression(equalsQueryFnVader, equalsQueryNonExisting);
        QueryBuilder queryBuilder = new QueryBuilder(andQuery, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsDarthANDFirstnameEqualsMaul() {
        Query orQuery = filterTranslator.createOrExpression(equalsQueryFnVader, equalsQueryFnMaul);
        QueryBuilder queryBuilder = new QueryBuilder(orQuery, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        AssertJUnit.assertEquals(2, hits.size());
    }

    @Test
    public void testFunctionQueryExpressionWhereNotIsTrue() {
        final String expected = "fn:not(matches($x/firstname, '123'))";
        String fn = "matches";
        String[] args = {"$x/firstname", "'123'"};
        FunctionQuery functionQuery = new FunctionQuery(args, fn, true);

        AssertJUnit.assertEquals(expected, functionQuery.getExpression());
    }

    @Test
    public void testFunctionQueryExpressionWhereNotIsFalse() {
        String fn = "matches";
        String[] args = {"$x/firstname", "'123'"};
        String expected = "fn:matches($x/firstname, '123')";
        FunctionQuery functionQuery = new FunctionQuery(args, fn, false);

        AssertJUnit.assertEquals(expected, functionQuery.getExpression());
    }

    @Test
    public void searchForFirstnameContainingIghtShouldReturnOneHit() {
        ContainsFilter filter = new ContainsFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "ight"));
        Query query = filterTranslator.createContainsExpression(filter, false);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameNotContainingAShouldReturnOneHit() {
        ContainsFilter filter = new ContainsFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "a"));
        Query query = filterTranslator.createContainsExpression(filter, true);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameStartingWithDShouldReturnOneHit() {
        StartsWithFilter filter = new StartsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "D"));
        Query query = filterTranslator.createStartsWithExpression(filter, false);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameNotStartingWithJShouldReturnTwoHits() {
        StartsWithFilter filter = new StartsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "J"));
        Query query = filterTranslator.createStartsWithExpression(filter, true);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        AssertJUnit.assertEquals(2, hits.size());
    }

    @Test
    public void searchForFirstnameEndingWithHShouldReturnOneHit() {
        EndsWithFilter filter = new EndsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "h"));
        Query query = filterTranslator.createEndsWithExpression(filter, false);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForAllShouldReturnMultipleHits() {
        QueryBuilder queryBuilder = new QueryBuilder(null, ObjectClass.ACCOUNT);
        List<ConnectorObject> hits = new ArrayList<ConnectorObject>(
                xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT));

        AssertJUnit.assertTrue(hits.size() > 0);
    }

    @Test
    public void searchForMSEmployedGT1AndMSEmployedLT999999ShouldReturnOneHit() {
        Query andQuery = filterTranslator.createAndExpression(gtQueryMs, ltQueryMs);
        List<ConnectorObject> hits = getResultsFromQuery(andQuery);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void searchForYearsEmployedGTOREQ200AndYearsEmployedLTOREQ200ShouldReturnOneHit() {
        Query andQuery = filterTranslator.createAndExpression(gtoreqQueryYearsEmployed, ltoreqQueryYearsEmployed);
        List<ConnectorObject> hits = getResultsFromQuery(andQuery);

        AssertJUnit.assertEquals(1, hits.size());
    }

    @Test
    public void testTwoAndExpressionsChainedWithOr() {
        Query vaderAndQuery = filterTranslator.createAndExpression(equalsQueryFnVader, equalsQueryLnVader);
        Query maulAndQuery = filterTranslator.createAndExpression(equalsQueryFnMaul, equalsQueryLnMaul);
        List<ConnectorObject> results = getResultsFromQuery(vaderAndQuery);
        AssertJUnit.assertEquals(1, results.size());

        results = getResultsFromQuery(maulAndQuery);
        AssertJUnit.assertEquals(1, results.size());

        Query orQuery = filterTranslator.createOrExpression(vaderAndQuery, maulAndQuery);
        results = getResultsFromQuery(orQuery);
        AssertJUnit.assertEquals(2, results.size());
    }

    @Test
    public void searchForFirstnameWithContainsAllValuesExpressionWhereValuesEqualsDarthOrLighterShouldReturnTwoHits() {
        ContainsAllValuesFilter filter = new ContainsAllValuesFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, ATTR_ACCOUNT_VALUE_FIRST_NAME, LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME));

        Query query = filterTranslator.createContainsAllValuesExpression(filter, false);
        List<ConnectorObject> results = getResultsFromQuery(query);

        AssertJUnit.assertEquals(2, results.size());
    }

    @Test
    public void searchForFirstnameWithContainsAllVAluesExpressionWhereValuesEqualsDarthShouldReturnOneHit() {
        ContainsAllValuesFilter filter = new ContainsAllValuesFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, ATTR_ACCOUNT_VALUE_FIRST_NAME));

        Query query = filterTranslator.createContainsAllValuesExpression(filter, false);
        List<ConnectorObject> results = getResultsFromQuery(query);

        AssertJUnit.assertEquals(1, results.size());
    }

    @Test
    public void testContainsAllValuesExpressionChainedWithEqualsExpression() {
        ContainsAllValuesFilter filter = new ContainsAllValuesFilter(
                AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME,
                ATTR_ACCOUNT_VALUE_FIRST_NAME, LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME));

        Query query = filterTranslator.createContainsAllValuesExpression(filter, false);
        Query andQuery = filterTranslator.createAndExpression(query, equalsQueryLnVader);

        List<ConnectorObject> results = getResultsFromQuery(andQuery);

        AssertJUnit.assertEquals(1, results.size());
    }

    private List<ConnectorObject> getResultsFromQuery(Query query) {

        return new ArrayList<ConnectorObject>(
                xmlHandler.search(new QueryBuilder(query, ObjectClass.ACCOUNT).toString(), ObjectClass.ACCOUNT));
    }

    @Test
    public void comparisonExpressionsAreUnchanged() {
        AssertJUnit.assertEquals("$x/icf:__NAME__ = 'O''B'", expression(filterTranslator.createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(ATTR_NAME, "O'B")), false)));
        AssertJUnit.assertEquals("$x/icf:__NAME__ != 'x'", expression(filterTranslator.createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(ATTR_NAME, "x")), true)));
        AssertJUnit.assertEquals("$x/ri:lastname >= 'M'", expression(filterTranslator.createGreaterThanOrEqualExpression(
                new GreaterThanOrEqualFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "M")), false)));
        AssertJUnit.assertEquals("$x/ri:hourly-wage = '9.5'", expression(filterTranslator.createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_HOURLY_WAGE, 9.5)), false)));
    }

    @Test
    public void orderingNegationsKeepTheBound() {
        Attribute m = AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "M");
        AssertJUnit.assertEquals("$x/ri:lastname > 'M'",
                expression(filterTranslator.createGreaterThanExpression(new GreaterThanFilter(m), false)));
        AssertJUnit.assertEquals("$x/ri:lastname <= 'M'",
                expression(filterTranslator.createGreaterThanExpression(new GreaterThanFilter(m), true)));
        AssertJUnit.assertEquals("$x/ri:lastname >= 'M'",
                expression(filterTranslator.createGreaterThanOrEqualExpression(new GreaterThanOrEqualFilter(m), false)));
        AssertJUnit.assertEquals("$x/ri:lastname < 'M'",
                expression(filterTranslator.createGreaterThanOrEqualExpression(new GreaterThanOrEqualFilter(m), true)));
        AssertJUnit.assertEquals("$x/ri:lastname < 'M'",
                expression(filterTranslator.createLessThanExpression(new LessThanFilter(m), false)));
        AssertJUnit.assertEquals("$x/ri:lastname >= 'M'",
                expression(filterTranslator.createLessThanExpression(new LessThanFilter(m), true)));
        AssertJUnit.assertEquals("$x/ri:lastname <= 'M'",
                expression(filterTranslator.createLessThanOrEqualExpression(new LessThanOrEqualFilter(m), false)));
        AssertJUnit.assertEquals("$x/ri:lastname > 'M'",
                expression(filterTranslator.createLessThanOrEqualExpression(new LessThanOrEqualFilter(m), true)));
    }

    @Test
    public void startsWithAndEndsWithTestEachValue() {
        Attribute y = AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, "y");
        Attribute quote = AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, "O'B");
        AssertJUnit.assertEquals("$x/ri:email[fn:starts-with(., 'y')]",
                expression(filterTranslator.createStartsWithExpression(new StartsWithFilter(y), false)));
        AssertJUnit.assertEquals("fn:not($x/ri:email[fn:starts-with(., 'y')])",
                expression(filterTranslator.createStartsWithExpression(new StartsWithFilter(y), true)));
        AssertJUnit.assertEquals("$x/ri:email[fn:ends-with(., 'O''B')]",
                expression(filterTranslator.createEndsWithExpression(new EndsWithFilter(quote), false)));
        AssertJUnit.assertEquals("fn:not($x/ri:email[fn:ends-with(., 'O''B')])",
                expression(filterTranslator.createEndsWithExpression(new EndsWithFilter(quote), true)));
    }

    @Test
    public void containsEscapesItsValue() {
        Attribute quote = AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "O'B");
        AssertJUnit.assertEquals("$x/ri:firstname[fn:contains(., 'O''B')]",
                expression(filterTranslator.createContainsExpression(new ContainsFilter(quote), false)));
        AssertJUnit.assertEquals("fn:not($x/ri:firstname[fn:contains(., 'O''B')])",
                expression(filterTranslator.createContainsExpression(new ContainsFilter(quote), true)));
    }

    @Test
    public void literalsEscapeAmpersands() {
        AssertJUnit.assertEquals("$x/ri:lastname = 'AT&amp;T'", expression(filterTranslator.createEqualsExpression(
                new EqualsFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "AT&T")), false)));
        AssertJUnit.assertEquals("$x/ri:email[fn:starts-with(., 'O''B&amp;amp;')]",
                expression(filterTranslator.createStartsWithExpression(
                        new StartsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_EMAIL, "O'B&amp;")), false)));
    }

    @Test
    public void numbersAreComparedAsNumbers() {
        AssertJUnit.assertEquals("$x/ri:age[. != '' and not(number(.) <= xs:double('9'))]",
                expression(filterTranslator.createGreaterThanExpression(
                        new GreaterThanFilter(AttributeBuilder.build(ATTR_ACCOUNT_AGE, 9)), false)));
        AssertJUnit.assertEquals("$x/ri:age[. != '' and not(number(.) > xs:double('9'))]",
                expression(filterTranslator.createGreaterThanExpression(
                        new GreaterThanFilter(AttributeBuilder.build(ATTR_ACCOUNT_AGE, 9)), true)));
        AssertJUnit.assertEquals("$x/ri:age[. != '' and not(number(.) >= xs:double('-5'))]",
                expression(filterTranslator.createLessThanExpression(
                        new LessThanFilter(AttributeBuilder.build(ATTR_ACCOUNT_AGE, -5)), false)));
        AssertJUnit.assertEquals("$x/ri:age[. != '' and not(number(.) <= xs:double('5'))]",
                expression(filterTranslator.createGreaterThanExpression(
                        new GreaterThanFilter(AttributeBuilder.build(ATTR_ACCOUNT_AGE, (byte) 5)), false)));
        AssertJUnit.assertEquals("$x/ri:ms-employed[. != '' and not(number(.) < xs:double('9007199254740992'))]",
                expression(filterTranslator.createGreaterThanExpression(new GreaterThanFilter(
                        AttributeBuilder.build(ATTR_ACCOUNT_MS_EMPLOYED, 9007199254740992L)), false)));
        AssertJUnit.assertEquals("$x/ri:hourly-wage[. != '' and not(number(.) > xs:double('INF'))]",
                expression(filterTranslator.createLessThanExpression(new LessThanFilter(
                        AttributeBuilder.build(ATTR_ACCOUNT_HOURLY_WAGE, Double.POSITIVE_INFINITY)), false)));
        AssertJUnit.assertEquals("$x/ri:hourly-wage[. != '' and not(number(.) < xs:double('-INF'))]",
                expression(filterTranslator.createGreaterThanExpression(new GreaterThanFilter(
                        AttributeBuilder.build(ATTR_ACCOUNT_HOURLY_WAGE, Double.NEGATIVE_INFINITY)), false)));
        AssertJUnit.assertEquals("$x/ri:lastname > 'M'", expression(filterTranslator.createGreaterThanExpression(
                new GreaterThanFilter(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "M")), false)));
    }

    @Test
    public void floatBoundsUseTheEndsOfTheRoundingInterval() {
        // 0.125f is a power of two: the float below it is 2^-27 away, the float above 2^-26.
        AssertJUnit.assertEquals(0.125 - 0x1p-28, XMLFilterTranslator.roundingEdge(0.125f, true), 0.0);
        AssertJUnit.assertEquals(0.125 + 0x1p-27, XMLFilterTranslator.roundingEdge(0.125f, false), 0.0);
    }

    private static String expression(Query query) {
        AssertJUnit.assertEquals(1, query.getParts().size());
        return query.getParts().iterator().next().getExpression();
    }
}
