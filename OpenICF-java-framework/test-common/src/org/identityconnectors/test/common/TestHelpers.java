/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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
package org.identityconnectors.test.common;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

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
import org.identityconnectors.test.common.spi.TestHelpersSpi;

/**
 * Bag of utility methods useful to connector tests.
 */
public final class TestHelpers {

    private static final Log LOG = Log.getLog(TestHelpers.class);
    private static final Object LOCK = new Object();

    private TestHelpers() {
    }

    /**
     * Method for convenient testing of local connectors.
     */
    public static APIConfiguration createTestConfiguration(
            Class<? extends Connector> clazz, Configuration config) {
        return getSpi().createTestConfiguration(clazz, config);
    }

    /**
     * Fills a configuration bean with data from the given map. The map
     * keys are configuration property names and the values are
     * configuration property values.
     * 
     * @param config the configuration bean.
     * @param configData the map with configuration data.
     */
    public static void fillConfiguration(Configuration config,
            Map<String, ? extends Object> configData) {
        getSpi().fillConfiguration(config, configData);
    }

    /**
     * Creates an dummy message catalog ideal for unit testing. All messages are
     * formatted as follows:
     * <p>
     * <code><i>message-key</i>: <i>arg0.toString()</i>, ..., <i>argn.toString</i></code>
     * 
     * @return A dummy message catalog.
     */
    public static ConnectorMessages createDummyMessages() {
        return getSpi().createDummyMessages();
    }

    public static List<ConnectorObject> searchToList(SearchApiOp search,
            ObjectClass oclass, Filter filter) {
        return searchToList(search, oclass, filter, null);
    }

    public static List<ConnectorObject> searchToList(SearchApiOp search,
            ObjectClass oclass, Filter filter, OperationOptions options) {
        ToListResultsHandler handler = new ToListResultsHandler();
        search.search(oclass, filter, handler, options);
        return handler.getObjects();
    }

    /**
     * Performs a raw, unfiltered search at the SPI level, eliminating
     * duplicates from the result set.
     * 
     * @param search
     *            The search SPI
     * @param oclass
     *            The object class - passed through to connector so it may be
     *            null if the connecor allowing it to be null. (This is
     *            convenient for unit tests, but will not be the case in
     *            general)
     * @param filter
     *            The filter to search on
     * @return The list of results.
     */
    public static List<ConnectorObject> searchToList(SearchOp<?> search,
            ObjectClass oclass, Filter filter) {
        return searchToList(search, oclass, filter, null);
    }

    /**
     * Performs a raw, unfiltered search at the SPI level, eliminating
     * duplicates from the result set.
     * 
     * @param search
     *            The search SPI
     * @param oclass
     *            The object class - passed through to connector so it may be
     *            null if the connecor allowing it to be null. (This is
     *            convenient for unit tests, but will not be the case in
     *            general)
     * @param filter
     *            The filter to search on
     * @param options
     *            The options - may be null - will be cast to an empty
     *            OperationOptions
     * @return The list of results.
     */
    public static List<ConnectorObject> searchToList(SearchOp<?> search,
            ObjectClass oclass, Filter filter, OperationOptions options) {
        ToListResultsHandler handler = new ToListResultsHandler();
        search(search, oclass, filter, handler, options);
        return handler.getObjects();
    }

    /**
     * Performs a raw, unfiltered search at the SPI level, eliminating
     * duplicates from the result set.
     * 
     * @param search
     *            The search SPI
     * @param oclass
     *            The object class - passed through to connector so it may be
     *            null if the connecor allowing it to be null. (This is
     *            convenient for unit tests, but will not be the case in
     *            general)
     * @param filter
     *            The filter to search on
     * @param handler
     *            The result handler
     * @param options
     *            The options - may be null - will be cast to an empty
     *            OperationOptions
     */
    public static void search(SearchOp<?> search, final ObjectClass oclass,
            final Filter filter, ResultsHandler handler,
            OperationOptions options) {
        getSpi().search(search, oclass, filter, handler, options);
    }

    // At some point we might make this pluggable, but for now, hard-code
    private static final String IMPL_NAME = "org.identityconnectors.framework.impl.test.TestHelpersImpl";

    private static TestHelpersSpi _instance;

    /**
     * Returns the instance of the SPI implementation.
     * 
     * @return The instance of the SPI implementation.
     */
    private static synchronized TestHelpersSpi getSpi() {
        if (_instance == null) {
            try {
                Class<?> clazz = Class.forName(IMPL_NAME);
                Object object = clazz.newInstance();
                _instance = TestHelpersSpi.class.cast(object);
            } catch (Exception e) {
                throw ConnectorException.wrap(e);
            }
        }
        return _instance;
    }

