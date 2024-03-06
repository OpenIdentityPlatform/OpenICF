/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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

import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.AndFilter
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


public class CypherFilterVisitor implements FilterVisitor<String, Object> {

    private ObjectClass objectClass;

    CypherFilterVisitor(ObjectClass objectClass) {
        this.objectClass = objectClass
    }

    String visitOrFilter(Object o, OrFilter filter) {
        return createCompositeFilter(filter, o, "OR")
    }

    String visitAndFilter(Object o, AndFilter filter) {
        return createCompositeFilter(filter, o, "AND")
    }

    String createCompositeFilter(CompositeFilter filter, Object param, String type) {
        def cypher = new StringBuilder()
        if (filter.filters.any()) {
            boolean first = true;
            cypher.append('(')
            filter.filters.each {
                if (first) {
                    first = false
                } else {
                    cypher.append(' ').append(type).append(' ')
                }
                cypher.append(it.accept(this, param))
            }
            cypher.append(')')
        }

        return cypher.toString()
    }

    static String createPropertyName(Attribute attribute) {
        if (attribute.is(Uid.NAME) || attribute.is(Name.NAME)) {
            return "ID(n)"
        } else if (AttributeUtil.isSpecial(attribute)) {
            //Apply the mapping
            throw new OperationNotSupportedException("Special attribute name not supported")
        } else {
            return "n." + attribute.name
        }
    }

    String visitNotFilter(Object o, NotFilter filter) {
        return "NOT ( ${filter.filter.accept(this, o)} )"
    }

    String visitContainsAllValuesFilter(Object o, ContainsAllValuesFilter filter) {
        return null
    }

    String visitContainsFilter(Object o, ContainsFilter filter) {
        return "${createPropertyName(filter.attribute)} =~ '.*${filter.value}.*'"
    }

    String visitStartsWithFilter(Object o, StartsWithFilter filter) {
        return "${createPropertyName(filter.attribute)} =~ '^${filter.value}.*'"
    }

    String visitEndsWithFilter(Object o, EndsWithFilter filter) {
        return "${createPropertyName(filter.attribute)} =~ '.*${filter.value}\$'"
    }

    String visitEqualsFilter(Object o, EqualsFilter filter) {
        def value = AttributeUtil.getSingleValue(filter.attribute)
        def cypher = new StringBuilder(createPropertyName(filter.attribute)).append(" = ")
        if (filter.attribute.is(Uid.NAME) || filter.attribute.is(Name.NAME)) {
            value = value as long
        }
        if (value instanceof String) {
            cypher.append("'").append(value).append("'")
        } else if (value instanceof Number) {
            cypher.append(value)
        } else if (value instanceof Boolean) {
            if (value as Boolean) {
                cypher.append("TRUE")
            } else {
                cypher.append("FALSE")
            }
        } else {
            throw new OperationNotSupportedException("Not supported type")
        }

        return cypher.toString()
    }

    String visitGreaterThanFilter(Object o, GreaterThanFilter filter) {
        def value = filter.value;
        if (value instanceof Number) {
            return "${createPropertyName(filter.attribute)} > ${filter.value}"
        }
        throw new OperationNotSupportedException("Not supported type")
    }

    String visitGreaterThanOrEqualFilter(Object o, GreaterThanOrEqualFilter filter) {
        def value = filter.value;
        if (value instanceof Number) {
            return "${createPropertyName(filter.attribute)} >= ${filter.value}"
        }
        throw new OperationNotSupportedException("Not supported type")
    }

    String visitLessThanFilter(Object o, LessThanFilter filter) {
        def value = filter.value;
        if (value instanceof Number) {
            return "${createPropertyName(filter.attribute)} < ${filter.value}"
        }
        throw new OperationNotSupportedException("Not supported type")
    }

    String visitLessThanOrEqualFilter(Object o, LessThanOrEqualFilter filter) {
        def value = filter.value;
        if (value instanceof Number) {
            return "${createPropertyName(filter.attribute)} <= ${filter.value}"
        }
        throw new OperationNotSupportedException("Not supported type")
    }

    String visitExtendedFilter(Object o, Filter filter) {
        return null
    }
}