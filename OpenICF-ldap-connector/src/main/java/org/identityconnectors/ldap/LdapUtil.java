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
 * 
 * Portions Copyrighted 2013 Forgerock
 */
package org.identityconnectors.ldap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import static org.identityconnectors.ldap.LdapEntry.isDNAttribute;
import org.identityconnectors.ldap.search.LdapInternalSearch;

public class LdapUtil {

    private static final Log log = Log.getLog(LdapUtil.class);
    
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
        Set<String> result = new HashSet<String>();
        addStringAttrValues(ldapAttrs, ldapAttrName, result);
        return result;
    }

    public static void addStringAttrValues(Attributes ldapAttrs, String ldapAttrName, Set<String> toSet) {
        Attribute attr = ldapAttrs.get(ldapAttrName);
        if (attr == null) {
            return;
        }
        try {
            NamingEnumeration<?> attrEnum = attr.getAll();
            while (attrEnum.hasMore()) {
                toSet.add((String) attrEnum.next());
            }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    /**
     * Escape DN value of JNDI reserved characters
     * forward slash (/)
     */
    public static String escapeDNValueOfJNDIReservedChars(String DN) {
	StringBuilder toBuilder = new StringBuilder();
	for (int i = 0; i < DN.length(); i++) {
	    char ch = DN.charAt(i);
	    switch (ch) {
	    case '/':
		toBuilder.append("\\2f");
		break;
	    default:
		toBuilder.append(ch);
	    }
	}
        return toBuilder.toString();        
    }

    /**
     * Escapes the given attribute value to the given {@code StringBuilder}.
     * Returns {@code true} iff anything was written to the builder.
     */
    public static boolean escapeAttrValue(Object value, StringBuilder toBuilder) {
        if (value == null) {
            return false;
        }
        if (value instanceof byte[]) {
            return escapeByteArrayAttrValue((byte[]) value, toBuilder);
        } else {
            return escapeStringAttrValue(value.toString(), toBuilder);
        }
    }
    
    /**
     * Normalize the DN string
     * @param The DN string to normalize
     * @return The normalized DN string
     */
    
    public static String  normalizeLdapString(String ldapString)
        {
            StringBuilder normalPath = new StringBuilder();
            String[] parts = ldapString.split(",");
            for (int i = 0; i < parts.length; i++)
            {
                normalPath.append(parts[i].trim());
                // append a comma after each part (except the last one)
                if (i < (parts.length - 1))
                {
                    normalPath.append(",");
                }
            }
            return normalPath.toString();
        }

    private static boolean escapeByteArrayAttrValue(byte[] bytes, StringBuilder toBuilder) {
        if (bytes.length == 0) {
            return false;
        }
        for (byte b : bytes) {
            toBuilder.append('\\');
            String hex = Integer.toHexString(b & 0xff); // Make a negative byte positive.
            if (hex.length() < 2) {
                toBuilder.append('0');
            }
            toBuilder.append(hex);
        }
        return true;
    }

    private static boolean escapeStringAttrValue(String string, StringBuilder toBuilder) {
        if (StringUtil.isEmpty(string)) {
            return false;
        }
        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
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

    public static boolean isUnderContexts(LdapName entry, List<LdapName> contexts) {
        for (LdapName context : contexts) {
            if (entry.startsWith(context)) {
                return true;
            }
        }
        return false;
    }

    public static String[] nullAsEmpty(String[] array) {
        if (array == null) {
            return new String[0];
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> checkedListByFilter(List list, Class<T> clazz) {
        return new CheckedListByFilter<T>(list, clazz);
    }

    @SuppressWarnings("unchecked")
    private static final class CheckedListByFilter<E> implements List<E> {

        private final List list;
        private final Class<E> clazz;

        public CheckedListByFilter(List list, Class<E> clazz) {
            this.clazz = clazz;
            this.list = list;
        }

        private E cast(Object o) {
            return clazz.cast(o);
        }

        public boolean add(E o) {
            return list.add(o);
        }

        public void add(int index, E element) {
            list.add(index, element);
        }

        public boolean addAll(Collection<? extends E> c) {
            return list.addAll(c);
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            return list.addAll(index, c);
        }

        public void clear() {
            list.clear();
        }

        public boolean contains(Object o) {
            return list.contains(o);
        }

        public boolean containsAll(Collection<?> c) {
            return list.containsAll(c);
        }

        public E get(int index) {
            return cast(list.get(index));
        }

        public int indexOf(Object o) {
            return list.indexOf(o);
        }

        public boolean isEmpty() {
            return list.isEmpty();
        }

        public Iterator<E> iterator() {
            return new Itr(list.iterator());
        }

        public int lastIndexOf(Object o) {
            return list.lastIndexOf(o);
        }

        public ListIterator<E> listIterator() {
            return new ListItr(list.listIterator());
        }

        public ListIterator<E> listIterator(int index) {
            return new ListItr(list.listIterator(index));
        }

        public boolean remove(Object o) {
            return list.remove(o);
        }

        public E remove(int index) {
            return cast(list.remove(index));
        }

        public boolean removeAll(Collection<?> c) {
            return list.removeAll(c);
        }

        public boolean retainAll(Collection<?> c) {
            return list.retainAll(c);
        }

        public E set(int index, E element) {
            return cast(list.set(index, element));
        }

        public int size() {
            return list.size();
        }

        public List<E> subList(int fromIndex, int toIndex) {
            return list.subList(fromIndex, toIndex);
        }

        public Object[] toArray() {
            return list.toArray();
        }

        public <T> T[] toArray(T[] a) {
            Object[] result = list.toArray(a);
            for (Object o : result) {
                cast(o);
            }
            return (T[]) result;
        }

        @Override
        public boolean equals(Object obj) {
            return list.equals(obj);
        }

        @Override
        public int hashCode() {
            return list.hashCode();
        }

        @Override
        public String toString() {
            return list.toString();
        }

        private final class Itr implements Iterator<E> {

            private final Iterator iter;

            public Itr(Iterator iter) {
                this.iter = iter;
            }

            public boolean hasNext() {
                return iter.hasNext();
            }

            public E next() {
                return cast(iter.next());
            }

            public void remove() {
                iter.remove();
            }

            @Override
            public boolean equals(Object obj) {
                return iter.equals(obj);
            }

            @Override
            public int hashCode() {
                return iter.hashCode();
            }

            @Override
            public String toString() {
                return iter.toString();
            }
        }

        private final class ListItr implements ListIterator<E> {

            private final ListIterator iter;

            public ListItr(ListIterator iter) {
                this.iter = iter;
            }

            public void add(E o) {
                iter.add(o);
            }

            public boolean hasNext() {
                return iter.hasNext();
            }

            public boolean hasPrevious() {
                return iter.hasPrevious();
            }

            public E next() {
                return cast(iter.next());
            }

            public int nextIndex() {
                return iter.nextIndex();
            }

            public E previous() {
                return cast(iter.previous());
            }

            public int previousIndex() {
                return iter.previousIndex();
            }

            public void remove() {
                iter.remove();
            }

            public void set(E o) {
                iter.set(o);
            }

            @Override
            public boolean equals(Object obj) {
                return iter.equals(obj);
            }

            @Override
            public int hashCode() {
                return iter.hashCode();
            }

            @Override
            public String toString() {
                return iter.toString();
            }
        }
    }
    
    private static String getIDfromDN(LdapConnection conn, String dn) throws NamingException{
                    if (isDNAttribute(conn.getConfiguration().getUidAttribute())) {
                        return dn;
                    } else {
                        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
                        controls.setSearchScope(SearchControls.OBJECT_SCOPE);
                        controls.setReturningAttributes(new String[]{conn.getConfiguration().getUidAttribute()});
                        LdapContext context = conn.getInitialContext().newInstance(null);
                        NamingEnumeration<SearchResult> entries = context.search(escapeDNValueOfJNDIReservedChars(dn), "objectclass=*", controls);
                        SearchResult res = entries.next();
                        String uidAttr = conn.getConfiguration().getUidAttribute();
                        if (LdapConstants.MS_GUID_ATTR.equalsIgnoreCase(uidAttr)) {
                            return(ADLdapUtil.objectGUIDtoString(res.getAttributes().get(conn.getConfiguration().getUidAttribute())));
                        } else {
                            return(res.getAttributes().get(conn.getConfiguration().getUidAttribute()).get(0).toString());
                        }
                    }
    }
    
    // This function builds a _memberId attribute which is a helper
    // that contains the group members' GUID
    public static org.identityconnectors.framework.common.objects.Attribute buildMemberIdAttribute(LdapConnection conn, javax.naming.directory.Attribute attr) {
        ArrayList<String> membersIds = new ArrayList<String>();
        try {
            if (attr != null) {
                NamingEnumeration<?> vals = attr.getAll();
                while (vals.hasMore()) {
                    membersIds.add(getIDfromDN(conn, vals.next().toString()));
                }
            }
        } catch (NamingException e) {
            log.error(e,"Error reading group member attribute");
        }
        return AttributeBuilder.build("_memberId", membersIds);
    }

    public static org.identityconnectors.framework.common.objects.Attribute buildMemberIdAttribute(LdapConnection conn, org.identityconnectors.framework.common.objects.Attribute attr) {
        ArrayList<String> membersIds = new ArrayList<String>();
        try {
            if (attr != null) {
                for(Object val: attr.getValue()){
                    membersIds.add(getIDfromDN(conn, val.toString()));
                }
            }
        } catch (NamingException e) {
            log.error(e,"Error reading group member attribute");
        }
        return AttributeBuilder.build("_memberId", membersIds);
    }
        
        
        
}