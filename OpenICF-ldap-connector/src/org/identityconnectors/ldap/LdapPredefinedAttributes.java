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

/**
 * Contains the attributes defined by the adapter which don't have a counterpart
 * with the same name in LDAP. For example, this class doesn't contain
 * a field for {@code modifyTimeStamp}, because that is an LDAP attribute.
 * Not that both {@code dn} and {@code entryDN} are considered LDAP attributes.
 *
 * @author Andrei Badea
 */
public class LdapPredefinedAttributes {

    public static final String PASSWORD_NAME = "password";

    public static final String FIRSTNAME_NAME = "firstname";

    public static final String LASTNAME_NAME = "lastname";
}
