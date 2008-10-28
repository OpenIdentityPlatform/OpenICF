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
package org.identityconnectors.framework.common.objects;

import java.util.Map;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.serializer.ObjectSerializerFactory;
import org.identityconnectors.framework.common.serializer.SerializerUtil;


/**
 * Arbitrary options to be passed into various operations. This serves as a
 * catch-all for extra options.
 */
public final class OperationOptions {
    
    /**
     * An option to use with {@link SearchApiOp} that specified the scope
     * under which to perform the search. To be used in conjunction with
     * {@link #OP_CONTAINER}. Must be one of the following values
     * <ol>
     *    <li>{@link #SCOPE_OBJECT}</li>
     *    <li>{@link #SCOPE_ONE_LEVEL}</li>
     *    <li>{@link #SCOPE_SUBTREE}</li>
     * </ol>
     */
    public static final String OP_SCOPE = "SCOPE";
    public static final String SCOPE_OBJECT = "object";
    public static final String SCOPE_ONE_LEVEL = "onelevel";
    public static final String SCOPE_SUBTREE = "subtree";
    
    /**
     * An option to use with {@link SearchApiOp} that specified the container
     * under which to perform the search. Must be of type {@link QualifiedUid}.
     * Should be implemented for those object classes whose {@link ObjectClassInfo#isContainer()}
     * returns true.
     */
    public static final String OP_CONTAINER = "CONTAINER";
    
    /**
     * An option to use with {@link ScriptOnResourceApiOp} and possibly others
     * that specifies an account under which to execute the script/operation.
     * The specified account will appear to have performed any action that the
     * script/operation performs.
     * <p>
     * Check the javadoc for a particular connector to see whether that
     * connector supports this option.
     */
    public static final String OP_RUN_AS_USER = "RUN_AS_USER";

    /**
     * An option to use with {@link ScriptOnResourceApiOp} and possibly others
     * that specifies a password under which to execute the script/operation.
     */
    public static final String OP_RUN_WITH_PASSWORD = "RUN_WITH_PASSWORD";

    /**
     * Determines which attributes to retrieve during {@link SearchApiOp} and
     * {@link SyncApiOp}.
     * <p>
     * This option overrides the default behavior, which is for the connector
     * to return exactly the set of attributes that are identified as 
     * {@link AttributeInfo#isReturnedByDefault() returned by default}
     * in the schema for that connector.
     * <p>
     * This option allows a client application to 
     * request <i>additional attributes</i> that would not otherwise not be returned
     * (generally because such attributes are more expensive 
     * for a connector to fetch and to format) 
     * and/or to request only a <i>subset of the attributes</i> 
     * that would normally be returned.
     */
    public static final String OP_ATTRIBUTES_TO_GET = "ATTRS_TO_GET";

    private final Map<String, Object> _operationOptions;

    /**
     * Public only for serialization; please use {@link OperationOptionsBuilder}.
     * 
     * @param operationOptions
     *            The options.
     */
    public OperationOptions(Map<String, Object> operationOptions) {
        for (Object value : operationOptions.values()) {
            FrameworkUtil.checkOperationOptionValue(value);
        }
        // clone options to do a deep copy in case anything
        // is an array
        @SuppressWarnings("unchecked")
        Map<String, Object> operationOptionsClone = (Map<String, Object>) SerializerUtil
                .cloneObject(operationOptions);
        _operationOptions = CollectionUtil.asReadOnlyMap(operationOptionsClone);
    }

    /**
     * Returns a map of options. Each value in the map must be of a type that
     * the framework can serialize. See {@link ObjectSerializerFactory} for a
     * list of supported types.
     * 
     * @return A map of options.
     */
    public Map<String, Object> getOptions() {
        return _operationOptions;
    }

    /**
     * Add basic debugging of internal data.
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder();
        bld.append("OperationOptions: ").append(getOptions());
        return bld.toString();
    }

    /**
     * Convenience method that returns {@link #OP_SCOPE}.
     * @return The value for {@link #OP_SCOPE}.
     */
    public String getScope() {
        return (String) _operationOptions.get(OP_SCOPE);
    }
    
    /**
     * Convenience method that returns {@link #OP_CONTAINER}.
     * @return The value for {@link #OP_CONTAINER}.
     */
    public QualifiedUid getContainer() {
        return (QualifiedUid)_operationOptions.get(OP_CONTAINER);
    }
    
    /**
     * Get the string array of attribute names to return in the object.
     */
    public String[] getAttributesToGet() {
        return (String[]) _operationOptions.get(OP_ATTRIBUTES_TO_GET);
    }

    /**
     * Get the account to run the operation as..
     */
    public String getRunAsUser() {
        return (String) _operationOptions.get(OP_RUN_AS_USER);
    }
    
    /**
     * Get the password to run the operation as..
     */
    public GuardedString getRunWithPassword() {
        return (GuardedString) _operationOptions.get(OP_RUN_WITH_PASSWORD);
    }
}
