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
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
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
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.reflection.ClassInfo;
import org.forgerock.openicf.connectors.groovy.ScriptedPoolableConnector;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.util.GroovyScriptEngine;
import groovy.util.ScriptException;

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

    /** While set, a lookup of Trigger.groovy in a {@link #blockingRoot()} waits for it. */
    private static volatile CountDownLatch lookupGate;

    /** Counted down when a lookup starts waiting for {@link #lookupGate}. */
    private static volatile CountDownLatch lookupWaiting;

    private File scriptRoot;

    private ScriptedConfiguration configuration;

    private ExecutorService executor;

    @BeforeMethod
    public void setUp() throws Exception {
        scriptRoot = Files.createTempDirectory("script-on-connector").toFile();
        writeHelper("class Helper { static String greet() { 'hello' } }\n");
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
        final CountDownLatch lookup = lookupGate;
        lookupGate = null;
        if (null != lookup) {
            lookup.countDown();
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

    @Test(timeOut = 60000)
    public void testTextAndOperationScriptThatLoadAScriptRootClassTogetherShareIt() throws Exception {
        configuration = withBlockingRoot();
        write("op.groovy", "def h = Helper\nif (false) { Trigger.x() }\nreturn h\n");
        lookupWaiting = new CountDownLatch(1);
        lookupGate = new CountDownLatch(1);
        final Future<Object> operation =
                executor.submit(() -> configuration.evaluate("op.groovy", new Binding(), null));
        // The engine compiles Helper together with the operation script, and stops before the end.
        assertThat(lookupWaiting.await(10, SECONDS)).isTrue();

        final FutureTask<Object> text = new FutureTask<Object>(
                () -> configuration.evaluateScriptOnConnector("return Helper", new Binding()));
        final Thread textThread = new Thread(text);
        textThread.setDaemon(true);
        textThread.start();
        awaitBlocked(textThread);
        lookupGate.countDown();

        assertThat(text.get(10, SECONDS)).isSameAs(operation.get(10, SECONDS));
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
    public void testCachedScriptLinksTheScriptRootClassesItWasCompiledAgainst() throws Exception {
        writeHelper("class Helper { static String greet() { 'hello' }\n"
                + "static class Greeting { static String text() { 'hello' } } }\n");
        write("op.groovy", "return Helper.greet() + ' ' + Helper.Greeting.text()\n");
        final String text = "return useHelper ? Helper.greet() + ' ' + Helper.Greeting.text() : 'not run'";
        // Compiled and cached, but the code that refers to Helper has not run yet.
        assertThat(configuration.evaluateScriptOnConnector(text, useHelper(false))).isEqualTo("not run");

        editHelper("class Helper { static String greet() { 'hi' }\n"
                + "static class Greeting { static String text() { 'hi' } } }\n");
        // The engine recompiles Helper for the operation script.
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("hi hi");

        // The cached text refers to Helper and to its nested class for the first time.
        assertThat(configuration.evaluateScriptOnConnector(text, useHelper(true))).isEqualTo("hello hello");
    }

    @Test
    public void testCachedScriptLinksTheEngineClassesItWasCompiledAgainst() throws Exception {
        writeHelper("class Helper { static String greet() { 'hello' } }\n"
                + "class Farewell { static String text() { 'bye' } }\n");
        write("op.groovy", "return Helper.greet() + ' ' + Farewell.text()\n");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("hello bye");
        // Farewell has no source of its own: the text gets it from the engine, without Helper.
        final String text = "return useHelper ? Farewell.text() : 'not run'";
        assertThat(configuration.evaluateScriptOnConnector(text, useHelper(false))).isEqualTo("not run");

        editHelper("class Helper { static String greet() { 'hi' } }\n"
                + "class Farewell { static String text() { 'ciao' } }\n");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("hi ciao");

        assertThat(configuration.evaluateScriptOnConnector(text, useHelper(true))).isEqualTo("bye");
    }

    @Test
    public void testCachedStaticScriptLinksTheClassesCompiledWithAScriptRootClass() throws Exception {
        writeHelper("class Helper { static Greeting make() { new Greeting() }\n"
                + "static Farewell leave() { new Farewell() }\n"
                + "static class Greeting { String text() { 'hello' } } }\n"
                + "class Farewell { String text() { 'bye' } }\n");
        write("op.groovy", "return Helper.make().text() + ' ' + Helper.leave().text()\n");
        // The text names neither Greeting nor Farewell: the compiler takes them from Helper's methods.
        final String text = "@groovy.transform.CompileStatic\n"
                + "String use(boolean b) { b ? Helper.make().text() + ' ' + Helper.leave().text() : 'not run' }\n"
                + "return use((boolean) useHelper)\n";
        assertThat(configuration.evaluateScriptOnConnector(text, useHelper(false))).isEqualTo("not run");

        editHelper("class Helper { static Greeting make() { new Greeting() }\n"
                + "static Farewell leave() { new Farewell() }\n"
                + "static class Greeting { String text() { 'hi' } } }\n"
                + "class Farewell { String text() { 'ciao' } }\n");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("hi ciao");

        assertThat(configuration.evaluateScriptOnConnector(text, useHelper(true))).isEqualTo("hello bye");
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
    public void testUrlThatATextAddsToItsLoaderReachesTheOperationScripts() throws Exception {
        final File lib = Files.createTempDirectory("script-on-connector-lib").toFile();
        try {
            final CompilerConfiguration libConfiguration = new CompilerConfiguration();
            libConfiguration.setTargetDirectory(lib);
            final CompilationUnit unit = new CompilationUnit(libConfiguration);
            unit.addSource("Driver.groovy", "package drv\nclass Driver { static String id() { 'drv' } }\n");
            unit.compile();

            configuration.evaluateScriptOnConnector(
                    "this.class.classLoader.addURL(new URL('" + toUrl(lib) + "'))", new Binding());
            write("op.groovy", "return this.class.classLoader.loadClass('drv.Driver').id()\n");
            assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("drv");
        } finally {
            delete(lib);
        }
    }

    @Test
    public void testClassThatATextParsesReachesTheOperationScripts() throws Exception {
        final GroovyClassLoader textLoader = (GroovyClassLoader) configuration.evaluateScriptOnConnector(
                "return this.class.classLoader", new Binding());
        textLoader.parseClass("class Parsed { static String id() { 'parsed' } }");
        write("op.groovy", "return Parsed.id()\n");
        assertThat(configuration.evaluate("op.groovy", new Binding(), null)).isEqualTo("parsed");
    }

    @Test(timeOut = 60000)
    public void testClassThatATextParsesWaitsForTheEngineCompilation() throws Exception {
        configuration = withBlockingRoot();
        final GroovyClassLoader textLoader = (GroovyClassLoader) configuration.evaluateScriptOnConnector(
                "return this.class.classLoader", new Binding());
        write("op.groovy", "if (false) { Trigger.x() }\nreturn 1\n");
        lookupWaiting = new CountDownLatch(1);
        lookupGate = new CountDownLatch(1);
        final Future<Object> operation =
                executor.submit(() -> configuration.evaluate("op.groovy", new Binding(), null));
        // The engine compiles the operation script, and stops before the end.
        assertThat(lookupWaiting.await(10, SECONDS)).isTrue();

        final FutureTask<Class> parsed = new FutureTask<Class>(
                () -> textLoader.parseClass("class Parsed { static String id() { 'parsed' } }"));
        final Thread parseThread = new Thread(parsed);
        parseThread.setDaemon(true);
        parseThread.start();
        awaitBlocked(parseThread);
        assertThat(parsed.isDone()).isFalse();
        lookupGate.countDown();

        assertThat(operation.get(10, SECONDS)).isEqualTo(1);
        assertThat(parsed.get(10, SECONDS).getName()).isEqualTo("Parsed");
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
    public void testReleaseUnregistersTheCachedClassesWhenTheReleaseClosureThrows() {
        final Class released = configuration.getScriptOnConnectorClass("return 1");
        registerMetaClass(released);
        configuration.setReleaseClosure(new Closure<Void>(this) {
            public Void doCall() {
                throw new IllegalStateException("release failed");
            }
        });
        try {
            configuration.release();
            fail("release() did not rethrow the failure of the release closure");
        } catch (IllegalStateException expected) {
            // The cache is cleared anyway.
        } finally {
            configuration.setReleaseClosure(null);
        }
        assertThat(ClassInfo.getClassInfo(released).getStrongMetaClass()).isNull();
    }

    @Test
    public void testEngineBuiltByASubclassCompilesTheScriptWithItsOwnConfiguration() {
        configuration = withScriptRoot(new ScriptedConfiguration() {
            private GroovyScriptEngine engine;

            @Override
            protected synchronized GroovyScriptEngine getGroovyScriptEngine() {
                if (null == engine) {
                    final CompilerConfiguration own = new CompilerConfiguration();
                    own.addCompilationCustomizers(
                            new ImportCustomizer().addImport("EngineList", ArrayList.class.getName()));
                    engine = new GroovyScriptEngine(new URL[] { toUrl(scriptRoot) },
                            new GroovyClassLoader(ScriptOnConnectorCacheTest.class.getClassLoader(), own));
                }
                return engine;
            }
        });
        assertThat(configuration.evaluateScriptOnConnector("return new EngineList()", new Binding()))
                .isInstanceOf(ArrayList.class);
    }

    @Test
    public void testScriptCompilesWithWhatASubclassAddsToTheEngineConfiguration() throws Exception {
        configuration = withScriptRoot(new ScriptedConfiguration() {
            @Override
            protected URL[] getRoots(CompilerConfiguration compilerConfiguration, GroovyClassLoader loader) {
                compilerConfiguration.addCompilationCustomizers(
                        new ImportCustomizer().addImport("RootsList", ArrayList.class.getName()));
                final SecureASTCustomizer secure = new SecureASTCustomizer();
                secure.setMethodDefinitionAllowed(false);
                compilerConfiguration.addCompilationCustomizers(secure);
                return super.getRoots(compilerConfiguration, loader);
            }
        });
        assertThat(configuration.evaluateScriptOnConnector("return new RootsList()", new Binding()))
                .isInstanceOf(ArrayList.class);
        try {
            configuration.evaluateScriptOnConnector("def f() { 1 }\nreturn f()", new Binding());
            fail("A text defined a method that the engine's SecureASTCustomizer does not allow");
        } catch (CompilationFailedException expected) {
            assertThat(expected.getMessage()).contains("Method definitions are not allowed");
        }
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
        // log.@clazz is the class the logger reports for.
        final ScriptContext request = new ScriptContext("Groovy", "return [a + 1, log.@clazz]",
                Collections.<String, Object>singletonMap("a", 41));
        assertThat(connector().runScriptOnConnector(request, null))
                .isEqualTo(Arrays.<Object>asList(42, ScriptedConnectorBase.class));
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

    @Test
    public void testScriptExceptionFromTheEngineFailsAsACompilationError() {
        configuration = withScriptRoot(new ScriptedConfiguration() {
            private GroovyScriptEngine failing;

            @Override
            protected synchronized GroovyScriptEngine getGroovyScriptEngine() {
                final GroovyScriptEngine engine = super.getGroovyScriptEngine();
                if (null == failing) {
                    // A GroovyScriptEngine subclass may throw what loadScriptByName declares.
                    failing = new GroovyScriptEngine(new URL[] { toUrl(scriptRoot) }, engine.getGroovyClassLoader()) {
                        @Override
                        public Class loadScriptByName(String scriptName) throws ScriptException {
                            throw new ScriptException("Cannot load " + scriptName);
                        }
                    };
                }
                return failing;
            }
        });
        try {
            configuration.evaluateScriptOnConnector("return Helper.greet()", new Binding());
            fail("The text compiled although the engine could not load the script-root classes");
        } catch (CompilationFailedException expected) {
            assertThat(expected.getCause()).isInstanceOf(ScriptException.class);
        }
    }

    private void writeBrokenClass() throws IOException {
        write("Broken.groovy", "class Broken { static int x() { return 1 +  } }\n");
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
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

    private static Binding useHelper(boolean useHelper) {
        final Binding binding = new Binding();
        binding.setVariable("useHelper", useHelper);
        return binding;
    }

    /** Writes Helper.groovy before the engine first loads it. */
    private void writeHelper(String text) throws IOException {
        final File helper = write("Helper.groovy", text);
        // In the past, so that an edit is newer whatever the file system's time resolution.
        assertThat(helper.setLastModified(System.currentTimeMillis() - 60000L)).isTrue();
    }

    /** Rewrites Helper.groovy so that the engine sees it as changed on its next check. */
    private void editHelper(String text) throws Exception {
        // Past the minimum recompilation interval, 100 ms by default, before and after the edit.
        Thread.sleep(200);
        write("Helper.groovy", text);
        Thread.sleep(200);
    }

    /** A configuration with a {@link #blockingRoot()} after the script root. */
    private ScriptedConfiguration withBlockingRoot() {
        return withScriptRoot(new ScriptedConfiguration() {
            @Override
            protected URL[] getRoots(CompilerConfiguration compilerConfiguration, GroovyClassLoader loader) {
                final URL[] roots = super.getRoots(compilerConfiguration, loader);
                final URL[] result = Arrays.copyOf(roots, roots.length + 1);
                result[roots.length] = blockingRoot();
                return result;
            }
        });
    }

    /** Waits until the thread has been blocked on a monitor for a while, for at most 10 s. */
    private static void awaitBlocked(Thread thread) throws InterruptedException {
        for (int i = 0, blocked = 0; i < 1000 && blocked < 5; i++) {
            Thread.sleep(10);
            blocked = Thread.State.BLOCKED == thread.getState() ? blocked + 1 : 0;
        }
    }

    /**
     * A script root without sources. While {@link #lookupGate} is set, a lookup of Trigger.groovy
     * in it waits for the gate.
     */
    private static URL blockingRoot() {
        try {
            return new URL(null, "blocking:/", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(final URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() throws IOException {
                            throw new FileNotFoundException(url.toString());
                        }

                        @Override
                        public InputStream getInputStream() throws IOException {
                            final CountDownLatch gate = lookupGate;
                            if (null != gate && "/Trigger.groovy".equals(url.getPath())) {
                                lookupWaiting.countDown();
                                try {
                                    gate.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            throw new FileNotFoundException(url.toString());
                        }
                    };
                }
            });
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
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
