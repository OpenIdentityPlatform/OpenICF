/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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
package org.identityconnectors.ldap.schema;

import static java.util.Collections.emptySet;
import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveMap;
import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveSet;
import static org.identityconnectors.common.CollectionUtil.newSet;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.LdapNativeSchema;
import org.identityconnectors.ldap.LdapAttributeType;
import org.identityconnectors.ldap.ADGroupType;

/* 
 * This class provides basic static schema for Active Directory User/Group/Ou
 */
public class ADStaticSchema implements LdapNativeSchema {

    private static final String[] MULTI_RO_STRING = {"objectClass"};

    private static final String[] TOP_SINGLE_RO_STRING = {"cn", "displayName", "name",
        "objectGUID", "uSNChanged", "uSNCreated", "whenChanged", "whenCreated", "distinguishedName"
    };
    private static final String[] TOP_SINGLE_STRING = {"description"};

    private static final String[] GROUP_SINGLE_STRING = {"mail", "groupType", "sAMAccountName",
        "managedBy", "info", ADGroupType.GROUP_TYPE_NAME, ADGroupType.GROUP_SCOPE_NAME
    };
    private static final String[] GROUP_MULTI_RO_STRING = {"memberOf"};
    private static final String[] GROUP_MULTI_STRING = {"member"};

    private static final String[] OU_SINGLE_RO_STRING = {"ou"};
    private static final String[] OU_SINGLE_STRING = {"l", "postalCode", "co", "countryCode",
        "c", "streetAddress", "st", "street", "managedBy", "physicalDeliveryOfficeName",
        "telephoneNumber", "facsimileTelephoneNumber"
    };
    private static final String[] OU_MULTI_RO_STRING = {"directReports"};
    private static final String[] OU_MULTI_STRING = {"seeAlso", "businessCategory",
        "postOfficeBox", "postalAddress", "internationalISDNNumber", "telexNumber"
    };
    private static final String[] USER_SINGLE_RO_STRING = {"lastLogon", "pwdLastSet", "lockoutTime"};
    private static final String[] USER_SINGLE_STRING = {"sn", "sAMAccountName", "userPrincipalName",
        "userAccountControl", "givenName", "mail", "middleName", "company", "division",
        "facsimileTelephoneNumber", "homePhone", "l", "postalCode", "postOfficeBox",
        "physicalDeliveryOfficeName", "st", "manager", "accountExpires", "department",
        "initials", "streetAddress", "mobile", "employeeID", "employeeNumber", "employeeType",
        "co", "countryCode", "c", "info", "telephoneNumber", "title"
    };
    private static final String[] USER_MULTI_RO_STRING = {"memberOf", "directReports"};
    private static final String[] USER_MULTI_STRING = {"otherHomePhone", "seeAlso", "uid", "o", "url", "ou"};
    private static final String[] USER_SPECIAL_BOOLEAN = {ADUserAccountControl.SMARTCARD_REQUIRED_NAME,
        ADUserAccountControl.DONT_EXPIRE_PASSWORD_NAME, ADUserAccountControl.PASSWORD_NOTREQD_NAME,
        OperationalAttributes.ENABLE_NAME, OperationalAttributes.PASSWORD_EXPIRED_NAME,
        OperationalAttributes.LOCK_OUT_NAME
    };

    private static final String USER_OBJ = "user";
    private static final String GROUP_OBJ = "group";
    private static final String OU_OBJ = "organizationalUnit";

    private final Map<String, LdapAttributeType> attrName2Type = newCaseInsensitiveMap();

    private static final Set<String> OBJECT_CLASSES;

    static {
        OBJECT_CLASSES = newCaseInsensitiveSet();
        OBJECT_CLASSES.add("user");
        OBJECT_CLASSES.add("group");
        OBJECT_CLASSES.add("organizationalUnit");
    }

    public ADStaticSchema() {
        initAttributeDescriptions();
    }

    public Set<String> getStructuralObjectClasses() {
        return newSet(OU_OBJ);
    }

    public Set<String> getRequiredAttributes(String ldapClass) {
        return emptySet();
    }

    public Set<String> getOptionalAttributes(String ldapClass) {
        Set<String> result = newCaseInsensitiveSet();

        if (USER_OBJ.equalsIgnoreCase(ldapClass)) {
            result.add(OperationalAttributes.CURRENT_PASSWORD_NAME);
            result.add(ADUserAccountControl.ACCOUNT_EXPIRES_NAME);
            result.addAll(newSet(USER_SPECIAL_BOOLEAN));
            result.addAll(newSet(USER_SINGLE_STRING));
            result.addAll(newSet(USER_MULTI_STRING));
            result.addAll(newSet(USER_SINGLE_RO_STRING));
            result.addAll(newSet(USER_MULTI_RO_STRING));
        } else if (GROUP_OBJ.equalsIgnoreCase(ldapClass)) {
            result.addAll(newSet(GROUP_SINGLE_STRING));
            result.addAll(newSet(GROUP_MULTI_STRING));
            result.addAll(newSet(GROUP_MULTI_RO_STRING));
        } else if (OU_OBJ.equalsIgnoreCase(ldapClass)) {
            result.addAll(newSet(OU_SINGLE_STRING));
            result.addAll(newSet(OU_MULTI_STRING));
            result.addAll(newSet(OU_SINGLE_RO_STRING));
            result.addAll(newSet(OU_MULTI_RO_STRING));
        }
        result.addAll(newSet(MULTI_RO_STRING));
        result.addAll(newSet(TOP_SINGLE_RO_STRING));
        result.addAll(newSet(TOP_SINGLE_STRING));
        return result;
    }

