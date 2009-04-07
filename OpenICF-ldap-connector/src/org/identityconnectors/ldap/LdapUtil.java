/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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

import java.util.Locale;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public class LdapUtil {

    private static final String LDAP_BINARY_OPTION = ";binary";

    private LdapUtil() {
    }

    /**
     * Returns true if the names of the given LDAP attributes are equal. Deals
     * with null values as a convenience.
     */
    public static boolean attrNameEquals(String name1, String name2) {
        if (name1 == null) {
            return name2 == null;
        }
        return name1.equalsIgnoreCase(name2);
    }

    /**
     * Returns {@code true} if the attribute has the binary option,
     * e.g., {@code userCertificate;binary}.
     */
    public static boolean hasBinaryOption(String ldapAttrName) {
        return ldapAttrName.toLowerCase(Locale.US).endsWith(LDAP_BINARY_OPTION);
    }

    /**
     * Adds the binary option to the attribute if not present already.
     */
    public static String addBinaryOption(String ldapAttrName) {
        if (!hasBinaryOption(ldapAttrName)) {
            return ldapAttrName + LDAP_BINARY_OPTION;
        }
        return ldapAttrName;
    }

    /**
     * Removes the binary option from the attribute.
     */
    public static String removeBinaryOption(String ldapAttrName) {
        if (hasBinaryOption(ldapAttrName)) {
            return ldapAttrName.substring(0, ldapAttrName.length() - LDAP_BINARY_OPTION.length());
        }
        return ldapAttrName;
    }

    /**
     * Return the value of the {@code ldapAttrName} parameter cast to a String.
     */
    public static String getStringAttrValue(Attributes ldapAttrs, String ldapAttrName) {
        Attribute attr = ldapAttrs.get(ldapAttrName);
        if (attr != null) {
            try {
                return (String) attr.get();
            } catch (NamingException e) {
                throw new ConnectorException(e);
            }
        }
        return null;
    }

    /**
     * Return the <b>case insensitive</b> set of values of the {@code
     * ldapAttrName} parameter cast to a String.
     */
    public static Set<String> getStringAttrValues(Attributes ldapAttrs, String ldapAttrName) {
        Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        Attribute attr = ldapAttrs.get(ldapAttrName);
        if (attr == null) {
            return result;
        }
        try {
            NamingEnumeration<?> attrEnum = attr.getAll();
            while (attrEnum.hasMore()) {
                result.add((String) attrEnum.next());
            }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
        return result;
    }

    /**
     * Escapes the given attribute value to the given {@code StringBuilder}.
     * Returns {@code true} iff anything was written to the builder.
     */
    public static boolean escapeAttrValue(Object value, StringBuilder toBuilder) {
        if (value == null) {
            return false;
        }
        String str = value.toString();
        if (StringUtil.isEmpty(str)) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            switch (ch) {
            case '*':
                toBuilder.append("\\2a");
                break;
            case '(':
                toBuilder.append("\\28");
                break;
            case ')':
                toBuilder.append("\\29");
                break;
            case '\\':
                toBuilder.append("\\5c");
                break;
            case '\0':
                toBuilder.append("\\00");
                break;
            default:
                toBuilder.append(ch);
            }
        }
        return true;
    }

    public static LdapName quietCreateLdapName(String ldapName) {
        try {
            return new LdapName(ldapName);
        } catch (InvalidNameException e) {
            throw new ConnectorException(e);
        }
    }
}
