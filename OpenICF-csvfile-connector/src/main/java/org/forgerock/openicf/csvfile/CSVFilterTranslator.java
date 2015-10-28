/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for
 * the specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file
 * and include the License file at legal/CDDLv1.0.txt. If applicable, add the following
 * below the CDDL Header, with the fields enclosed by brackets [] replaced by your
 * own identifying information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openicf.csvfile;

import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.and;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.not;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.or;

public class CSVFilterTranslator extends AbstractFilterTranslator<Filter> {

    public static final AbstractFilterTranslator<Filter> INSTANCE = new CSVFilterTranslator();

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createAndExpression(final Filter leftExpression, final Filter rightExpression) {
        return and(leftExpression, rightExpression);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createContainsAllValuesExpression(final ContainsAllValuesFilter filter,
                                                       boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createContainsExpression(final ContainsFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createEndsWithExpression(final EndsWithFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createEqualsExpression(final EqualsFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createGreaterThanExpression(final GreaterThanFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createGreaterThanOrEqualExpression(final GreaterThanOrEqualFilter filter,
                                                        boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createLessThanExpression(final LessThanFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createLessThanOrEqualExpression(final LessThanOrEqualFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createOrExpression(final Filter leftExpression, final Filter rightExpression) {
        return or(leftExpression, rightExpression);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter createStartsWithExpression(final StartsWithFilter filter, boolean not) {
        return not ? not(filter) : filter;
    }
}
