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
package org.identityconnectors.common.security;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class GuardedByteArrayTests {
    
    @BeforeMethod
	public void setUp() {
        GuardedByteArray.setEncryptor(new SimpleEncryptor());
    }
    
    @AfterMethod
	public void tearDown() {
        GuardedByteArray.setEncryptor(null);
    }
    
    @Test
    public void testBasics() {
        GuardedByteArray bytes = new GuardedByteArray(new byte[] { 0x00, 0x01, 0x02 });
        AssertJUnit.assertTrue(Arrays.equals(new byte[] { 0x00, 0x01, 0x02 }, decryptToBytes(bytes)));
        bytes.appendByte((byte) 0x03);
        AssertJUnit.assertTrue(Arrays.equals(new byte[] { 0x00, 0x01, 0x02, 0x03 }, decryptToBytes(bytes)));
        AssertJUnit.assertFalse(bytes.verifyBase64SHA1Hash(SecurityUtil.computeBase64SHA1Hash(new byte[] { 0x00, 0x01, 0x02 })));
        AssertJUnit.assertTrue(bytes.verifyBase64SHA1Hash(SecurityUtil.computeBase64SHA1Hash(new byte[] { 0x00, 0x01, 0x02, 0x03 })));
    }
    
    @Test
    public void testEquals() {
        GuardedByteArray bytes1 = new GuardedByteArray();
        GuardedByteArray bytes2 = new GuardedByteArray();
        AssertJUnit.assertEquals(bytes1, bytes2);
        bytes2.appendByte((byte) 0x03);
        AssertJUnit.assertFalse(bytes1.equals(bytes2));
        bytes1.appendByte((byte) 0x03);
        AssertJUnit.assertEquals(bytes1, bytes2);
    }
    
    @Test
    public void testReadOnly() {
        GuardedByteArray bytes = new GuardedByteArray(new byte[] { 0x00, 0x01, 0x02 });
        AssertJUnit.assertEquals(false, bytes.isReadOnly());
        bytes.makeReadOnly();
        AssertJUnit.assertEquals(true, bytes.isReadOnly());
        AssertJUnit.assertTrue(Arrays.equals(new byte[] { 0x00, 0x01, 0x02 }, decryptToBytes(bytes)));
        try {
            bytes.appendByte((byte) 0x03);
            AssertJUnit.fail("expected exception");
        }
        catch (IllegalStateException e) {
            
        }
        bytes = bytes.copy();
        AssertJUnit.assertTrue(Arrays.equals(new byte[] { 0x00, 0x01, 0x02 }, decryptToBytes(bytes)));
        bytes.appendByte((byte) 0x03);
        AssertJUnit.assertTrue(Arrays.equals(new byte[] { 0x00, 0x01, 0x02, 0x03 }, decryptToBytes(bytes)));
    }
    
    @Test
    public void testDispose() {
        GuardedByteArray str = new GuardedByteArray(new byte[] { 0x00, 0x01, 0x02 });
        str.dispose();
        try {
            decryptToBytes(str);
            AssertJUnit.fail("expected exception");
        }
        catch (IllegalStateException e) {
            
        }
        try {
            str.isReadOnly();
            AssertJUnit.fail("expected exception");
        }
        catch (IllegalStateException e) {
            
        }
        try {
            str.appendByte((byte) 0x03);
            AssertJUnit.fail("expected exception");
        }
        catch (IllegalStateException e) {
            
        }
        try {
            str.copy();
            AssertJUnit.fail("expected exception");
        }
        catch (IllegalStateException e) {
            
        }
        try {
            str.verifyBase64SHA1Hash("foo");
            AssertJUnit.fail("expected exception");
        }
        catch (IllegalStateException e) {
            
        }
    }
    
    @Test
    public void testRange() {
        for ( int i = -128; i < 128; i++) {
            final byte expected = (byte) i;
            GuardedByteArray bytes = new GuardedByteArray(new byte[]{ (byte) i });
            bytes.access(new GuardedByteArray.Accessor() {
                public void access(byte[] clearBytes) {
                    byte v = clearBytes[0];
                    AssertJUnit.assertEquals(expected, v);
                }
    
            });
        }
    }

    /**
     * Highly insecure method! Do not do this in production
     * code. This is only for test purposes
     */
    private byte[] decryptToBytes(GuardedByteArray bytes) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        bytes.access(
                new GuardedByteArray.Accessor() {
                    public void access(byte[] bytes) {
                        out.write(bytes, 0, bytes.length);
                    }                    
                });
        return out.toByteArray();
    }
}
