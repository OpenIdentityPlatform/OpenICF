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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.framework.test.TestHelpers;
import org.junit.Test;

public class AdapterCompatibilityTests extends LdapConnectorTestBase {

    // TODO test authenticate.

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testAccountSchema() {
        Schema schema = newFacade().schema();

        ObjectClassInfo oci = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
        assertFalse(oci.isContainer());

        Set<AttributeInfo> attrInfos = oci.getAttributeInfo();

        AttributeInfo info = AttributeInfoUtil.find(LdapPredefinedAttributes.PASSWORD_NAME, attrInfos);
        assertEquals(AttributeInfoBuilder.build(LdapPredefinedAttributes.PASSWORD_NAME, GuardedString.class, EnumSet.noneOf(Flags.class)), info);

        info = AttributeInfoUtil.find(LdapPredefinedAttributes.FIRSTNAME_NAME, attrInfos);
        assertEquals(AttributeInfoBuilder.build(LdapPredefinedAttributes.FIRSTNAME_NAME, String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find(LdapPredefinedAttributes.LASTNAME_NAME, attrInfos);
        assertEquals(AttributeInfoBuilder.build(LdapPredefinedAttributes.LASTNAME_NAME, String.class, EnumSet.of(Flags.MULTIVALUED, Flags.REQUIRED)), info);

        info = AttributeInfoUtil.find("modifyTimeStamp", attrInfos);
        assertEquals(AttributeInfoBuilder.build("modifyTimeStamp", String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE)), info);

        final Set<String> RET_BY_DEF_ATTRS = CollectionUtil.newSet(
                Name.NAME,
                LdapPredefinedAttributes.PASSWORD_NAME,
                LdapPredefinedAttributes.FIRSTNAME_NAME,
                LdapPredefinedAttributes.LASTNAME_NAME,
                "modifyTimeStamp"
        );
        Set<String> retByDefAttrs = new HashSet<String>();
        for (AttributeInfo attrInfo : attrInfos) {
            if (attrInfo.isReturnedByDefault()) {
                retByDefAttrs.add(attrInfo.getName());
            }
        }
        assertEquals(RET_BY_DEF_ATTRS, retByDefAttrs);
    }

    @Test
    public void testAccountAttributes() {
        ConnectorFacade facade = newFacade();
        ConnectorObject user0 = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(USER_0_DN));

        assertEquals(USER_0_DN, user0.getName().getNameValue());
        assertEquals(USER_0_SN, AttributeUtil.getAsStringValue(user0.getAttributeByName(LdapPredefinedAttributes.LASTNAME_NAME)));
        assertEquals(USER_0_GIVEN_NAME, AttributeUtil.getAsStringValue(user0.getAttributeByName(LdapPredefinedAttributes.FIRSTNAME_NAME)));
    }

