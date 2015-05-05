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

package org.forgerock.openicf.framework.remote.security;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.engines.IESEngine;
import org.bouncycastle.crypto.generators.KDF2BytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.IESParameters;
import org.bouncycastle.jce.provider.DHUtil;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.asymmetric.ec.ECUtil;
import org.identityconnectors.common.security.Encryptor;

public class ECIESEncryptor implements Encryptor {

    protected final CipherParameters privateKeyParameter;
    protected final CipherParameters publicKeyParameter;
    protected final SecureRandom random = new SecureRandom();

    public ECIESEncryptor(PrivateKey privateKey, PublicKey publicKey) throws InvalidKeyException {

        if (publicKey instanceof ECPublicKey) {
            publicKeyParameter = ECUtil.generatePublicKeyParameter(publicKey);
            privateKeyParameter = ECUtil.generatePrivateKeyParameter(privateKey);
        } else {
            publicKeyParameter = DHUtil.generatePublicKeyParameter(publicKey);
            privateKeyParameter = DHUtil.generatePrivateKeyParameter(privateKey);
        }

    }

    protected IESEngine initialiseEngine(boolean encrypt, CipherParameters privateKey,
            CipherParameters publicKey, IESParameters parameters) {
        IESEngine engine =
                new IESEngine(new ECDHBasicAgreement(), new KDF2BytesGenerator(new SHA1Digest()),
                        new HMac(new SHA1Digest()));
        engine.init(encrypt, privateKey, publicKey, parameters);
        return engine;
    }

    protected byte[] doFinal(byte[] bytes, IESParameters params, boolean encrypt) {
        try {
            final IESEngine engine =
                    initialiseEngine(encrypt, privateKeyParameter, publicKeyParameter,
                            new IESParameters(params.getDerivationV(), params.getEncodingV(),
                                    params.getMacKeySize()));
            return engine.processBlock(bytes, 0, bytes.length);
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected IESParameters generateIESParameterSpec() {
        byte[] d = new byte[16];
        byte[] e = new byte[16];
        random.nextBytes(d);
        random.nextBytes(e);
        return new IESParameters(d, e, 128);
    }

    @Override
    public byte[] decrypt(byte[] bytes) {

        byte[] d = new byte[16];
        byte[] e = new byte[16];

        System.arraycopy(bytes, 0, d, 0, 16);
        System.arraycopy(bytes, 16, e, 0, 16);

        IESParameters param = new IESParameters(d, e, 128);

        return doFinal(bytes, param, false);
    }

    @Override
    public byte[] encrypt(byte[] bytes) {
        IESParameters parameters = generateIESParameterSpec();
        byte[] encrypted = doFinal(bytes, parameters, true);
        byte[] result = new byte[32 + encrypted.length];

        System.arraycopy(parameters.getDerivationV(), 0, result, 0, 16);
        System.arraycopy(parameters.getEncodingV(), 0, result, 16, 16);
        System.arraycopy(encrypted, 0, result, 32, encrypted.length);

        return result;
    }
}
