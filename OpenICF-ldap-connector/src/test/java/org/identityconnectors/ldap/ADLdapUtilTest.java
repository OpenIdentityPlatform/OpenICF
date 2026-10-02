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
            assertEquals(e.getMessage(), "Not a valid Active Directory numeric value: 'not-a-number'");
            assertTrue(e.getCause() instanceof NumberFormatException, String.valueOf(e.getCause()));
            return;
        }
        throw new AssertionError("Expected a ConnectorException");
    }

    @Test
    public void parseADLongParsesAValidValue() {
        assertEquals(ADLdapUtil.parseADLong("131425440000000000"), 131425440000000000L);
    }

    @Test
    public void parseADLongParsesAUSNPastTheIntRange() {
        assertEquals(ADLdapUtil.parseADLong("2147483648"), 2147483648L);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void parseADLongWrapsAMalformedValue() {
        ADLdapUtil.parseADLong("not-a-number");
    }

    @Test
    public void parseADLongErrorMessageNamesTheValue() {
        try {
            ADLdapUtil.parseADLong("not-a-number");
        } catch (ConnectorException e) {
            assertEquals(e.getMessage(), "Not a valid Active Directory numeric value: 'not-a-number'");
            assertTrue(e.getCause() instanceof NumberFormatException, String.valueOf(e.getCause()));
            return;
        }
        throw new AssertionError("Expected a ConnectorException");
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void getJavaDateFromADTimeWrapsAMalformedValue() {
        ADLdapUtil.getJavaDateFromADTime("not-a-number");
    }
}
