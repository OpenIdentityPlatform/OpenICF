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

package org.forgerock.openicf.misc.scriptedcommon;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.connectors.ssh.SSHConfiguration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the {@code customize} DSL of {@link SSHConfiguration} when its customizer is retried. It
 * lives in this package to reach {@link ScriptedConfiguration#getGroovyScriptEngine()}: a subclass
 * would break the customizer's closures, which set private fields of {@link SSHConfiguration}.
 */
public class SSHConfigurationCustomizerTest {

    /** How often {@link #isFirstAttempt()} was called; reset by {@link #setUp()}. */
    private static final AtomicInteger attempts = new AtomicInteger();

    /** Whether the release closure of the customizer script ran; reset by {@link #setUp()}. */
    private static volatile boolean releaseClosureRan;

    /** Called from the customizer script to behave differently on its first attempt. */
    public static boolean isFirstAttempt() {
        return attempts.incrementAndGet() == 1;
    }

    /** Called from the release closure of the customizer script. */
    public static void releaseClosureRan() {
        releaseClosureRan = true;
    }

    private File scriptRoot;

    @BeforeMethod
    public void setUp() throws Exception {
        attempts.set(0);
        releaseClosureRan = false;
        scriptRoot = Files.createTempDirectory("ssh-configuration").toFile();
    }

    @AfterMethod
    public void tearDown() {
        new File(scriptRoot, "Customizer.groovy").delete();
        scriptRoot.delete();
    }

    @Test
    public void testRetriedCustomizerDropsTheReleaseClosureOfTheFailedAttempt() throws Exception {
        final String test = SSHConfigurationCustomizerTest.class.getName();
        Files.write(new File(scriptRoot, "Customizer.groovy").toPath(),
                ("if (" + test + ".isFirstAttempt()) {\n"
                        + "    customize {\n"
                        + "        release { " + test + ".releaseClosureRan() }\n"
                        + "    }\n"
                        + "    throw new IllegalStateException('customizer failed')\n"
                        + "}\n"
                        + "customize {\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
        final SSHConfiguration configuration = new SSHConfiguration();
        configuration.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        configuration.setCustomizerScriptFileName("Customizer.groovy");

        try {
            configuration.getGroovyScriptEngine();
            fail("The customizer failure must reach the caller");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "customizer failed");
        }
        assertNotNull(configuration.getGroovyScriptEngine());

        configuration.release();
        assertFalse(releaseClosureRan, "release closure of the failed attempt ran");
    }
}
