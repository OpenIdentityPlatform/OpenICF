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
package org.identityconnectors.ldap;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

/**
 * A trust provider which blindly trusts any certificate. This saves
 * us from having to generate the certificate, import it into a trust file,
 * specify the file, etc.
 *
 * Inspired by <a href="http://www.howardism.org/Technical/Java/SelfSignedCerts.html">
 * http://www.howardism.org/Technical/Java/SelfSignedCerts.html</a>.
 */
public class BlindTrustProvider extends Provider {

    private static final long serialVersionUID = 1L;
    private static final String ID = "BlindTrustProvider";
    private static final String ALGORITHM = "Blind";

    public static final void register() {
        if (Security.getProvider(ID) == null) {
            Security.insertProviderAt(new BlindTrustProvider(), 1);
            Security.setProperty("ssl.TrustManagerFactory.algorithm", ALGORITHM);
        }
    }

    public BlindTrustProvider() {
        super(ID, 1.0, ID);
        put("TrustManagerFactory." + ALGORITHM, BlindTrustManagerFactory.class.getName());
    }

    public static final class BlindTrustManagerFactory extends TrustManagerFactorySpi {

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[] { new BlindTrustManager() };
        }

        @Override
        protected void engineInit(KeyStore ks) throws KeyStoreException {
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        }
    }

    public static final class BlindTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
