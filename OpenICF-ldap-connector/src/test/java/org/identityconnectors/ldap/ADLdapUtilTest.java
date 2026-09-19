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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

public class ADLdapUtilTest {

    @Test
    public void parseADIntegerParsesAValidValue() {
        assertEquals(ADLdapUtil.parseADInteger("512"), 512);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void parseADIntegerWrapsAMalformedValue() {
        ADLdapUtil.parseADInteger("not-a-number");
    }

    @Test
    public void parseADIntegerErrorMessageNamesTheValue() {
        try {
            ADLdapUtil.parseADInteger("not-a-number");
        } catch (ConnectorException e) {
            assertTrue(e.getMessage().contains("not-a-number"), e.getMessage());
            return;
        }
        throw new AssertionError("Expected a ConnectorException");
    }

    @Test
    public void parseADLongParsesAValidValue() {
        assertEquals(ADLdapUtil.parseADLong("131425440000000000"), 131425440000000000L);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void parseADLongWrapsAMalformedValue() {
        ADLdapUtil.parseADLong("not-a-number");
    }
}
