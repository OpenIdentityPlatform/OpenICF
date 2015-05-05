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

import java.io.Serializable;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;

public class SharedSecretPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * @serial
     */
    private final String name;
    private final GuardedString secret;

    private SharedSecretPrincipal(String name, GuardedString secret) {
        this.name = name;
        this.secret = secret;
    }

    public static SharedSecretPrincipal createBasic(String name, GuardedString secret) {
        return new SharedSecretPrincipal(Assertions.blankChecked(name, "name"), Assertions
                .nullChecked(secret, "secret"));
    }

    public static SharedSecretPrincipal createMutual(X509Certificate clientCertificate)
            throws CertificateEncodingException {
        return new SharedSecretPrincipal(SecurityUtil.computeHexSHA1Hash(Assertions.nullChecked(
                clientCertificate, "clientCertificate").getEncoded(), false), null);
    }

    public String getName() {
        return name;
    }

    public boolean isMutual() {
        return secret == null;
    }

    public boolean verify(String hash) {
        return null != secret && secret.verifyBase64SHA1Hash(hash);
    }

    public String toString() {
        return "SharedSecretPrincipal{"
                + (null != secret ? "name='" + name + '\'' + ", secret=" + secret : "fingerPrint='"
                        + name + '\'') + '}';
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SharedSecretPrincipal))
            return false;

        SharedSecretPrincipal that = (SharedSecretPrincipal) o;

        if (!name.equals(that.name))
            return false;
        if (secret != null ? !secret.equals(that.secret) : that.secret != null)
            return false;

        return true;
    }

    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (secret != null ? secret.hashCode() : 0);
        return result;
    }
}
