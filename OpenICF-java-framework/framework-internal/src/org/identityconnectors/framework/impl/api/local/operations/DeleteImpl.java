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
package org.identityconnectors.framework.impl.api.local.operations;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.DeleteOp;

public class DeleteImpl extends ConnectorAPIOperationRunner implements
        org.identityconnectors.framework.api.operations.DeleteApiOp {

    /**
     * Initializes the operation works.
     */
    public DeleteImpl(final ConnectorOperationalContext context,
            final Connector connector) {
        super(context,connector);
    }
    /**
     * Calls the delete method on the Connector side.
     * 
     * @see org.identityconnectors.framework.api.operations.CreateApiOp#create(java.util.Set)
     */
    public void delete(final ObjectClass objClass, 
            final Uid uid,
            OperationOptions options) {
        Assertions.nullCheck(objClass, "objClass");
        Assertions.nullCheck(uid, "uid");
        //cast null as empty
        if ( options == null ) {
            options = new OperationOptionsBuilder().build();
        }
        Connector connector = getConnector();
        final ObjectNormalizerFacade normalizer =
            getNormalizer(objClass);
        ((DeleteOp) connector).delete(objClass, 
                (Uid)normalizer.normalizeAttribute(uid),
                options);
    }
}
