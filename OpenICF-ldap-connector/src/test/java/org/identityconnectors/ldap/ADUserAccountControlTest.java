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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

public class ADUserAccountControlTest {

    /**
     * userAccountControl bit 0x0002 is ACCOUNTDISABLE.
     */
    @Test
    public void isAccountDisabledReadsTheBitmask() {
        assertTrue(ADUserAccountControl.isAccountDisabled("2"));
        assertFalse(ADUserAccountControl.isAccountDisabled("512"));
    }

    /**
     * A corrupted userAccountControl value must fail with a ConnectorException
     * naming it, not a bare NumberFormatException.
     */
    @Test(expectedExceptions = ConnectorException.class)
    public void isAccountDisabledRejectsAMalformedValue() {
        ADUserAccountControl.isAccountDisabled("not-a-number");
    }
}
