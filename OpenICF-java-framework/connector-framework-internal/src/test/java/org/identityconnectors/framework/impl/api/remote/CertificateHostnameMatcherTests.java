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
package org.identityconnectors.framework.impl.api.remote;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.testng.annotations.Test;

/**
 * Test certificates:
 * <ul>
 * <li>{@code KeyStore.jks}: {@code CN=localhost}, no subjectAltName;</li>
 * <li>{@code KeyStore-san.jks}: {@code CN=localhost},
 * {@code SAN=dns:localhost,ip:127.0.0.1,ip:::1};</li>
 * <li>{@code wildcard-san.pem}: {@code CN=cn.example.org},
 * {@code SAN=dns:*.example.com,dns:example.net};</li>
 * <li>{@code multi-cn.pem}: {@code CN=right.example,OU=x,CN=wrong.example},
 * no subjectAltName;</li>
 * <li>{@code private-wildcard-san.pem}: {@code O=OpenICF test},
 * {@code SAN=dns:*.com,dns:w*.example.org,dns:300.1.1.1};</li>
 * <li>{@code no-names.pem}: {@code O=OpenICF test}, no CN, no subjectAltName.</li>
 * </ul>
 * The PEM files are self-signed, generated with e.g.
 * {@code openssl req -x509 -newkey rsa:2048 -nodes -keyout /dev/null -days 3650
 * -subj "/O=OpenICF test" -addext "subjectAltName=DNS:*.com,DNS:w*.example.org"}.
 */
public class CertificateHostnameMatcherTests {

    @Test
    public void matchesCommonNameWhenCertificateHasNoSubjectAltName() throws Exception {
        X509Certificate cert = keyStoreCertificate("KeyStore.jks");
        assertTrue(CertificateHostnameMatcher.matches("localhost", cert));
        assertTrue(CertificateHostnameMatcher.matches("LOCALHOST", cert));
        assertFalse(CertificateHostnameMatcher.matches("example.com", cert));
    }

    @Test
    public void neverMatchesIpAddressAgainstCommonName() throws Exception {
        X509Certificate cert = keyStoreCertificate("KeyStore.jks");
        assertFalse(CertificateHostnameMatcher.matches("127.0.0.1", cert));
    }

    @Test
    public void matchesDnsSubjectAltName() throws Exception {
        X509Certificate cert = keyStoreCertificate("KeyStore-san.jks");
        assertTrue(CertificateHostnameMatcher.matches("localhost", cert));
        assertFalse(CertificateHostnameMatcher.matches("localhost.localdomain", cert));
    }

    @Test
    public void matchesIpSubjectAltName() throws Exception {
        X509Certificate cert = keyStoreCertificate("KeyStore-san.jks");
        assertTrue(CertificateHostnameMatcher.matches("127.0.0.1", cert));
        assertTrue(CertificateHostnameMatcher.matches("::1", cert));
        assertTrue(CertificateHostnameMatcher.matches("0:0:0:0:0:0:0:1", cert));
        assertFalse(CertificateHostnameMatcher.matches("127.0.0.2", cert));
    }

    @Test
    public void ignoresCommonNameWhenSubjectAltNameHasDnsNames() throws Exception {
        X509Certificate cert = pemCertificate("wildcard-san.pem");
        assertFalse(CertificateHostnameMatcher.matches("cn.example.org", cert));
        assertTrue(CertificateHostnameMatcher.matches("example.net", cert));
        assertTrue(CertificateHostnameMatcher.matches("EXAMPLE.NET", cert));
    }

    @Test
    public void matchesWildcardForExactlyOneLeftmostLabel() throws Exception {
        X509Certificate cert = pemCertificate("wildcard-san.pem");
        assertTrue(CertificateHostnameMatcher.matches("www.example.com", cert));
        assertTrue(CertificateHostnameMatcher.matches("WWW.Example.COM", cert));
        assertFalse(CertificateHostnameMatcher.matches("example.com", cert));
        assertFalse(CertificateHostnameMatcher.matches("a.b.example.com", cert));
        assertFalse(CertificateHostnameMatcher.matches("wwwexample.com", cert));
    }

    @Test
    public void acceptsWildcardFormsJsseAcceptsFromPrivateCa() throws Exception {
        X509Certificate cert = pemCertificate("private-wildcard-san.pem");
        assertTrue(CertificateHostnameMatcher.matches("example.com", cert));
        assertFalse(CertificateHostnameMatcher.matches("com", cert));
        assertFalse(CertificateHostnameMatcher.matches("www.example.com", cert));
        assertTrue(CertificateHostnameMatcher.matches("www.example.org", cert));
        assertTrue(CertificateHostnameMatcher.matches("w.example.org", cert));
        assertFalse(CertificateHostnameMatcher.matches("x.example.org", cert));
    }

    @Test
    public void usesMostSpecificCommonName() throws Exception {
        X509Certificate cert = pemCertificate("multi-cn.pem");
        assertTrue(CertificateHostnameMatcher.matches("right.example", cert));
        assertFalse(CertificateHostnameMatcher.matches("wrong.example", cert));
    }

    @Test
    public void matchesNothingWithoutCommonNameOrSubjectAltName() throws Exception {
        X509Certificate cert = pemCertificate("no-names.pem");
        assertFalse(CertificateHostnameMatcher.matches("localhost", cert));
        assertFalse(CertificateHostnameMatcher.matches("127.0.0.1", cert));
    }

    @Test
    public void treatsOutOfRangeOctetsAsHostName() throws Exception {
        // 300.1.1.1 is not an IPv4 literal: it is matched against dNSName
        // entries instead of being resolved and compared as an address
        X509Certificate cert = pemCertificate("private-wildcard-san.pem");
        assertTrue(CertificateHostnameMatcher.matches("300.1.1.1", cert));
    }

    @Test
    public void describeListsSubjectAndSubjectAltNames() throws Exception {
        String description = CertificateHostnameMatcher.describe(keyStoreCertificate("KeyStore-san.jks"));
        assertTrue(description.contains("CN=localhost"), description);
        assertTrue(description.contains("dns:localhost"), description);
        assertTrue(description.contains("ip:127.0.0.1"), description);

        description = CertificateHostnameMatcher.describe(keyStoreCertificate("KeyStore.jks"));
        assertTrue(description.contains("CN=localhost"), description);
        assertFalse(description.contains("dns:"), description);
    }

    private static X509Certificate keyStoreCertificate(String name) throws Exception {
        try (InputStream in = CertificateHostnameMatcherTests.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, "missing test resource " + name);
            KeyStore store = KeyStore.getInstance("JKS");
            store.load(in, "changeit".toCharArray());
            return (X509Certificate) store.getCertificate(store.aliases().nextElement());
        }
    }

    private static X509Certificate pemCertificate(String name) throws Exception {
        try (InputStream in = CertificateHostnameMatcherTests.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, "missing test resource " + name);
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }
}
