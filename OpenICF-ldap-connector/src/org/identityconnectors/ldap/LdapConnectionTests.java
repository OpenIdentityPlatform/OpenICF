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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.sun.jndi.ldap.ctl.PagedResultsControl;
import com.sun.jndi.ldap.ctl.VirtualListViewControl;

public class LdapConnectionTests extends LdapConnectorTestBase {

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testSupportedControls() {
        LdapConnection conn = new LdapConnection(newConfiguration());
        assertTrue(conn.supportsControl(PagedResultsControl.OID));
        assertTrue(conn.supportsControl(VirtualListViewControl.OID));
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
        conn = new LdapConnection(config);
        conn.test();
    }

    @Test(expected = RuntimeException.class)
    public void testCheckAlive() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true); // Since we are calling createNativeSchema() below.
        LdapConnection conn = new LdapConnection(config);
        conn.checkAlive();
        // Ensure the connection is really connected to the server.
        conn.createNativeSchema();
        conn.checkAlive();
        stopServer();
        // This should throw RuntimeException.
        conn.checkAlive();
    }
}
