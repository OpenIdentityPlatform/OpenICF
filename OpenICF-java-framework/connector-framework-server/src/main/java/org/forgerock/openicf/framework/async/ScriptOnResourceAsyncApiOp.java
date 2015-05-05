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
import org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ScriptContext;

/**
 * {@inheritDoc}
 *
 * @since 1.5
 */
public interface ScriptOnResourceAsyncApiOp extends ScriptOnResourceApiOp {

    /**
     * Runs a script on a specific target resource.
     *
     * @param request
     *            The script and arguments to run.
     * @param options
     *            Additional options which control how the script is run. Please
     *            refer to the connector documentation for supported options.
     * @return The result of the script. The return type must be a type that the
     *         connector framework supports for serialization. See
     *         {@link org.identityconnectors.framework.common.serializer.ObjectSerializerFactory}
     *         for a list of supported return types.
     */
    public Promise<Object, RuntimeException> runScriptOnResourceAsync(ScriptContext request,
            OperationOptions options);
}
