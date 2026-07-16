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
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 *
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.contract.data;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.spi.Configuration;

/**
 * DataProvider is a facility used for getting (reading/generating) data for
 * Contract test suite.
 *
 * @author Dan Vernon
 */
public interface DataProvider {

    /**
     * Gets data value by the specified parameters
     *
     * @param dataTypeName the Java type that the returned value should be converted to
     * @param name the name of the data item to look up
     * @param componentName the name of the test component (object class, test suite, ...)
     *            the data belongs to
     * @param sequenceNumber the iteration index to use when several values are defined
     *            for the same name (e.g. i0, i1, i2, ...)
     * @param isMultivalue switch between single and multivalue query
     * @return the data value matching the given parameters, converted to {@code dataTypeName}
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no data value matching the given parameters could be found
     */
    public Object get(Class<?> dataTypeName, String name,
            String componentName, int sequenceNumber, boolean isMultivalue);

    /**
     * Gets data value by the specified parameters
     *
     * @param dataTypeName the Java type that the returned value should be converted to
     * @param name the name of the data item to look up
     * @param componentName the name of the test component (object class, test suite, ...)
     *            the data belongs to
     * @return the data value matching the given parameters, converted to {@code dataTypeName}
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no data value matching the given parameters could be found
     */
    public Object get(Class<?> dataTypeName, String name,
            String componentName);

    /**
     * Gets data value by the specified parameters
     *
     * @param name the name of the data item to look up
     * @param componentName the name of the test component (object class, test suite, ...)
     *            the data belongs to
     * @param sequenceNumber the iteration index to use when several values are defined
     *            for the same name (e.g. i0, i1, i2, ...)
     * @return the {@code String} value matching the given parameters
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no data value matching the given parameters could be found
     */
    public String getString(String name,
            String componentName, int sequenceNumber);

    /**
     * Gets data value by the specified parameters
     *
     * @param name the name of the data item to look up
     * @param componentName the name of the test component (object class, test suite, ...)
     *            the data belongs to
     * @return the {@code String} value matching the given parameters
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no data value matching the given parameters could be found
     */
    public String getString(String name,
            String componentName);

    /**
     * Gets data value by the specified parameters
     * @param propName the name of the connector configuration property to look up,
     *            relative to the {@code connector} property prefix
     *
     * @return the value configured for the given connector attribute
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no value is configured for the given attribute name
     */
    public Object getConnectorAttribute(String propName);

    /**
     * Gets test suite attribute
     * @param propName the name of the test suite property to look up, relative to the
     *            {@code testsuite} property prefix
     *
     * @return the value configured for the given test suite attribute
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no value is configured for the given attribute name
     */
    public Object getTestSuiteAttribute(String propName);

    /**
     * Gets test suite attribute
     * @param propName the name of the test suite property to look up, relative to
     *            {@code testsuite.testName}
     * @param testName the name of the test whose attributes are queried
     *
     * @return the value configured for the given test suite attribute
     * @throws org.identityconnectors.contract.exceptions.ObjectNotFoundException
     *             if no value is configured for the given attribute name
     */
    public Object getTestSuiteAttribute(String propName,
            String testName);

    /* *********** METHODS FOR UNIT TESTS ************** */

    /**
     * Acquire a property value for given name
     */
    public Object get(String name);

    /**
     * Aquire a property value marked with given iteration,
     * for example i1.testProperty
     *
     * @param name the suffix
     * @param sequenceNumber the iteration index prepended to {@code name} (e.g. 1 for i1.testProperty)
     * @return the property value
     */
    public Object get(String name, int sequenceNumber);

    /**
     * <p>
     * Random generator uses a <strong>pattern</strong> to generate a random
     * sequence based on given pattern.
     * </p>
     * <p>
     * the supported characters are (can appear in pattern string):
     * </p>
     * <ul>
     * <li># - numeric</li>
     * <li>a - lowercase letter</li>
     * <li>A - uppercase letter</li>
     * <li>? - lowercase and uppercase letter</li>
     * <li>. - any character</li>
     * </ul>
     * <p>
     * Any other character inside the pattern is directly printed to the output.
     * </p>
     * <p>
     * Backslash is used to escape any character. For instance pattern
     * "###\\.##" prints a floating point random number
     * </p>
     *
     * @param pattern the pattern for generation
     * @param clazz the type of returned random object
     * @return randomly generated object with content based on given type.
     */
    public Object generate(String pattern, Class<?> clazz);

    /**
     * generates a random string dynamically.
     * {@link DataProvider#generate(String, Class)}
     */
    public Object generate(String pattern);

    /* ***************** ADDITIONAL PROPERTY UTILS ************** */
    /**
     * adds to 'cfg' the complete map defined by property 'propertyName'. For every entry
     * of the submap, the setter method whose name derives from the entry's key is looked
     * up on {@code cfg} and invoked reflectively with the entry's value; if the setter
     * cannot be found, is not accessible, or throws while being invoked, the failure is
     * wrapped and re-thrown as an unchecked exception.
     *
     * @param propertyName
     *            the name of property which represents the submap that will be
     *            converted to configuration
     * @param cfg
     *            the configuration that will be updated by information from
     *            property 'propertyName'
     *            <p>
     *            Sample usage:<br>
     *
     * <pre>
     *     static final String DEFAULT_CONFIGURATINON = "configuration.init"
     *
     *     // attempt to create the database in the directory..
     *     config = new ConnectorConfiguration();
     *
     *     // LOAD THE submap in 'configuration' <strong>prefix</strong> to 'config' object.
     *     dataProvider.loadConfiguration(DEFAULT_CONFIGURATINON, config);
     * //////// The groovy configuration
     *
     *     // account configurations
     *     configuration{
     *       init.driver="foo"
     *       init.hostName="bar"
     *       init.port="boo"
     *     }
     * </pre>
     * @throws SecurityException if reflective access to the configuration's setter
     *             method is denied
     */
    public void loadConfiguration(final String propertyName, Configuration cfg);

    /**
     * converts the given property submap to Attribute set.
     *
     * @param propertySetName the property that marks the submap for conversion.
     * @return the converted attribute set
     * <p>
     * Sample usage:
     * <pre>
     *  createAttrs = dataProvider.getAttributeSet("account.create");
     *
     *  //////// The groovy configuration
     *
     *     // account sets
     *     account{
     *       create.driver="foo"
     *       create.hostName="bar"
     *       create.port="boo"
     *
     *       update.driver="foo2"
     *       update.hostName="bar2"
     *       update.port="boo2"
     *     }
     * </pre>
     */
    public Set<Attribute> getAttributeSet(final String propertySetName);

    /* ************************************************* */

    /** free the allocated resources */
    public void dispose();

}
