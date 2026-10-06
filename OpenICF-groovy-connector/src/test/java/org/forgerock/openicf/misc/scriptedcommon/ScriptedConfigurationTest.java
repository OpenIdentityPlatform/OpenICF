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
import org.codehaus.groovy.runtime.InvokerHelper;
import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConfiguration;
import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConfiguration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import groovy.lang.Binding;
import groovy.lang.Closure;
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

    /** How often {@link #isFirstAttempt()} was called; reset by {@link #setUp()}. */
    private static final AtomicInteger attempts = new AtomicInteger();

    /** Whether the release closure of a customizer script ran; reset by {@link #setUp()}. */
    private static volatile boolean releaseClosureRan;

    /** Called from a customizer script to behave differently on its first attempt. */
    public static boolean isFirstAttempt() {
        return attempts.incrementAndGet() == 1;
    }

    /** Called from the release closure of a customizer script. */
    public static void releaseClosureRan() {
        releaseClosureRan = true;
    }

    private File scriptRoot;

    private ScriptedConfiguration configuration;

    private ExecutorService executor;

    @BeforeMethod
    public void setUp() throws Exception {
        attempts.set(0);
        releaseClosureRan = false;
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
        configuration = registeringMetaClassOnCustomizer(customizerClass);

        try {
            configuration.getGroovyScriptEngine();
            fail("The customizer failure must reach the caller");
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("customizer failed");
        }

        assertThat(customizerClass.get()).isNotNull();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass()).isNull();
    }

    @Test
    public void testReleaseUnregistersThePublishedCustomizerClass() {
        customizer = () -> {
        };
        final AtomicReference<Class> customizerClass = new AtomicReference<>();
        configuration = registeringMetaClassOnCustomizer(customizerClass);

        assertThat(configuration.getGroovyScriptEngine()).isNotNull();
        assertThat(customizerClass.get()).isNotNull();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass())
                .as("metaclass of the published customizer before release()").isNotNull();

        configuration.release();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass()).isNull();

        // A released configuration must not touch a class it no longer publishes.
        ExpandoMetaClass again = new ExpandoMetaClass(customizerClass.get(), false, true);
        again.initialize();
        GroovySystem.getMetaClassRegistry().setMetaClass(customizerClass.get(), again);
        try {
            configuration.release();
            assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass())
                    .as("metaclass registered after the first release()").isNotNull();
        } finally {
            InvokerHelper.removeClass(customizerClass.get());
        }
    }

    @Test
    public void testThrowingReleaseClosureStillUnregistersTheCustomizerClass() {
        customizer = () -> {
        };
        final AtomicReference<Class> customizerClass = new AtomicReference<>();
        configuration = registeringMetaClassOnCustomizer(customizerClass);
        assertThat(configuration.getGroovyScriptEngine()).isNotNull();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass())
                .as("metaclass of the published customizer before release()").isNotNull();
        configuration.setReleaseClosure(new Closure<Void>(this) {
            public Void doCall() {
                throw new IllegalStateException("release failed");
            }
        });

        try {
            configuration.release();
            fail("The release closure failure must reach the caller");
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("release failed");
        }
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass()).isNull();
    }

    @Test
    public void testCRESTReleaseUnregistersTheCustomizeMetaClass() throws Exception {
        final AtomicReference<Class> customizerClass = new AtomicReference<>();
        assertReleaseUnregistersTheCustomizeMetaClass(new ScriptedCRESTConfiguration() {
            @Override
            protected Script createCustomizerScript(Class clazz, Binding binding) {
                customizerClass.set(clazz);
                return super.createCustomizerScript(clazz, binding);
            }
        }, customizerClass);
    }

    @Test
    public void testRESTReleaseUnregistersTheCustomizeMetaClass() throws Exception {
        final AtomicReference<Class> customizerClass = new AtomicReference<>();
        assertReleaseUnregistersTheCustomizeMetaClass(new ScriptedRESTConfiguration() {
            @Override
            protected Script createCustomizerScript(Class clazz, Binding binding) {
                customizerClass.set(clazz);
                return super.createCustomizerScript(clazz, binding);
            }
        }, customizerClass);
    }

    /**
     * Runs a real {@code customize {}} DSL, which registers a metaclass on the customizer class,
     * and checks that the subclass's own {@code release()} unregisters it.
     */
    private void assertReleaseUnregistersTheCustomizeMetaClass(ScriptedConfiguration subject,
            AtomicReference<Class> customizerClass) throws Exception {
        Files.write(new File(scriptRoot, "Customizer.groovy").toPath(),
                "customize {\n}\n".getBytes(StandardCharsets.UTF_8));
        configuration = subject;
        configuration.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        configuration.setCustomizerScriptFileName("Customizer.groovy");

        assertThat(configuration.getGroovyScriptEngine()).isNotNull();
        assertThat(customizerClass.get()).isNotNull();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass())
                .as("metaclass registered by customize {} before release()").isNotNull();

        configuration.release();
        assertThat(ClassInfo.getClassInfo(customizerClass.get()).getStrongMetaClass()).isNull();
    }

    /**
     * A configuration that registers a metaclass on its customizer class, as the REST, CREST and
     * SSH configurations do; a registered metaclass keeps the class's loader alive.
     */
    private ScriptedConfiguration registeringMetaClassOnCustomizer(
            final AtomicReference<Class> customizerClass) {
        ScriptedConfiguration result = new ScriptedConfiguration() {
            @Override
            protected Script createCustomizerScript(Class clazz, Binding binding) {
                customizerClass.set(clazz);
                ExpandoMetaClass metaClass = new ExpandoMetaClass(clazz, false, true);
                metaClass.initialize();
                GroovySystem.getMetaClassRegistry().setMetaClass(clazz, metaClass);
                return super.createCustomizerScript(clazz, binding);
            }
        };
        result.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        result.setCustomizerScriptFileName("Customizer.groovy");
        return result;
    }

    @Test
    public void testRetriedCRESTCustomizerDropsTheReleaseClosureOfTheFailedAttempt() throws Exception {
        final String test = ScriptedConfigurationTest.class.getName();
        Files.write(new File(scriptRoot, "Customizer.groovy").toPath(),
                ("if (" + test + ".isFirstAttempt()) {\n"
                        + "    customize {\n"
                        + "        release { " + test + ".releaseClosureRan() }\n"
                        + "    }\n"
                        + "    throw new IllegalStateException('customizer failed')\n"
                        + "}\n"
                        + "customize {\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
        configuration = new ScriptedCRESTConfiguration();
        configuration.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        configuration.setCustomizerScriptFileName("Customizer.groovy");

        try {
            configuration.getGroovyScriptEngine();
            fail("The customizer failure must reach the caller");
        } catch (IllegalStateException e) {
            assertThat(e).hasMessage("customizer failed");
        }
        assertThat(configuration.getGroovyScriptEngine()).isNotNull();

        configuration.release();
        assertThat(releaseClosureRan).as("release closure of the failed attempt ran").isFalse();
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
