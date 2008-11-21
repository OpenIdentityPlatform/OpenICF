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

import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;


/**
 * The developer of a Connector should implement either this interface or the
 * {@link AdvancedUpdateOp} interface if the Connector will allow an authorized
 * caller to update (i.e., modify or replace) objects on the target resource.
 * <p>
 * This update method modifies a target object based on the specified deltas.
 * The input set of {@code Attribute} instances contains the {@link Uid} necessary to find the
 * object in question. The rest of the input {@code Attribute} instances are deltas.
 * <p>
 * This update method is simpler to implement than {@code AdvancedUpdateOp},
 * which must handle any of several different types of update that the caller
 * may specify.
 * <p>
 * The developer of a {@code Connector} needs to implement only one of
 * {@code UpdateOp} or {@code AdvancedUpdateOp}; there is no need to implement
 * both. The common code in the framework prefers {@code AdvanceUpdateOp}
 * if {@code AdvanceUpdateOp} is implemented. If
 * {@code AdvanceUpdateOp} is not implemented, then the common code in
 * the framework performs the processing that is needed to support incremental
 * update. The common code fetches the current {@code ConnectorObject},
 * applies the values from each attribute in the the input set, and passes the merged
 * attributes as input to the Connector's implementation of {@code UpdateOp}.
 * 
 * @author Will Droste
 * @version $Revision $
 * @since 1.0
 */
public interface UpdateOp extends SPIOperation {
    /**
     * Modify the target object based on the information provided. 
     * <p>
     * Replace the current values of each attribute with the values provided.
     * That is, for each attribute in the input set
     * replace all of the current values of that attribute in the target object
     * with the values from that attribute in the input set.
     * <p>
     * If the target object does not currently contain an attribute that the
     * input set contains, then add this attribute
     * (along with the provided values) to the target object.
     * <p>
     * If the value of an attribute in the input set is <code>null</code>, 
     * then do one of the following, depending on which is
     * most appropriate for the target:
     * <ul>
     * <li>If possible, <em>remove</em> that attribute from the target object
     * entirely.</li>
     * <li>Otherwise, <em>replace all of the current values</em> of that
     * attribute in the target object with a single value of <code>null</code>.</li>
     * </ul>
     * <p>
     * If the operation cannot be accomplished with the information provided,
     * then throw the subclass of {@link RuntimeException} that best describes
     * the problem.
     * <p>
     * *Note: {@link Uid} is the only attribute guaranteed to be in the attribute set, and it
     * is how you will reference the object to update.
     * 
     * @param objclass
     *            the type of object to update.
     * 
     * @param attrs
     *            set of deltas and the {@link Uid} attribute to update.
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes null, the framework will convert this into
     *            an empty set of options, so SPI need not worry
     *            about this ever being null.
     * @return the {@code Uid} of the updated object in case this update
     *         operation changes the values that form the unique identifier.
     */
    Uid update(final ObjectClass objclass, final Set<Attribute> attrs, final OperationOptions options);
}
