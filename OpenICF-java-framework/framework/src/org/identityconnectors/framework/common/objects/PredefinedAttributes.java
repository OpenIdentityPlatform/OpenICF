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

/**
 * List of well known or pre-defined attributes. Common attributes that most
 * resources have that are not operational in nature.
 */
public class PredefinedAttributes {

    /**
     * Attribute that should hold a reasonable value for an application
     * to display as a label for an object.  
     * If {@code SHORT_NAME} is not present, then the application
     * should use the value of {@link Name the NAME attribute} as a label.
     */
    public static final String SHORT_NAME = createSpecialName("SHORT_NAME");
    
    /**
     * Attribute that should hold the value of the object's description,
     * if one is available.
     */
    public static final String DESCRIPTION = createSpecialName("DESCRIPTION");
    
    /**
     * Read-only attribute that shows the last date/time the password was
     * changed.
     */
    public static final String LAST_PASSWORD_CHANGE_DATE_NAME = createSpecialName("LAST_PASSWORD_CHANGE_DATE");

    /**
     * Common password policy attribute where the password must be changed every
     * so often. The value for this attribute is milliseconds since its the
     * lowest common denominator.
     */
    public static final String PASSWORD_CHANGE_INTERVAL_NAME = createSpecialName("PASSWORD_CHANGE_INTERVAL");
    
    /**
     * Last login date for an account.  This is usually used to determine inactivity.
     */
    public static final String LAST_LOGIN_DATE_NAME = createSpecialName("LAST_LOGIN_DATE");
    
    /**
     * Groups an account object belongs to. The values inside the attribute will
     * be the string value of a Uid.
     */
    public static final String GROUPS_NAME = createSpecialName("GROUPS");

    /**
     * Accounts that belong to a group or organization. The values inside the
     * attribute will be the string value of a Uid.
     */
    public static final String ACCOUNTS_NAME = createSpecialName("ACCOUNTS");

    /**
     * An organization that that an account/person belongs to. The values inside
     * the attribute will be the string value of a Uid.
     */
    public static final String ORGANIZATION_NAME = createSpecialName("ORGANIZATION");
}
