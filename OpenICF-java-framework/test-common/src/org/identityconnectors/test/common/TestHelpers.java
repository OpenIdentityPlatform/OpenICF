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
package org.identityconnectors.test.common;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Properties;

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
    private static Properties _properties;
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
        ret.putAll(_properties);
        return ret;
    }

    private static Properties loadProjectProperties() {
        final String ERR = "Unable to load optional properties file: {0}";
        final String CONNECTORS_DIR = System.getProperty("user.home") + "/.connectors/";
        Properties props = null;
        Properties ret = new Properties();
        // load global properties (if present)
        try {
            props = IOUtil.loadPropertiesFile(CONNECTORS_DIR + GLOBAL_PROPS);
            ret.putAll(props);
        } catch (IOException e) {
            LOG.info(ERR, CONNECTORS_DIR + GLOBAL_PROPS);
        }

        // load the local (public) properties file
        try {
            props = IOUtil.loadPropertiesFile(BUNDLE_PROPS);
            ret.putAll(props);
        } catch (IOException e) {
            LOG.error("Bundle properties file could not be found: {0}",
                    BUNDLE_PROPS);
        }

        String prjName = System.getProperty("project.name", null);
        if (StringUtil.isNotBlank(prjName)) {
            String fName = null;
            //load the private bundle properties file (if present)
            try {
                fName = CONNECTORS_DIR + prjName + "/build.groovy";
                props = loadGroovyConfigFile(fName);
                ret.putAll(props);
            } catch (IOException e) {
                LOG.info(ERR, fName);
            }

            String cfg = System.getProperty("configuration", null);
            if (StringUtil.isNotBlank(cfg) && !"default".equals(cfg)) {
                //load the configuration-specific properties file (if present)
                try {
                    fName = CONNECTORS_DIR + prjName + "/" + cfg + "/build.groovy";
                    props = loadGroovyConfigFile(fName);
                    ret.putAll(props);
                } catch (IOException e) {
                    LOG.info(ERR, fName);
                }
            }
        }
        // load the system properties
        ret.putAll(System.getProperties());
        return ret;
    }

    private static Properties loadGroovyConfigFile(String fileName) throws IOException{
        try {
            Class<?> slurper = Class.forName("groovy.util.ConfigSlurper");
            Class<?> configObject = Class.forName("groovy.util.ConfigObject");
            Object slurpInstance = slurper.newInstance();
            Method parse = slurper.getMethod("parse", URL.class);
            Object config = parse.invoke(slurpInstance, IOUtil.makeURL(null, fileName));
            Method toProps = configObject.getMethod("toProperties");
            Object result = toProps.invoke(config);
            return (Properties) result;
        } catch (IOException e) { 
            throw e;
        } catch (Exception e) {
            LOG.error(e, "Could not load Groovy objects: {0}", e.getMessage());
            return null;
        } 
    }

}
