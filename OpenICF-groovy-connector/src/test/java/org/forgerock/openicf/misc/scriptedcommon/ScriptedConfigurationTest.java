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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.groovy.reflection.ClassInfo;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import groovy.lang.Binding;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.Script;
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

    @Test
    public void testFailedCustomizerClassIsUnregistered() {
        customizer = () -> {
            throw new IllegalStateException("customizer failed");
        };
        final AtomicReference<Class> customizerClass = new AtomicReference<>();
        // Registers a metaclass on the customizer class, as the REST, CREST and SSH
        // configurations do; a registered metaclass keeps the class's loader alive.
        configuration = new ScriptedConfiguration() {
            @Override
            protected Script createCustomizerScript(Class clazz, Binding binding) {
                customizerClass.set(clazz);
                ExpandoMetaClass metaClass = new ExpandoMetaClass(clazz, false, true);
                metaClass.initialize();
                GroovySystem.getMetaClassRegistry().setMetaClass(clazz, metaClass);
                return super.createCustomizerScript(clazz, binding);
            }
        };
        configuration.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        configuration.setCustomizerScriptFileName("Customizer.groovy");

        try {
            configuration.getGroovyScriptEngine();
            fail("The customizer failure must reach the caller");
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("customizer failed");
        }

        assertThat(customizerClass.get()).isNotNull();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass()).isNull();
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
        final AtomicReference<Thread> secondThread = new AtomicReference<>();
        Future<GroovyScriptEngine> second = executor.submit(() -> {
            secondThread.set(Thread.currentThread());
            return configuration.getGroovyScriptEngine();
        });
        // The second caller either returns early or blocks on the configuration's monitor.
        while (!second.isDone() && (secondThread.get() == null
                || secondThread.get().getState() != Thread.State.BLOCKED)) {
            Thread.sleep(10);
        }
        assertThat(second.isDone()).as("second caller returned while the customizer ran").isFalse();

        finishCustomizing.countDown();
        assertThat(first.get()).isNotNull();
        assertThat(second.get()).isSameAs(first.get());
    }
}
