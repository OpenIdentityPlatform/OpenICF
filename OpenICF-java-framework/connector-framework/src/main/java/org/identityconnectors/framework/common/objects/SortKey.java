/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS. All Rights Reserved
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

import org.identityconnectors.common.Assertions;

/**
 * A sort key which can be used to specify the order in which connector objects
 * should be included in the results of a search request.
 *
 * @author Laszlo Hordos
 * @since 1.4
 */
public final class SortKey {

    /**
     * Creates a new ascending-order sort key for the provided field.
     *
     * @param field
     *            The sort key field.
     * @return A new ascending-order sort key.
     * @throws IllegalArgumentException
     *             If {@code field} is not a valid JSON pointer.
     */
    public static SortKey ascendingOrder(final String field) {
        return new SortKey(field, true);
    }

    /**
     * Creates a new descending-order sort key for the provided field.
     *
     * @param field
     *            The sort key field.
     * @return A new descending-order sort key.
     * @throws IllegalArgumentException
     *             If {@code field} is not a valid JSON pointer.
     */
    public static SortKey descendingOrder(final String field) {
        return new SortKey(field, false);
    }

    /**
     * Creates a new sort key having the same field as the provided key, but in
     * reverse sort order.
     *
     * @param key
     *            The sort key to be reversed.
     * @return The reversed sort key.
     */
    public static SortKey reverseOrder(final SortKey key) {
        return new SortKey(key.field, !key.isAscendingOrder);
    }

    private final String field;

    private final boolean isAscendingOrder;

    public SortKey(final String field, final boolean isAscendingOrder) {
        this.field = Assertions.blankChecked(field, "field");
        this.isAscendingOrder = isAscendingOrder;
    }

    /**
     * Returns the sort key field.
     *
     * @return The sort key field.
     */
    public String getField() {
        return field;
    }

    /**
     * Returns {@code true} if this sort key is in ascending order, or
     * {@code false} if it is in descending order.
     *
     * @return {@code true} if this sort key is in ascending order, or
     *         {@code false} if it is in descending ord)er.
     */
    public boolean isAscendingOrder() {
        return isAscendingOrder;
    }

    /**
     * Returns the string representation of this sort key. It will be composed
     * of a plus symbol, if the key is ascending, or a minus symbol, if the key
     * is descending, followed by the field name.
     *
     * @return The string representation of this sort key.
     */
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(isAscendingOrder ? '+' : '-');
        builder.append(field);
        return builder.toString();
    }
}
