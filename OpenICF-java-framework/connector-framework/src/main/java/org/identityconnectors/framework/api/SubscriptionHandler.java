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

package org.identityconnectors.framework.api;

import org.identityconnectors.common.FailureHandler;

/**
 * A SubscriptionHandler represents a subscription to an asynchronous event
 * channel.
 *
 * @since 1.5
 */
public interface SubscriptionHandler {

    /**
     * Unsubscribes this {@code SubscriptionHandler} from receiving messages
     * sent to this channel.
     *
     * @see org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp#subscribe(org.identityconnectors.framework.common.objects.ObjectClass,
     *      org.identityconnectors.framework.common.objects.filter.Filter,
     *      org.identityconnectors.framework.common.objects.ResultsHandler,
     *      org.identityconnectors.framework.common.objects.OperationOptions)
     * @see org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp#subscribe(org.identityconnectors.framework.common.objects.ObjectClass,
     *      org.identityconnectors.framework.common.objects.SyncToken,
     *      org.identityconnectors.framework.common.objects.SyncResultsHandler,
     *      org.identityconnectors.framework.common.objects.OperationOptions)
     */
    void unsubscribe();

    /**
     * Registers the provided completion handler for notification if this
     * {@code SubscriptionHandler} does not complete successfully. If this
     * {@code SubscriptionHandler} completes successfully then the completion
     * handler will not be notified.
     * <p>
     * This method can be used for asynchronous completion notification.
     *
     * @param onFailure
     *            The completion handler which will be notified upon
     *            unsuccessful completion of this {@code SubscriptionHandler}.
     */
    void onFailure(FailureHandler<RuntimeException> onFailure);
}
