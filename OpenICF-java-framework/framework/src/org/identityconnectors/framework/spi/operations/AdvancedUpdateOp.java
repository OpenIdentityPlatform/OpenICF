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


/**
 * The developer of a Connector should implement either this interface or the
 * {@link UpdateOp} interface if the Connector will allow an authorized caller
 * to update (i.e., modify or replace) objects on the target resource.
 * <p>
 * This update method is expected to handle the types of update that are
 * enumerated by the contained interface {@link Type Type}. Each
 * {@code Type} value specifies how the method must apply to the target
 * object the values of the input attributes.
 * <p>
 * This <em>incremental</em> update requires a more sophisticated
 * implementation than {@code UpdateOp}, which replaces the
 * corresponding attributes of the target object with the attributes (and
 * attribute values) from the input set but does not take a
 * {@code Type} argument. (In effect, {@code UpdateOp} acts as if
 * it received a value of {@link Type#REPLACE Type.REPLACE}.)
 * <p>
 * The developer of a Connector should implement only one of
 * {@code UpdateOp} or {@code AdvanceUpdateOp}; there is no need
 * to implement both. The common code in the framework prefers
 * {@code AdvanceUpdateOp} if {@code AdvanceUpdateOp} is implemented. 
 * If {@code AdvanceUpdateOp} is not implemented, then the
 * common code in the framework uses {@code UpdateOp} and performs the
 * additional processing that is needed to support incremental update. 
 * The common code fetches the current {@code ConnectorObject}, 
 * applies the values from the attributes of the input set, 
 * and passes the resulting attributes as input to the
 * Connector's implementation of {@code UpdateOp}.
 * 
 * @author Will Droste
 * @version $Revision $
 * @since 1.0
 */
public interface AdvancedUpdateOp extends SPIOperation {

    /**
     * Used as a parameter to specify the type of update to perform.
     */
    enum Type {
        /**
         * Replace the current values of each attribute with the values
         * provided.
         * <p>
         * For each attribute that the input ConnectorObject contains, replace
         * all of the current values of that attribute in the target object with
         * the values of that attribute in the input ConnectorObject.
         * <p>
         * If the target object does not currently contain an attribute that the
         * input set contains, then add this
         * attribute (along with the provided values) to the target object.
         * <p>
         * If the value of an attribute in the input set is
         * {@code null}, then do one of the following, depending on
         * which is most appropriate for the target:
         * <ul>
         * <li>If possible, <em>remove</em> that attribute from the target
         * object entirely.</li>
         * <li>Otherwise, <em>replace all of the current values</em> of that
         * attribute in the target object with a single value of
         * {@code null}.</li>
         * </ul>
         */
        REPLACE,
        /**
         * Add to the current values of each attribute the values provided.
         * <p>
         * For each attribute that the input set contains, add to
         * the current values of that attribute in the target object all of the
         * values of that attribute in the input set.
         * <p>
         * NOTE that this does not specify how to handle duplicate values. 
         * The general assumption for an attribute of a {@code ConnectorObject} 
         * is that the values for an attribute may contain duplicates. 
         * Therefore, in general simply <em>append</em> the provided values 
         * to the current value for each attribute.
         */
        ADD,
        /**
         * Remove from the current values of each attribute the values provided.
         * <p>
         * For each attribute that the input set contains, 
         * remove from the current values of that attribute in the target object 
         * any value that matches one of the values of the attribute from the input set.
         * <p>
         * NOTE that this does not specify how to handle unmatched values. 
         * The general assumption for an attribute of a {@code ConnectorObject}
         * is that the values for an attribute are merely <i>representational state</i>.
         * Therefore, the implementer should simply ignore any provided value
         * that does not match a current value of that attribute in the target
         * object. Deleting an unmatched value should always succeed.
         */
        DELETE
    }

    /**
     * Modify the target object based on the specified {@code type} and
     * the information provided in the input set of attributes.
     * <p>
     * If the operation cannot be accomplished with the information provided,
     * then throw the subclass of {@link RuntimeException} that best describes
     * the problem.
     * 
     * @param type
     *            specifies how to apply to the target object the attribute
     *            values that the input set of attributes contains. 
     *            For details, see {@link Type}.
     * 
     * @param objclass
     *            class of object to modify.
     * @param attrs
     *            set of all attributes to change, plus the {@link Uid} of the
     *            object. Only changed values are passed in each {@code Attribute}.
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes {@code null}, the framework will convert this
     *            into an empty set of options, so SPI need not worry
     *            about options ever being null.
     * 
     * @return the {@code Uid} of the updated object in case this update
     *         operation changes the values that form the unique identifier.
     */
    Uid update(final AdvancedUpdateOp.Type type, final ObjectClass objclass,
            final Set<Attribute> attrs,
            final OperationOptions options);
}
