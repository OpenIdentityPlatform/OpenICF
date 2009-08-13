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

import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.ldap.LdapConnection.ServerType;
import org.junit.Test;

import com.sun.jndi.ldap.ctl.PagedResultsControl;
import com.sun.jndi.ldap.ctl.VirtualListViewControl;

public class LdapConnectionTests extends LdapConnectorTestBase {

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testSSL() throws NamingException {
        BlindTrustProvider.register();
        LdapConfiguration config = newConfiguration();
        config.setSsl(true);
        config.setPort(SSL_PORT);
        testConnection(config);
    }

    @Test
    public void testFailover() throws NamingException {
        LdapConfiguration config = newConfiguration();
        config.setHost("foobarbaz");
        config.setPort(65535);
        try {
            testConnection(config);
        } catch (ConnectorException e) {
            // OK.
        } catch (NamingException e) {
            // Should not normally occur.
            throw e;
        }

        config = newConfiguration();
        config.setHost("foobarbaz");
        config.setPort(65535);
        config.setFailover("ldap://localhost:" + PORT);
        testConnection(config);
    }

    private void testConnection(LdapConfiguration config) throws NamingException {
        LdapConnection conn = new LdapConnection(config);
        Attributes attrs = conn.getInitialContext().getAttributes(BUGS_BUNNY_DN);
        assertEquals(BUGS_BUNNY_CN, getStringAttrValue(attrs, "cn"));
    }

    @Test
    public void testDefaultAuthenticationMethodIsInferred() throws NamingException {
        LdapConfiguration config = newConfiguration();
        config.setPrincipal(null);
        LdapConnection conn = new LdapConnection(config);
        assertEquals("none", conn.getInitialContext().getEnvironment().get(Context.SECURITY_AUTHENTICATION));

        config = newConfiguration();
        config.setPrincipal(ADMIN_DN);
        config.setCredentials(ADMIN_PASSWORD);
        conn = new LdapConnection(config);
        assertEquals("simple", conn.getInitialContext().getEnvironment().get(Context.SECURITY_AUTHENTICATION));
    }

    @Test
    public void testTest() {
        LdapConfiguration config = newConfiguration();
        config.setPort(4242);
        LdapConnection conn = new LdapConnection(config);
        try {
            conn.test();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        config = newConfiguration();
        config.setHost("invalid");
        conn = new LdapConnection(config);
        try {
            conn.test();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        config = newConfiguration();
        config.setPrincipal("uid=nobody");
        conn = new LdapConnection(config);
        try {
            conn.test();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        config = newConfiguration();
        config.setCredentials(new GuardedString("bogus".toCharArray()));
        conn = new LdapConnection(config);
        try {
            conn.test();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        config = newConfiguration();
        conn = new LdapConnection(config);
        conn.test();
    }

    @Test
    public void testCheckAlive() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true); // Since we are calling createNativeSchema() below.
        LdapConnection conn = new LdapConnection(config);
        conn.checkAlive();
        // Ensure the connection is really connected to the server.
        conn.createNativeSchema();
        conn.checkAlive();
        stopServer();
        try {
            // This should throw RuntimeException.
            conn.checkAlive();
            fail();
        } catch (RuntimeException e) {
            // OK.
        }
    }

    @Test
    public void testSupportedControls() {
        LdapConnection conn = new LdapConnection(newConfiguration());
        assertTrue(conn.supportsControl(PagedResultsControl.OID));
        assertTrue(conn.supportsControl(VirtualListViewControl.OID));
    }

    @Test
    public void testServerType() {
        LdapConnection conn = new LdapConnection(newConfiguration());
        assertEquals(ServerType.OPENDS, conn.getServerType());
    }
}
