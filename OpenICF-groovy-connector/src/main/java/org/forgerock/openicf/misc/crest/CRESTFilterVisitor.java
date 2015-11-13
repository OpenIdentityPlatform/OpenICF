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

package org.forgerock.openicf.misc.crest;

import java.util.ArrayList;
import java.util.Collection;

import org.forgerock.util.query.QueryFilter;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
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
 * A CRESTFilterVisitor converts ICF
 * {@link org.identityconnectors.framework.common.objects.filter.Filter} to
 * CREST {@link QueryFilter}.
 *
 * @author Laszlo Hordos
 */
public class CRESTFilterVisitor implements FilterVisitor<QueryFilter<String>, VisitorParameter> {

    public static final CRESTFilterVisitor VISITOR = new CRESTFilterVisitor();

    public QueryFilter visitAndFilter(VisitorParameter parameter, AndFilter subFilters) {
        final Collection<QueryFilter<String>> filters =
                new ArrayList<QueryFilter<String>>(subFilters.getFilters().size());
        for (Filter filter : subFilters.getFilters()) {
            filters.add(filter.accept(this, parameter));
        }
        return QueryFilter.and(filters);
    }

    public QueryFilter visitOrFilter(VisitorParameter parameter, OrFilter subFilters) {
        final Collection<QueryFilter<String>> filters =
                new ArrayList<QueryFilter<String>>(subFilters.getFilters().size());
        for (Filter filter : subFilters.getFilters()) {
            filters.add(filter.accept(this, parameter));
        }
        return QueryFilter.or(filters);
    }

    public QueryFilter visitNotFilter(VisitorParameter parameter, NotFilter subFilter) {
        return QueryFilter.not(subFilter.getFilter().accept(this, parameter));
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

    public QueryFilter visitEqualsFilter(VisitorParameter parameter, EqualsFilter filter) {
        // TODO: Support other then Single values
        return QueryFilter.equalTo(parameter.translateName(filter.getName()), parameter
                .convertValue(filter.getAttribute()));
    }

    public QueryFilter visitContainsAllValuesFilter(VisitorParameter parameter,
            ContainsAllValuesFilter filter) {
        // TODO: Support other then Single values
        return QueryFilter.comparisonFilter(parameter.translateName(filter.getName()), CA,
                parameter.convertValue(filter.getAttribute()));
    }

    // StringFilter

    public QueryFilter visitContainsFilter(VisitorParameter parameter, ContainsFilter filter) {
        return QueryFilter.contains(parameter.translateName(filter.getName()), parameter
                .convertValue(filter.getAttribute()));
    }

    public QueryFilter visitStartsWithFilter(VisitorParameter parameter, StartsWithFilter filter) {
        return QueryFilter.startsWith(parameter.translateName(filter.getName()), parameter
                .convertValue(filter.getAttribute()));
    }

    public QueryFilter visitEndsWithFilter(VisitorParameter parameter, EndsWithFilter filter) {
        return QueryFilter.comparisonFilter(parameter.translateName(filter.getName()), EW,
                parameter.convertValue(filter.getAttribute()));
    }

    // ComparableAttributeFilter

    public QueryFilter visitGreaterThanFilter(VisitorParameter parameter, GreaterThanFilter filter) {
        return QueryFilter.greaterThan(parameter.translateName(filter.getName()), parameter
                .convertValue(filter.getAttribute()));
    }

    public QueryFilter visitGreaterThanOrEqualFilter(VisitorParameter parameter,
            GreaterThanOrEqualFilter filter) {
        return QueryFilter.greaterThanOrEqualTo(parameter.translateName(filter.getName()),
                parameter.convertValue(filter.getAttribute()));
    }

    public QueryFilter visitLessThanFilter(VisitorParameter parameter, LessThanFilter filter) {
        return QueryFilter.lessThan(parameter.translateName(filter.getName()), parameter
                .convertValue(filter.getAttribute()));
    }

    public QueryFilter visitLessThanOrEqualFilter(VisitorParameter parameter,
            LessThanOrEqualFilter filter) {
        return QueryFilter.lessThanOrEqualTo(parameter.translateName(filter.getName()), parameter
                .convertValue(filter.getAttribute()));
    }

    public QueryFilter visitExtendedFilter(VisitorParameter visitorParameter, Filter filter) {
        throw new UnsupportedOperationException("Filter type is not supported: "
                + filter.getClass());
    }
}
