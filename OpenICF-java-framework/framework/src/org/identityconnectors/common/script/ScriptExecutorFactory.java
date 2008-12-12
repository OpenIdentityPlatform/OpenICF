/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.common.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.operations.ScriptOnConnectorApiOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;


/**
 * Abstraction for finding script executors to allow us to invoke scripts from
 * java.
 * <p>
 * <b>NOTE: </b> This is intended purely for executing java-embedable scripting
 * languages. That is, the execution model is assumed to be same-JVM, so scripts
 * can have side effects on objects passed to the script. This is used
 * internally by the connector framework in its implementation of
 * {@link ScriptOnConnectorApiOp} and should be used by connectors should they
 * need to have a custom implementation of {@link ScriptOnConnectorOp}. This is
 * <b>not</b> intended for use by connectors that implement
 * {@link ScriptOnResourceOp}.
 */
public abstract class ScriptExecutorFactory {

    private static final Map<String,String> REGISTRY = new HashMap<String,String>();
    
    private static Map<String, Class<?>> _factoryCache;

    static {
        // attempt to register
        // TODO: need a distributed way to register more languages
        register("GROOVY",
                "org.identityconnectors.common.script.groovy.GroovyScriptExecutorFactory");
    }

    private static void register(String lang, String className) {
        REGISTRY.put(lang.toUpperCase(), className);
    }
    
    private static synchronized Map<String, Class<?>> 
    getFactoryCache() {
        if ( _factoryCache == null ) {
            _factoryCache = new HashMap<String,Class<?>>();
            for (Map.Entry<String, String> entry : REGISTRY.entrySet()) {
                try {
                    String lang = entry.getKey();
                    String className = entry.getValue();
                    // attempt to get the factory..
                    Class<?> clazz = Class.forName(className);
                    // try to create a new instance..
                    clazz.newInstance();
                    // if we've made it this far it should have its deps..
                    _factoryCache.put(lang.toUpperCase(), clazz);
                }
                catch (Exception e) {
                    
                }
            }
        }
        return _factoryCache;
    }
    

    /**
     * Returns the set of supported languages.
     * 
     * @return The set of supported languages.
     */
    public static Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(getFactoryCache().keySet());
    }

    /**
     * Creates a ScriptExecutorFactory for the given language
     * 
     * @param language
     *            The name of the language
     * @return The script executor factory
     * @throws IllegalArgumentException
     *             If the given language is not supported.
     */
    public static ScriptExecutorFactory newInstance(String language)
            throws IllegalArgumentException {
        if (StringUtil.isBlank(language)) {
            throw new IllegalArgumentException("Language must be specified");
        }
        String lang = language.toUpperCase();
        Class<?> clazz = getFactoryCache().get(lang);
        if (clazz == null) {
            String MSG = String.format("Language not supported: %s", language);
            throw new IllegalArgumentException(MSG);
        }
        // exceptions here should not happend because of the register
        try {
            return (ScriptExecutorFactory)clazz.newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // should never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a script executor for the given script.
     * 
     * @param loader
     *            The classloader that contains the java classes that the script
     *            should have access to.
     * @param script
     *            The script text.
     * @param compile
     *            A hint to tell the script executor whether or not to compile
     *            the given script. This need not be implemented by all script
     *            executors. If true, the caller is saying that they intend to
     *            call the script multiple times with different arguments, so
     *            compile if possible.
     * @return A script executor.
     */
    public abstract ScriptExecutor newScriptExecutor(ClassLoader loader,
            String script, boolean compile);

}
