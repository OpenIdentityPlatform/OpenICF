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
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.forgerock.openicf.connectors.xml;

import org.forgerock.openicf.connectors.xml.query.ComparisonQuery;
import org.forgerock.openicf.connectors.xml.query.FunctionQuery;
import org.forgerock.openicf.connectors.xml.query.abstracts.Query;
import org.forgerock.openicf.connectors.xml.query.QueryImpl;
import org.forgerock.openicf.connectors.xml.util.NamespaceLookupUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * This is an implementation of AbstractFilterTranslator that gives a concrete representation
 * of which filters can be applied at the connector level (natively). If the
 * XQuery doesn't support a certain expression type, that factory
 * method should return null. This level of filtering is present only to allow any
 * native contructs that may be available to help reduce the result set for the framework,
 * which will (strictly) reapply all filters specified after the connector does the initial
 * filtering.
 * <p>Note: The generic query type is most commonly a String, but does not have to be.
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
public class XMLFilterTranslator extends AbstractFilterTranslator<Query> {

    private boolean preferUidOverName = false;

    public XMLFilterTranslator(boolean preferUidOverName) {
        this.preferUidOverName = preferUidOverName;
    }

    private String getAttributeName(Attribute attribute) {
        return elementName(attribute.getName(), preferUidOverName);
    }

    /**
     * The element an attribute is stored in: {@code __UID__} is stored as {@code __NAME__} unless
     * {@code preferUidOverName}. XMLHandlerImpl looks entries up by the same rule.
     */
    static String elementName(String attributeName, boolean preferUidOverName) {
        return !preferUidOverName && Uid.NAME.equals(attributeName) ? Name.NAME : attributeName;
    }

    @Override
    public Query createEndsWithExpression(EndsWithFilter filter, boolean not) {
        String attrName = getAttributeName(filter.getAttribute());
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createPredicateQuery(prefixedName, createFunctionCondition("ends-with", value), not);
    }

