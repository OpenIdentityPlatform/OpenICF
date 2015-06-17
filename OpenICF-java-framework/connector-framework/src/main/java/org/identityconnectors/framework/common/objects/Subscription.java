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

package org.identityconnectors.framework.common.objects;

import java.io.Closeable;

import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * A SubscriptionHandler represents a subscription to an asynchronous event
 * channel.
 *
 * @since 1.5
 */
public interface Subscription extends Closeable {

    /**
     * Unsubscribes this {@code SubscriptionHandler} from receiving messages
     * sent to this channel.
     *
     * @see org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp#subscribe(ObjectClass,
     *      Filter, Observer, OperationOptions)
     * @see org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp#subscribe(ObjectClass,
     *      SyncToken, Observer, OperationOptions)
     */
    void close();

    /**
     * Indicates whether this {@code Subscription} is currently unsubscribed.
     *
     * @return {@code true} if this {@code Subscription} is currently
     *         unsubscribed, {@code false} otherwise
     */
    boolean isUnsubscribed();
}
