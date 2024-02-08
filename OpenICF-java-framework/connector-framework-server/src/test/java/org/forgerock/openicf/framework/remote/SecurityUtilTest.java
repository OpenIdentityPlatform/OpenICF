/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.remote;

import org.forgerock.openicf.framework.remote.security.ECIESEncryptor;
import org.identityconnectors.common.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.SecureRandom;

public class SecurityUtilTest {

    @Test
    public void testECIESEncryptor() throws Exception {
        KeyPair client = SecurityUtil.generateKeyPair();
        KeyPair server = SecurityUtil.generateKeyPair();

        ECIESEncryptor clientEncryptor = new ECIESEncryptor(client, server.getPublic());
        ECIESEncryptor serverEncryptor = new ECIESEncryptor(server, client.getPublic());

        byte[] expected = "password".getBytes();
        byte[] secure = clientEncryptor.encrypt(expected);
        Assert.assertEquals(serverEncryptor.decrypt(secure), expected);
    }

    @Test
    public void testCheckMutualVerification() throws Exception {
        SecureRandom random = new SecureRandom();
        Pair<String, byte[]> v = SecurityUtil.generateVerifier(ConnectionPrincipal.DEFAULT_NAME, "Passw0rd",
                random, SecurityUtil.SRP_1024);

        Assert.assertTrue(SecurityUtil.checkMutualVerification(ConnectionPrincipal.DEFAULT_NAME, "Passw0rd",
                v, random, SecurityUtil.SRP_1024));
    }

}