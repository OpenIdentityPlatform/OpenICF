/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
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

package org.identityconnectors.framework.common.objects;

import java.util.Objects;

/**
 * The final result of a query request returned after all connector objects
 * matching the request have been returned. In addition to indicating that no
 * more objects are to be returned by the search, the search result will contain
 * page results state information if result paging has been enabled for the
 * search.
 *
 * @since 1.4
 */
public final class SearchResult {

    /**
     * An enum of count policy types.
     *
     * @see OperationOptionsBuilder#setTotalPagedResultsPolicy(CountPolicy)
     * @see SearchResult#getTotalPagedResultsPolicy()
     */
    public enum CountPolicy {
        /**
         * There should be no count returned. No overhead should be incurred.
         */
        NONE,

        /**
         * Estimated count may be used. If no estimation is available it is up
         * to the implementor whether to return an {@link #EXACT} count or
         * {@link #NONE}. It should be known to the client which was used as in
         * {@link SearchResult#getTotalPagedResultsPolicy()}
         */
        ESTIMATE,

        /**
         * Exact count is required.
         */
        EXACT
    }

    /**
     * The value provided when no count is known or can reasonably be supplied.
     */
    public static final int NO_COUNT = -1;

    private final String pagedResultsCookie;
    private final CountPolicy totalPagedResultsPolicy;
    private final int totalPagedResults;
    private final int remainingPagedResults;

    /**
     * Creates a new search result with a {@code null} paged results cookie and
     * no estimate of the total number of remaining results.
     */
    public SearchResult() {
        this(null, CountPolicy.NONE, NO_COUNT, NO_COUNT);
    }

    /**
     * Creates a new search result with the provided paged results cookie and
     * estimate of the total number of remaining results.
     *
     * @param pagedResultsCookie
     *            The opaque cookie which should be used with the next paged
     *            results search request, or {@code null} if paged results were
     *            not requested, or if there are not more pages to be returned.
     * @param remainingPagedResults
     *            An estimate of the total number of remaining results to be
     *            returned in subsequent paged results search requests, or
     *            {@code -1} if paged results were not requested, or if the
     *            total number of remaining results is unknown.
     */
    public SearchResult(final String pagedResultsCookie, final int remainingPagedResults) {
        this(pagedResultsCookie, CountPolicy.NONE, NO_COUNT, remainingPagedResults);
    }

    /**
     * Creates a new query response with the provided paged results cookie and a
     * count of the total number of resources according to
     * {@link #totalPagedResultsPolicy}.
     *
     * @param pagedResultsCookie
     *            The opaque cookie which should be used with the next paged
     *            results query request, or {@code null} if paged results were
     *            not requested, or if there are not more pages to be returned.
     * @param totalPagedResultsPolicy
     *            The policy that was used to calculate
     *            {@link #totalPagedResults}. If none is specified ({@code null}
     *            ), then {@link CountPolicy#NONE} is assumed.
     * @param totalPagedResults
     *            The total number of paged results requested in adherence to
     *            the {@link OperationOptions#getTotalPagedResultsPolicy()} in
     *            the request, or {@link #NO_COUNT} if paged results were not
     *            requested, the count policy is {@code NONE}, or if the total
     *            number of results is unknown.
     * @param remainingPagedResults
     *            An estimate of the total number of remaining results to be
     *            returned in subsequent paged results query requests, or
     *            {@code -1} if paged results were not requested, or if the
     *            total number of remaining results is unknown.
     * @since 1.5
     */
    public SearchResult(String pagedResultsCookie, CountPolicy totalPagedResultsPolicy,
            int totalPagedResults, int remainingPagedResults) {
        this.pagedResultsCookie = pagedResultsCookie;
        if (totalPagedResultsPolicy == null) {
            totalPagedResultsPolicy = CountPolicy.NONE;
        }
        this.totalPagedResultsPolicy = totalPagedResultsPolicy;
        this.totalPagedResults = totalPagedResults;
        this.remainingPagedResults = remainingPagedResults;
    }

    /**
     * Returns the opaque cookie which should be used with the next paged
     * results search request.
     *
     * @return The opaque cookie which should be used with the next paged
     *         results search request, or {@code null} if paged results were not
     *         requested, or if there are not more pages to be returned.
     */
    public String getPagedResultsCookie() {
        return pagedResultsCookie;
    }

    /**
     * Returns the policy that was used to calculate the
     * {@literal totalPagedResults}.
     *
     * @return The count policy.
     * @see #getTotalPagedResults()
     * @since 1.5
     */
    public CountPolicy getTotalPagedResultsPolicy() {
        return totalPagedResultsPolicy;
    }

    /**
     * Returns the total number of paged results in adherence with the
     * {@link OperationOptions#getTotalPagedResultsPolicy()} in the request or
     * {@link #NO_COUNT} if paged results were not requested, the count policy
     * is {@code NONE}, or the total number of paged results is unknown.
     *
     * @return A count of the total number of paged results to be returned in
     *         subsequent paged results query requests, or {@link #NO_COUNT} if
     *         paged results were not requested, or if the total number of paged
     *         results is unknown.
     * @since 1.5
     */
    public int getTotalPagedResults() {
        return totalPagedResults;
    }

    /**
     * Returns an estimate of the total number of remaining results to be
     * returned in subsequent paged results search requests.
     *
     * @return An estimate of the total number of remaining results to be
     *         returned in subsequent paged results search requests, or
     *         {@code -1} if paged results were not requested, or if the total
     *         number of remaining results is unknown.
     */
    public int getRemainingPagedResults() {
        return remainingPagedResults;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SearchResult that = (SearchResult) o;
        return totalPagedResults == that.totalPagedResults
                && Objects.equals(pagedResultsCookie, this.pagedResultsCookie)
                && totalPagedResultsPolicy == that.totalPagedResultsPolicy
                && remainingPagedResults == that.remainingPagedResults;

    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(pagedResultsCookie);
        result = 31 * result + totalPagedResultsPolicy.hashCode();
        result = 31 * result + totalPagedResults;
        result = 31 * result + remainingPagedResults;
        return result;
    }
}
