/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
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

import static org.identityconnectors.framework.common.objects.AttributeUtil.createSpecialName;
import static org.identityconnectors.ldap.ADLdapUtil.guidStringtoByteString;
import static org.identityconnectors.ldap.LdapEntry.isDNAttribute;
import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;

import java.text.ParseException;

import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.ldap.search.LdapInternalSearch;

/* 
 * This class provides static helper methods to handle 
 * the MS AD userAccountControl computed attribute.
 */
public class ADUserAccountControl {

    private int uac = NORMAL_ACCOUNT;
    private int msDSUac = 0;

    /*
     * The Pwd-Last-Set attribute represents the date and time that the password for this account was last changed.
     * for read purpose, only values that can be set:
     * 0 - To force a user to change his password at next logon
     * -1 - To set it to current date
     */
    public static final String PWD_LAST_SET = "pwdLastSet";

    /*
     * The date when a Microsoft Active Directory account expires.
     * For read purpose
     * A value of 0 or 9,223,372,036,854,775,807 indicates that the account never expires.
     */
    public static final String ACCOUNT_EXPIRES = "accountExpires";
    public static final String ACCOUNT_EXPIRES_NAME = createSpecialName("ACCOUNT_EXPIRES");
    public static final String ACCOUNT_NEVER_EXPIRES = "9223372036854775807";

    /*
     * The date and time (UTC) that this account was locked out. 
     * Represents the number of 100-nanosecond intervals since January 1, 1601 (UTC). 
     * A value of zero means that the account is not currently locked out.
     */
    public static final String LOCKOUT_TIME = "lockoutTime";

    /*
     * The last time the user logged on. 
     * Represents the number of 100-nanosecond intervals since January 1, 1601 (UTC). 
     * A value of zero means that the last logon time is unknown.
     */
    public static final String LAST_LOGON = "lastLogon";

    /*
     * The date when this object was last changed.
     */
    public static final String WHEN_CHANGED = "whenChanged";

    /*
     * The date when this object was created.
     */
    public static final String WHEN_CREATED = "whenCreated";

    /*
     * Flags that control the behavior of the user account.
     */
    public static final String MS_USR_ACCT_CTRL_ATTR = "userAccountControl";

    /* 
     * userAccountControl value for a normal enabled account
     */
    public static final String NORMAL_ENABLED = "512";
    /* 
     * userAccountControl value for a normal enabled account
     */
    public static final String NORMAL_DISABLED = "514";

    /*
     * Computed system attributes used to check if account is locked out 
     * or if password is expired. This is not provided by userAccountControl
     */
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
     * No password is required special attribute
     */
    public static final String PASSWORD_NOTREQD_NAME = createSpecialName("PASSWORD_NOTREQD");
    /*
     * The user cannot change the password. !!! this does not work - can't read nor set it with userAccountControl
     */
    public static final int PASSWD_CANT_CHANGE = 0x00000040;
    /*
     * The user cannot change the password special attribute
     */
    public static final String PASSWD_CANT_CHANGE_NAME = createSpecialName("PASSWD_CANT_CHANGE");
    /*
     * This is a default account type that represents a typical user.
     */
    public static final int NORMAL_ACCOUNT = 0x00000200;
    /*
     * The password for this account will never expire.
     */
    public static final int DONT_EXPIRE_PASSWORD = 0x00010000;
    /*
     * The password for this account will never expire - special name
     */
    public static final String DONT_EXPIRE_PASSWORD_NAME = createSpecialName("DONT_EXPIRE_PASSWORD");
    /*
     * Smart card required for logon
     */
    public static final int SMARTCARD_REQUIRED = 0x40000;
    /*
     * Smart card required for logon special name
     */
    public static final String SMARTCARD_REQUIRED_NAME = createSpecialName("SMARTCARD_REQUIRED");
    /*
     * The user's password has expired.
     */
    public static final int PASSWORD_EXPIRED = 0x00800000;
    /*
     * The user can send an encrypted password
     */
    public static final int ENCRYPTED_TEXT_PASSWORD_ALLOWED = 0x00000080;

    public final static Set<String> AD_CONTROLS_DATES
            = CollectionUtil.newReadOnlySet(
                    PWD_LAST_SET,
                    ACCOUNT_EXPIRES,
                    ACCOUNT_EXPIRES_NAME,
                    LOCKOUT_TIME,
                    LAST_LOGON
            );

