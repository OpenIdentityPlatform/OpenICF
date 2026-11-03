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

import org.testng.annotations.Test;

/** The embedded OpenDJ that {@link LdapConnectorTestBase} sets up, rather than the connector. */
public class LdapConnectorTestBaseServerTests extends LdapConnectorTestBase {

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    /**
     * OpenDJ 5.2.0's JMX connection handler exports its RMI connector on an anonymous port at every
     * start, and the JDK never closes an anonymous RMI port. That port is random, so it can be one
     * the server is about to bind for LDAP: the bind fails, and the RMI socket accepts what was
     * meant for the server (#219). The JDK serves each anonymous RMI port from a thread named
     * "RMI TCP Accept-0", and nothing else in these tests exports anything over RMI.
     */
    @Test
    public void aRestartLeavesNoRmiPortListening() throws Exception {
        stopServer();
        // Setting the instance up and before() have started the server in this JVM already, so
        // a count taken now as the baseline would not see a handler switched off too late.
        assertEquals(anonymousRmiListeners(), 0L);

        startServer();
        stopServer();

        assertEquals(anonymousRmiListeners(), 0L);
    }

    private static long anonymousRmiListeners() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals("RMI TCP Accept-0"))
                .count();
    }
}
