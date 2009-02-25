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
package org.identityconnectors.ldap.modify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnectorTestBase;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

public class LdapUpdateTests extends LdapConnectorTestBase {

    private static final String NUMBER1 = "+1 800 123 4567";
    private static final String NUMBER2 = "+1 800 765 4321";

    @Override
    protected boolean restartServerAfterEachTest() {
        return true;
    }

    @Test
    public void testSimpleAddRemoveAttrs() {
        ConnectorFacade facade = newFacade();
        ConnectorObject bugs = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));

        Attribute number1 = AttributeBuilder.build("telephoneNumber", NUMBER1);

        Uid newUid = facade.addAttributeValues(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(number1), null);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("telephoneNumber");
        OperationOptions options = builder.build();

        bugs = facade.getObject(ObjectClass.ACCOUNT, newUid, options);
        List<Object> numberAttr = bugs.getAttributeByName("telephoneNumber").getValue();
        assertEquals(NUMBER1, numberAttr.get(0));
        assertEquals(1, numberAttr.size());

        Attribute number2 = AttributeBuilder.build("telephoneNumber", NUMBER2);
        newUid = facade.addAttributeValues(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(number2), null);

        bugs = facade.getObject(ObjectClass.ACCOUNT, newUid, options);
        numberAttr = bugs.getAttributeByName("telephoneNumber").getValue();
        assertEquals(NUMBER1, numberAttr.get(0));
        assertEquals(NUMBER2, numberAttr.get(1));
        assertEquals(2, numberAttr.size());

        newUid = facade.removeAttributeValues(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(number1, number2), null);

        bugs = facade.getObject(ObjectClass.ACCOUNT, newUid, options);
        assertNull(bugs.getAttributeByName("telephoneNumber"));
    }

    @Test
    public void testRename() {
        ConnectorFacade facade = newFacade();
        ConnectorObject bugs = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));

        Name name = new Name("uid=daffy.duck,ou=Users,o=Acme,dc=example,dc=com");
        Attribute number = AttributeBuilder.build("telephoneNumber", NUMBER1);
        Uid newUid = facade.update(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(name, number), null);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("telephoneNumber");

        ConnectorObject daffy = facade.getObject(ObjectClass.ACCOUNT, newUid, builder.build());
        assertEquals(name, daffy.getName());
        assertEquals(NUMBER1, daffy.getAttributeByName("telephoneNumber").getValue().get(0));
    }

    @Test
    public void testEmptyAttributeValueRemovesAttribute() {
        ConnectorFacade facade = newFacade();
        ConnectorObject bugs = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));

        Attribute number = AttributeBuilder.build("telephoneNumber", NUMBER1);
        Uid newUid = facade.update(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(number), null);

        Attribute noNumber = AttributeBuilder.build("telephoneNumber");
        assertNull(noNumber.getValue());
        newUid = facade.update(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(noNumber), null);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("telephoneNumber");

        bugs = facade.getObject(ObjectClass.ACCOUNT, newUid, builder.build());
        assertNull(bugs.getAttributeByName("telephoneNumber"));
    }

    @Test
    public void testUpdateBinaryAttributes() throws IOException {
        ConnectorFacade facade = newFacade();
        ConnectorObject bugs = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));

        byte[] certificate = IOUtil.getResourceAsBytes(LdapUpdateTests.class, "certificate.cert");
        Attribute certAttr = AttributeBuilder.build("userCertificate", certificate);
        Uid newUid = facade.update(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(certAttr), null);

        byte[] photo = IOUtil.getResourceAsBytes(LdapUpdateTests.class, "photo.jpg");
        Attribute photoAttr = AttributeBuilder.build("jpegPhoto", photo);
        newUid = facade.addAttributeValues(ObjectClass.ACCOUNT, newUid, CollectionUtil.newSet(photoAttr), null);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("userCertificate", "jpegPhoto");

        bugs = facade.getObject(ObjectClass.ACCOUNT, newUid, builder.build());
        byte[] storedCertificate = (byte[]) bugs.getAttributeByName("userCertificate").getValue().get(0);
        assertTrue(Arrays.equals(certificate, storedCertificate));
        byte[] storedPhoto = (byte[]) bugs.getAttributeByName("jpegPhoto").getValue().get(0);
        assertTrue(Arrays.equals(photo, storedPhoto));
    }

    @Test
    public void testAdminCanChangePassword() {
        ConnectorFacade facade = newFacade();
        ConnectorObject elmer = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(ELMER_FUDD_DN));

        GuardedString password = new GuardedString("shotgun".toCharArray());
        Attribute pwdAttr = AttributeBuilder.buildPassword(password);
        facade.update(ObjectClass.ACCOUNT, elmer.getUid(), CollectionUtil.newSet(pwdAttr), null);

        // Now test that the user can login with the new password and execute an operation.
        LdapConfiguration config = newConfiguration();
        config.setPrincipal(ELMER_FUDD_DN);
        config.setCredentials(password);
        config.setBlockCount(0); // Do not use paged search, since the user doesn't have the privilege.
        facade = newFacade(config);
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, new ObjectClass("organization"), null);
        assertNotNull(findByAttribute(objects, Name.NAME, ACME_DN));
    }

    @Test
    public void testUserCanChangePassword() {
        LdapConfiguration config = newConfiguration();
        config.setPrincipal(BUGS_BUNNY_DN);
        config.setCredentials(new GuardedString("carrot".toCharArray()));
        config.setBlockCount(0); // Do not use paged search, since the user doesn't have the privilege.
        ConnectorFacade facade = newFacade(config);
        ConnectorObject bugs = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));

        GuardedString password = new GuardedString("cabbage".toCharArray());
        Attribute pwdAttr = AttributeBuilder.buildPassword(password);
        facade.update(ObjectClass.ACCOUNT, bugs.getUid(), CollectionUtil.newSet(pwdAttr), null);

        // Now test that the user can login with the new password and execute an operation.
        config.setCredentials(password);
        facade = newFacade(config);
        ConnectorObject elmer = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(ELMER_FUDD_DN));
        assertNotNull(elmer);
    }
}
