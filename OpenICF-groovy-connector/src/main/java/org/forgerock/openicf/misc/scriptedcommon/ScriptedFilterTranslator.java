/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock Inc. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

/**
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 *
 */
public class ScriptedFilterTranslator extends AbstractFilterTranslator<Map> {

    /**
     * {@inheritDoc}
     */
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
    private Map<String,Object> createMap(String operation, AttributeFilter filter, boolean not) {
        Map<String,Object> map = new HashMap<String,Object>();
        String name = filter.getAttribute().getName();
        String value = AttributeUtil.getAsStringValue(filter.getAttribute());
        if (StringUtil.isBlank(value)) {
            return null;
        } else {
            map.put("not", not);
            map.put("operation", operation);
            map.put("left", name);
            map.put("right", value);
            return map;
        }
    }

    @Override
    protected Map<String,Object> createContainsExpression(ContainsFilter filter, boolean not) {
        return createMap("CONTAINS", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createEndsWithExpression(EndsWithFilter filter, boolean not) {
        return createMap("ENDSWITH", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createStartsWithExpression(StartsWithFilter filter, boolean not) {
        return createMap("STARTSWITH", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createEqualsExpression(EqualsFilter filter, boolean not) {
        return createMap("EQUALS", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createAndExpression(Map leftExpression, Map rightExpression) {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("operation", "AND");
        map.put("left", leftExpression);
        map.put("right", rightExpression);
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createOrExpression(Map leftExpression, Map rightExpression) {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("operation", "OR");
        map.put("left", leftExpression);
        map.put("right", rightExpression);
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createGreaterThanExpression(GreaterThanFilter filter, boolean not) {
        return createMap("GREATERTHAN", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createGreaterThanOrEqualExpression(GreaterThanOrEqualFilter filter, boolean not) {
        return createMap("GREATERTHANOREQUAL", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createLessThanExpression(LessThanFilter filter, boolean not) {
        return createMap("LESSTHAN", filter, not);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Object> createLessThanOrEqualExpression(LessThanOrEqualFilter filter, boolean not) {
        return createMap("LESSTHANOREQUAL", filter, not);
    }
}
