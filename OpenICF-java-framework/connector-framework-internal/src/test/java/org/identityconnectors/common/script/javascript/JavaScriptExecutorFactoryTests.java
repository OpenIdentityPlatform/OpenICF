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

package org.identityconnectors.common.script.javascript;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.net.URLClassLoader;

import org.identityconnectors.common.script.ScriptExecutor;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.testng.annotations.Test;

public class JavaScriptExecutorFactoryTests {

    @Test
    public void testValidScript() throws Exception {
        ScriptExecutor ex = getScriptExecutor("1 + 1;", false);
        assertEquals(((Number) ex.execute(null)).intValue(), 2);
    }

    @Test
    public void testCompiledScript() throws Exception {
        ScriptExecutor ex = getScriptExecutor("1 + 1;", true);
        assertEquals(((Number) ex.execute(null)).intValue(), 2);
    }

    @Test
    public void testUncompiledScriptRunsWithProvidedClassLoader() throws Exception {
        ClassLoader custom = new URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
        ScriptExecutor ex = ScriptExecutorFactory.newInstance("JavaScript").newScriptExecutor(custom,
                "java.lang.Thread.currentThread().getContextClassLoader();", false);
        assertSame(ex.execute(null), custom);
    }

    @Test
    public void testCompiledScriptRunsWithProvidedClassLoader() throws Exception {
        ClassLoader custom = new URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
        ScriptExecutor ex = ScriptExecutorFactory.newInstance("JavaScript").newScriptExecutor(custom,
                "java.lang.Thread.currentThread().getContextClassLoader();", true);
        assertSame(ex.execute(null), custom);
    }

    @Test
    public void testScriptDoesNotLeakClassLoaderAfterExecution() throws Exception {
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        ClassLoader custom = new URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
        ScriptExecutor ex = getScriptExecutor("1;", custom, false);
        ex.execute(null);
        assertSame(Thread.currentThread().getContextClassLoader(), before);
    }

    private ScriptExecutor getScriptExecutor(String script, boolean compile) {
        return getScriptExecutor(script, getClass().getClassLoader(), compile);
    }

    private ScriptExecutor getScriptExecutor(String script, ClassLoader loader, boolean compile) {
        return ScriptExecutorFactory.newInstance("JavaScript").newScriptExecutor(loader, script, compile);
    }
}
