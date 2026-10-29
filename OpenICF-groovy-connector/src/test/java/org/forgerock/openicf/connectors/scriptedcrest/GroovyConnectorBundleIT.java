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
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.annotations.Test;

/**
 * Pins the {@code org.apache.http} ranges that the packaged bundle imports (issue #207).
 * <p>
 * bnd derives them from the httpcomponents versions that the build resolves through the imported
 * opendj-parent BOM. They decide which hosts wire a connector installed as a bundle, for example
 * into OpenIDM's {@code bundle/}, so a version bump there must not change them unnoticed.
 */
public class GroovyConnectorBundleIT {

    /** An Import-Package clause: the package, then its attributes and directives. */
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

    private static Map<String, String> importPackage() throws IOException {
        File bundle = new File(System.getProperty("bundleJar"));
        assertTrue(bundle.isFile(), "bundle jar: " + bundle);
        String header;
        try (JarFile jar = new JarFile(bundle)) {
            header = jar.getManifest().getMainAttributes().getValue("Import-Package");
        }
        Map<String, String> imports = new HashMap<>();
        Matcher clause = CLAUSE.matcher(header);
        while (clause.find()) {
            imports.put(clause.group(1), clause.group(2));
        }
        return imports;
    }

    private static String version(Map<String, String> imports, String pkg) {
        assertTrue(imports.containsKey(pkg), "Import-Package has no " + pkg);
        Matcher version = VERSION.matcher(imports.get(pkg));
        return version.find() ? version.group(1) : null;
    }
}
