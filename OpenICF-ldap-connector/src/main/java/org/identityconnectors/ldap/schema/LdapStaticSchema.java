/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html See the License for the specific
 * language governing permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://forgerock.org/license/CDDLv1.0.html If
 * applicable, add the following below the CDDL Header, with the fields enclosed
 * by brackets [] replaced by your own identifying information: "Portions
 * Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.ldap.schema;

import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveMap;
import static org.identityconnectors.common.CollectionUtil.newSet;
import static org.identityconnectors.common.CollectionUtil.newReadOnlySet;
import static org.identityconnectors.common.CollectionUtil.union;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.ldap.LdapNativeSchema;
import org.identityconnectors.ldap.LdapAttributeType;
import org.identityconnectors.ldap.LdapConstants;

public class LdapStaticSchema implements LdapNativeSchema {

    private static final String OBJ_CLASS = "objectClass";

    //Persons
    private final static String ACCOUNT = "account";
    private final static String PERSON = "person";
    private final static String ORGANIZATIONAL_PERSON = "organizationalPerson";
    private final static String INETORG_PERSON = "inetOrgPerson";

    //Groups
    private final static String GROUPOFNAMES = "groupOfNames";
    private final static String GROUPOFUNIQUENAMES = "groupOfUniqueNames";

    //Orgs
    private final static String ORGUNIT = "organizationalUnit";
    private final static String ORG = "organization";

    private final static Set<String> PERSON_MUST = newReadOnlySet("cn", "sn");

    private final static Set<String> PERSON_MAY = newReadOnlySet(
            "userPassword", "telephoneNumber", "seeAlso", "description",
            LdapConstants.LDAP_GROUPS_NAME, OBJ_CLASS
    );

    private final static Set<String> ORG_PERSON_MAY = newReadOnlySet(
            "title", "x121Address", "registeredAddress",
            "destinationIndicator", "preferredDeliveryMethod",
            "telexNumber", "teletexTerminalIdentifier",
            "internationalISDNNumber", "facsimileTelephoneNumber",
            "street", "postOfficeBox", "postalCode", "postalAddress",
            "physicalDeliveryOfficeName", "ou", "st", "l"
    );

    private final static Set<String> INETORG_PERSON_MAY = newReadOnlySet(
            "audio", "businessCategory", "carLicense", "departmentNumber",
            "displayName", "employeeNumber", "employeeType", "givenName",
            "homePhone", "homePostalAddress", "initials", "jpegPhoto",
            "labeledURI", "mail", "manager", "mobile", "o ", "pager", "photo",
            "roomNumber", "secretary", "uid", "userCertificate",
            "x500uniqueIdentifier", "preferredLanguage",
            "userSMIMECertificate", "userPKCS12"
    );

    private final static Set<String> GROUP_MAY = newReadOnlySet(
            "businessCategory", "seeAlso", "owner",
            "ou", "o", "description",
            OBJ_CLASS
    );

    private final static Set<String> ORG_MAY = newReadOnlySet(
            "userPassword", "searchGuide", "seeAlso", "businessCategory",
            "x121Address", "registeredAddress", "destinationIndicator",
            "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier",
            "telephoneNumber", "internationaliSDNNumber",
            "facsimileTelephoneNumber", "street", "postOfficeBox", "postalCode",
            "postalAddress", "physicalDeliveryOfficeName", "st", "l", "description",
            OBJ_CLASS
    );

    private final static String[] SINGLE_VALUE = new String[]{
        "preferredDeliveryMethod", "displayName",
        "employeeNumber", "preferredLanguage"
    };

    private final static String[] MULTI_BYTE_ARRAY = new String[]{
        "audio", "userPassword", "jpegPhoto",
        "photo", "userCertificate", "x500uniqueIdentifier"
    };

    private final Map<String, LdapAttributeType> attrName2Type = newCaseInsensitiveMap();

    public LdapStaticSchema() {
        initAttributeDescriptions();
    }

