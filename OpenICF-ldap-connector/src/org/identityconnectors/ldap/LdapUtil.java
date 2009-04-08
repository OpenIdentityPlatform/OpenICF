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

import static org.identityconnectors.common.CollectionUtil.newSet;

import java.util.Collection;
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
import javax.naming.ldap.LdapName;

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
        Set<String> result = newSet();
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
}
