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
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The helpers of {@link LdapConnectorTestBase} that need no server. Deliberately not a subclass:
 * that would start the embedded OpenDJ around every test.
 */
public class LdapConnectorTestBaseTests {

    @Test
    public void listeningNamesOnlyPortsThatAcceptConnections() throws Exception {
        try (ServerSocket first = new ServerSocket(0); ServerSocket second = new ServerSocket(0)) {
            int closed;
            try (ServerSocket s = new ServerSocket(0)) {
                closed = s.getLocalPort();
            }
            Map<String, Integer> ports = new LinkedHashMap<>();
            ports.put("first", first.getLocalPort());
            ports.put("closed", closed);
            ports.put("second", second.getLocalPort());

            assertEquals(LdapConnectorTestBase.listening(ports),
                    "first " + first.getLocalPort() + ", second " + second.getLocalPort());
        }
    }

    @Test
    public void stillUpNamesWhatTheWaitLoopSawLast() {
        String stillUp = LdapConnectorTestBase.stillUp(false, "LDAPS 36323");

        assertTrue(stillUp.startsWith(
                "EmbeddedUtils.isRunning() is false, ports still accepting connections: [LDAPS 36323]; logs in "),
                stillUp);
        assertTrue(stillUp.endsWith(File.separator + new File(new File("instance", "opendj"), "logs").getPath()),
                stillUp);
    }

    /** A port left out of the map would let the next start find it still bound. */
    @Test
    public void theStopWaitWatchesTheLdapAdminAndReplicationPorts() {
        Set<String> watched = LdapConnectorTestBase.serverPorts().keySet();

        assertTrue(watched.containsAll(Arrays.asList("LDAP", "LDAPS", "admin", "replication")),
                watched.toString());
    }

    /**
     * OpenDJ's errors log only gets the server's messages through OpenDJ's own SLF4J binding;
     * without it SLF4J falls back to its no-operation logger. The build adds the binding to the
     * classpath from a file it unpacks, and neither a missing file nor an unpack that matched
     * nothing fails the build.
     */
    @Test
    public void slf4jLogsThroughOpenDJ() {
        skipUnlessRunByTheBuild();
        assertEquals(LoggerFactory.getILoggerFactory().getClass().getName(),
                "org.opends.server.loggers.slf4j.OpenDJLoggerFactory",
                "Without OpenDJ's SLF4J binding its logs/errors stays empty: is "
                        + "target/opendj-slf4j-adapter/opendj/lib/opendj-slf4j-adapter.jar there?");
    }

    /** Anywhere else, the CI failure artifact would not carry the server's logs. */
    @Test
    public void theBuildKeepsTheInstanceUnderTarget() {
        skipUnlessRunByTheBuild();
        String parent = System.getProperty("opendj.workDirParent");
        assertTrue(parent != null, "opendj.workDirParent is not set");
        assertEquals(new File(parent), new File(System.getProperty("basedir"), "target"));
    }

    /**
     * Keyed on a property Surefire sets by itself rather than on one from the pom, so that losing
     * the pom's settings fails these pins instead of skipping them.
     */
    private static void skipUnlessRunByTheBuild() {
        if (System.getProperty("surefire.test.class.path") == null) {
            throw new SkipException("Not run by the build, which sets up the classpath and properties pinned here");
        }
    }

    /** The work directory is deleted recursively, so it must never be the directory given. */
    @Test
    public void workDirIsADedicatedChildOfTheGivenDirectory() {
        assertEquals(LdapConnectorTestBase.workDir("/some/dir"), new File("/some/dir", "openicf-ldap-opendj"));
    }

    @Test
    public void workDirIgnoresWhitespaceAroundTheGivenDirectory() {
        assertEquals(LdapConnectorTestBase.workDir(" /some/dir "), new File("/some/dir", "openicf-ldap-opendj"));
    }

    @Test
    public void workDirFallsBackToTheTemporaryDirectory() {
        File expected = new File(System.getProperty("java.io.tmpdir"), "openicf-ldap-opendj");
        assertEquals(LdapConnectorTestBase.workDir(null), expected);
        assertEquals(LdapConnectorTestBase.workDir(" "), expected);
    }
}
