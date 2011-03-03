/*
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
import com.forgerock.openicf.xml.XMLHandlerImpl;
import com.forgerock.openicf.xml.query.FunctionQuery;
import com.forgerock.openicf.xml.query.QueryBuilder;
import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.xsdparser.SchemaParser;
import static com.forgerock.openicf.xml.tests.XmlConnectorTestUtil.*;
import java.io.File;
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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class XMLFilterTranslatorTests {

    private static final String XML_FILEPATH = "test/xml_store/test.xml";
    private static final String LOCAL_ATTR_ACCOUNT_VALUE_LAST_NAME = "Maul";
    private static final String LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME = "Lighter";

    private static XMLHandlerImpl xmlHandler;
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

    public XMLFilterTranslatorTests() {}

    @BeforeClass
    public static void setUpClass() throws Exception {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(XML_FILEPATH);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        SchemaParser parser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());

        xmlHandler = new XMLHandlerImpl(config, parser.parseSchema(), parser.getXsdSchema());

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


        filterTranslator = new XMLFilterTranslator();

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

    @AfterClass
    public static void tearDownClass() throws Exception {
        File xmlFile = new File(XML_FILEPATH);

        if(xmlFile.exists()){
            xmlFile.delete();
        }
    }

    @Test
    public void searchForFirstnameEqualsDarthShouldReturnOneHit() {
        QueryBuilder queryBuilder = new QueryBuilder(equalsQueryFnVader, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsDarthAndLastnameEqualsVaderShouldReturnOneHit() {
        Query andQuery = filterTranslator.createAndExpression(equalsQueryFnVader, equalsQueryLnVader);
        QueryBuilder queryBuilder = new QueryBuilder(andQuery, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsNonexistingShouldReturnZeroHits() {
        QueryBuilder queryBuilder = new QueryBuilder(equalsQueryNonExisting, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        assertEquals(0, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsNonexistingANDFirstnameEqualsDarthShouldReturnOneHit() {
        Query andQuery = filterTranslator.createOrExpression(equalsQueryFnVader, equalsQueryNonExisting);
        QueryBuilder queryBuilder = new QueryBuilder(andQuery, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameEqualsDarthANDFirstnameEqualsMaul() {
        Query orQuery = filterTranslator.createOrExpression(equalsQueryFnVader, equalsQueryFnMaul);
        QueryBuilder queryBuilder = new QueryBuilder(orQuery, ObjectClass.ACCOUNT);
        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT);

        assertEquals(2, hits.size());
    }

    @Test
    public void testFunctionQueryExpressionWhereNotIsTrue() {
        final String expected = "fn:not(matches($x/firstname, '123'))";
        String fn = "matches";
        String [] args = {"$x/firstname", "'123'"};
        FunctionQuery functionQuery = new FunctionQuery(args, fn, true);

        assertEquals(expected, functionQuery.getExpression());
    }

    @Test
    public void testFunctionQueryExpressionWhereNotIsFalse() {
        String fn = "matches";
        String [] args = {"$x/firstname", "'123'"};
        String expected = "fn:matches($x/firstname, '123')";
        FunctionQuery functionQuery = new FunctionQuery(args, fn, false);

        assertEquals(expected, functionQuery.getExpression());
    }

    @Test
    public void searchForFirstnameContainingIghtShouldReturnOneHit() {
        ContainsFilter filter = new ContainsFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "ight"));
        Query query = filterTranslator.createContainsExpression(filter, false);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameNotContainingOShouldReturnTwoHits() {
        ContainsFilter filter = new ContainsFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "o"));
        Query query = filterTranslator.createContainsExpression(filter, true);
        List<ConnectorObject> hits = getResultsFromQuery(query);


        assertEquals(2, hits.size());
    }

    @Test
    public void searchForFirstnameStartingWithDShouldReturnOneHit() {
        StartsWithFilter filter = new StartsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "D"));
        Query query = filterTranslator.createStartsWithExpression(filter, false);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForFirstnameNotStartingWithJShouldReturnTwoHits() {
        StartsWithFilter filter = new StartsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "J"));
        Query query = filterTranslator.createStartsWithExpression(filter, true);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        assertEquals(2, hits.size());
    }

    @Test
    public void searchForFirstnameEndingWithHShouldReturnOneHit() {
        EndsWithFilter filter = new EndsWithFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "h"));
        Query query = filterTranslator.createEndsWithExpression(filter, false);
        List<ConnectorObject> hits = getResultsFromQuery(query);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForAllShouldReturnMultipleHits() {
        QueryBuilder queryBuilder = new QueryBuilder(null, ObjectClass.ACCOUNT);
        List<ConnectorObject> hits = new ArrayList<ConnectorObject>(
                xmlHandler.search(queryBuilder.toString(), ObjectClass.ACCOUNT));

        assertTrue(hits.size() > 0);
    }

    @Test
    public void searchForMSEmployedGT1AndMSEmployedLT999999ShouldReturnOneHit() {
        Query andQuery = filterTranslator.createAndExpression(gtQueryMs, ltQueryMs);
        List<ConnectorObject> hits = getResultsFromQuery(andQuery);

        assertEquals(1, hits.size());
    }

    @Test
    public void searchForYearsEmployedGTOREQ200AndYearsEmployedLTOREQ200ShouldReturnOneHit() {
        Query andQuery = filterTranslator.createAndExpression(gtoreqQueryYearsEmployed, ltoreqQueryYearsEmployed);
        List<ConnectorObject> hits = getResultsFromQuery(andQuery);

        assertEquals(1, hits.size());
    }

    @Test
    public void testTwoAndExpressionsChainedWithOr() {
        Query vaderAndQuery = filterTranslator.createAndExpression(equalsQueryFnVader, equalsQueryLnVader);
        Query maulAndQuery = filterTranslator.createAndExpression(equalsQueryFnMaul, equalsQueryLnMaul);
        List<ConnectorObject> results = getResultsFromQuery(vaderAndQuery);
        assertEquals(1, results.size());

        results = getResultsFromQuery(maulAndQuery);
        assertEquals(1, results.size());

        Query orQuery = filterTranslator.createOrExpression(vaderAndQuery, maulAndQuery);
        results = getResultsFromQuery(orQuery);
        assertEquals(2, results.size());
    }

    @Test
    public void searchForFirstnameWithContainsAllValuesExpressionWhereValuesEqualsDarthOrLighterShouldReturnTwoHits() {
        ContainsAllValuesFilter filter = new ContainsAllValuesFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, ATTR_ACCOUNT_VALUE_FIRST_NAME, LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME));

        Query query = filterTranslator.createContainsAllValuesExpression(filter, false);
        List<ConnectorObject> results = getResultsFromQuery(query);

        assertEquals(2, results.size());
    }

    @Test
    public void searchForFirstnameWithContainsAllVAluesExpressionWhereValuesEqualsDarthShouldReturnOneHit() {
        ContainsAllValuesFilter filter = new ContainsAllValuesFilter(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, ATTR_ACCOUNT_VALUE_FIRST_NAME));

        Query query = filterTranslator.createContainsAllValuesExpression(filter, false);
        List<ConnectorObject> results = getResultsFromQuery(query);

        assertEquals(1, results.size());
    }

    @Test
    public void testContainsAllValuesExpressionChainedWithEqualsExpression() {
        ContainsAllValuesFilter filter = new ContainsAllValuesFilter(
                AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME,
                ATTR_ACCOUNT_VALUE_FIRST_NAME, LOCAL_ATTR_ACCOUNT_VALUE_FIRST_NAME));

        Query query = filterTranslator.createContainsAllValuesExpression(filter, false);
        Query andQuery = filterTranslator.createAndExpression(query, equalsQueryLnVader);

        List<ConnectorObject> results = getResultsFromQuery(andQuery);

        assertEquals(1, results.size());
    }

    private List<ConnectorObject> getResultsFromQuery(Query query) {
        return new ArrayList<ConnectorObject>(
            xmlHandler.search(new QueryBuilder(query, ObjectClass.ACCOUNT).toString(), ObjectClass.ACCOUNT));
    }
}