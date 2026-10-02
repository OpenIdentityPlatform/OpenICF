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

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import groovy.util.GroovyScriptEngine;

/**
 * Tests that {@link ScriptedConfiguration#getGroovyScriptEngine()} only hands out an engine whose
 * customizer script has run.
 */
public class ScriptedConfigurationTest {

    /** What the customizer script does; set by each test. */
    private static volatile Runnable customizer;

    /** Called from the customizer script written by {@link #setUp()}. */
    public static void customize() {
        customizer.run();
    }

    private File scriptRoot;

    private ScriptedConfiguration configuration;

    private ExecutorService executor;

    @BeforeMethod
    public void setUp() throws Exception {
        scriptRoot = Files.createTempDirectory("scripted-configuration").toFile();
        Files.write(new File(scriptRoot, "Customizer.groovy").toPath(),
                (ScriptedConfigurationTest.class.getName() + ".customize()\n")
                        .getBytes(StandardCharsets.UTF_8));
        configuration = new ScriptedConfiguration();
        configuration.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        configuration.setCustomizerScriptFileName("Customizer.groovy");
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        customizer = null;
        executor.shutdownNow();
        new File(scriptRoot, "Customizer.groovy").delete();
        scriptRoot.delete();
    }

    @Test
    public void testFailedCustomizerIsRetriedOnTheNextCall() {
        final AtomicInteger calls = new AtomicInteger();
        customizer = () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("customizer failed");
            }
        };

        try {
            configuration.getGroovyScriptEngine();
            fail("The customizer failure must reach the caller");
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("customizer failed");
        }

        assertThat(configuration.getGroovyScriptEngine()).isNotNull();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test(timeOut = 30000)
    public void testEngineIsHiddenFromOtherThreadsUntilCustomized() throws Exception {
        final CountDownLatch customizing = new CountDownLatch(1);
        final CountDownLatch finishCustomizing = new CountDownLatch(1);
        customizer = () -> {
            customizing.countDown();
            try {
                finishCustomizing.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Future<GroovyScriptEngine> first = executor.submit(configuration::getGroovyScriptEngine);
        customizing.await();
        Future<GroovyScriptEngine> second = executor.submit(configuration::getGroovyScriptEngine);
        try {
            GroovyScriptEngine early = second.get(500, TimeUnit.MILLISECONDS);
            fail("Got " + early + " while the customizer was still running");
        } catch (TimeoutException expected) {
            // the second caller waits for the customization to finish
        }

        finishCustomizing.countDown();
        assertThat(first.get()).isNotNull();
        assertThat(second.get()).isSameAs(first.get());
    }
}
