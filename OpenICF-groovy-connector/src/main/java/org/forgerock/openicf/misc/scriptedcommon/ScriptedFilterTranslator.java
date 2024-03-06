/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS. All rights reserved.
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

import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.*;

/**
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */
public class ScriptedFilterTranslator extends AbstractFilterTranslator<Filter> {

    public static final AbstractFilterTranslator<Filter> INSTANCE = new ScriptedFilterTranslator();

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
