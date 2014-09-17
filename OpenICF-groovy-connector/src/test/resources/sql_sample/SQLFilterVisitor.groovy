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
 *
 */

import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.filter.AndFilter
import org.identityconnectors.framework.common.objects.filter.AttributeFilter
import org.identityconnectors.framework.common.objects.filter.CompositeFilter
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter
import org.identityconnectors.framework.common.objects.filter.ContainsFilter
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.Filter
import org.identityconnectors.framework.common.objects.filter.FilterVisitor
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter
import org.identityconnectors.framework.common.objects.filter.LessThanFilter
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter
import org.identityconnectors.framework.common.objects.filter.NotFilter
import org.identityconnectors.framework.common.objects.filter.OrFilter
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter

import javax.naming.OperationNotSupportedException

/**
 * SQLFilterVisitor converts Filter to SQL String with named Parameters.
 *
 * @author Laszlo Hordos
 */
class SQLFilterVisitor implements FilterVisitor<String, Map<String, Object>> {

    String visitNotFilter(Map<String, Object> params, NotFilter filter) {
        return "NOT ( " + filter.filter.accept(this, null) + " )";
    }

    String visitAndFilter(Map<String, Object> params, AndFilter filter) {
        return visitCompositeFilter(filter, "AND")
    }

    String visitOrFilter(Map<String, Object> params, OrFilter filter) {
        return visitCompositeFilter(filter, "OR")
    }

    def String visitCompositeFilter(final CompositeFilter filter, String operator) {
        StringBuilder sb = new StringBuilder()
        boolean first = true
        for (Filter subFilter : filter.filters) {
            if (first) {
                sb.append("( ").append(subFilter.accept(this, null)).append(" )")
                first = false
            } else {
                sb.append(" ").append(operator).append(" ( ").append(subFilter.accept(this, null)).append(" )")
            }
        }
        return sb.toString();
    }

    def boolean isAlwaysTrue(AttributeFilter filter) {
        return null == filter.attribute || null == filter.attribute.value || filter.attribute.value.isEmpty()
    }

    def String addParameter(final Map<String, Object> params, final Attribute attribute) {
        def name = attribute.name + params.size();
        params.put(name, AttributeUtil.getSingleValue(attribute))
    }

    String visitContainsFilter(Map<String, Object> params, ContainsFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} LIKE :" + addParameter(params, filter.attribute)
        }
    }

    String visitStartsWithFilter(Map<String, Object> params, StartsWithFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} LIKE CONCAT(:" + addParameter(params, filter.attribute) + ", '%')"
        }
    }

    String visitEndsWithFilter(Map<String, Object> params, EndsWithFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} LIKE CONCAT('%',:" + addParameter(params, filter.attribute) + ")"
        }
    }

    String visitEqualsFilter(Map<String, Object> params, EqualsFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} = :" + addParameter(params, filter.attribute)
        }
    }

    String visitGreaterThanFilter(Map<String, Object> params, GreaterThanFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} > :" + addParameter(params, filter.attribute)
        }
    }

    String visitGreaterThanOrEqualFilter(Map<String, Object> params, GreaterThanOrEqualFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} >= :" + addParameter(params, filter.attribute)
        }
    }

    String visitLessThanFilter(Map<String, Object> params, LessThanFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} < :" + addParameter(params, filter.attribute)
        }
    }

    String visitLessThanOrEqualFilter(Map<String, Object> params, LessThanOrEqualFilter filter) {
        if (isAlwaysTrue(filter)) {
            return "TRUE"
        } else {
            return "\${" + filter.name + "} <= :" + addParameter(params, filter.attribute)
        }
    }

    String visitContainsAllValuesFilter(Map<String, Object> stringObjectMap, ContainsAllValuesFilter filter) {
        throw new OperationNotSupportedException("ContainsAll Filter translation is not supported to SQL")
    }

    String visitExtendedFilter(Map<String, Object> stringObjectMap, Filter filter) {
        throw new OperationNotSupportedException("Extended Filter translation is not supported to SQL")
    }
}