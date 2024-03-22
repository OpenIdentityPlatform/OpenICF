/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.identityconnectors.common.script.javascript;

import java.util.Map;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.script.ScriptExecutor;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

/**
 * Creates a new ScriptExecutorFactory for executing JavaScript scripts.
 */
public class JavaScriptExecutorFactory extends ScriptExecutorFactory {
    public static final String JAVA_SCRIPT = "JavaScript";
    private final ScriptEngineManager manager =
            Double.parseDouble(System.getProperty("java.specification.version")) < 15
                    ? new ScriptEngineManager(null) : new ScriptEngineManager();
    private final boolean compilable;

    /**
     * Make sure we blow up if JavaScript does not exist.
     */
    public JavaScriptExecutorFactory() {
        ScriptEngine engine = manager.getEngineByName(JAVA_SCRIPT);
        if (null == engine) {
            throw new IllegalStateException("JavaScript Engine is not found");
        }
        compilable = (engine instanceof Compilable);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always compile the script.
     */
    @Override
    public ScriptExecutor newScriptExecutor(ClassLoader loader, String script, boolean compile) {
        ScriptEngine engine = manager.getEngineByName(JAVA_SCRIPT);
        try {
            if (compile) {
                if (compilable) {
                    CompiledScript compiled = ((Compilable) engine).compile(script);
                    return new CompiledJavaScriptExecutor(loader, compiled);
                } else {
                    engine.eval(script);
                }
            }
            return new JavaScriptExecutor(loader, script);
        } catch (ScriptException e) {
            throw new ConnectorException(e.getMessage(), e);
        }
    }

    private static class CompiledJavaScriptExecutor implements ScriptExecutor {
        private final CompiledScript compiled;

        public CompiledJavaScriptExecutor(ClassLoader loader, CompiledScript compiled) {
            this.compiled = compiled;
        }

        public Object execute(Map<String, Object> arguments) throws Exception {
            Map<String, Object> args = CollectionUtil.nullAsEmpty(arguments);
            ScriptContext newContext = new SimpleScriptContext();
            Bindings engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                engineScope.put(entry.getKey(), entry.getValue());
            }
            return compiled.eval(newContext);
        }
    }

    private class JavaScriptExecutor implements ScriptExecutor {

        private final String script;

        public JavaScriptExecutor(ClassLoader loader, String script) {
            this.script = script;
        }

        public Object execute(Map<String, Object> arguments) throws Exception {
            Map<String, Object> args = CollectionUtil.nullAsEmpty(arguments);
            ScriptEngine engine = manager.getEngineByName(JAVA_SCRIPT);

            for (Map.Entry<String, Object> entry : args.entrySet()) {
                engine.put(entry.getKey(), entry.getValue());
            }
            return engine.eval(script);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLanguageName() {
        return JAVA_SCRIPT;
    }
}
