/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.ldap;

public class LdapPredefinedAttributes {

    public static final String LDAP_GROUPS_NAME = "ldapGroups";

    public static final String POSIX_GROUPS_NAME = "posixGroups";

    public static boolean isLdapGroups(String attrName) {
        return LDAP_GROUPS_NAME.equalsIgnoreCase(attrName);
    }

    public static boolean isPosixGroups(String attrName) {
        return POSIX_GROUPS_NAME.equalsIgnoreCase(attrName);
    }
}
