/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.spi.operations;

import org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.serializer.ObjectSerializerFactory;

/**
 * Operation that runs a script directly on a target resource.
 * (Compare to {@link ScriptOnConnectorOp}, which runs a script
 * in the context of a particular connector.)
 * <p>
 * A connector that intends to support 
 * {@link ScriptOnResourceApiOp}
 * should implement this interface.  Each connector that implements
 * this interface must document which script languages the connector supports,
 * as well as any supported {@link OperationOptions}.
  */
public interface ScriptOnResourceOp extends SPIOperation {
    /**
     * Run the specified script <i>on the target resource</i>
     * that this connector manages.  
     * @param request The script and arguments to run.
     * @param options Additional options that control 
     *                  how the script is run.
     * @return The result of the script. The return type must be
     * a type that the framework supports for serialization.
     * See {@link ObjectSerializerFactory} for a list of supported types.
     */
    public Object runScriptOnResource(ScriptContext request,
            OperationOptions options);
}
