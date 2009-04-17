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
package org.identityconnectors.ldap.search;

import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

/**
 * Encapsulates an LDAP filter. An instance of this class is consists of
 * an optional entry DN and an optional native LDAP filter. The semantics of
 * such an instance is "an LDAP entry with this entry DN (if specified) and
 * matching this native filter (if specified)".
 *
 * <p>The problem this class solves is the following.
 * When an attribute, for instance {@link Name}, is mapped to an entry's DN,
 * and the user constructs an {@link EqualsFilter} for that attribute, the connector
 * needs to translate that <code>Filter</code> into a native LDAP filter. The connector
 * could use the <code>entryDN</code> attribute in the native filter, but some servers might
 * not support that attribute. Instead, such a <code>Filter</code> is translated
 * to an <code>LdapFilter</code> with that entry DN. A composed filter, for instance:</p>
 *
 * <pre>
 * Name name = new Name("uid=foo,dc=example,dc=com");
 * Attribute attr = AttributeBuilder.build("foo", "bar");
 * FilterBuilder.and(
 *     FilterBuilder.equalTo(name),
 *     FilterBuilder.equalTo(attr));
 * </pre>
 *
 * <p>can be translated to a single <code>LdapFilter</code> whose entry DN corresponds
 * to <code>aName</code> and whose filter string is <code>(foo=bar)</code>.</p>
 */
public final class LdapFilter {

    private final String nativeFilter;
    private final String entryDN;

    public static LdapFilter forEntryDN(String entryDN) {
        return new LdapFilter(null, entryDN);
    }

    public static LdapFilter forNativeFilter(String nativeFilter) {
        return new LdapFilter(nativeFilter, null);
    }

    private LdapFilter(String nativeFilter, String entryDN) {
        this.nativeFilter = nativeFilter;
        this.entryDN = entryDN;
    }

    public LdapFilter withNativeFilter(String nativeFilter) {
        return new LdapFilter(nativeFilter, this.entryDN);
    }

    public String getNativeFilter() {
        return nativeFilter;
    }

    public String getEntryDN() {
        return entryDN;
    }

    /**
     * Logically "ANDs" together this filter with another filter.
     *
     * <p>If at most one of the two filters has an entry DN, the
     * result is a filter with that entry DN (if any) and a native filter
     * whose value is the native filters of the two filters "ANDed"
     * together using the LDAP <code>&</code> operator.</p>
     *
     * <p>Otherwise, the method returns <code>null</code>.
     *
     * @param other the other filter.
     *
     * @return the two filters "ANDed" together or <code>null</code>.
     */
    public LdapFilter and(LdapFilter other) {
        if (entryDN == null || other.entryDN == null) {
            return new LdapFilter(
                    combine(nativeFilter, other.nativeFilter, '&'),
                    entryDN != null ? entryDN : other.entryDN);
        }
        return null;
    }

    /**
     * Logically "ORs" together this filter with another filter.
     *
     * <p>If none of the two filters has an entry DN, the
     * result is a filter with no entry DN  and a native filter
     * whose value is the native filters of the two filters "ORed"
     * together using the LDAP <code>|</code> filter operator.</p>
     *
     * <p>Otherwise, the method returns <code>null</code>.
     *
     * @param other the other filter.
     *
     * @return the two filters "ORed" together or <code>null</code>.
     */
    public LdapFilter or(LdapFilter other) {
        if (entryDN == null && other.entryDN == null) {
            return new LdapFilter(
                    combine(nativeFilter, other.nativeFilter, '|'), null);
        }
        return null;
    }

    private static String combine(String left, String right, char op) {
        if (left != null) {
            if (right != null) {
                StringBuilder builder = new StringBuilder();
                builder.append('(');
                builder.append(op);
                builder.append(left);
                builder.append(right);
                builder.append(')');
                return builder.toString();
            } else {
                return left;
            }
        } else {
            return right;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LdapFilter) {
            LdapFilter that = (LdapFilter)o;
            if ((nativeFilter == null) ? (that.nativeFilter != null) : !nativeFilter.equals(that.nativeFilter)) {
                return false;
            }
            if ((entryDN == null) ? (that.entryDN != null) : !entryDN.equals(that.entryDN)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (nativeFilter != null ? nativeFilter.hashCode() : 0) ^ (entryDN != null ? entryDN.hashCode() : 0);
    }

    @Override
    public String toString() {
        return "LdapFilter[nativeFilter: " + nativeFilter + "; entryDN: " + entryDN + "]";
    }
}
