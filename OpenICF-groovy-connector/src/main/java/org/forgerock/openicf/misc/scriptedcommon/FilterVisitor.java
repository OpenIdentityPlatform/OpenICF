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

import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

/**
 * A visitor of {@code Filter}s, in the style of the visitor design pattern.
 * <p>
 * Classes implementing this interface can query filters in a type-safe manner.
 * When a visitor is passed to a filter's accept method, the corresponding visit
 * method most applicable to that filter is invoked.
 *
 * @param <R>
 *            The return type of this visitor's methods. Use
 *            {@link java.lang.Void} for visitors that do not need to return
 *            results.
 * @param <P>
 *            The type of the additional parameter to this visitor's methods.
 *            Use {@link java.lang.Void} for visitors that do not need an
 *            additional parameter.
 */
public interface FilterVisitor<P, R> {

    /**
     * Visits an {@code and} filter.
     * <p>
     * <b>Implementation note</b>: for the purposes of matching, an empty
     * sub-filter list should always evaluate to {@code true}.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param subFilters
     *            The unmodifiable list of sub-filters.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, AndFilter subFilters);

    /**
     * Visits a {@code contains all} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, ContainsAllValuesFilter filter);

    /**
     * Visits a {@code contains} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, ContainsFilter filter);

    /**
     * Visits a {@code ends with} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, EndsWithFilter filter);

    /**
     * Visits a {@code equality} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, EqualsFilter filter);

    /**
     * Visits a {@code greater than} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, GreaterThanFilter filter);

    /**
     * Visits a {@code greater than or equal to} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, GreaterThanOrEqualFilter filter);

    /**
     * Visits a {@code less than} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, LessThanFilter filter);

    /**
     * Visits a {@code less than or equal to} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, LessThanOrEqualFilter filter);

    /**
     * Visits a {@code not} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param subFilter
     *            The sub-filter.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, NotFilter subFilter);

    /**
     * Visits an {@code or} filter.
     * <p>
     * <b>Implementation note</b>: for the purposes of matching, an empty
     * sub-filter list should always evaluate to {@code false}.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param subFilters
     *            The unmodifiable list of sub-filters.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, OrFilter subFilters);

    /**
     * Visits a {@code starts with} filter.
     *
     * @param parameter
     *            A visitor specified parameter.
     * @param filter
     *            The value assertion.
     * @return Returns a visitor specified result.
     */
    public R visit(P parameter, StartsWithFilter filter);
}
