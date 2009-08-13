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

import static org.identityconnectors.common.CollectionUtil.newList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Before;
import org.junit.Test;

public class LdapConfigurationTests {

    private static final String INVALID_DN = "dc=a,,";

    private LdapConfiguration config;

    @Before
    public void before() throws Exception {
        config = new LdapConfiguration();
        config.setConnectorMessages(TestHelpers.createDummyMessages());
        config.setHost("localhost");
        config.setBaseContexts("dc=example,dc=com");
        assertCanValidate(config);
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsNotEmpty() {
        config.setBaseContexts();
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsItemNotNull() {
        config.setBaseContexts((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsItemNotBlank() {
        config.setBaseContexts(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsValid() {
        config.setBaseContexts(LdapConnectorTestBase.ACME_DN, INVALID_DN);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordAttributeNotNull() {
        config.setPasswordAttribute(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordAttributeNotBlank() {
        config.setPasswordAttribute(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testAccountObjectClassesNotEmpty() {
        config.setAccountObjectClasses();
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testAccountObjectClassesItemNotNull() {
        config.setAccountObjectClasses((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testAccountUserNameAttributesNotEmpty() {
        config.setAccountUserNameAttributes();
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testAccountUserNameAttributesItemNotNull() {
        config.setAccountUserNameAttributes((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testGroupMemberAttributeNotNull() {
        config.setGroupMemberAttribute(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testGroupMemberAttributeNotBlank() {
        config.setGroupMemberAttribute(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBlockCountGreatherThanZero() {
        config.setBlockSize(0);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testVlvSortAttributeNotNull() {
        config.setUidAttribute(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testVlvSortAttributeNotBlank() {
        config.setUidAttribute(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testUidAttributeNotNull() {
        config.setUidAttribute(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testUidAttributeNotBlank() {
        config.setUidAttribute(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testReadSchemaMustBeTrueWhenUsingExtendedObjectClasses() {
        config.setBaseContexts(LdapConnectorTestBase.ACME_DN);
        config.setExtendedObjectClasses("dNSDomain");
        config.setReadSchema(false);
        // Fails because readSchema is false.
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsToSynchronizeItemNotNull() {
        config.setBaseContextsToSynchronize((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsToSynchronizeItemNotBlank() {
        config.setBaseContextsToSynchronize(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testBaseContextsToSyncronizeValid() {
        config.setBaseContextsToSynchronize(LdapConnectorTestBase.ACME_DN, INVALID_DN);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testObjectClassesToSynchronizeNotEmpty() {
        config.setObjectClassesToSynchronize();
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testObjectClassesToSynchronizeItemNotNull() {
        config.setObjectClassesToSynchronize((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testObjectClassesToSynchronizeItemNotBlank() {
        config.setObjectClassesToSynchronize(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testAttributesToSynchronizeItemNotNull() {
        config.setAttributesToSynchronize((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testAttributesToSynchronizeItemNotBlank() {
        config.setAttributesToSynchronize(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testModifiersNamesToFilterOutItemNotNull() {
        config.setModifiersNamesToFilterOut((String) null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testModifiersNamesToFilterOutItemNotBlank() {
        config.setModifiersNamesToFilterOut(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testModifiersNamesToFilterOutValid() {
        config.setModifiersNamesToFilterOut(LdapConnectorTestBase.ACME_DN, INVALID_DN);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testChangeNumberAttributeNotNull() {
        config.setChangeNumberAttribute(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testChangeNumberAttributeNotBlank() {
        config.setChangeNumberAttribute(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testChangeLogBlockSizeGreatherThanZero() {
        config.setChangeLogBlockSize(0);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordAttributeToSynchronizeNotNull() {
        config.setSynchronizePasswords(true);
        config.setPasswordAttributeToSynchronize(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordAttributeToSynchronizeNotBlank() {
        config.setSynchronizePasswords(true);
        config.setPasswordAttributeToSynchronize(" ");
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordDecryptionKeyNotNull() {
        config.setSynchronizePasswords(true);
        config.setPasswordAttributeToSynchronize("somepassword");
        config.setPasswordDecryptionKey(null);
        config.setPasswordDecryptionInitializationVector(new GuardedByteArray(new byte[1]));
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordDecryptionKeyNotBlank() {
        config.setSynchronizePasswords(true);
        config.setPasswordAttributeToSynchronize("somepassword");
        config.setPasswordDecryptionKey(new GuardedByteArray(new byte[0]));
        config.setPasswordDecryptionInitializationVector(new GuardedByteArray(new byte[1]));
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordDecryptionInitializationVectorNotNull() {
        config.setSynchronizePasswords(true);
        config.setPasswordAttributeToSynchronize("somepassword");
        config.setPasswordDecryptionKey(new GuardedByteArray(new byte[1]));
        config.setPasswordDecryptionInitializationVector(null);
        config.validate();
    }

    @Test(expected = ConfigurationException.class)
    public void testPasswordDecryptionInitializationVectorNotBlank() {
        config.setSynchronizePasswords(true);
        config.setPasswordAttributeToSynchronize("somepassword");
        config.setPasswordDecryptionKey(new GuardedByteArray(new byte[1]));
        config.setPasswordDecryptionInitializationVector(new GuardedByteArray(new byte[0]));
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
        assertEquals(0, config.getBaseContexts().length);
        assertEquals("userPassword", config.getPasswordAttribute());
        assertEquals(newList("top", "person", "organizationalPerson", "inetOrgPerson"), Arrays.asList(config.getAccountObjectClasses()));
        assertEquals(newList("uid", "cn"), Arrays.asList(config.getAccountUserNameAttributes()));
        assertNull(config.getAccountSearchFilter());
        assertEquals("uniqueMember", config.getGroupMemberAttribute());
        assertFalse(config.isMaintainLdapGroupMembership());
        assertFalse(config.isMaintainPosixGroupMembership());
        assertFalse(config.isRespectResourcePasswordPolicyChangeAfterReset());
        assertNull(config.getPasswordHashAlgorithm());
        assertTrue(config.isUseBlocks());
        assertEquals(100, config.getBlockSize());
        assertFalse(config.isUsePagedResultControl());
        assertEquals("uid", config.getVlvSortAttribute());
        assertEquals("entryUUID", config.getUidAttribute());
        assertTrue(config.isReadSchema());
        assertEquals(0, config.getExtendedObjectClasses().length);
        assertEquals(0, config.getBaseContextsToSynchronize().length);
        assertTrue(Arrays.equals(new String[] { "inetOrgPerson" }, config.getObjectClassesToSynchronize()));
        assertEquals(0, config.getAttributesToSynchronize().length);
        assertEquals(0, config.getModifiersNamesToFilterOut().length);
        assertNull(config.getAccountSynchronizationFilter());
        assertEquals(100, config.getChangeLogBlockSize());
        assertEquals("changeNumber", config.getChangeNumberAttribute());
        assertFalse(config.isFilterWithOrInsteadOfAnd());
        assertTrue(config.isRemoveLogEntryObjectClassFromFilter());
        assertFalse(config.isSynchronizePasswords());
        assertNull(config.getPasswordAttributeToSynchronize());
        assertNull(config.getPasswordDecryptionKey());
        assertNull(config.getPasswordDecryptionInitializationVector());
    }

    private static void assertCanValidate(LdapConfiguration config) {
        try {
            config.validate();
        } catch (Exception e) {
            fail();
        }
    }
}
