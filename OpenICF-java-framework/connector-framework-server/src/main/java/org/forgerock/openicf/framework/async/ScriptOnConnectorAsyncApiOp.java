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
import org.identityconnectors.framework.api.operations.ScriptOnConnectorApiOp;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ScriptContext;

/**
 * {@inheritDoc}
 *
 * @since 1.5
 */
public interface ScriptOnConnectorAsyncApiOp extends ScriptOnConnectorApiOp {

    /**
     * Runs the script.
     *
     * @param request
     *            The script and arguments to run.
     * @param options
     *            Additional options that control how the script is run. The
     *            framework does not currently recognize any options but
     *            specific connectors might. Consult the documentation for each
     *            connector to identify supported options.
     * @return The result of the script. The return type must be a type that the
     *         framework supports for serialization.
     * @see org.identityconnectors.framework.common.serializer.ObjectSerializerFactory
     *      for a list of supported return types.
     */
    public Promise<Object, RuntimeException> runScriptOnConnectorAsync(ScriptContext request,
            OperationOptions options);
}
