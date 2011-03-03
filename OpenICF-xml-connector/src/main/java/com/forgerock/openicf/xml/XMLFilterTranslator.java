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
package com.forgerock.openicf.xml;

import com.forgerock.openicf.xml.query.ComparisonQuery;
import com.forgerock.openicf.xml.query.FunctionQuery;
import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.query.QueryImpl;
import com.forgerock.openicf.xml.util.NamespaceLookupUtil;
import java.util.ArrayList;
import java.util.List;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.*;

/**
 * This is an implementation of AbstractFilterTranslator that gives a concrete representation
 * of which filters can be applied at the connector level (natively). If the
 * XQuery doesn't support a certain expression type, that factory
 * method should return null. This level of filtering is present only to allow any
 * native contructs that may be available to help reduce the result set for the framework,
 * which will (strictly) reapply all filters specified after the connector does the initial
 * filtering.<p><p>Note: The generic query type is most commonly a String, but does not have to be.
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
public class XMLFilterTranslator extends AbstractFilterTranslator<Query> {

    @Override
    public Query createEndsWithExpression(EndsWithFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();
        String[] args = createFunctionArgs(prefixedName, value);

        return createFunctionQuery(args, "ends-with", not);
    }

    @Override
    public Query createContainsAllValuesExpression(ContainsAllValuesFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        List<Object> values = filter.getAttribute().getValue();

        int numOfValues = values.size();

        if (numOfValues == 0) {
            return null;
        } else if (numOfValues == 1) {
            ContainsFilter cf = new ContainsFilter(AttributeBuilder.build(attrName, values.get(0)));

            return createContainsExpression(cf, not);
        } else {

            List<Query> equalsQueries = new ArrayList<Query>();

            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i).toString();
                EqualsFilter ef = new EqualsFilter(AttributeBuilder.build(attrName, value));
                Query query = createEqualsExpression(ef, not);
                equalsQueries.add(query);
            }

            Query orQuery = null;
            Query leftSide = equalsQueries.get(0);

            // creates and returns chained or-expression with all values
            for (int i = 1; i < numOfValues; i++) {
                Query rightSide = equalsQueries.get(i);
                orQuery = createOrExpression(leftSide, rightSide);
                if (i != numOfValues) {
                    leftSide = orQuery;
                }
            }

            return orQuery;
        }
    }

    @Override
    public Query createStartsWithExpression(StartsWithFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();
        String[] args = createFunctionArgs(prefixedName, value);

        return createFunctionQuery(args, "starts-with", not);
    }

    @Override
    public Query createContainsExpression(ContainsFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();
        String[] args = createFunctionArgs(prefixedName, value);

        return createFunctionQuery(args, "matches", not);
    }

    @Override
    public Query createEqualsExpression(EqualsFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createComparisonQuery(prefixedName, not ? "!=" : "=", value);
    }

    @Override
    public Query createGreaterThanExpression(GreaterThanFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createComparisonQuery(prefixedName, not ? "<" : ">", value);
    }

    @Override
    public Query createGreaterThanOrEqualExpression(GreaterThanOrEqualFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createComparisonQuery(prefixedName, not ? "<=" : ">=", value);
    }

    @Override
    public Query createAndExpression(Query leftExpression, Query rightExpression) {
        leftExpression.and(rightExpression);

        return leftExpression;
    }

    @Override
    public Query createOrExpression(Query leftExpression, Query rightExpression) {
        leftExpression.or(rightExpression);

        return leftExpression;
    }

    @Override
    public Query createLessThanExpression(LessThanFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createComparisonQuery(prefixedName, not ? ">" : "<", value);
    }

    @Override
    public Query createLessThanOrEqualExpression(LessThanOrEqualFilter filter, boolean not) {
        String attrName = filter.getAttribute().getName();
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createComparisonQuery(prefixedName, not ? ">=" : "<=", value);
    }

    private Query createComparisonQuery(String name, String operator, String value) {
        Query query = new QueryImpl();
        query.set(new ComparisonQuery("$x/" + name, operator, "'" + value + "'"));

        return query;
    }

    private Query createFunctionQuery(String[] args, String function, boolean not) {
        Query query = new QueryImpl();
        query.set(new FunctionQuery(args, function, not));

        return query;
    }

    private String[] createFunctionArgs(String attrName, String value) {
        String[] args = {"$x/" + attrName, "'" + value + "'"};

        return args;
    }

    private String createNameWithNamespace(String attrName) {
        String prefix = NamespaceLookupUtil.INSTANCE.getAttributePrefix(attrName);

        return prefix + ":" + attrName;
    }
}
