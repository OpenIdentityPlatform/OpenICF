/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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
package org.identityconnectors.ldap.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnectorTestBase;
import org.junit.Test;

public class LdapSchemaMappingTests extends LdapConnectorTestBase {

    // TODO operational attributes.
    // TODO test for operation option infos.

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testObjectClassAttrIsReadOnly() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        Schema schema = newFacade(config).schema();
        for (ObjectClassInfo oci : schema.getObjectClassInfo()) {
            AttributeInfo attrInfo = AttributeInfoUtil.find("objectClass", oci.getAttributeInfo());
            assertFalse(attrInfo.isRequired());
            assertFalse(attrInfo.isCreateable());
            assertFalse(attrInfo.isUpdateable());
        }
    }

    @Test
    public void testAccountSchema() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        Schema schema = newFacade(config).schema();

        ObjectClassInfo oci = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
        assertFalse(oci.isContainer());

        Set<AttributeInfo> attrInfos = oci.getAttributeInfo();

        AttributeInfo info = AttributeInfoUtil.find("cn", attrInfos);
        assertEquals(AttributeInfoBuilder.build("cn", String.class, EnumSet.of(Flags.REQUIRED, Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("uid", attrInfos);
        assertEquals(AttributeInfoBuilder.build("uid", String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("givenName", attrInfos);
        assertEquals(AttributeInfoBuilder.build("givenName", String.class, EnumSet.of(Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find("sn", attrInfos);
        assertEquals(AttributeInfoBuilder.build("sn", String.class, EnumSet.of(Flags.REQUIRED, Flags.MULTIVALUED)), info);

        info = AttributeInfoUtil.find(OperationalAttributes.PASSWORD_NAME, attrInfos);
        assertEquals(OperationalAttributeInfos.PASSWORD, info);
    }

    @Test
    public void testGroupSchema() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        Schema schema = newFacade(config).schema();

        ObjectClassInfo oci = schema.findObjectClassInfo(ObjectClass.GROUP_NAME);
        assertFalse(oci.isContainer());

        Set<AttributeInfo> attrInfos = oci.getAttributeInfo();

        AttributeInfo info = AttributeInfoUtil.find("cn", attrInfos);
        assertEquals(AttributeInfoBuilder.build("cn", String.class, EnumSet.of(Flags.REQUIRED, Flags.MULTIVALUED)), info);
    }

    @Test
    public void testAuthenticationOnlyForAccounts() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        Schema schema = newFacade(config).schema();
        Set<ObjectClassInfo> ocis = schema.getSupportedObjectClassesByOperation().get(AuthenticationApiOp.class);
        assertEquals(1, ocis.size());
        assertTrue(ocis.iterator().next().is(ObjectClass.ACCOUNT_NAME));
    }

    @Test
    public void testExtendedObjectClasses() {
        Schema schema = newFacade(newConfiguration("dNSDomain:dc")).schema();

        ObjectClassInfo dnsDomainInfo = schema.findObjectClassInfo("dNSDomain");
        Set<AttributeInfo> dnsDomainAttrInfos = dnsDomainInfo .getAttributeInfo();

        // Inherited from domain.
        AttributeInfo dcInfo = AttributeInfoUtil.find("telephoneNumber", dnsDomainAttrInfos );
        assertEquals(AttributeInfoBuilder.build("telephoneNumber", String.class, EnumSet.of(Flags.MULTIVALUED)), dcInfo);
        // Defined in dNSDomain.
        AttributeInfo mxRecordInfo = AttributeInfoUtil.find("MXRecord", dnsDomainAttrInfos );
        assertEquals(AttributeInfoBuilder.build("MXRecord", String.class, EnumSet.of(Flags.MULTIVALUED)), mxRecordInfo);
    }

    @Test
    public void testAttributeTypes() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        Schema schema = newFacade(config).schema();
        ObjectClassInfo accountInfo = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
        Set<AttributeInfo> accountAttrInfos = accountInfo.getAttributeInfo();

        assertEquals(String.class, AttributeInfoUtil.find("cn", accountAttrInfos).getType());
        assertEquals(String.class, AttributeInfoUtil.find("ou", accountAttrInfos).getType());
        assertEquals(String.class, AttributeInfoUtil.find("telephoneNumber", accountAttrInfos).getType());

        assertEquals(byte[].class, AttributeInfoUtil.find("audio", accountAttrInfos).getType());
        assertEquals(byte[].class, AttributeInfoUtil.find("jpegPhoto", accountAttrInfos).getType());
        assertEquals(byte[].class, AttributeInfoUtil.find("userCertificate", accountAttrInfos).getType());
        assertEquals(byte[].class, AttributeInfoUtil.find("x500UniqueIdentifier", accountAttrInfos).getType());
    }
}
