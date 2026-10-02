/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.identityconnectors.ldap;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ADLdapUtilTests {

    private static BasicAttribute unreadableGuid() {
        return new BasicAttribute("objectGUID") {
            private static final long serialVersionUID = 1L;

            @Override
            public Object get() throws NamingException {
                throw new NamingException("unreadable");
            }
        };
    }

    @Test
    public void testDashedStringCarriesNamingException() {
        try {
            ADLdapUtil.objectGUIDtoDashedString(unreadableGuid());
            Assert.fail("ConnectorException expected");
        } catch (ConnectorException e) {
            Assert.assertTrue(e.getCause() instanceof NamingException);
        }
    }

    @Test
    public void testStringCarriesNamingException() {
        try {
            ADLdapUtil.objectGUIDtoString(unreadableGuid());
            Assert.fail("ConnectorException expected");
        } catch (ConnectorException e) {
            Assert.assertTrue(e.getCause() instanceof NamingException);
        }
    }
}
