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
package org.identityconnectors.ldap;

import static org.testng.Assert.assertEquals;

import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;

import org.testng.annotations.Test;

/**
 * The helpers of {@link LdapConnectorTestBase} that need no server. Deliberately not a subclass:
 * that would start the embedded OpenDJ around every test.
 */
public class LdapConnectorTestBaseTests {

    @Test
    public void listeningNamesOnlyPortsThatAcceptConnections() throws Exception {
        try (ServerSocket open = new ServerSocket(0)) {
            int closed;
            try (ServerSocket s = new ServerSocket(0)) {
                closed = s.getLocalPort();
            }
            Map<String, Integer> ports = new LinkedHashMap<>();
            ports.put("open", open.getLocalPort());
            ports.put("closed", closed);

            assertEquals(LdapConnectorTestBase.listening(ports), "open " + open.getLocalPort());
        }
    }
}