    public Set<String> getStructuralObjectClasses() {
        // we skip groupOfUniqueNames and inetOrgPerson since they are 
        // the default object classes used for __ACCOUNT__ and __GROUP__
        return newSet(ACCOUNT,
                PERSON,
                ORGANIZATIONAL_PERSON,
                GROUPOFNAMES,
                ORGUNIT,
                ORG
        );
    }

    public Set<String> getRequiredAttributes(String ldapClass) {
        if (ACCOUNT.equalsIgnoreCase(ldapClass)) {
            return newSet("uid");
        } else if (PERSON.equalsIgnoreCase(ldapClass)) {
            return PERSON_MUST;
        } else if (ORGANIZATIONAL_PERSON.equalsIgnoreCase(ldapClass)) {
            return PERSON_MUST;
        } else if (INETORG_PERSON.equalsIgnoreCase(ldapClass)) {
            return PERSON_MUST;
        } else if (GROUPOFNAMES.equalsIgnoreCase(ldapClass)) {
            return newSet("cn", "member");
        } else if (GROUPOFUNIQUENAMES.equalsIgnoreCase(ldapClass)) {
            return newSet("cn", "uniqueMember");
        } else if (ORGUNIT.equalsIgnoreCase(ldapClass)) {
            return newSet("ou");
        } else if (ORG.equalsIgnoreCase(ldapClass)) {
            return newSet("o");
        }
        return newSet();
    }

    public Set<String> getOptionalAttributes(String ldapClass) {
        if (ACCOUNT.equalsIgnoreCase(ldapClass)) {
            return newSet("description", "seeAlso", "l", "o", "ou", "host");
        } else if (PERSON.equalsIgnoreCase(ldapClass)) {
            return PERSON_MAY;
        } else if (ORGANIZATIONAL_PERSON.equalsIgnoreCase(ldapClass)) {
            return union(PERSON_MAY, ORG_PERSON_MAY);
        } else if (INETORG_PERSON.equalsIgnoreCase(ldapClass)) {
            return union(union(PERSON_MAY, ORG_PERSON_MAY), INETORG_PERSON_MAY);
        } else if (GROUPOFNAMES.equalsIgnoreCase(ldapClass)) {
            return GROUP_MAY;
        } else if (GROUPOFUNIQUENAMES.equalsIgnoreCase(ldapClass)) {
            return GROUP_MAY;
        } else if (ORGUNIT.equalsIgnoreCase(ldapClass)) {
            return ORG_MAY;
        } else if (ORG.equalsIgnoreCase(ldapClass)) {
            return ORG_MAY;
        }
        return newSet();
    }

    public Set<String> getEffectiveObjectClasses(String ldapClass) {
        return newSet(ldapClass);
    }

    public LdapAttributeType getAttributeDescription(String ldapAttrName) {
        if (attrName2Type.get(ldapAttrName) != null) {
            return attrName2Type.get(ldapAttrName);
        }
        // default to multi valued strings...
        Set<Flags> flagsMulti = EnumSet.noneOf(Flags.class);
        flagsMulti.add(Flags.MULTIVALUED);
        return new LdapAttributeType(String.class, flagsMulti);
    }

    private void initAttributeDescriptions() {
        // Single value attributes
        for (String name : SINGLE_VALUE) {
            attrName2Type.put(name, new LdapAttributeType(String.class, EnumSet.noneOf(Flags.class)));
        }

        // Multi values Byte[] attributes
        Set<Flags> flagsMulti = EnumSet.noneOf(Flags.class);
        flagsMulti.add(Flags.MULTIVALUED);
        for (String name : MULTI_BYTE_ARRAY) {
            attrName2Type.put(name, new LdapAttributeType(byte[].class, flagsMulti));
        }

        // Special case for PASSWORD - no read, not return by default - just create/update
        Set<Flags> flagsNotRDef = EnumSet.noneOf(Flags.class);
        flagsNotRDef.add(Flags.NOT_READABLE);
        flagsNotRDef.add(Flags.NOT_RETURNED_BY_DEFAULT);
        attrName2Type.put(OperationalAttributes.PASSWORD_NAME, new LdapAttributeType(GuardedString.class, flagsNotRDef));
    }

}
