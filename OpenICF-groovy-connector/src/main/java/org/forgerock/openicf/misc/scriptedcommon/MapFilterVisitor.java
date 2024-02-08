/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.misc.scriptedcommon;

import java.util.LinkedHashMap;
import java.util.Map;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterVisitor;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

/**
 * A MapFilterVisitor converts a
 * {@link org.identityconnectors.framework.common.objects.filter.Filter} to
 * {@link Map}.
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */
public class MapFilterVisitor implements FilterVisitor<Map<String, Object>, Void> {

    public static final MapFilterVisitor INSTANCE = new MapFilterVisitor();

    // @formatter:off
    // The Query map describes the filter used.
    //
    // query = [ operation: "CONTAINS", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "ENDSWITH", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "STARTSWITH", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "EQUALS", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "GREATERTHAN", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "GREATERTHANOREQUAL", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "LESSTHAN", left: attribute, right: "value", not: true/false ]
    // query = [ operation: "LESSTHANOREQUAL", left: attribute, right: "value", not: true/false ]
    // query = null : then we assume we fetch everything
    //
    // AND and OR filter just embed a left/right couple of queries.
    // query = [ operation: "AND", left: query1, right: query2 ]
    // query = [ operation: "OR", left: query1, right: query2 ]
    //
    // @formatter:on
    private Map<String, Object> createMap(String operation, AttributeFilter filter) {
        Map<String, Object> map = new LinkedHashMap<String, Object>(4);
        String name = filter.getAttribute().getName();
        String value = AttributeUtil.getAsStringValue(filter.getAttribute());
        map.put("not", false);
        map.put("operation", operation);
        map.put("left", name);
        map.put("right", value);
        return map;
    }

    public Map<String, Object> visitAndFilter(Void parameter, AndFilter subFilters) {
        Map<String, Object> map = new LinkedHashMap<String, Object>(3);
        map.put("operation", "AND");
        map.put("left", subFilters.getLeft().accept(this, null));
        map.put("right", subFilters.getRight().accept(this, null));
        return map;
    }

    public Map<String, Object> visitContainsAllValuesFilter(Void parameter,
            ContainsAllValuesFilter filter) {
        throw new UnsupportedOperationException(
                "ContainsAllValuesFilter transformation is not supported");
    }

    public Map<String, Object> visitContainsFilter(Void parameter, ContainsFilter filter) {
        return createMap("CONTAINS", filter);
    }

    public Map<String, Object> visitEndsWithFilter(Void parameter, EndsWithFilter filter) {
        return createMap("ENDSWITH", filter);
    }

    public Map<String, Object> visitEqualsFilter(Void parameter, EqualsFilter filter) {
        return createMap("EQUALS", filter);
    }

    public Map<String, Object> visitGreaterThanFilter(Void parameter, GreaterThanFilter filter) {
        return createMap("GREATERTHAN", filter);
    }

    public Map<String, Object> visitGreaterThanOrEqualFilter(Void parameter,
            GreaterThanOrEqualFilter filter) {
        return createMap("GREATERTHANOREQUAL", filter);
    }

    public Map<String, Object> visitLessThanFilter(Void parameter, LessThanFilter filter) {
        return createMap("LESSTHAN", filter);
    }

    public Map<String, Object> visitLessThanOrEqualFilter(Void parameter,
            LessThanOrEqualFilter filter) {
        return createMap("LESSTHANOREQUAL", filter);
    }

    public Map<String, Object> visitNotFilter(Void parameter, NotFilter subFilter) {
        Map<String, Object> map = subFilter.getFilter().accept(this, null);
        map.put("not", true);
        return map;
    }

    public Map<String, Object> visitOrFilter(Void parameter, OrFilter subFilters) {
        Map<String, Object> map = new LinkedHashMap<String, Object>(3);
        map.put("operation", "OR");
        map.put("left", subFilters.getLeft().accept(this, null));
        map.put("right", subFilters.getRight().accept(this, null));
        return map;
    }

    public Map<String, Object> visitStartsWithFilter(Void parameter, StartsWithFilter filter) {
        return createMap("STARTSWITH", filter);
    }

    public Map<String, Object> visitExtendedFilter(Void aVoid, Filter filter) {
        throw new UnsupportedOperationException("Filter type is not supported: "
                + filter.getClass());
    }
}
