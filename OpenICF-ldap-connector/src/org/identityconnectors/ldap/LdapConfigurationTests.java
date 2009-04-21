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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

public class LdapConfigurationTests /* extends LdapConnectorTestBase*/ {

    private static final String INVALID_DN = "dc=a,,";

    private LdapConfiguration config;

    @Before
    public void before() throws Exception {
        config = new LdapConfiguration();
        config.setHost("localhost");
        config.setBaseContexts("dc=example,dc=com");
        assertCanValidate(config);
    }

    @Test(expected = ConfigurationException.class)
    public void testNoBaseDNsNull() {
        config.setBaseContexts((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testNoBaseDNsEmpty() {
        config.setBaseContexts();
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testInvalidBaseDNsNotAllowed() {
        config.setBaseContexts(LdapConnectorTestBase.ACME_DN, INVALID_DN);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testUidAttributeCannotBeNull() {
        assertCanValidate(config);
        config.setUidAttribute(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testUidAttributeCannotBeBlank() {
        config.setUidAttribute("");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testReadSchemaMustBeTrueWhenUsingExtendedObjectClasses() {
        config.setBaseContexts(LdapConnectorTestBase.ACME_DN);
        config.setExtendedObjectClasses("dNSDomain");
        config.setExtendedNamingAttributes("dc");
        config.setReadSchema(false);
        // Fails because readSchema is false.
        config.validate();
    }

    @Test
    public void testDefaultValues() {
        config = new LdapConfiguration();
        assertNull(config.getHost());
        assertEquals(LdapConfiguration.DEFAULT_PORT, config.getPort());
        assertFalse(config.isSsl());
        assertEquals(0, config.getFailover().length);
        assertNull(config.getPrincipal());
        assertNull(config.getCredentials());
        assertEquals("userPassword", config.getPasswordAttribute());
        assertNull(config.getAuthentication());
        assertEquals(0, config.getBaseContexts().length);
        assertNull(config.getAccountSearchFilter());
        assertEquals("uniqueMember", config.getGroupMemberAttribute());
        assertFalse(config.isMaintainLdapGroupMembership());
        assertFalse(config.isMaintainPosixGroupMembership());
        assertFalse(config.isRespectResourcePasswordPolicyChangeAfterReset());
        assertTrue(config.isUseBlocks());
        assertEquals(100, config.getBlockCount());
        assertTrue(config.isUsePagedResultControl());
        assertEquals("uid", config.getVlvSortAttribute());
        assertEquals("entryUUID", config.getUidAttribute());
        assertTrue(config.isReadSchema());
        assertEquals(0, config.getExtendedObjectClasses().length);
        assertEquals(0, config.getExtendedNamingAttributes().length);
        assertEquals(0, config.getBaseContextsToSynchronize().length);
        assertTrue(Arrays.equals(new String[] { "inetOrgPerson" }, config.getObjectClassesToSynchronize()));
        assertEquals(0, config.getAttributesToSynchronize().length);
        assertEquals(0, config.getModifiersNamesToFilterOut().length);
        assertNull(config.getAccountSynchronizationFilter());
        assertEquals(100, config.getChangeLogBlockSize());
        assertEquals("changeNumber", config.getChangeNumberAttribute());
        assertFalse(config.isFilterWithOrInsteadOfAnd());
        assertTrue(config.isRemoveLogEntryObjectClassFromFilter());
    }

    private static void assertCanValidate(LdapConfiguration config) {
        try {
            config.validate();
        } catch (Exception e) {
            fail();
        }
    }
}
