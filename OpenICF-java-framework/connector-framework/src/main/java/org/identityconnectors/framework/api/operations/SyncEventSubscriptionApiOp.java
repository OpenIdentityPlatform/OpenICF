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

package org.identityconnectors.framework.api.operations;

import org.identityconnectors.framework.api.SubscriptionHandler;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;

/**
 *
 * @since 1.5
 */
public interface SyncEventSubscriptionApiOp extends APIOperation {

    /**
     * Create a subscription to a given sync topic.
     * 
     * @param objectClass
     * @param handler
     * @param operationOptions
     * @return
     * @throws java.lang.RuntimeException
     *             when the operation failed to create subscription.
     */
    SubscriptionHandler subscribe(ObjectClass objectClass, SyncToken token,
            SyncResultsHandler handler, OperationOptions operationOptions);
}