    public Set<String> getEffectiveObjectClasses(String ldapClass) {
        return newSet(ldapClass);
    }

    public LdapAttributeType getAttributeDescription(String ldapAttrName) {
        return attrName2Type.get(ldapAttrName);
    }

    private void initAttributeDescriptions() {
        Set<String> singleRo = newCaseInsensitiveSet();
        Set<String> single = newCaseInsensitiveSet();
        Set<String> multi = newCaseInsensitiveSet();
        Set<String> multiRo = newCaseInsensitiveSet();
        Set<String> specialBool = newCaseInsensitiveSet();

        singleRo.addAll(newSet(TOP_SINGLE_RO_STRING));
        singleRo.addAll(newSet(USER_SINGLE_RO_STRING));
        singleRo.addAll(newSet(OU_SINGLE_RO_STRING));

        single.addAll(newSet(TOP_SINGLE_STRING));
        single.addAll(newSet(USER_SINGLE_STRING));
        single.addAll(newSet(GROUP_SINGLE_STRING));
        single.addAll(newSet(OU_SINGLE_STRING));

        multi.addAll(newSet(USER_MULTI_STRING));
        multi.addAll(newSet(GROUP_MULTI_STRING));
        multi.addAll(newSet(OU_MULTI_STRING));

        multiRo.addAll(newSet(MULTI_RO_STRING));
        multiRo.addAll(newSet(USER_MULTI_RO_STRING));
        multiRo.addAll(newSet(GROUP_MULTI_RO_STRING));
        multiRo.addAll(newSet(OU_MULTI_RO_STRING));

        specialBool.addAll(newSet(USER_SPECIAL_BOOLEAN));

        Set<Flags> flags = EnumSet.noneOf(Flags.class);
        for (String name : single) {
            attrName2Type.put(name, new LdapAttributeType(String.class, flags));
        }
        for (String name : specialBool) {
            attrName2Type.put(name, new LdapAttributeType(boolean.class, flags));
        }

        Set<Flags> flagsMulti = EnumSet.noneOf(Flags.class);
        flagsMulti.add(Flags.MULTIVALUED);
        for (String name : multi) {
            attrName2Type.put(name, new LdapAttributeType(String.class, flagsMulti));
        }

        Set<Flags> flagsMultiNotCru = EnumSet.noneOf(Flags.class);
        flagsMultiNotCru.add(Flags.MULTIVALUED);
        flagsMultiNotCru.add(Flags.NOT_CREATABLE);
        flagsMultiNotCru.add(Flags.NOT_UPDATEABLE);
        for (String name : multiRo) {
            attrName2Type.put(name, new LdapAttributeType(String.class, flagsMultiNotCru));
        }

        Set<Flags> flagsNotCru = EnumSet.noneOf(Flags.class);
        flagsNotCru.add(Flags.NOT_CREATABLE);
        flagsNotCru.add(Flags.NOT_UPDATEABLE);
        for (String name : singleRo) {
            attrName2Type.put(name, new LdapAttributeType(String.class, flagsNotCru));
        }

        // Special case for lock out - can't be created
        Set<Flags> flagsNotCreate = EnumSet.noneOf(Flags.class);
        flagsNotCreate.add(Flags.NOT_CREATABLE);
        attrName2Type.put(OperationalAttributes.LOCK_OUT_NAME, new LdapAttributeType(boolean.class, flagsNotCreate));

        // Special case for current password and special account expires- no create, no read, not returned by default
        Set<Flags> flagsNotCRDef = EnumSet.noneOf(Flags.class);
        flagsNotCRDef.add(Flags.NOT_CREATABLE);
        flagsNotCRDef.add(Flags.NOT_READABLE);
        flagsNotCRDef.add(Flags.NOT_RETURNED_BY_DEFAULT);
        attrName2Type.put(OperationalAttributes.CURRENT_PASSWORD_NAME, new LdapAttributeType(GuardedString.class, flagsNotCRDef));
        
        // Special case for account_expire - no read, not return by default - just create/update
        Set<Flags> flagsNotRDef = EnumSet.noneOf(Flags.class);
        flagsNotRDef.add(Flags.NOT_READABLE);
        flagsNotRDef.add(Flags.NOT_RETURNED_BY_DEFAULT);
        attrName2Type.put(ADUserAccountControl.ACCOUNT_EXPIRES_NAME, new LdapAttributeType(String.class, flagsNotRDef));
    }

}