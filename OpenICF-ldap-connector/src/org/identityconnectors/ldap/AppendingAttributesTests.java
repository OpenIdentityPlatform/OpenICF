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

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import org.junit.Before;
import org.junit.Test;

public class AppendingAttributesTests {

    private Attribute foo, existingFoo, existingCapitalFoo;
    private Attribute bar, existingBar;

    @Before
    public void before() {
        foo = new BasicAttribute("foo", "Foo");
        existingFoo = new BasicAttribute("foo", "Existing Foo");
        existingCapitalFoo = new BasicAttribute("FOO", "EXISTING FOO");
        bar = new BasicAttribute("bar", "Bar");
        existingBar = new BasicAttribute("bar", "Existing Bar");
    }

    @Test
    public void testAppending() throws Exception {
        BasicAttributes existingAttrs = new BasicAttributes(true);
        AppendingAttrImpl appendingAttrs = new AppendingAttrImpl(existingAttrs, foo, bar);
        assertEquals(2, appendingAttrs.size());
        assertHasAttribute(foo, "foo", appendingAttrs);
        assertHasAttribute(bar, "bar", appendingAttrs);
        assertEquals(foo, appendingAttrs.get("FOO"));
        assertNull(existingAttrs.get("inexistent"));
    }

    @Test
    public void testAppendAndReplaceCaseInsensitive() throws Exception {
        BasicAttributes existingAttrs = new BasicAttributes(true);
        existingAttrs.put(existingCapitalFoo);
        AppendingAttrImpl appendingAttrs = new AppendingAttrImpl(existingAttrs, foo, bar);
        assertEquals(2, appendingAttrs.size());
        assertHasAttribute(foo, "foo", appendingAttrs);
        assertHasAttribute(bar, "bar", appendingAttrs);
        assertEquals(foo, appendingAttrs.get("FOO"));
        assertNull(existingAttrs.get("inexistent"));
    }

    @Test
    public void testAppendCaseInsensitive() throws Exception {
        BasicAttributes existingAttrs = new BasicAttributes(false);
        existingAttrs.put(existingCapitalFoo);
        AppendingAttrImpl appendingAttrs = new AppendingAttrImpl(existingAttrs, foo, bar);
        assertEquals(3, appendingAttrs.size());
        assertHasAttribute(foo, "foo", appendingAttrs);
        assertHasAttribute(existingCapitalFoo, "FOO", appendingAttrs);
        assertHasAttribute(bar, "bar", appendingAttrs);
        assertNull(existingAttrs.get("inexistent"));
    }

    @Test
    public void testReplace() throws Exception {
        BasicAttributes existingAttrs = new BasicAttributes(true);
        existingAttrs.put(existingFoo);
        existingAttrs.put(existingBar);
        AppendingAttrImpl appendingAttrs = new AppendingAttrImpl(existingAttrs, foo, bar);
        assertEquals(2, appendingAttrs.size());
        assertHasAttribute(foo, "foo", appendingAttrs);
        assertHasAttribute(bar, "bar", appendingAttrs);
        assertNull(existingAttrs.get("inexistent"));
    }

    private void assertHasAttribute(Attribute expected, String attrID, Attributes attrs) throws NamingException {
        assertEquals(expected, attrs.get(attrID));

        assertEquals(expected, findAttrUsingEnumMore(attrs.getAll(), attrID));
        assertEquals(expected, findAttrUsingEnumMoreElements(attrs.getAll(), attrID));
        assertEquals(attrID, findAttrIDUsingEnumMore(attrs.getIDs(), attrID));
        assertEquals(attrID, findAttrIDUsingEnumMoreElements(attrs.getIDs(), attrID));
    }

    private static Attribute findAttrUsingEnumMore(NamingEnumeration<? extends Attribute> attrEnum, String attrID) throws NamingException {
        Attribute attr = null;
        int found = 0;
        while (attrEnum.hasMore()) {
            Attribute nextAttr = attrEnum.next();
            if (nextAttr.getID().equals(attrID)) {
                attr = nextAttr;
                found++;
            }
        }
        assertEquals(1, found);
        return attr;
    }

    private static Attribute findAttrUsingEnumMoreElements(NamingEnumeration<? extends Attribute> attrEnum, String attrID) {
        Attribute attr = null;
        int found = 0;
        while (attrEnum.hasMoreElements()) {
            Attribute nextAttr = attrEnum.nextElement();
            if (nextAttr.getID().equals(attrID)) {
                attr = nextAttr;
                found++;
            }
        }
        assertEquals(1, found);
        return attr;
    }

    private static String findAttrIDUsingEnumMore(NamingEnumeration<String> idEnum, String attrID) throws NamingException {
        String id = null;
        int found = 0;
        while (idEnum.hasMore()) {
            String nextID = idEnum.next();
            if (nextID.equals(attrID)) {
                id = nextID;
                found++;
            }
        }
        assertEquals(1, found);
        return id;
    }

    private static String findAttrIDUsingEnumMoreElements(NamingEnumeration<String> idEnum, String attrID) {
        String id = null;
        int found = 0;
        while (idEnum.hasMoreElements()) {
            String nextID = idEnum.nextElement();
            if (nextID.equals(attrID)) {
                id = nextID;
                found++;
            }
        }
        assertEquals(1, found);
        return id;
    }

    private static final class AppendingAttrImpl extends AppendingAttributes {

        private static final long serialVersionUID = 1L;

        private final Attribute[] toAppend;

        public AppendingAttrImpl(Attributes delegate, Attribute... toAppend) {
            super(delegate);
            this.toAppend = toAppend;
        }

        @Override
        public Object clone() {
            return new AppendingAttrImpl((Attributes) delegate.clone(), toAppend);
        }

        @Override
        protected Attribute getAttributeToAppend(String attrID) {
            for (Attribute attr : toAppend) {
                if (attrID.equals(attr.getID())) {
                    return attr;
                }
            }
            return null;
        }

        @Override
        protected Set<String> getAttributeIDsToAppend() {
            Set<String> result = new HashSet<String>();
            for (Attribute attr : toAppend) {
                result.add(attr.getID());
            }
            return result;
        }
    }
}