    /**
     * Load properties in the following order to black box testing.
     */
    private static Map<?, ?> _properties;
    public static final String GLOBAL_PROPS = "connectors.properties";
    public static final String BUNDLE_PROPS = "build.properties";

    /**
     * Loads the properties files just like the connector 'build' environment
     * the only exception is properties in the 'global' file are filtered for
     * those properties that prefix the project's name.
     * 
     * @param name
     *            Key to the properties..
     * @param def
     *            default value to return if the key does not exist
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
        for (Entry<?, ?> entry : _properties.entrySet()) {
            Object value = entry.getValue();
            // Hashtable doesn't take null values.
            if (value != null) {
                ret.put(entry.getKey(), value.toString());
            }
        }
        return ret;
    }

    private static Map<?, ?> loadProjectProperties() {
        final String ERR = "Unable to load optional properties file: {0}";
        final String GERR = "Unable to load configuration groovy file: {0}";
        final String BERR = "Unable to load bundle properties file: {0}";
        final char FS = File.separatorChar;
        final String CONNECTORS_DIR = System.getProperty("user.home") + FS + ".connectors";
        final String CONFIG_DIR = (new File(".")).getAbsolutePath() + FS + "config";
        final String BUILD_GROOVY = "build.groovy";
        Map<?, ?> props = null;
        Map<Object, Object> ret = new HashMap<Object, Object>();
        String fName = null;

        // load global properties (if present)
        try {
            fName = CONNECTORS_DIR + FS + GLOBAL_PROPS;
            props = IOUtil.loadPropertiesFile(fName);
            ret.putAll(props);
        } catch (IOException e) {
            LOG.info(ERR, fName);
        }

        //load the private bundle properties file (if present)
        try {
            props = IOUtil.loadPropertiesFile(BUNDLE_PROPS);
            ret.putAll(props);
        } catch (IOException e) {
            LOG.error(BERR, BUNDLE_PROPS);
        }

        // load the project (public) configuration groovy file
        try {
            fName = CONFIG_DIR + FS + BUILD_GROOVY;
            props = loadGroovyConfigFile(IOUtil.makeURL(null, fName));
            ret.putAll(props);
        } catch (IOException e) {
            LOG.info(GERR, fName);
        }
        String cfg = System.getProperty("testConfig", null);

        // load the project (public) configuration-specific configuration groovy file
        if (StringUtil.isNotBlank(cfg) && !"default".equals(cfg)) {
            try {
                fName = CONFIG_DIR + FS + cfg + FS + BUILD_GROOVY;
                props = loadGroovyConfigFile(IOUtil.makeURL(null, fName));
                ret.putAll(props);
            } catch (IOException e) {
                LOG.info(GERR, fName);
            }
        }

        String prjName = System.getProperty("project.name", null);
        if (StringUtil.isNotBlank(prjName)) {
            fName = null;
            //load the private bundle configuration groovy file (if present)
            try {
                fName = CONNECTORS_DIR + FS + prjName + FS + BUILD_GROOVY;
                props = loadGroovyConfigFile(IOUtil.makeURL(null, fName));
                ret.putAll(props);
            } catch (IOException e) {
                LOG.info(GERR, fName);
            }

            if (StringUtil.isNotBlank(cfg) && !"default".equals(cfg)) {
                //load the configuration-specific configuration groovy file (if present)
                try {
                    fName = CONNECTORS_DIR + FS + prjName + FS + cfg + FS + BUILD_GROOVY;
                    props = loadGroovyConfigFile(IOUtil.makeURL(null, fName));
                    ret.putAll(props);
                } catch (IOException e) {
                    LOG.info(GERR, fName);
                }
            }
        }
        // load the system properties
        ret.putAll(System.getProperties());
        return ret;
    }

    static Map<?, ?> loadGroovyConfigFile(URL url) {
        try {
            Class<?> slurper = Class.forName("groovy.util.ConfigSlurper");
            Class<?> configObject = Class.forName("groovy.util.ConfigObject");
            Object slurpInstance = slurper.newInstance();
            Method parse = slurper.getMethod("parse", URL.class);
            Object config = parse.invoke(slurpInstance, url);
            Method toProps = configObject.getMethod("flatten");
            Object result = toProps.invoke(config);
            return (Map<?, ?>) result;
        } catch (Exception e) {
            LOG.error(e, "Could not load Groovy objects: {0}", e.getMessage());
            return null;
        }
    }

}
