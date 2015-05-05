/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.identityconnectors.common.logging.Log;

/**
 * @since 1.5
 */
public class SecurityUtil {

    private static final Log logger = Log.getLog(SecurityUtil.class);
    private static final String BC_PROVIDER = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    public static final String PROVIDER_NAME = "BC";
    private static final String providerName;

    static {
        if (checkProvider()) {
            providerName = PROVIDER_NAME;
        } else {
            providerName = null;
        }
    }

    private static boolean checkProvider() {

        if (Security.getProvider(PROVIDER_NAME) == null) {
            try {
                Class<?> providerClass = loadClass(BC_PROVIDER, null);
                Security.addProvider((Provider) providerClass.newInstance());
                // logger.ok("Enabling Bouncy Castle Provider");
                return true;
            } catch (ClassNotFoundException ex) {
                //
            } catch (InstantiationException e) {
                //
            } catch (IllegalAccessException e) {
                //
            }
        } else {
            logger.ok("Found Bouncy Castle Provider");
        }
        return false;
    }

    public static PublicKey createPublicKey(byte[] publicKeyBytes) {
        try {

            KeyFactory kf = KeyFactory.getInstance("ECDSA", PROVIDER_NAME);
            return kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            // logger.warn("Not Found Bouncy Castle Provider");
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KeyPair generateKeyPair() {
        checkProvider();
        try {
            org.bouncycastle.jce.spec.ECNamedCurveParameterSpec ecSpec =
                    org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("B-571"); // "secp256k1"
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDH", "BC");
            keyGen.initialize(ecSpec, new SecureRandom());
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Load a class with a given name.
     * <p/>
     * It will try to load the class in the following order:
     * <ul>
     * <li>From Thread.currentThread().getContextClassLoader()
     * <li>Using the basic Class.forName()
     * <li>From SecurityUtil.class.getClassLoader()
     * <li>From the callingClass.getClassLoader()
     * </ul>
     *
     * @param className
     *            The name of the class to load
     * @param callingClass
     *            The Class object of the calling object
     * @throws ClassNotFoundException
     *             If the class cannot be found anywhere.
     */
    public static Class<?> loadClass(String className, Class<?> callingClass)
            throws ClassNotFoundException {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            if (cl != null) {
                return cl.loadClass(className);
            }
        } catch (ClassNotFoundException e) {
            // ignore
        }
        return loadClass2(className, callingClass);
    }

    private static Class<?> loadClass2(String className, Class<?> callingClass)
            throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            try {
                if (SecurityUtil.class.getClassLoader() != null) {
                    return SecurityUtil.class.getClassLoader().loadClass(className);
                }
            } catch (ClassNotFoundException exc) {
                if (callingClass != null && callingClass.getClassLoader() != null) {
                    return callingClass.getClassLoader().loadClass(className);
                }
            }
            throw ex;
        }
    }

}
