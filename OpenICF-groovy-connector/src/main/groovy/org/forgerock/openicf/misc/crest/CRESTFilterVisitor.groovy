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

package org.forgerock.openicf.misc.crest

import org.forgerock.json.resource.QueryFilter
import org.forgerock.openicf.misc.scriptedcommon.AbstractFilterVisitor
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.filter.AndFilter
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter
import org.identityconnectors.framework.common.objects.filter.ContainsFilter
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter
import org.identityconnectors.framework.common.objects.filter.LessThanFilter
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter
import org.identityconnectors.framework.common.objects.filter.NotFilter
import org.identityconnectors.framework.common.objects.filter.OrFilter
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter

/**
 * A CRESTFilterVisitor converts ICF {@link org.identityconnectors.framework.common.objects.filter.Filter}
 * to CREST {@link QueryFilter}.
 *
 * @author Laszlo Hordos
 */
class CRESTFilterVisitor extends AbstractFilterVisitor<Void, QueryFilter> {

    static final CRESTFilterVisitor VISITOR = new CRESTFilterVisitor()

    QueryFilter visit(Void parameter, AndFilter subFilters) {
        QueryFilter left = this.accept(parameter, subFilters.left)
        QueryFilter right = this.accept(parameter, subFilters.right)
        return QueryFilter.and(left, right)
    }

    QueryFilter visit(Void parameter, OrFilter subFilters) {
        QueryFilter left = this.accept(parameter, subFilters.left)
        QueryFilter right = this.accept(parameter, subFilters.right)
        return QueryFilter.or(left, right)
    }

    QueryFilter visit(Void parameter, NotFilter subFilter) {
        return QueryFilter.not(this.accept(parameter, subFilter.filter))
    }

    /**
     * EndsWith filter
     */
    private static final String EW = "ew";

    /**
     * ContainsAll filter
     */
    private static final String CA = "ca";

    // AttributeFilter

    QueryFilter visit(Void parameter, EqualsFilter filter) {
        //TODO: Support other then Single values
        return QueryFilter.equalTo(filter.name, AttributeUtil.getSingleValue(filter.attribute))
    }

    QueryFilter visit(Void parameter, ContainsAllValuesFilter filter) {
        //TODO: Support other then Single values
        return QueryFilter.comparisonFilter(filter.name, CA, AttributeUtil.getSingleValue(filter.attribute))
    }

    // StringFilter

    QueryFilter visit(Void parameter, ContainsFilter filter) {
        return QueryFilter.contains(filter.name, AttributeUtil.getStringValue(filter.attribute))
    }

    QueryFilter visit(Void parameter, StartsWithFilter filter) {
        return QueryFilter.startsWith(filter.name, AttributeUtil.getSingleValue(filter.attribute))
    }

    QueryFilter visit(Void parameter, EndsWithFilter filter) {
        return QueryFilter.comparisonFilter(filter.name, EW, AttributeUtil.getStringValue(filter.attribute))
    }

    // ComparableAttributeFilter

    QueryFilter visit(Void parameter, GreaterThanFilter filter) {
        return QueryFilter.greaterThan(filter.name, AttributeUtil.getSingleValue(filter.attribute))
    }

    QueryFilter visit(Void parameter, GreaterThanOrEqualFilter filter) {
        return QueryFilter.greaterThanOrEqualTo(filter.name, AttributeUtil.getSingleValue(filter.attribute))
    }

    QueryFilter visit(Void parameter, LessThanFilter filter) {
        return QueryFilter.lessThan(filter.name, AttributeUtil.getSingleValue(filter.attribute))
    }

    QueryFilter visit(Void parameter, LessThanOrEqualFilter filter) {
        return QueryFilter.lessThanOrEqualTo(filter.name, AttributeUtil.getSingleValue(filter.attribute))
    }

}