    @Override
    public Query createContainsAllValuesExpression(ContainsAllValuesFilter filter, boolean not) {
        String attrName = getAttributeName(filter.getAttribute());
        List<Object> values = filter.getAttribute().getValue();

        int numOfValues = values.size();

        // null leaves the filter to the framework: every entry is returned for it to check.
        if (numOfValues == 0 || values.contains(null)) {
            return null;
        } else if (numOfValues == 1) {
            // Equal values, as below: a stored x1 does not contain the value x.
            EqualsFilter ef = new EqualsFilter(AttributeBuilder.build(attrName, values.get(0).toString()));

            return createEqualsExpression(ef, not);
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
                leftSide = orQuery;
            }

            return orQuery;
        }
    }

    @Override
    public Query createStartsWithExpression(StartsWithFilter filter, boolean not) {
        String attrName = getAttributeName(filter.getAttribute());
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createPredicateQuery(prefixedName, createFunctionCondition("starts-with", value), not);
    }

    @Override
    public Query createContainsExpression(ContainsFilter filter, boolean not) {
        String attrName = getAttributeName(filter.getAttribute());
        String prefixedName = createNameWithNamespace(attrName);
        String value = AttributeUtil.getSingleValue(filter.getAttribute()).toString();

        return createPredicateQuery(prefixedName, createFunctionCondition("contains", value), not);
    }

    @Override
    public Query createEqualsExpression(EqualsFilter filter, boolean not) {
        String attrName = getAttributeName(filter.getAttribute());
        String prefixedName = createNameWithNamespace(attrName);
        List<Object> values = filter.getAttribute().getValue();
        // The framework compares whole lists of values; null leaves anything but one value to it.
        if (values == null || values.size() != 1 || values.get(0) == null) {
            return null;
        }
        String value = values.get(0).toString();

        return createComparisonQuery(prefixedName, not ? "!=" : "=", value);
    }

    @Override
    public Query createGreaterThanExpression(GreaterThanFilter filter, boolean not) {
        return createOrderingQuery(filter.getAttribute(), not ? "<=" : ">");
    }

    @Override
    public Query createGreaterThanOrEqualExpression(GreaterThanOrEqualFilter filter, boolean not) {
        return createOrderingQuery(filter.getAttribute(), not ? "<" : ">=");
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
        return createOrderingQuery(filter.getAttribute(), not ? ">=" : "<");
    }

    @Override
    public Query createLessThanOrEqualExpression(LessThanOrEqualFilter filter, boolean not) {
        return createOrderingQuery(filter.getAttribute(), not ? ">" : "<=");
    }

    /** {@code attribute operator value}: numerically for a {@link Number} value, as strings otherwise. */
    private Query createOrderingQuery(Attribute attribute, String operator) {
        String prefixedName = createNameWithNamespace(getAttributeName(attribute));
        Object value = AttributeUtil.getSingleValue(attribute);

        if (value instanceof Number) {
            return createPredicateQuery(prefixedName, numericCondition(operator, (Number) value), false);
        }
        return createComparisonQuery(prefixedName, operator, value.toString());
    }

    /**
     * The condition a stored value meets if {@code value operator bound} may hold, where
     * {@code operator} is one of {@code >}, {@code >=}, {@code <} and {@code <=}: that the value
     * is not excluded, {@code . != '' and not(number(.) <= xs:double('9'))} for {@code > 9}.
     * <ul>
     * <li>Text that Java parses and XPath does not, such as {@code Infinity} or non-Latin digits,
     * reads as NaN, and {@code not(NaN <= x)} holds.</li>
     * <li>{@code . != ''} skips the empty elements of unset attributes.</li>
     * </ul>
     * An integer bound of magnitude below 2^53 keeps a strict operator strict: it is a double, and
     * no other integer reads as it. Any other bound only excludes the values on the wrong side of
     * it, {@code not(number(.) < xs:double('L'))} for both {@code >} and {@code >=} (mirrored for
     * {@code <} and {@code <=}). This adds the values that read as equal to the bound:
     * <ul>
     * <li>Reading a decimal as a double is monotonic, so a value above the bound never reads
     * below it.</li>
     * <li>{@code -0.0} and {@code 0.0} are equal in XPath.</li>
     * </ul>
     * The literal is the value's own {@code toString()}, except for a {@link Float}: see
     * {@link #roundingEdge}.
     */
    static String numericCondition(String operator, Number value) {
        boolean lower = operator.startsWith(">");
        boolean strict = operator.length() == 1 && isExactInDouble(value);
        String excluded = (lower ? "<" : ">") + (strict ? "=" : "");
        String literal = value instanceof Float
                ? Double.toString(roundingEdge((Float) value, lower)) : value.toString();
        String bound = "xs:double('" + literal.replace("Infinity", "INF") + "')";

        return ". != '' and not(number(.) " + excluded + " " + bound + ")";
    }

    /**
     * The lower or upper end of the decimals Java reads as the float, exact as a double. XPath
     * reads a stored float as a double, and its text may be any of those decimals: before
     * JDK 19, {@code Float.toString} wrote some floats with more digits, such as
     * {@code 3.3565272E7} for what it now writes as {@code 3.356527E7}.
     */
    static double roundingEdge(float value, boolean lower) {
        return lower ? ((double) Math.nextDown(value) + value) / 2 : ((double) value + Math.nextUp(value)) / 2;
    }

    /** Whether the value is an integer of magnitude below 2^53, which every double holds exactly. */
    static boolean isExactInDouble(Number value) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return true;
        }
        if (value instanceof Long) {
            long v = value.longValue();
            // Not Math.abs: it overflows on Long.MIN_VALUE.
            return -(1L << 53) < v && v < (1L << 53);
        }
        if (value instanceof BigInteger) {
            // abs(): the signed bitLength() of -2^53 is 53.
            return ((BigInteger) value).abs().bitLength() <= 53;
        }
        return false;
    }

    private Query createComparisonQuery(String name, String operator, String value) {
        Query query = new QueryImpl();
        query.set(new ComparisonQuery("$x/" + name, operator, value, escape(value)));

        return query;
    }

    /**
     * Matches an entry if one of its values meets the condition: {@code $x/name[condition]}, with
     * {@code .} standing for the value. Unlike {@code fn:starts-with($x/name, ...)}, this does not
     * fail when an entry has several values.
     */
    private Query createPredicateQuery(String name, String condition, boolean not) {
        String predicate = "$x/" + name + "[" + condition + "]";
        String expression = not ? "fn:not(" + predicate + ")" : predicate;
        Query query = new QueryImpl();
        query.set(() -> expression);

        return query;
    }

    private String createFunctionCondition(String function, String value) {
        return new FunctionQuery(new String[] {".", escape(value)}, function, false).getExpression();
    }

    private String createNameWithNamespace(String attrName) {
        String prefix = NamespaceLookupUtil.INSTANCE.getAttributePrefix(attrName);

        return prefix + ":" + attrName;
    }
    
    /** The value as an XQuery string literal, which decodes entity references such as {@code &amp;}. */
    private String escape(String value) {
        return "'" + value.replace("&", "&amp;").replace("'", "''") + "'";
    }
}
