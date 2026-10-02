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

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

/**
 * uac (userAccountControl) and msDSUac (ms-DS-User-Account-Control-Computed) are two
 * distinct LDAP attributes with different semantics; the constructor must keep them separate.
 */
public class ADUserAccountControlTests {

    @Test
    public void testLockoutAndPasswordExpiredAreReadFromMsDSUacNotUac() {
        // uac carries none of the bits under test; msDSUac carries both.
        ADUserAccountControl control =
                new ADUserAccountControl(ADUserAccountControl.NORMAL_ACCOUNT,
                        ADUserAccountControl.LOCKOUT | ADUserAccountControl.PASSWORD_EXPIRED);

        assertTrue("isAccountLockOut() must read the msDSUac value, not uac",
                control.isAccountLockOut());
        assertTrue("isPasswordExpired() must read the msDSUac value, not uac",
                control.isPasswordExpired());
    }

    @Test
    public void testLockoutAndPasswordExpiredIgnoreUacBits() {
        // uac carries both bits; msDSUac carries neither, so the computed status must be false.
        ADUserAccountControl control =
                new ADUserAccountControl(ADUserAccountControl.LOCKOUT
                        | ADUserAccountControl.PASSWORD_EXPIRED, 0);

        assertFalse("isAccountLockOut() must not be derived from uac",
                control.isAccountLockOut());
        assertFalse("isPasswordExpired() must not be derived from uac",
                control.isPasswordExpired());
    }
}
