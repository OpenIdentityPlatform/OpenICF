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
import static org.junit.Assert.fail;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

public class LdapConfigurationTests /* extends LdapConnectorTestBase*/ {

    private static final String INVALID_DN = "dc=a,,";

    private LdapConfiguration config;

    @Before
    public void before() throws Exception {
        config = new LdapConfiguration();
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
    public void testBaseDNsNoDuplicates() {
        config.setBaseContexts(LdapConnectorTestBase.ACME_DN, LdapConnectorTestBase.ACME_DN);
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
    public void testEffectiveValues() {
        assertEquals(389, config.getPort());
        config.setSsl(true);
        assertEquals(636, config.getPort());
        config.setPort(1234);
        assertEquals(1234, config.getPort());

        assertEquals("localhost", config.getHost());
        config.setHost("example.com");
        assertEquals("example.com", config.getHost());

        assertEquals(0, config.getExtendedObjectClasses().length);
    }

    private static void assertCanValidate(LdapConfiguration config) {
        try {
            config.validate();
        } catch (Exception e) {
            fail();
        }
    }
}
