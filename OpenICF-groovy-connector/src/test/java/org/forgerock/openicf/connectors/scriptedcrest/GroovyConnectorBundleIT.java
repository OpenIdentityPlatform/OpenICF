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
package org.forgerock.openicf.connectors.scriptedcrest;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.control.CompilationFailedException;
import org.testng.annotations.Test;

/**
 * Pins the {@code org.apache.http} ranges that the packaged bundle imports (issue #207), and
 * checks that the bundle wires every package its default scripts use (issue #214).
 * <p>
 * bnd derives the ranges from the httpcomponents versions that the build resolves through the
 * imported opendj-parent BOM. They decide which hosts wire a connector installed as a bundle, for
 * example into OpenIDM's {@code bundle/}, so a version bump there must not change them unnoticed.
 */
public class GroovyConnectorBundleIT {

    /** An Import-Package or Export-Package clause: the package, then its attributes and directives. */
    private static final Pattern CLAUSE = Pattern.compile("([\\w.]+)((?:;[^,;\"]*(?:\"[^\"]*\")?)*)");
    private static final Pattern VERSION = Pattern.compile("version=\"([^\"]+)\"");

    @Test
    public void testHttpComponentsImportRanges() throws IOException {
        Map<String, String> imports = importPackage();
        // httpcore 4.4, httpclient 4.5 and httpasyncclient 4.1, which OpenIDM 7.1.2 ships;
        // httpasyncclient 4.0.x fails a request on a reused connection that the server has answered
        assertEquals(version(imports, "org.apache.http"), "[4.4,5)");
        assertEquals(version(imports, "org.apache.http.nio"), "[4.4,5)");
        assertEquals(version(imports, "org.apache.http.client"), "[4.5,5)");
        assertEquals(version(imports, "org.apache.http.impl.client"), "[4.5,5)");
        assertEquals(version(imports, "org.apache.http.nio.client"), "[4.1,5)");
        assertEquals(version(imports, "org.apache.http.impl.nio.client"), "[4.1,5)");
    }

    @Test
    public void testHttpComponentsImportsAreOptional() throws IOException {
        // The bundle has to resolve without httpcomponents on a host that runs ScriptedSQL only
        for (Map.Entry<String, String> clause : importPackage().entrySet()) {
            if (clause.getKey().startsWith("org.apache.http")) {
                assertTrue(clause.getValue().contains("resolution:=optional"), clause.getKey());
            }
        }
    }

    @Test
    public void testDefaultScriptsCompileAgainstTheWiredPackages() throws IOException, URISyntaxException {
        // The connector compiles its default customizer at runtime, with the thread's context
        // loader as parent (ScriptedConfiguration.getParentLoader()). In OSGi that is the bundle's
        // loader, which sees java.*, what Import-Package wires, what Require-Bundle: groovy-all
        // exports and the bundle's own classes. bnd fills Import-Package from compiled classes only.
        Set<String> wired = new HashSet<>(importPackage().keySet());
        File groovyAll = new File(Script.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        wired.addAll(exportPackage(groovyAll));
        Map<String, String> scripts = new HashMap<>();
        try (JarFile jar = new JarFile(bundle())) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    wired.add(name.substring(0, name.lastIndexOf('/')).replace('/', '.'));
                } else if (name.endsWith(".groovy")) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        scripts.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        assertFalse(scripts.isEmpty(), "the bundle has no scripts");
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> script : scripts.entrySet()) {
            // As ScriptedConfiguration.getCustomizerClass() compiles the default customizer
            try (GroovyClassLoader loader = new GroovyClassLoader(new WiredLoader(wired))) {
                loader.parseClass(new GroovyCodeSource(script.getValue(), script.getKey(), "/groovy/script"));
            } catch (CompilationFailedException e) {
                failures.add(e.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static File bundle() {
        File bundle = new File(System.getProperty("bundleJar"));
        assertTrue(bundle.isFile(), "bundle jar: " + bundle);
        return bundle;
    }

    private static Map<String, String> importPackage() throws IOException {
        try (JarFile jar = new JarFile(bundle())) {
            return clauses(jar.getManifest().getMainAttributes().getValue("Import-Package"));
        }
    }

    private static Set<String> exportPackage(File bundle) throws IOException {
        try (JarFile jar = new JarFile(bundle)) {
            return clauses(jar.getManifest().getMainAttributes().getValue("Export-Package")).keySet();
        }
    }

    private static Map<String, String> clauses(String header) {
        Map<String, String> clauses = new HashMap<>();
        Matcher clause = CLAUSE.matcher(header);
        while (clause.find()) {
            clauses.put(clause.group(1), clause.group(2));
        }
        return clauses;
    }

    private static String version(Map<String, String> imports, String pkg) {
        assertTrue(imports.containsKey(pkg), "Import-Package has no " + pkg);
        Matcher version = VERSION.matcher(imports.get(pkg));
        return version.find() ? version.group(1) : null;
    }

    /** Loads only the classes of the given packages and of java.*, as a bundle's loader does. */
    private static final class WiredLoader extends ClassLoader {

        private final Set<String> packages;

        WiredLoader(Set<String> packages) {
            // No parent: javax.* comes from the boot loader, but a bundle has to import it
            super(null);
            this.packages = packages;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            int dot = name.lastIndexOf('.');
            if (!name.startsWith("java.") && (dot < 0 || !packages.contains(name.substring(0, dot)))) {
                throw new ClassNotFoundException(name);
            }
            return Class.forName(name, resolve, GroovyConnectorBundleIT.class.getClassLoader());
        }
    }
}
