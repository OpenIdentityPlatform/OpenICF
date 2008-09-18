/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.SearchOp;


/**
 * Bag of utility methods intended for test-only
 */
public abstract class TestHelpers {
    
    private static final Log LOG = Log.getLog(TestHelpers.class);
    private static final Object LOCK = new Object();
    
    /**
     * Method for convenient testing of local connectors. 
     */
    public static APIConfiguration createTestConfiguration(Class<? extends Connector> clazz,
            Configuration config) {
        return getInstance().createTestConfigurationImpl(clazz, config);
    }
    
    /**
     * Creates an dummy message catalog ideal for unit testing.
     * All messages are formatted as follows:
     * <p>
     * <code><i>message-key</i>: <i>arg0.toString()</i>, ..., <i>argn.toString</i></code>
     * @return A dummy message catalog.
     */
    public static ConnectorMessages createDummyMessages() {
        return getInstance().createDummyMessagesImpl();
    }
        
    public static List<ConnectorObject> searchToList(SearchApiOp search, 
            ObjectClass oclass, 
            Filter filter) {
        return searchToList(search, oclass, filter, null);
    }
    
    public static List<ConnectorObject> searchToList(SearchApiOp search, 
            ObjectClass oclass, 
            Filter filter,
            OperationOptions options) {
        ToListResultsHandler handler = new
             ToListResultsHandler();
        search.search(oclass,filter, handler,options);
        return handler.getObjects();
    }
    /**
     * Performs a raw, unfiltered search at the SPI level,
     * eliminating duplicates from the result set.
     * @param search The search SPI
     * @param oclass The object class - passed through to
     * connector so it may be null if the connecor
     * allowing it to be null. (This is convenient for
     * unit tests, but will not be the case in general)
     * @param filter The filter to search on
     * @return The list of results.
     */
    public static List<ConnectorObject> searchToList(SearchOp<?> search, 
            ObjectClass oclass, 
            Filter filter) {
        return searchToList(search,oclass,filter,null);
    }
    /**
     * Performs a raw, unfiltered search at the SPI level,
     * eliminating duplicates from the result set.
     * @param search The search SPI
     * @param oclass The object class - passed through to
     * connector so it may be null if the connecor
     * allowing it to be null. (This is convenient for
     * unit tests, but will not be the case in general)
     * @param filter The filter to search on
     * @param options The options - may be null - will
     *  be cast to an empty OperationOptions
     * @return The list of results.
     */
    public static List<ConnectorObject> searchToList(SearchOp<?> search, 
            ObjectClass oclass, 
            Filter filter,
            OperationOptions options) {
        ToListResultsHandler handler = new
             ToListResultsHandler();
        search(search,oclass,filter, handler, options);
        return handler.getObjects();
    }
    
    /**
     * Performs a raw, unfiltered search at the SPI level,
     * eliminating duplicates from the result set.
     * @param search The search SPI
     * @param oclass The object class - passed through to
     * connector so it may be null if the connecor
     * allowing it to be null. (This is convenient for
     * unit tests, but will not be the case in general)
     * @param filter The filter to search on
     * @param handler The result handler
     * @param options The options - may be null - will
     *  be cast to an empty OperationOptions
     */
    public static void search(SearchOp<?> search,
            final ObjectClass oclass, 
            final Filter filter, 
            ResultsHandler handler,
            OperationOptions options) {
        getInstance().searchImpl(search, oclass, filter, handler, options);
    }
    
    
    //At some point we might make this pluggable, but for now, hard-code
    private static final String IMPL_NAME =
        "org.identityconnectors.framework.impl.test.TestHelpersImpl";
    
    private static TestHelpers _instance;
    
    /**
     * Returns the instance of this factory.
     * @return The instance of this factory
     */
    private static synchronized TestHelpers getInstance() {
        if (_instance == null) {
            try {
                Class<?> clazz = Class.forName(IMPL_NAME);
                Object object = clazz.newInstance();
                _instance = TestHelpers.class.cast(object);
            }
            catch (Exception e) {
                throw ConnectorException.wrap(e);
            }
        }
        return _instance;
    }
    
    /**
     * Load properties in the following order to black box testing.
     */
    private static Properties _properties;
    public static final String GLOBAL_PROPS = ".connectors.properties";

    /**
     * Loads the properties files just like the connector 'build' environment
     * the only exception is properties in the 'global' file are filtered for
     * those properties that prefix the project's name.
     * 
     * @param name Key to the properties..
     * @param def default value to return if the key does not exist
     * @return def if key is not preset return the default.
     */
    public static String getProperty(String name, String def) {
        // attempt to find the property..
        return getProperties().getProperty(name, def);
    }
    
    /**
     * Loads the properties files just like the connector 'build' environment
     * the only exception is properties in the 'global' file are filtered for
     * those properties that prefix the project's name.
     */
    public static Properties getProperties() {
        // make sure the properties are loaded
        synchronized (LOCK) {
            if (_properties == null) {
                _properties = loadProjectProperties();
            }
        }
        // create a new properties object so it can't be modified.
        Properties ret = new Properties();
        ret.putAll(_properties);
        return ret;
    }

    private static Properties loadProjectProperties() {
        final String ERR = "Unable to load optional properties file: {0}";
        Properties props = null;
        Properties ret = new Properties();
        try {
            // load the local properties file
            props = IOUtil.loadPropertiesFile("build.properties");
            ret.putAll(props);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // global settings are prefixed w/ the project name
        String prjName = System.getProperty("project.name", null);
        if (StringUtil.isNotBlank(prjName)) {
            // includes the parent configuration and the specific config.
            List<String> configurations = CollectionUtil.newList(prjName);
            // determine the configuration property
            String cfg = System.getProperty("configuration", null);
            if (StringUtil.isNotBlank(cfg)) {
                String name = prjName + "-" + cfg;
                configurations.add(name);
            }
            // load the user properties file (project specific)
            File userHome = new File(System.getProperty("user.home"));
            File f = new File(userHome, GLOBAL_PROPS);
            try {
                props = IOUtil.loadPropertiesFile(f);
                for (String cfgName : configurations) {
                    String cmp = cfgName + ".";
                    for (Object keyObj : props.keySet()) {
                        String key = keyObj.toString();
                        if (key.startsWith(cmp)) {
                            String newKey = key.substring(cmp.length());
                            ret.put(newKey, props.get(key));
                        }
                    }
                }
            } catch (IOException e) {
                LOG.info(ERR, f.toString());
            }
            // load the project file then the project 
            // configuration specific file
            for (String cfgFn : configurations) {
                // load the user project specific file
                try {
                    // load the local properties file
                    String fn = String.format(".%s.properties", cfgFn);
                    f = new File(userHome, fn);
                    props = IOUtil.loadPropertiesFile(f);
                    ret.putAll(props);
                } catch (IOException e) {
                    LOG.info(ERR, f.toString());
                }
            }
        }
        // load the system properties
        ret.putAll(System.getProperties());
        return ret;
    }
    abstract protected APIConfiguration createTestConfigurationImpl(Class<? extends Connector> clazz,
            Configuration config);
    abstract protected void searchImpl(SearchOp<?> search,
            final ObjectClass oclass, 
            final Filter filter, 
            ResultsHandler handler,
            OperationOptions options);
    abstract protected ConnectorMessages createDummyMessagesImpl();


}
