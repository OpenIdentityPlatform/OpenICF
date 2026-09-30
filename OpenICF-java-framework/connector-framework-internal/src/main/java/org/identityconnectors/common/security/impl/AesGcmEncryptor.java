/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.identityconnectors.common.security.impl;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

import org.identityconnectors.common.security.Encryptor;

/**
 * AES-256/GCM with a key generated for this instance and a fresh random IV
 * for every encryption; the IV is written in front of the ciphertext. Used
 * for secrets held in memory ({@code GuardedString}, {@code GuardedByteArray}),
 * which never leave the process, so there is no format to stay compatible
 * with.
 */
public final class AesGcmEncryptor implements Encryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Key key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
            generator.init(KEY_BITS);
            key = generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] encrypt(byte[] bytes) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(bytes);
            byte[] result = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, result, IV_BYTES, encrypted.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] bytes) {
        if (bytes.length < IV_BYTES) {
            throw new IllegalArgumentException("Ciphertext is shorter than its IV");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
