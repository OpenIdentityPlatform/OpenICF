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
package org.identityconnectors.ldap.search;

import static org.junit.Assert.*;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConnectorTestBase;
import org.identityconnectors.ldap.schema.LdapSchemaMapping;
import org.junit.Test;

public class LdapFilterTranslatorTests {

    @Test
    public void testAnd() {
        assertEquals("(&(foo=1)(bar=2))", newTranslator().createAndExpression("(foo=1)", "(bar=2)"));
    }

    @Test
    public void testOr() {
        assertEquals("(|(foo=1)(bar=2))", newTranslator().createOrExpression("(foo=1)", "(bar=2)"));
    }

    @Test
    public void testContains() {
        ContainsFilter filter = (ContainsFilter) FilterBuilder.contains(AttributeBuilder.build("foo", ""));
        assertEquals("(foo=*)", newTranslator().createContainsExpression(filter, false));

        filter = (ContainsFilter) FilterBuilder.contains(AttributeBuilder.build("foo", "bar"));
        assertEquals("(foo=*bar*)", newTranslator().createContainsExpression(filter, false));
    }

    @Test
    public void testStartsWith() {
        StartsWithFilter filter = (StartsWithFilter) FilterBuilder.startsWith(AttributeBuilder.build("foo", ""));
        assertEquals("(foo=*)", newTranslator().createStartsWithExpression(filter, false));
        assertEquals("(!(foo=*))", newTranslator().createStartsWithExpression(filter, true));

        filter = (StartsWithFilter) FilterBuilder.startsWith(AttributeBuilder.build("foo", "bar"));
        assertEquals("(foo=bar*)", newTranslator().createStartsWithExpression(filter, false));
        assertEquals("(!(foo=bar*))", newTranslator().createStartsWithExpression(filter, true));
    }

    @Test
    public void testEndsWith() {
        EndsWithFilter filter = (EndsWithFilter) FilterBuilder.endsWith(AttributeBuilder.build("foo", ""));
        assertEquals("(foo=*)", newTranslator().createEndsWithExpression(filter, false));
        assertEquals("(!(foo=*))", newTranslator().createEndsWithExpression(filter, true));

        filter = (EndsWithFilter) FilterBuilder.endsWith(AttributeBuilder.build("foo", "bar"));
        assertEquals("(foo=*bar)", newTranslator().createEndsWithExpression(filter, false));
        assertEquals("(!(foo=*bar))", newTranslator().createEndsWithExpression(filter, true));
    }

    @Test
    public void testEquals() {
        EqualsFilter filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build("foo"));
        assertNull(newTranslator().createEqualsExpression(filter, false));

        filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build("foo", ""));
        assertEquals("(foo=*)", newTranslator().createEqualsExpression(filter, false));
        assertEquals("(!(foo=*))", newTranslator().createEqualsExpression(filter, true));

        filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build("foo", "bar"));
        assertEquals("(foo=bar)", newTranslator().createEqualsExpression(filter, false));
        assertEquals("(!(foo=bar))", newTranslator().createEqualsExpression(filter, true));

        filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build("foo", "bar", "baz"));
        assertEquals("(&(foo=bar)(foo=baz))", newTranslator().createEqualsExpression(filter, false));
        assertEquals("(!(&(foo=bar)(foo=baz)))", newTranslator().createEqualsExpression(filter, true));
    }

    @Test
    public void testGreaterThan() {
        GreaterThanFilter filter = (GreaterThanFilter) FilterBuilder.greaterThan(AttributeBuilder.build("foo", 42));
        assertEquals("(!(foo<=42))", newTranslator().createGreaterThanExpression(filter, false));
        assertEquals("(foo<=42)", newTranslator().createGreaterThanExpression(filter, true));
    }

    @Test
    public void testGreaterThanOrEqual() {
        GreaterThanOrEqualFilter filter = (GreaterThanOrEqualFilter) FilterBuilder.greaterThanOrEqualTo(AttributeBuilder.build("foo", 42));
        assertEquals("(foo>=42)", newTranslator().createGreaterThanOrEqualExpression(filter, false));
        assertEquals("(!(foo>=42))", newTranslator().createGreaterThanOrEqualExpression(filter, true));
    }

    @Test
    public void testLessThan() {
        LessThanFilter filter = (LessThanFilter) FilterBuilder.lessThan(AttributeBuilder.build("foo", 42));
        assertEquals("(!(foo>=42))", newTranslator().createLessThanExpression(filter, false));
        assertEquals("(foo>=42)", newTranslator().createLessThanExpression(filter, true));
    }

    @Test
    public void testLessThanOrEqual() {
        LessThanOrEqualFilter filter = (LessThanOrEqualFilter) FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("foo", 42));
        assertEquals("(foo<=42)", newTranslator().createLessThanOrEqualExpression(filter, false));
        assertEquals("(!(foo<=42))", newTranslator().createLessThanOrEqualExpression(filter, true));
    }

    private static LdapFilterTranslator newTranslator() {
        LdapConfiguration config = LdapConnectorTestBase.newConfiguration();
        LdapConnection conn = new LdapConnection(config);
        return new LdapFilterTranslator(new LdapSchemaMapping(conn), ObjectClass.ACCOUNT);
    }
}
