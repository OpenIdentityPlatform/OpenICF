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

import static org.junit.Assert.assertNull;

import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.ldap.LdapConnectorTestBase;
import org.identityconnectors.ldap.LdapObjectClass;
import org.junit.Test;

public class LdapDeleteTests extends LdapConnectorTestBase{

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test(expected = ConnectorException.class)
    public void testCannotDeleteExistingUidButWrongObjectClass() {
        ConnectorFacade facade = newFacade();
        ConnectorObject organization = searchByAttribute(facade, LdapObjectClass.ORGANIZATION, new Name(BIG_COMPANY_DN));
        // Should fail because the object class passed to delete() is not ORGANIZATION.
        facade.delete(ObjectClass.ACCOUNT, organization.getUid(), null);
    }

    @Test(expected = ConnectorException.class)
    public void testCannotDeleteNonEmptyDN() {
        // TODO: not sure this is the right behavior. Perhaps should instead
        // recursively delete everything under the deleted entry.
        ConnectorFacade facade = newFacade();
        ConnectorObject organization = searchByAttribute(facade, LdapObjectClass.ORGANIZATION, new Name(ACME_DN));
        facade.delete(LdapObjectClass.ORGANIZATION, organization.getUid(), null);
    }

    @Test()
    public void testDelete() {
        ConnectorFacade facade = newFacade();
        ConnectorObject account = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));
        facade.delete(ObjectClass.ACCOUNT, account.getUid(), null);

        account = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));
        assertNull(account);
    }
}
