/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.forgerock.openicf.csvfile.util.Base64;
import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

/**
 *
 * @author lazyman
 */
public class AuthenticateOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("authenticate.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
    }

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void passwordColumNameNotDefined() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setEncoding("utf-8");
        config.setFilePath(TestUtils.getTestFile("authenticate.csv"));
        config.setUniqueAttribute("uid");

        CSVFileConnector flat = new CSVFileConnector();
        flat.init(config);
        flat.authenticate(ObjectClass.ACCOUNT, "username",
                new GuardedString("password".toCharArray()), null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badObjectClass() {
        GuardedString guarded = new GuardedString(Base64.encode("good").toCharArray());
        connector.authenticate(ObjectClass.GROUP, "vilo", guarded, null);
    }

    @Test(expectedExceptions = InvalidPasswordException.class)
    public void badPassword() {
        GuardedString guarded = new GuardedString(Base64.encode("bad").toCharArray());
        connector.authenticate(ObjectClass.ACCOUNT, "vilo", guarded, null);
    }

    @Test(expectedExceptions = InvalidPasswordException.class)
    public void nullPassword() {
        connector.authenticate(ObjectClass.ACCOUNT, "vilo", null, null);
    }

    @Test(expectedExceptions = InvalidCredentialException.class)
    public void nullUsername() {
        GuardedString guarded = new GuardedString(Base64.encode("bad").toCharArray());
        connector.authenticate(ObjectClass.ACCOUNT, null, guarded, null);
    }

    @Test(expectedExceptions = InvalidCredentialException.class)
    public void nullUsernameAndPassword() {
        connector.authenticate(ObjectClass.ACCOUNT, null, null, null);
    }

    @Test(expectedExceptions = InvalidCredentialException.class)
    public void nonexistingUsername() {
        GuardedString guarded = new GuardedString(Base64.encode("bad").toCharArray());
        connector.authenticate(ObjectClass.ACCOUNT, "unexisting", guarded, null);
    }

    @Test
    public void correctAuthentication() {
        GuardedString guarded = new GuardedString(Base64.encode("good").toCharArray());
        Uid uid = connector.authenticate(ObjectClass.ACCOUNT, "vilo", guarded, null);

        assertNotNull(uid);
        assertEquals(uid.getUidValue(), "vilo");
    }
}
