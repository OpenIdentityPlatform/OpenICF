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
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.common.security.impl;

import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.identityconnectors.common.security.Encryptor;


/**
 * The wire format of the legacy connector server protocol: AES/CBC with a
 * key and IV built into the framework, so that any peer can read what
 * another wrote. This is obfuscation rather than protection - the connection
 * must be protected by TLS - and it must stay byte-for-byte compatible with
 * the .NET connector server, which is why it is kept as is. Secrets held in
 * memory use {@link AesGcmEncryptor} instead.
 */
public class EncryptorImpl implements Encryptor {

    private static final String ALGORITHM = "AES";
    private static final String FULL_ALGORITHM = "AES/CBC/PKCS5Padding";

    private final static byte [] DEFAULT_KEY_BYTES = {
        (byte) 0x23,(byte) 0x65,(byte) 0x87,(byte) 0x22,
        (byte) 0x59,(byte) 0x78,(byte) 0x54,(byte) 0x43,
        (byte) 0x64,(byte) 0x05,(byte) 0x6A,(byte) 0xBD,
        (byte) 0x34,(byte) 0xA2,(byte) 0x34,(byte) 0x57,
    };

    private final static byte [] DEFAULT_IV_BYTES = {
        (byte) 0x51,(byte) 0x65,(byte) 0x22,(byte) 0x23,
        (byte) 0x64,(byte) 0x05,(byte) 0x6A,(byte) 0xBE,
        (byte) 0x51,(byte) 0x65,(byte) 0x22,(byte) 0x23,
        (byte) 0x64,(byte) 0x05,(byte) 0x6A,(byte) 0xBE,
    };

    private final Key key = new SecretKeySpec(DEFAULT_KEY_BYTES, ALGORITHM);
    private final IvParameterSpec iv = new IvParameterSpec(DEFAULT_IV_BYTES);

    public EncryptorImpl() {
    }

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