    public final static Set<Integer> ACCOUNT_CONTROLS
            = CollectionUtil.newReadOnlySet(
                    ACCOUNT_DISABLED,
                    LOCKOUT,
                    PASSWD_NOTREQD,
                    PASSWD_CANT_CHANGE,
                    NORMAL_ACCOUNT,
                    DONT_EXPIRE_PASSWORD,
                    PASSWORD_EXPIRED,
                    ENCRYPTED_TEXT_PASSWORD_ALLOWED
            );
    /*
     * Some of the controls are readonly
     */
    public final static Set<Integer> READ_ONLY_CONTROLS
            = CollectionUtil.newReadOnlySet(
                    PASSWD_CANT_CHANGE,
                    NORMAL_ACCOUNT
            );

    public final static Set<String> AD_SPECIAL_ATTRIBUTES
            = CollectionUtil.newReadOnlySet(
                    ACCOUNT_EXPIRES_NAME,
                    PASSWORD_NOTREQD_NAME,
                    //PASSWD_CANT_CHANGE_NAME,
                    DONT_EXPIRE_PASSWORD_NAME,
                    SMARTCARD_REQUIRED_NAME
            );

    public ADUserAccountControl() {
    }

    private ADUserAccountControl(int uac, int msDSUac) {
        this.uac = uac;
        this.msDSUac = uac;
    }

    public boolean isNormalAccount() {
        return (uac & NORMAL_ACCOUNT) == NORMAL_ACCOUNT;
    }

    public boolean isAccountDisabled() {
        return (uac & ACCOUNT_DISABLED) == ACCOUNT_DISABLED;
    }

    public boolean isPasswordNotReq() {
        return (uac & PASSWD_NOTREQD) == PASSWD_NOTREQD;
    }

    public boolean isPasswordCantChange() {
        return (uac & PASSWD_CANT_CHANGE) == PASSWD_CANT_CHANGE;
    }

    public boolean isDontExpirePassword() {
        return (uac & DONT_EXPIRE_PASSWORD) == DONT_EXPIRE_PASSWORD;
    }

    public boolean isSmartCardRequired() {
        return (uac & SMARTCARD_REQUIRED) == SMARTCARD_REQUIRED;
    }

    public boolean isAccountLockOut() {
        return (msDSUac & LOCKOUT) == LOCKOUT;
    }

    public boolean isPasswordExpired() {
        return (msDSUac & PASSWORD_EXPIRED) == PASSWORD_EXPIRED;
    }

    public String setAccountDisabled(boolean ctrl) {
        if (ctrl) {
            uac = uac | ACCOUNT_DISABLED;
        } else {
            uac = uac & ~ACCOUNT_DISABLED;
        }
        return Integer.toString(uac);
    }

    public String setPasswordNotReq(boolean ctrl) {
        if (ctrl) {
            uac = uac | PASSWD_NOTREQD;
        } else {
            uac = uac & ~PASSWD_NOTREQD;
        }
        return Integer.toString(uac);
    }

    public String setDontExpirePassword(boolean ctrl) {
        if (ctrl) {
            uac = uac | DONT_EXPIRE_PASSWORD;
        } else {
            uac = uac & ~DONT_EXPIRE_PASSWORD;
        }
        return Integer.toString(uac);
    }

    public String setSmartCardRequired(boolean ctrl) {
        if (ctrl) {
            uac = uac | SMARTCARD_REQUIRED;
        } else {
            uac = uac & ~SMARTCARD_REQUIRED;
        }
        return Integer.toString(uac);
    }

