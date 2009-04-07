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
package org.identityconnectors.ldap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Test;

public class LdapAuthenticateTests extends LdapConnectorTestBase {

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testAuthenticate() {
        ConnectorFacade facade = newFacade();
        ConnectorObject bugs = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));
        Uid uid = facade.authenticate(ObjectClass.ACCOUNT, BUGS_BUNNY_DN, new GuardedString("carrot".toCharArray()), null);
        assertEquals(bugs.getUid(), uid);
    }

    @Test(expected = InvalidCredentialException.class)
    public void testAuthenticateInvalidPassword() {
        ConnectorFacade facade = newFacade();
        facade.authenticate(ObjectClass.ACCOUNT, BUGS_BUNNY_DN, new GuardedString("rabbithole".toCharArray()), null);
    }

    @Test(expected = InvalidCredentialException.class)
    public void testAuthenticateUnknownAccount() {
        ConnectorFacade facade = newFacade();
        facade.authenticate(ObjectClass.ACCOUNT, "hopefully.inexisting.user", new GuardedString("none".toCharArray()), null);
    }

    @Test
    public void testAuthenticateExpiredPassword() {
        LdapConfiguration config = newConfiguration();
        assertFalse(config.isRespectResourcePasswordPolicyChangeAfterReset());
        ConnectorFacade facade = newFacade(config);
        facade.authenticate(ObjectClass.ACCOUNT, EXPIRED_DN, new GuardedString("password".toCharArray()), null);

        config = newConfiguration();
        config.setRespectResourcePasswordPolicyChangeAfterReset(true);
        facade = newFacade(config);
        try {
            facade.authenticate(ObjectClass.ACCOUNT, EXPIRED_DN, new GuardedString("password".toCharArray()), null);
            fail();
        } catch (PasswordExpiredException e) {
            // OK.
        }
    }
}
