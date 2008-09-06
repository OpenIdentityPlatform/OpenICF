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

import static org.identityconnectors.framework.common.objects.AttributeUtil.createSpecialName;

import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.CreateOp;


/**
 * Operational attributes have special meaning and cannot be represented by pure
 * operations. For instance some administrators would like to create an account
 * in the disabled state. They do not want this to be a two operation process
 * since this can leave the door open to abuse. Therefore special attributes
 * that can perform operations were introduced. The enable attribute could be
 * added to the set of attributes sent to a Connector for the {@link CreateOp}
 * operation. To tell the {@link Connector} to create the account with it in the
 * disabled state whether the target resource itself has an attribute or an
 * additional method must be called.
 */
public final class OperationalAttributes {

    /**
     * Gets/sets the enable status of an object.
     */
    public static final String ENABLE_NAME = createSpecialName("ENABLE");
    /**
     * Gets/sets the enable date for an object.
     */
    public static final String ENABLE_DATE_NAME = createSpecialName("ENABLE_DATE");
    /**
     * Gets/sets the disable date for an object.
     */
    public static final String DISABLE_DATE_NAME = createSpecialName("DISABLE_DATE");
    /**
     * Gets/sets the lock out attribute for an object.
     */
    public static final String LOCK_OUT_NAME = createSpecialName("LOCK_OUT");
    /**
     * Gets/sets the password expiration date for an object.
     */
    public static final String PASSWORD_EXPIRATION_DATE_NAME = createSpecialName("PASSWORD_EXPIRATION_DATE");
    /**
     * Gets/sets the password expired for an object.
     */
    public static final String PASSWORD_EXPIRED_NAME = createSpecialName("PASSWORD_EXPIRED");
    /**
     * Normally this is a write-only attribute. Sets the password for an object.
     */
    public static final String PASSWORD_NAME = createSpecialName("PASSWORD");
    /**
     * Used in conjunction with password to do an account level password change.
     * This is for a non-administrator change of the password and therefore
     * requires the current password.
     */
    public static final String CURRENT_PASSWORD_NAME = createSpecialName("CURRENT_PASSWORD");
    /**
     * Used to do an administrator reset of the password. The value is the reset
     * password value.
     */
    public static final String RESET_PASSWORD_NAME = createSpecialName("RESET_PASSWORD");

    // =======================================================================
    // Helper Methods..
    // =======================================================================
    public final static Set<String> OPERATIONAL_ATTRIBUTE_NAMES = 
        CollectionUtil.newReadOnlySet(
            LOCK_OUT_NAME, 
            ENABLE_NAME, 
            ENABLE_DATE_NAME,
            DISABLE_DATE_NAME, 
            PASSWORD_EXPIRATION_DATE_NAME,
            PASSWORD_NAME, 
            CURRENT_PASSWORD_NAME, 
            RESET_PASSWORD_NAME,
            PASSWORD_EXPIRED_NAME
        );

    public static Set<String> getOperationalAttributeNames() {
        return CollectionUtil.newReadOnlySet(OPERATIONAL_ATTRIBUTE_NAMES);
    }

    public static boolean isOperationalAttribute(Attribute attr) {
        String name = (attr != null) ? attr.getName() : null;
        return OPERATIONAL_ATTRIBUTE_NAMES.contains(name);
    }
}