    @Test
    public void testGroupSchema() {
        Schema schema = newFacade().schema();

        ObjectClassInfo oci = schema.findObjectClassInfo(LdapObjectClass.GROUP_NAME);
        assertFalse(oci.isContainer());

        Set<AttributeInfo> attrInfos = oci.getAttributeInfo();

        // XXX not using the infos in PredefinedAttributeInfos because the LDAP attributes that
        // SHORT_NAME and DESCRIPTION are multivalued. Check if we need to override LDAP to make them single-valued.

        AttributeInfo info = AttributeInfoUtil.find(PredefinedAttributes.SHORT_NAME, attrInfos);
        assertEquals(AttributeInfoBuilder.build(PredefinedAttributes.SHORT_NAME, String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find(PredefinedAttributes.DESCRIPTION, attrInfos);
        assertEquals(AttributeInfoBuilder.build(PredefinedAttributes.DESCRIPTION, String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("dn", attrInfos);
        assertEquals(AttributeInfoBuilder.build("dn", String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE)), info);

        info = AttributeInfoUtil.find("objectClass", attrInfos);
        assertEquals(AttributeInfoBuilder.build("objectClass", String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE, Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("cn", attrInfos);
        assertEquals(AttributeInfoBuilder.build("cn", String.class, EnumSet.of(Flags.REQUIRED, Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("description", attrInfos);
        assertEquals(AttributeInfoBuilder.build("description", String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("owner", attrInfos);
        assertEquals(AttributeInfoBuilder.build("owner", String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("uniqueMember", attrInfos);
        assertEquals(AttributeInfoBuilder.build("uniqueMember", String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        final Set<String> RET_BY_DEF_ATTRS = CollectionUtil.newSet(
                Name.NAME,
                PredefinedAttributes.SHORT_NAME,
                PredefinedAttributes.DESCRIPTION,
                "dn",
                "objectClass",
                "cn",
                "description",
                "owner",
                "uniqueMember"
        );
        Set<String> retByDefAttrs = new HashSet<String>();
        for (AttributeInfo attrInfo : attrInfos) {
            if (attrInfo.isReturnedByDefault()) {
                retByDefAttrs.add(attrInfo.getName());
            }
        }
        assertEquals(RET_BY_DEF_ATTRS, retByDefAttrs);
    }

    @Test
    public void testGroupAttributes() {
        ConnectorFacade facade = newFacade();
        ConnectorObject object = searchByAttribute(facade, LdapObjectClass.GROUP, new Name(LOONEY_TUNES_DN));

        assertEquals(LOONEY_TUNES_DN, object.getName().getNameValue());
        assertEquals(LOONEY_TUNES_CN, AttributeUtil.getStringValue(object.getAttributeByName(PredefinedAttributes.SHORT_NAME)));
        assertEquals(LOONEY_TUNES_DESCRIPTION, AttributeUtil.getStringValue(object.getAttributeByName(PredefinedAttributes.DESCRIPTION)));
        assertEquals(LOONEY_TUNES_DN, AttributeUtil.getStringValue(object.getAttributeByName("dn")));
        assertEquals(LOONEY_TUNES_CN, AttributeUtil.getStringValue(object.getAttributeByName("cn")));
        assertEquals(LOONEY_TUNES_DESCRIPTION, AttributeUtil.getStringValue(object.getAttributeByName("description")));

        // The owner attribute is the CN of an account.
        String owner = AttributeUtil.getStringValue(object.getAttributeByName("owner"));
        assertEquals(WALT_DISNEY_CN, owner);

        // The uniqueMembers attribute contains CN's of accounts and groups.
        Set<Object> members = CollectionUtil.newSet(object.getAttributeByName("uniqueMember").getValue());
        assertTrue(members.contains(BUGS_AND_FRIENDS_CN));
        assertTrue(members.contains(SYLVESTER_CN));
    }

    @Test
    public void testOrganizationSchema() {
        Schema schema = newFacade().schema();

        ObjectClassInfo oci = schema.findObjectClassInfo(LdapObjectClass.ORGANIZATION_NAME);
        assertTrue(oci.isContainer());

        Set<AttributeInfo> attrInfos = oci.getAttributeInfo();

        AttributeInfo info = AttributeInfoUtil.find("dn", attrInfos);
        assertEquals(AttributeInfoBuilder.build("dn", String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE)), info);

        info = AttributeInfoUtil.find("objectClass", attrInfos);
        assertEquals(AttributeInfoBuilder.build("objectClass", String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE, Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("o", attrInfos);
        assertEquals(AttributeInfoBuilder.build("o", String.class, EnumSet.of(Flags.REQUIRED, Flags.MULTIVALUED)), info);

        final Set<String> RET_BY_DEF_ATTRS = CollectionUtil.newSet(
                Name.NAME,
                PredefinedAttributes.SHORT_NAME,
                "dn",
                "objectClass",
                "o"
        );
        Set<String> retByDefAttrs = new HashSet<String>();
        for (AttributeInfo attrInfo : attrInfos) {
            if (attrInfo.isReturnedByDefault()) {
                retByDefAttrs.add(attrInfo.getName());
            }
        }
        assertEquals(RET_BY_DEF_ATTRS, retByDefAttrs);
    }

    @Test
    public void testOrganizationAttributes() {
        ConnectorFacade facade = newFacade();
        ConnectorObject object = searchByAttribute(facade, LdapObjectClass.ORGANIZATION, new Name(ACME_DN), null);

        assertEquals(ACME_DN, object.getName().getNameValue());
        assertEquals(ACME_O, AttributeUtil.getStringValue(object.getAttributeByName(PredefinedAttributes.SHORT_NAME)));
        assertEquals(ACME_O, AttributeUtil.getAsStringValue(object.getAttributeByName("o")));
    }

    @Test
    public void testInetOrgPersonAttributes() {
        // The LDAP edit group form does exactly this operation.

        LdapConfiguration config = newConfiguration();
        config.setBaseDNs(ACME_DN);
        ConnectorFacade facade = newFacade(config);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("cn", "dn");
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, new ObjectClass("inetOrgPerson"), null, builder.build());

        ConnectorObject object = findObjectByAttribute(objects, "dn", BUGS_BUNNY_DN);
        assertEquals(BUGS_BUNNY_CN, AttributeUtil.getStringValue(object.getAttributeByName("cn")));
    }

    private static ConnectorObject findObjectByAttribute(List<ConnectorObject> objects, String attrName, Object value) {
        for (ConnectorObject object : objects) {
            Attribute attr = object.getAttributeByName(attrName);
            if (attr != null) {
                Object attrValue = AttributeUtil.getSingleValue(attr);
                if (value.equals(attrValue)) {
                    return object;
                }
            }
        }
        return null;
    }
}
