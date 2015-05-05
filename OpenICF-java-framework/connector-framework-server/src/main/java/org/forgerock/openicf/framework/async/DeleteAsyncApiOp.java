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
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * {@inheritDoc}
 *
 * @since 1.5
 */
public interface DeleteAsyncApiOp extends DeleteApiOp {

    /**
     * Delete the object that the specified Uid identifies (if any).
     *
     * @param objectClass
     *            type of object to delete.
     * @param uid
     *            The unique id that specifies the object to delete.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     * @throws org.identityconnectors.framework.common.exceptions.UnknownUidException
     *             if the
     *             {@link org.identityconnectors.framework.common.objects.Uid}
     *             does not exist on the resource.
     * @throws RuntimeException
     *             if a problem occurs during the operation (for instance, an
     *             operational timeout).
     */
    public Promise<Void, RuntimeException> deleteAsync(final ObjectClass objectClass,
            final Uid uid, final OperationOptions options);
}
