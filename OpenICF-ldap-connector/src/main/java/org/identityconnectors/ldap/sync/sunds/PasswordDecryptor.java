package org.identityconnectors.ldap.sync.sunds;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public class PasswordDecryptor {
    
    private static final String ENCRYPTION_ALGORITHM = "DESede/CBC/NoPadding";

    // This version magic is used to prefix the password before encryption.
    // The magic here must match the magic used in wpsync/connector/native/plugin/Util.cpp
    private static final int KEY_VERSION_MAGIC = 0x132d1403;
    
    // Decrypted password format: (4 bytes) magic, (4 bytes) length, password, (4 bytes) magic, padding.
    private static final int LENGTH_INDEX = 4;
    
    private final Cipher cipher;
    private final int blockSize;

    public PasswordDecryptor(byte[] desedeKey, byte[] iv) {
        Assertions.nullCheck(desedeKey, "desedeKey");
        Assertions.nullCheck(iv, "iv");

        IvParameterSpec ivspec = new IvParameterSpec(iv);
        // triple-DES key is used
        SecretKeySpec keyspec = new SecretKeySpec(desedeKey, "DESede");
        
        try {
            // The cipher algorithm is triple-DES (DESede).
            // Mode: cipher blocking chaining.
            // Padding: no padding.
            cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keyspec, ivspec);
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectorException(e);
        } catch (NoSuchPaddingException e) {
            throw new ConnectorException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new ConnectorException(e);
        } catch (InvalidKeyException e) {
            throw new ConnectorException(e);
        }
        blockSize = cipher.getBlockSize();
    }

    /**
     * Decrypts the password value using the configured symmetric key.
     * 
     * @param encryptedPassword the encrypted password to decrypt.
     * @return The clear-text password.
     * @throws ConnectorException if the password value could not be decrypted.
     */
    public String decryptPassword(byte[] encryptedPassword) {
        byte decryptInput[];
        byte decryptedBytes[];
        
        // Pad input if necessary.
        if (encryptedPassword.length % blockSize != 0) {
            decryptInput = new byte[((encryptedPassword.length / blockSize) + 1) * blockSize];
            System.arraycopy(encryptedPassword, 0, decryptInput, 0, encryptedPassword.length);
            Arrays.fill(decryptInput, encryptedPassword.length, decryptInput.length, (byte) 0);
        } else {
            decryptInput = encryptedPassword;
        }

        try {
            decryptedBytes = cipher.doFinal(decryptInput);
        } catch (IllegalStateException e) {
            throw new ConnectorException(e);
        } catch (IllegalBlockSizeException e) {
            throw new ConnectorException(e);
        } catch (BadPaddingException e) {
            throw new ConnectorException(e);
        }

        try {
            return getDecryptedPassword(decryptedBytes);
        } catch (UnsupportedEncodingException e) {
            throw new ConnectorException(e);
        }
    }

    /**
     * @param password byte array including the decrypted password value.
     * @return the decrypted password extracted from the raw, unencrypted byte array.
     * @throws ConnectorException
     *             if the magic cannot be extracted from the byte array for some
     *             reason, or the magic does not match the expected value.
     * @throws UnsupportedEncodingException
     *             if conversion of the password value to String fails.
     */
    private final String getDecryptedPassword(byte[] password) throws ConnectorException, UnsupportedEncodingException {
        if (password.length < LENGTH_INDEX + 4) { // Length is 4 bytes.
            throw new ConnectorException("Invalid decrypted password value: too short");
        }
        int len = getIntValueFromByteArray(password, LENGTH_INDEX);
        if (len < 0) {
            throw new ConnectorException("Weird decrypted password value: negative length");
        }

        // Check to see if the computed password length is in the valid range
        // 12: = 2 * magic length + password length
        // the raw password field is padded with 0 ... blockSize - 1 bytes.
        if (len <= password.length - 12 - blockSize || len > password.length - 12) {
            throw new ConnectorException("Invalid password length");
        }

        checkKeyVersionMagic(password, 8 + len);
        return new String(password, 8, len, "UTF8");
    }

    private final void checkKeyVersionMagic(byte[] password, int postMagicIndex) throws ConnectorException {
        if (postMagicIndex < LENGTH_INDEX + 4 || postMagicIndex > password.length - 4) {
            throw new ConnectorException("Invalid start index for post password magic");
        }
        int premagic = getIntValueFromByteArray(password, 0);
        int postmagic = getIntValueFromByteArray(password, postMagicIndex);

        if (premagic != KEY_VERSION_MAGIC || postmagic != KEY_VERSION_MAGIC) {
            throw new ConnectorException("Key magic mismatch");
        }
    }

    private int getIntValueFromByteArray(byte[] bytes, int index) {
        return (getUnsignedByteValueAsInt(bytes[index]) << 24)
                + (getUnsignedByteValueAsInt(bytes[index + 1]) << 16)
                + (getUnsignedByteValueAsInt(bytes[index + 2]) << 8) 
                + getUnsignedByteValueAsInt(bytes[index + 3]);
    }

    private int getUnsignedByteValueAsInt(byte b) {
        if (b < 0) {
            return 256 + b;
        } else {
            return b;
        }
    }
}
