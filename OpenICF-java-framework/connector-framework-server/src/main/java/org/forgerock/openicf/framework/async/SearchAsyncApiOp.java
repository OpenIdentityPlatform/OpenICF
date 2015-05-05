/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.async;

import org.forgerock.util.promise.Promise;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * {@inheritDoc}
 *
 * @since 1.5
 */
public interface SearchAsyncApiOp extends SearchApiOp {

    /**
     * Search the resource for all objects that match the object class and
     * filter.
     *
     * @param objectClass
     *            reduces the number of entries to only those that match the
     *            {@link ObjectClass} provided.
     * @param filter
     *            Reduces the number of entries to only those that match the
     *            {@link Filter} provided, if any. May be null.
     * @param handler
     *            class responsible for working with the objects returned from
     *            the search.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     * @return The query result or {@code null}.
     * @throws RuntimeException
     *             if there is problem during the processing of the results.
     */
    public Promise<SearchResult, RuntimeException> searchAsync(final ObjectClass objectClass,
            final Filter filter, final ResultsHandler handler, final OperationOptions options);
}
