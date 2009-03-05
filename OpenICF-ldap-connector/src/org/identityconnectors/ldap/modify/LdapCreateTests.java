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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnectorTestBase;
import org.junit.Test;

public class LdapCreateTests extends LdapConnectorTestBase{

    // TODO test that we can create an entry of an object class not in the schema.
    // TODO test that we can't create an entry outside the configured base DNs.

    @Override
    protected boolean restartServerAfterEachTest() {
        return true;
    }

    @Test
    public void testCreateAccountWhenNotReadingSchema() {
        LdapConfiguration config = newConfiguration();
        config.setBaseContexts(SMALL_COMPANY_DN);
        ConnectorFacade facade = newFacade(config);

        doCreateAccount(facade);
    }

    @Test
    public void testCreateAccountWhenReadingSchema() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        config.setBaseContexts(SMALL_COMPANY_DN);
        config.setAccountObjectClasses("inetOrgPerson");
        ConnectorFacade facade = newFacade(config);

        doCreateAccount(facade);
    }

    private void doCreateAccount(ConnectorFacade facade) {
        Set<Attribute> attributes = new HashSet<Attribute>();
        Name name = new Name("uid=another.worker," + SMALL_COMPANY_DN);
        attributes.add(name);
        attributes.add(AttributeBuilder.build("uid", "another.worker"));
        attributes.add(AttributeBuilder.build("cn", "Another Worker"));
        attributes.add(AttributeBuilder.build("givenName", "Another"));
        attributes.add(AttributeBuilder.build("sn", "Worker"));
        Uid uid = facade.create(ObjectClass.ACCOUNT, attributes, null);

        ConnectorObject newAccount = facade.getObject(ObjectClass.ACCOUNT, uid, null);
        assertEquals(name, newAccount.getName());
    }

    @Test
    public void testCreateGroupWhenNotReadingSchema() {
        LdapConfiguration config = newConfiguration();
        config.setBaseContexts(SMALL_COMPANY_DN);
        ConnectorFacade facade = newFacade(config);

        doCreateGroup(facade);
    }

    @Test
    public void testCreateGroupWhenReadingSchema() {
        LdapConfiguration config = newConfiguration();
        config.setReadSchema(true);
        config.setBaseContexts(SMALL_COMPANY_DN);
        config.setAccountObjectClasses("inetOrgPerson");
        ConnectorFacade facade = newFacade(config);

        doCreateGroup(facade);
    }

    private void doCreateGroup(ConnectorFacade facade) {
        Set<Attribute> attributes = new HashSet<Attribute>();
        Name name = new Name("cn=Another Group," + SMALL_COMPANY_DN);
        attributes.add(name);
        attributes.add(AttributeBuilder.build("cn", "Another Group"));
        Uid uid = facade.create(ObjectClass.GROUP, attributes, null);

        ConnectorObject newGroup = facade.getObject(ObjectClass.GROUP, uid, null);
        assertEquals(name, newGroup.getName());
    }

    @Test
    public void testCreateBinaryAttributes() throws IOException {
        ConnectorFacade facade = newFacade();

        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(new Name("uid=daffy.duck,ou=Users,o=Acme,dc=example,dc=com"));
        attributes.add(AttributeBuilder.build("uid", "daffy.duck"));
        attributes.add(AttributeBuilder.build("cn", "Daffy Duck"));
        attributes.add(AttributeBuilder.build("givenName", "Daffy"));
        attributes.add(AttributeBuilder.build("sn", "Duck"));
        byte[] certificate = IOUtil.getResourceAsBytes(LdapCreateTests.class, "certificate.cert");
        attributes.add(AttributeBuilder.build("userCertificate", certificate));
        byte[] photo = IOUtil.getResourceAsBytes(LdapCreateTests.class, "photo.jpg");
        attributes.add(AttributeBuilder.build("jpegPhoto", photo));
        Uid uid = facade.create(ObjectClass.ACCOUNT, attributes, null);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("userCertificate", "jpegPhoto");

        ConnectorObject newAccount = facade.getObject(ObjectClass.ACCOUNT, uid, builder.build());
        byte[] storedCertificate = (byte[]) newAccount.getAttributeByName("userCertificate").getValue().get(0);
        assertTrue(Arrays.equals(certificate, storedCertificate));
        byte[] storedPhoto = (byte[]) newAccount.getAttributeByName("jpegPhoto").getValue().get(0);
        assertTrue(Arrays.equals(photo, storedPhoto));
    }

    @Test
    public void testCreatePassword() {
        ConnectorFacade facade = newFacade();

        Set<Attribute> attributes = new HashSet<Attribute>();
        String name = "uid=daffy.duck,ou=Users,o=Acme,dc=example,dc=com";
        attributes.add(new Name(name));
        attributes.add(AttributeBuilder.build("uid", "daffy.duck"));
        attributes.add(AttributeBuilder.build("cn", "Daffy Duck"));
        attributes.add(AttributeBuilder.build("givenName", "Daffy"));
        attributes.add(AttributeBuilder.build("sn", "Duck"));
        GuardedString password = new GuardedString("I.hate.rabbits".toCharArray());
        attributes.add(AttributeBuilder.buildPassword(password));
        facade.create(ObjectClass.ACCOUNT, attributes, null);

        facade.authenticate(ObjectClass.ACCOUNT, name, password, null);
    }
}
