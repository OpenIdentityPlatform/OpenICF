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

package org.identityconnectors.framework.spi;

import org.identityconnectors.common.SuccessHandler;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;

/**
 * A SyncTokenResultsHandler is a Callback interface that an application
 * implements in order to handle results from
 * {@link org.identityconnectors.framework.api.operations.SyncApiOp} in a
 * stream-processing fashion.
 *
 * @author Laszlo Hordos
 * @since 1.4
 */
public interface SyncTokenResultsHandler extends SyncResultsHandler, SuccessHandler<SyncToken> {

    /**
     * Invoked when the request has completed successfully.
     *
     * @param result
     *            The sync result indicating that no more resources are to be
     *            returned and, if applicable, including information which
     *            should be used for next sync requests.
     */
     void handleResult(SyncToken result);

}
