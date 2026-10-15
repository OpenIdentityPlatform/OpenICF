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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.assertions.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.reflection.ClassInfo;
import org.forgerock.openicf.connectors.groovy.ScriptedPoolableConnector;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import groovy.lang.Binding;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;

/**
 * Tests how {@link ScriptedConfiguration} compiles, caches and releases the scripts of
 * runScriptOnConnector.
 */
public class ScriptOnConnectorCacheTest {

    /** What the customizer script does; set by the test that writes one. */
    private static volatile Runnable customizer;

    /** Called from the customizer script written by {@link #writeCustomizer()}. */
    public static void customize() {
        customizer.run();
    }

    /** While set, every compilation of a {@link #blockingCompilations()} configuration waits for it. */
    private static volatile CountDownLatch compileGate;

    /** Counted down when a compilation starts waiting for {@link #compileGate}. */
    private static volatile CountDownLatch compileWaiting;

    private File scriptRoot;

    private ScriptedConfiguration configuration;

    private ExecutorService executor;

    @BeforeMethod
    public void setUp() throws Exception {
        scriptRoot = Files.createTempDirectory("script-on-connector").toFile();
        final File helper = write("Helper.groovy", "class Helper { static String greet() { 'hello' } }\n");
        // In the past, so that an edit is newer whatever the file system's time resolution.
        assertThat(helper.setLastModified(System.currentTimeMillis() - 60000L)).isTrue();
        configuration = withScriptRoot(new ScriptedConfiguration());
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod
    public void tearDown() {
        System.clearProperty(ScriptedConfiguration.SCRIPT_ON_CONNECTOR_CACHE_SIZE_PROPERTY);
        customizer = null;
        final CountDownLatch gate = compileGate;
        compileGate = null;
        if (null != gate) {
            gate.countDown();
        }
        executor.shutdownNow();
        configuration.release();
        delete(scriptRoot);
    }

    @Test
    public void testSameTextReusesTheCompiledClass() {
        final Class first = configuration.getScriptOnConnectorClass("return 1");
        assertThat(configuration.getScriptOnConnectorClass("return 1")).isSameAs(first);
        assertThat(configuration.getScriptOnConnectorClass("return 2")).isNotSameAs(first);
    }

    @Test
    public void testScriptClassStaysOutOfTheEngineLoader() {
        final GroovyClassLoader engineLoader = configuration.getGroovyScriptEngine().getGroovyClassLoader();
        final int loadedBefore = engineLoader.getLoadedClasses().length;
        final Class scriptClass = configuration.getScriptOnConnectorClass("return 1");
        assertThat(engineLoader.getLoadedClasses()).hasSize(loadedBefore);
        assertThat(compilingLoader(scriptClass).getParent()).isSameAs(engineLoader);
    }

    @Test
    public void testScriptSeesTheDefaultImports() {
        assertThat(configuration.evaluateScriptOnConnector("return ObjectClass.ACCOUNT_NAME", new Binding()))
                .isEqualTo(ObjectClass.ACCOUNT_NAME);
    }

    @Test
    public void testScriptSeesTheClassesInTheScriptRoots() {
        assertThat(configuration.evaluateScriptOnConnector("return Helper.greet()", new Binding()))
                .isEqualTo("hello");
    }

    @Test
    public void testScriptSharesScriptRootClassesWithOperationScripts() throws Exception {
        write("op.groovy", "return Helper\n");
        // The text first: the operation script must see the same Helper.
        final Object fromText = configuration.evaluateScriptOnConnector("return Helper", new Binding());
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isSameAs(fromText);
    }

    @Test(timeOut = 60000)
    public void testScriptsCompileTogetherOnANewEngine() throws Exception {
        for (int round = 0; round < 5; round++) {
            for (Object result : evaluateTogetherOnANewEngine("return 1")) {
                assertThat(result).isEqualTo(1);
            }
        }
    }

    @Test(timeOut = 60000)
    public void testScriptsCompiledTogetherShareTheScriptRootClasses() throws Exception {
        for (int round = 0; round < 5; round++) {
            final List<Object> helpers = evaluateTogetherOnANewEngine("return Helper");
            for (Object helper : helpers) {
                assertThat(helper).isSameAs(helpers.get(0));
            }
        }
    }

    @Test
    public void testScriptRootClassShadowsAClassOfTheConnectorLoader() throws Exception {
        final String name = ShadowedByScriptRoot.class.getName();
        write(name.replace('.', '/') + ".groovy", "package " + ShadowedByScriptRoot.class.getPackage().getName()
                + "\nclass ShadowedByScriptRoot { static String origin() { 'script root' } }\n");
        write("op.groovy", "return " + name + ".origin()\n");
        assertThat(configuration.evaluateScriptOnConnector("return " + name + ".origin()", new Binding()))
                .isEqualTo("script root");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("script root");
    }

    @Test
    public void testCachedScriptKeepsTheScriptRootClassItWasCompiledAgainst() throws Exception {
        write("op.groovy", "return Helper.greet()\n");
        final Class cached = configuration.getScriptOnConnectorClass("return Helper.greet()");
        assertThat(configuration.evaluateScriptOnConnector("return Helper.greet()", new Binding())).isEqualTo("hello");

        editHelper("class Helper { static String greet() { 'hi' } }\n");

        assertThat(configuration.getScriptOnConnectorClass("return Helper.greet()")).isSameAs(cached);
        assertThat(configuration.evaluateScriptOnConnector("return Helper.greet()", new Binding())).isEqualTo("hello");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("hi");
    }

    @Test
    public void testNewScriptSharesTheEditedScriptRootClassWithOperationScripts() throws Exception {
        write("op.groovy", "return Helper\n");
        assertThat(configuration.evaluateScriptOnConnector("return Helper.greet()", new Binding())).isEqualTo("hello");

        editHelper("class Helper { static String greet() { 'hi' } }\n");

        // A new text first, before any operation script has seen the edit.
        final Class fromText = (Class) configuration.evaluateScriptOnConnector("return Helper", new Binding());
        assertThat(fromText.getMethod("greet").invoke(null)).isEqualTo("hi");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isSameAs(fromText);
    }

    @Test
    public void testScriptSeesItsBinding() {
        final Binding binding = new Binding();
        binding.setVariable("a", 41);
        assertThat(configuration.evaluateScriptOnConnector("return a + 1", binding)).isEqualTo(42);
    }

    @Test
    public void testReleaseUnregistersTheCachedClasses() {
        final Class released = configuration.getScriptOnConnectorClass("return 1");
        registerMetaClass(released);
        configuration.release();
        assertThat(ClassInfo.getClassInfo(released).getStrongMetaClass()).isNull();
        final Class recompiled = configuration.getScriptOnConnectorClass("return 1");
        assertThat(recompiled).isNotSameAs(released);
        assertThat(compilingLoader(recompiled).getParent())
                .isSameAs(configuration.getGroovyScriptEngine().getGroovyClassLoader());
    }

    @Test
    public void testCustomizerCanRunAScriptOnConnector() throws Exception {
        final AtomicReference<Object> result = new AtomicReference<Object>();
        customizer = () -> result.set(configuration.evaluateScriptOnConnector("return 41 + 1", new Binding()));
        writeCustomizer();
        configuration.getGroovyScriptEngine();
        assertThat(result.get()).isEqualTo(42);
    }

    @Test
    public void testScriptRunByTheCustomizerIsNotCached() throws Exception {
        final AtomicReference<Class> compiled = new AtomicReference<Class>();
        customizer = () -> compiled.set(configuration.getScriptOnConnectorClass("return 1"));
        writeCustomizer();
        configuration.getGroovyScriptEngine();
        assertThat(compiled.get()).isNotNull();
        assertThat(configuration.getScriptOnConnectorClass("return 1")).isNotSameAs(compiled.get());
    }

    @Test(timeOut = 60000)
    public void testCachedScriptDoesNotWaitForAnotherCompilation() throws Exception {
        final ScriptedConfiguration blocking = blockingCompilations();
        configuration = blocking;
        final Class cached = blocking.getScriptOnConnectorClass("return 1");
        compileWaiting = new CountDownLatch(1);
        compileGate = new CountDownLatch(1);
        final Future<Class> compiling = executor.submit(() -> blocking.getScriptOnConnectorClass("return 2"));
        assertThat(compileWaiting.await(10, SECONDS)).isTrue();
        final Future<Class> hit = executor.submit(() -> blocking.getScriptOnConnectorClass("return 1"));
        assertThat(hit.get(10, SECONDS)).isSameAs(cached);
        compileGate.countDown();
        assertThat(compiling.get(10, SECONDS)).isNotSameAs(cached);
    }

    @Test
    public void testCacheSizeComesFromTheSystemProperty() {
        final String property = ScriptedConfiguration.SCRIPT_ON_CONNECTOR_CACHE_SIZE_PROPERTY;
        assertThat(property).isEqualTo("org.identityconnectors.framework.groovy.scriptOnConnectorCacheSize");
        assertThat(ScriptedConfiguration.getScriptOnConnectorCacheSize()).isEqualTo(64);
        System.setProperty(property, "5");
        assertThat(ScriptedConfiguration.getScriptOnConnectorCacheSize()).isEqualTo(5);
        System.setProperty(property, " 7 ");
        assertThat(ScriptedConfiguration.getScriptOnConnectorCacheSize()).isEqualTo(7);
        System.setProperty(property, "0");
        assertThat(ScriptedConfiguration.getScriptOnConnectorCacheSize()).isEqualTo(64);
        System.setProperty(property, "-3");
        assertThat(ScriptedConfiguration.getScriptOnConnectorCacheSize()).isEqualTo(64);
        System.setProperty(property, "many");
        assertThat(ScriptedConfiguration.getScriptOnConnectorCacheSize()).isEqualTo(64);
    }

    @Test
    public void testLeastRecentlyUsedScriptIsEvicted() {
        System.setProperty(ScriptedConfiguration.SCRIPT_ON_CONNECTOR_CACHE_SIZE_PROPERTY, "2");
        final Class a = configuration.getScriptOnConnectorClass("return 'a'");
        final Class b = configuration.getScriptOnConnectorClass("return 'b'");
        assertThat(configuration.getScriptOnConnectorClass("return 'a'")).isSameAs(a);
        configuration.getScriptOnConnectorClass("return 'c'");
        assertThat(configuration.getScriptOnConnectorClass("return 'a'")).isSameAs(a);
        assertThat(configuration.getScriptOnConnectorClass("return 'b'")).isNotSameAs(b);
    }

    @Test
    public void testEvictedScriptClassesAreUnregistered() {
        System.setProperty(ScriptedConfiguration.SCRIPT_ON_CONNECTOR_CACHE_SIZE_PROPERTY, "1");
        final String text = "def c = { -> 1 }; return c.getClass()";
        final Class scriptClass = configuration.getScriptOnConnectorClass(text);
        final Class closureClass = (Class) configuration.evaluateScriptOnConnector(text, new Binding());
        assertThat(closureClass).isNotSameAs(scriptClass);
        registerMetaClass(scriptClass);
        registerMetaClass(closureClass);
        configuration.getScriptOnConnectorClass("return 2");
        assertThat(ClassInfo.getClassInfo(scriptClass).getStrongMetaClass()).isNull();
        assertThat(ClassInfo.getClassInfo(closureClass).getStrongMetaClass()).isNull();
    }

    @Test
    public void testEvictedScriptClassCanBeUnloaded() throws Exception {
        System.setProperty(ScriptedConfiguration.SCRIPT_ON_CONNECTOR_CACHE_SIZE_PROPERTY, "1");
        final WeakReference<Class> evicted = compileAndRun("def c = { it + 1 }; return c(1)");
        configuration.getScriptOnConnectorClass("return 2");
        assertThat(isCollected(evicted)).isTrue();
    }

    @Test
    public void testConnectorReusesTheCompiledScript() {
        final GroovyClassLoader engineLoader = configuration.getGroovyScriptEngine().getGroovyClassLoader();
        final int loadedBefore = engineLoader.getLoadedClasses().length;
        final ScriptedPoolableConnector connector = connector();
        final Object first = connector.runScriptOnConnector(groovy("return this.getClass()"), null);
        assertThat(connector.runScriptOnConnector(groovy("return this.getClass()"), null)).isSameAs(first);
        assertThat(engineLoader.getLoadedClasses()).hasSize(loadedBefore);
    }

    @Test
    public void testConnectorScriptSeesItsArgumentsAndTheLogger() {
        final ScriptContext request = new ScriptContext("Groovy", "return [a + 1, null != log]",
                Collections.<String, Object>singletonMap("a", 41));
        assertThat(connector().runScriptOnConnector(request, null)).isEqualTo(Arrays.asList(42, true));
    }

    @Test
    public void testScriptRunByTheConnectorCanBeUnloadedOnceEvicted() throws Exception {
        System.setProperty(ScriptedConfiguration.SCRIPT_ON_CONNECTOR_CACHE_SIZE_PROPERTY, "1");
        final WeakReference<Object> evicted = runOnConnectorAndForget("return this.getClass()");
        // Evicted through the configuration, so the connector code runs no other script after it.
        configuration.getScriptOnConnectorClass("return 2");
        assertThat(isCollected(evicted)).isTrue();
    }

    @Test(expectedExceptions = CompilationFailedException.class)
    public void testBrokenScriptRootClassFailsAsACompilationError() throws Exception {
        writeBrokenClass();
        configuration.evaluateScriptOnConnector("return Broken.x()", new Binding());
    }

    @Test(expectedExceptions = CompilationFailedException.class)
    public void testConnectorReportsABrokenScriptRootClassAsACompilationError() throws Exception {
        writeBrokenClass();
        connector().runScriptOnConnector(groovy("return Broken.x()"), null);
    }

    private void writeBrokenClass() throws IOException {
        write("Broken.groovy", "class Broken { static int x() { return 1 +  } }\n");
    }

    private File write(String fileName, String text) throws IOException {
        final File file = new File(scriptRoot, fileName);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static void delete(File file) {
        final File[] children = file.listFiles();
        if (null != children) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }

    /**
     * Releases the configuration, so that the next call creates a new engine, then runs a variant
     * of the text on each of four threads at once and returns the results.
     */
    private List<Object> evaluateTogetherOnANewEngine(String text) throws Exception {
        configuration.release();
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<Object>> futures = new ArrayList<Future<Object>>();
        for (int i = 0; i < 4; i++) {
            // A different text per thread, so that each one compiles.
            final String variant = text + " // " + i;
            futures.add(executor.submit(() -> {
                start.await();
                return configuration.evaluateScriptOnConnector(variant, new Binding());
            }));
        }
        start.countDown();
        final List<Object> results = new ArrayList<Object>();
        for (Future<Object> future : futures) {
            results.add(future.get(10, SECONDS));
        }
        return results;
    }

    /** Rewrites Helper.groovy so that the engine sees it as changed on its next check. */
    private void editHelper(String text) throws Exception {
        // Past the minimum recompilation interval, 100 ms by default, before and after the edit.
        Thread.sleep(200);
        write("Helper.groovy", text);
        Thread.sleep(200);
    }

    private ScriptedConfiguration withScriptRoot(ScriptedConfiguration result) {
        result.setScriptRoots(new String[] { scriptRoot.getAbsolutePath() });
        return result;
    }

    /** A configuration whose compilations wait for {@link #compileGate} while it is set. */
    private ScriptedConfiguration blockingCompilations() {
        return withScriptRoot(new ScriptedConfiguration() {
            @Override
            protected ImportCustomizer getImportCustomizer(ImportCustomizer parent) {
                return super.getImportCustomizer(new ImportCustomizer() {
                    @Override
                    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                        final CountDownLatch gate = compileGate;
                        if (null != gate) {
                            compileWaiting.countDown();
                            try {
                                gate.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        super.call(source, context, classNode);
                    }
                });
            }
        });
    }

    /** Writes a customizer script that calls {@link #customize()}, and configures it. */
    private void writeCustomizer() throws IOException {
        Files.write(new File(scriptRoot, "Customizer.groovy").toPath(),
                (ScriptOnConnectorCacheTest.class.getName() + ".customize()\n").getBytes(StandardCharsets.UTF_8));
        configuration.setCustomizerScriptFileName("Customizer.groovy");
    }

    /** Puts a strong metaclass on the class, as a script that changes its own metaclass does. */
    private static void registerMetaClass(Class clazz) {
        final ExpandoMetaClass metaClass = new ExpandoMetaClass(clazz, false, true);
        metaClass.initialize();
        GroovySystem.getMetaClassRegistry().setMetaClass(clazz, metaClass);
        assertThat(ClassInfo.getClassInfo(clazz).getStrongMetaClass()).isNotNull();
    }

    /** The loader whose class cache holds a compiled script class: the parent of its InnerLoader. */
    private static ClassLoader compilingLoader(Class scriptClass) {
        return scriptClass.getClassLoader().getParent();
    }

    /** Compiles and runs the script, keeping no strong reference to its class. */
    private WeakReference<Class> compileAndRun(String scriptText) {
        configuration.evaluateScriptOnConnector(scriptText, new Binding());
        return new WeakReference<Class>(configuration.getScriptOnConnectorClass(scriptText));
    }

    /** Runs full collections, which unload classes, until the referent is gone. */
    private static boolean isCollected(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 100 && null != reference.get(); i++) {
            System.gc();
            Thread.sleep(50);
        }
        return null == reference.get();
    }

    /** Runs the script through a connector and keeps no strong reference to what it returned. */
    private WeakReference<Object> runOnConnectorAndForget(String scriptText) {
        return new WeakReference<Object>(connector().runScriptOnConnector(groovy(scriptText), null));
    }

    private ScriptedPoolableConnector connector() {
        final ScriptedPoolableConnector connector = new ScriptedPoolableConnector();
        connector.init(configuration);
        return connector;
    }

    private static ScriptContext groovy(String scriptText) {
        return new ScriptContext("Groovy", scriptText, Collections.<String, Object>emptyMap());
    }
}
