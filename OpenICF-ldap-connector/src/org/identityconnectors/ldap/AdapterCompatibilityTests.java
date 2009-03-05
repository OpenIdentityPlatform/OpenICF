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
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

public class AdapterCompatibilityTests extends LdapConnectorTestBase {

    // TODO test authenticate.

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testAccountAttributes() {
        ConnectorFacade facade = newFacade();
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("uid", "cn", "givenName", "sn");
        ConnectorObject user0 = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(USER_0_DN), builder.build());

        assertEquals(USER_0_DN, user0.getName().getNameValue());
        assertEquals(USER_0_UID, AttributeUtil.getAsStringValue(user0.getAttributeByName("uid")));
        assertEquals(USER_0_CN, AttributeUtil.getAsStringValue(user0.getAttributeByName("cn")));
        assertEquals(USER_0_GIVEN_NAME, AttributeUtil.getAsStringValue(user0.getAttributeByName("givenName")));
        assertEquals(USER_0_SN, AttributeUtil.getAsStringValue(user0.getAttributeByName("sn")));
    }

    @Test
    public void testGroupAttributes() {
        ConnectorFacade facade = newFacade();
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("cn", "uniqueMember");
        ConnectorObject object = searchByAttribute(facade, ObjectClass.GROUP, new Name(LOONEY_TUNES_DN), builder.build());

        assertEquals(LOONEY_TUNES_CN, AttributeUtil.getAsStringValue(object.getAttributeByName("cn")));
        Attribute uniqueMember = object.getAttributeByName("uniqueMember");
        assertTrue(uniqueMember.getValue().contains(BUGS_AND_FRIENDS_DN));
    }

    @Test
    public void testOrganizationAttributes() {
        ConnectorFacade facade = newFacade();
        ObjectClass oclass = new ObjectClass("organization");
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("dn", "o", "objectClass");
        ConnectorObject object = searchByAttribute(facade, oclass, new Name(ACME_DN), builder.build());

        assertEquals(ACME_DN, object.getName().getNameValue());
        assertEquals(ACME_DN, AttributeUtil.getAsStringValue(object.getAttributeByName("dn")));
        assertEquals(ACME_O, AttributeUtil.getAsStringValue(object.getAttributeByName("o")));
    }

    @Test
    public void testInetOrgPersonAttributes() {
        // The LDAP edit group form does exactly this operation.

        LdapConfiguration config = newConfiguration();
        config.setBaseContexts(ACME_DN);
        ConnectorFacade facade = newFacade(config);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("cn", "dn");
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, new ObjectClass("inetOrgPerson"), null, builder.build());

        ConnectorObject object = findByAttribute(objects, "dn", BUGS_BUNNY_DN);
        assertEquals(BUGS_BUNNY_CN, AttributeUtil.getStringValue(object.getAttributeByName("cn")));
    }
}
