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
package org.identityconnectors.framework.api.operations;

import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;


public interface UpdateApiOp extends APIOperation {
    /**
     * Determines the type of update to perform.
     */
    public enum Type {
        /**
         * Replace each attribute value with the one provided.
         */
        REPLACE,
        /**
         * Added the values provided to the existing attribute values on the
         * native target.
         */
        ADD,
        /**
         * Remove the attribute values from the existing target values.
         */
        DELETE
    }

    /**
     * Update the object specified by the {@link ObjectClass} and {@link Uid}.
     * The type is used to determine if the updates are additive, subtractive,
     * or replacement of values provided.
     * 
     * @param type
     *            determines the type of update to expect.
     * @param objclass
     *            the type of object to modify.
     * @param attrs
     *            set of {@link Attribute} deltas with their values fully
     *            merged. The set will also include the {@link Uid} of the
     *            object.
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     * @return the {@link Uid} of the updated object in case the update changes
     *         the formation of the unique identifier.
     */
    public Uid update(final Type type, final ObjectClass objclass,
            final Set<Attribute> attrs,
            final OperationOptions options);

}
