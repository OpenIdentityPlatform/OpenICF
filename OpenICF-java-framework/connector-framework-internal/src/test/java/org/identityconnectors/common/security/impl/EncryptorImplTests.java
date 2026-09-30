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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.identityconnectors.common.security.Encryptor;
import org.identityconnectors.common.security.EncryptorFactory;
import org.testng.annotations.Test;

public class EncryptorImplTests {

    private static final byte[] SECRET = "secret".getBytes(StandardCharsets.UTF_8);

    /**
     * The default encryptor is the wire format of the legacy connector server
     * protocol, shared with the .NET connector server: its output for a given
     * input must not change.
     */
    @Test
    public void defaultEncryptorKeepsLegacyWireFormat() {
        Encryptor encryptor = EncryptorFactory.getInstance().getDefaultEncryptor();
        assertEquals(encryptor.encrypt(SECRET), hex("66df076267e3a421575ce6fa2ec1d5c7"));
        assertEquals(encryptor.decrypt(hex("66df076267e3a421575ce6fa2ec1d5c7")), SECRET);
    }

    @Test
    public void randomEncryptorRoundTrips() {
        Encryptor encryptor = EncryptorFactory.getInstance().newRandomEncryptor();
        assertEquals(encryptor.decrypt(encryptor.encrypt(SECRET)), SECRET);
    }

    @Test
    public void randomEncryptorsDoNotShareKeys() {
        Encryptor one = EncryptorFactory.getInstance().newRandomEncryptor();
        Encryptor other = EncryptorFactory.getInstance().newRandomEncryptor();
        try {
            byte[] decrypted = other.decrypt(one.encrypt(SECRET));
            assertFalse(Arrays.equals(decrypted, SECRET), "decrypted with another key");
        } catch (RuntimeException expected) {
            // an authenticated cipher rejects the foreign key outright
        }
    }

    /**
     * In-memory secrets: the same value must not encrypt to the same bytes
     * twice, otherwise equal secrets can be spotted in a heap dump.
     */
    @Test
    public void randomEncryptorUsesFreshIvForEveryEncryption() {
        Encryptor encryptor = EncryptorFactory.getInstance().newRandomEncryptor();
        assertFalse(Arrays.equals(encryptor.encrypt(SECRET), encryptor.encrypt(SECRET)),
                "same ciphertext for the same input");
    }

    @Test
    public void randomEncryptorRejectsTamperedCiphertext() {
        Encryptor encryptor = EncryptorFactory.getInstance().newRandomEncryptor();
        // several blocks long, so that a flipped byte in the first block
        // leaves a CBC padding check intact and only an authenticated
        // cipher notices
        byte[] plain = "a plaintext that spans several cipher blocks".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = encryptor.encrypt(plain);
        tampered[tampered.length - plain.length] ^= 0x01;
        try {
            byte[] decrypted = encryptor.decrypt(tampered);
            fail("tampered ciphertext decrypted to " + Arrays.toString(decrypted));
        } catch (RuntimeException expected) {
            // authentication failure
        }
    }

    private static byte[] hex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
