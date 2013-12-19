/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.ldap;

import java.util.Set;
import org.identityconnectors.common.CollectionUtil;

/**
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */

/* 
 * This class provides static helper methods to handle 
 * the MS AD userAccountControl computed attribute.
 */
public class ADUserAccountControl {
    
    // Need to investigate: msDS-User-Account-Control-Computed
    // http://msdn.microsoft.com/en-us/library/ms677840.aspx

    public static final String MS_USR_ACCT_CTRL_ATTR = "userAccountControl";
    
    public static final String MSDS_USR_ACCT_CTRL_ATTR = "msDS-User-Account-Control-Computed";
    /*
     * The user account is disabled
     */
    public static final int ACCOUNT_DISABLED = 0x00000002;
    /*
     * The account is currently locked out.
     */
    public static final int LOCKOUT = 0x00000010;
    /*
     * No password is required
     */
    public static final int PASSWD_NOTREQD = 0x00000020;
    /*
     * The user cannot change the password.
     */
    public static final int PASSWD_CANT_CHANGE = 0x00000040;
    /*
     * This is a default account type that represents a typical user.
     */
    public static final int NORMAL_ACCOUNT = 0x00000200;
    /*
     * The password for this account will never expire.
     */
    public static final int DONT_EXPIRE_PASSWORD = 0x00010000;
    /*
     * The user's password has expired.
     */
    public static final int PASSWORD_EXPIRED = 0x00800000;
    /*
     * The user can send an encrypted password
     */
    public static final int ENCRYPTED_TEXT_PASSWORD_ALLOWED = 0x00000080;
    public final static Set<Integer> CONTROLS =
            CollectionUtil.newReadOnlySet(
            ACCOUNT_DISABLED,
            LOCKOUT,
            PASSWD_NOTREQD,
            PASSWD_CANT_CHANGE,
            NORMAL_ACCOUNT,
            DONT_EXPIRE_PASSWORD,
            PASSWORD_EXPIRED,
            ENCRYPTED_TEXT_PASSWORD_ALLOWED);
    /*
     * Some of the controls are readonly
     */
    public final static Set<Integer> READ_ONLY_CONTROLS =
            CollectionUtil.newReadOnlySet(
            PASSWD_CANT_CHANGE,
            NORMAL_ACCOUNT);

    public static boolean isAccountDisabled(String status) {
        return ((Integer.parseInt(status) & ACCOUNT_DISABLED) == ACCOUNT_DISABLED);
    }

    public static boolean isAccountLockOut(String status) {
        return ((Integer.parseInt(status) & LOCKOUT) == LOCKOUT);
    }

    public static boolean isPasswordNotReq(String status) {
        return ((Integer.parseInt(status) & PASSWD_NOTREQD) == PASSWD_NOTREQD);
    }

    public static boolean isPasswordCantChange(String status) {
        return ((Integer.parseInt(status) & PASSWD_CANT_CHANGE) == PASSWD_CANT_CHANGE);
    }

    public static boolean isNormalAccount(String status) {
        return ((Integer.parseInt(status) & NORMAL_ACCOUNT) == NORMAL_ACCOUNT);
    }

    public static boolean isDontExpirePassword(String status) {
        return ((Integer.parseInt(status) & DONT_EXPIRE_PASSWORD) == DONT_EXPIRE_PASSWORD);
    }

    public static boolean isPasswordExpired(String status) {
        return ((Integer.parseInt(status) & PASSWORD_EXPIRED) == PASSWORD_EXPIRED);
    }

    public static boolean isEncryptedTextPasswordAllowed(String status) {
        return ((Integer.parseInt(status) & ENCRYPTED_TEXT_PASSWORD_ALLOWED) == ENCRYPTED_TEXT_PASSWORD_ALLOWED);
    }

    public static String setAccountControl(String status, int ctrl, boolean value) {
        if (READ_ONLY_CONTROLS.contains(ctrl)) {
            return status;
        } else {
            if (value) {
                return Integer.toString(Integer.parseInt(status) | ctrl);
            } else {
                return Integer.toString(Integer.parseInt(status) & ~ctrl);
            }
        }
    }

    public static String setAccountDisabled(String status, boolean ctrl) {
        if (ctrl) {
            return Integer.toString(Integer.parseInt(status) | ACCOUNT_DISABLED);
        } else {
            return Integer.toString(Integer.parseInt(status) & ~ACCOUNT_DISABLED);
        }
    }

    public static String setAccountLockOut(String status, boolean ctrl) {
        if (ctrl) {
            return Integer.toString(Integer.parseInt(status) | LOCKOUT);
        } else {
            return Integer.toString(Integer.parseInt(status) & ~LOCKOUT);
        }
    }

    public static String setPasswordNotReq(String status, boolean ctrl) {
        if (ctrl) {
            return Integer.toString(Integer.parseInt(status) | PASSWD_NOTREQD);
        } else {
            return Integer.toString(Integer.parseInt(status) & ~PASSWD_NOTREQD);
        }
    }

    public static String setDontExpirePassword(String status, boolean ctrl) {
        if (ctrl) {
            return Integer.toString(Integer.parseInt(status) | DONT_EXPIRE_PASSWORD);
        } else {
            return Integer.toString(Integer.parseInt(status) & ~DONT_EXPIRE_PASSWORD);
        }
    }

    public static String setPasswordExpired(String status, boolean ctrl) {
        if (ctrl) {
            return Integer.toString(Integer.parseInt(status) | PASSWORD_EXPIRED);
        } else {
            return Integer.toString(Integer.parseInt(status) & ~PASSWORD_EXPIRED);
        }
    }

    public static String setEncryptedTextPasswordAllowed(String status, boolean ctrl) {
        if (ctrl) {
            return Integer.toString(Integer.parseInt(status) | ENCRYPTED_TEXT_PASSWORD_ALLOWED);
        } else {
            return Integer.toString(Integer.parseInt(status) & ~ENCRYPTED_TEXT_PASSWORD_ALLOWED);
        }
    }
}
