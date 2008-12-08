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

import java.util.EnumSet;

import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

public class PredefinedAttributeInfos {
    
    /**
     * Attribute that should hold a reasonable value to
     * display for the value of an object.  If this is not present, then the
     * application will have to use the NAME to show the value.
     */
    public static final AttributeInfo SHORT_NAME = 
        AttributeInfoBuilder.build(PredefinedAttributes.SHORT_NAME);
    
    /**
     * Attribute that should hold the value of the object's description,
     * if one is available.
     */
    public static final AttributeInfo DESCRIPTION = 
        AttributeInfoBuilder.build(PredefinedAttributes.DESCRIPTION);
    
    /**
     * Read-only attribute that shows the last date/time the password was
     * changed.
     */
    public static final AttributeInfo LAST_PASSWORD_CHANGE_DATE = 
         AttributeInfoBuilder.build(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME, 
                 long.class,
                 EnumSet.of(Flags.NOT_CREATABLE,
                            Flags.NOT_UPDATEABLE));

    /**
     * Common password policy attribute where the password must be changed every
     * so often. The value for this attribute is milliseconds since its the
     * lowest common denominator.
     */
    public static final AttributeInfo PASSWORD_CHANGE_INTERVAL = 
        AttributeInfoBuilder.build(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME, 
                long.class);

    /**
     * Last login date for an account. This is usually used to determine
     * inactivity.
     */
    public static final AttributeInfo LAST_LOGIN_DATE = 
        AttributeInfoBuilder.build(PredefinedAttributes.LAST_LOGIN_DATE_NAME, 
                long.class,
                EnumSet.of(Flags.NOT_CREATABLE,
                        Flags.NOT_UPDATEABLE));
                

    /**
     * Groups that an account or person belong to. The Attribute values are the
     * UID value of each group that an account has membership in.
     */
    public static final AttributeInfo GROUPS =
        AttributeInfoBuilder.build(PredefinedAttributes.GROUPS_NAME,
                String.class,
                EnumSet.of(Flags.MULTIVALUED,
                        Flags.NOT_RETURNED_BY_DEFAULT));

    /**
     * Accounts that are members of a group or organization. The Attribute
     * values are the UID value of each account the has a group or organization
     * membership.
     */
    public static final AttributeInfo ACCOUNTS =
        AttributeInfoBuilder.build(PredefinedAttributes.ACCOUNTS_NAME,
                String.class,
                EnumSet.of(Flags.MULTIVALUED,
                        Flags.NOT_RETURNED_BY_DEFAULT));
                
        

    /**
     * Organizations that an account or person is a member of. The Attribute
     * values are the UID value of each organization that an account or person is
     * a member of.
     */
    public static final AttributeInfo ORGANIZATIONS =
        AttributeInfoBuilder.build(PredefinedAttributes.ORGANIZATION_NAME,
                String.class,
                EnumSet.of(Flags.NOT_RETURNED_BY_DEFAULT));
        
}
