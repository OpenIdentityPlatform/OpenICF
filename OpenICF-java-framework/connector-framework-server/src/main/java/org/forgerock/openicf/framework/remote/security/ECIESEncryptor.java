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
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2016 ForgeRock AS.
 */

package org.forgerock.openicf.framework.remote.security;

import java.security.Key;
import java.security.KeyPair;
import java.security.PublicKey;

import org.forgerock.openicf.framework.remote.SecurityUtil;
import org.identityconnectors.common.security.Encryptor;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.identityconnectors.common.security.impl.EncryptorImpl.ALGORITHM;
import static org.identityconnectors.common.security.impl.EncryptorImpl.FULL_ALGORITHM;

public class ECIESEncryptor implements Encryptor {

    private final Key key;
    private final IvParameterSpec iv;

    public ECIESEncryptor(KeyPair privateKey, PublicKey publicKey) {
        byte[] bytes = SecurityUtil.doECDH(privateKey, publicKey);
        byte[] secret = new byte[16];
        byte[] vector = new byte[16];
        System.arraycopy(bytes,0, secret, 0, 16);
        System.arraycopy(bytes,16, vector, 0, 16);

        key = new SecretKeySpec(secret, ALGORITHM);
        iv = new IvParameterSpec(vector);
    }

    @Override
    public byte[] decrypt(byte[] bytes) {
        try {
            Cipher cipher = Cipher.getInstance(FULL_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            return cipher.doFinal(bytes);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] encrypt(byte[] bytes) {
        try {
            Cipher cipher = Cipher.getInstance(FULL_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            return cipher.doFinal(bytes);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