    public javax.naming.directory.Attribute addControl(Attribute attr) throws ParseException {
        String name = attr.getName();
        String value = attr.getValue().get(0).toString();

        if (OperationalAttributeInfos.ENABLE.is(name)) {
            boolean flag = Boolean.parseBoolean(value);
            return new BasicAttribute(MS_USR_ACCT_CTRL_ATTR, flag ? setAccountDisabled(false) : setAccountDisabled(true));
        } else if (SMARTCARD_REQUIRED_NAME.equalsIgnoreCase(name)) {
            boolean flag = Boolean.parseBoolean(value);
            return new BasicAttribute(MS_USR_ACCT_CTRL_ATTR, flag ? setSmartCardRequired(true) : setSmartCardRequired(false));
        } else if (DONT_EXPIRE_PASSWORD_NAME.equalsIgnoreCase(name)) {
            boolean flag = Boolean.parseBoolean(value);
            return new BasicAttribute(MS_USR_ACCT_CTRL_ATTR, flag ? setDontExpirePassword(true) : setDontExpirePassword(false));
        } else if (PASSWORD_NOTREQD_NAME.equalsIgnoreCase(name)) {
            boolean flag = Boolean.parseBoolean(value);
            return new BasicAttribute(MS_USR_ACCT_CTRL_ATTR, flag ? setPasswordNotReq(true) : setPasswordNotReq(false));
        } else if (OperationalAttributeInfos.PASSWORD_EXPIRED.is(name)) {
            boolean flag = Boolean.parseBoolean(value);
            return new BasicAttribute(PWD_LAST_SET, flag ? "0" : "-1");
        } else if (OperationalAttributeInfos.PASSWORD_EXPIRATION_DATE.is(name)) {
        } else if (OperationalAttributeInfos.LOCK_OUT.is(name) && !Boolean.parseBoolean(value)) {
            return new BasicAttribute(LOCKOUT_TIME, "0");
        } else if (ACCOUNT_EXPIRES_NAME.equalsIgnoreCase(name)) {
            if ("0".equalsIgnoreCase(value)){
                return new BasicAttribute(ACCOUNT_EXPIRES, "0");
            }
            return new BasicAttribute(ACCOUNT_EXPIRES, ADLdapUtil.getADTimeFromISO8601Date(value));
        }
        return null;
    }

    public BasicAttributes encodeControls(Set<Attribute> attrs) throws ParseException {
        BasicAttributes control = new BasicAttributes();

        for (Attribute attr : attrs) {
            control.put(addControl(attr));
        }
        return control;
    }

    // Static helpers
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

    public static boolean isSmartCardRequired(String status) {
        return ((Integer.parseInt(status) & SMARTCARD_REQUIRED) == SMARTCARD_REQUIRED);
    }

    public static boolean isPasswordExpired(String status) {
        return ((Integer.parseInt(status) & PASSWORD_EXPIRED) == PASSWORD_EXPIRED);
    }

    public static boolean isEncryptedTextPasswordAllowed(String status) {
        return ((Integer.parseInt(status) & ENCRYPTED_TEXT_PASSWORD_ALLOWED) == ENCRYPTED_TEXT_PASSWORD_ALLOWED);
    }

    public static ADUserAccountControl createADUserAccountControl(LdapConnection conn, String id) throws NamingException {
        if (LdapConstants.MS_GUID_ATTR.equalsIgnoreCase(conn.getConfiguration().getUidAttribute())) {
            SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{MSDS_USR_ACCT_CTRL_ATTR, MS_USR_ACCT_CTRL_ATTR});

            for (String context : conn.getConfiguration().getBaseContexts()) {
                NamingEnumeration<SearchResult> entries = conn.getInitialContext().search(context, String.format("%s=%s", LdapConstants.MS_GUID_ATTR, guidStringtoByteString(id)), controls);
                if (entries.hasMore()) {
                    SearchResult res = entries.next();
                    int uac = Integer.parseInt(res.getAttributes().get(MS_USR_ACCT_CTRL_ATTR).get().toString());
                    int msDSUac = Integer.parseInt(res.getAttributes().get(MSDS_USR_ACCT_CTRL_ATTR).get().toString());
                    return new ADUserAccountControl(uac, msDSUac);
                }
            }
        } else if (isDNAttribute(conn.getConfiguration().getUidAttribute())) {
            Attributes attrs = conn.getInitialContext().getAttributes(escapeDNValueOfJNDIReservedChars(id), new String[]{MSDS_USR_ACCT_CTRL_ATTR, MS_USR_ACCT_CTRL_ATTR});
            int uac = Integer.parseInt(attrs.get(MS_USR_ACCT_CTRL_ATTR).get().toString());
            int msDSUac = Integer.parseInt(attrs.get(MSDS_USR_ACCT_CTRL_ATTR).get().toString());
            return new ADUserAccountControl(uac, msDSUac);
        }
        throw new NamingException("Entry not found");
    }
}