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
package org.identityconnectors.contract.data;

import java.util.Set;

import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
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
     * @param dataTypeName
     * @param name
     * @param componentName
     * @param sequenceNumber
     * @param isMultivalue switch between single and multivalue query
     * @return 
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public Object get(Class dataTypeName, String name,
            String componentName, int sequenceNumber, boolean isMultivalue) throws ObjectNotFoundException;
    
    /**
     * Gets data value by the specified parameters
     * 
     * @param dataTypeName
     * @param name
     * @param componentName
     * @return
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public Object get(Class dataTypeName, String name,
            String componentName) throws ObjectNotFoundException;

    /**
     * Gets data value by the specified parameters
     * 
     * @param name
     * @param componentName
     * @param sequenceNumber
     * @return
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public String getString(String name,
            String componentName, int sequenceNumber) throws ObjectNotFoundException;
    
    /**
     * Gets data value by the specified parameters
     * 
     * @param name
     * @param componentName
     * @return
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public String getString(String name,
            String componentName) throws ObjectNotFoundException;
    
    /**
     * Gets data value by the specified parameters
     * @param propName
     * 
     * @return
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public Object getConnectorAttribute(String propName) throws ObjectNotFoundException;
    
    /**
     * Gets test suite attribute
     * @param propName
     * 
     * @return
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public Object getTestSuiteAttribute(String propName) throws ObjectNotFoundException;
    
    /**
     * Gets test suite attribute
     * @param propName
     * 
     * @return
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException
     */
    public Object getTestSuiteAttribute(String propName, 
            String testName) throws ObjectNotFoundException;
    
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
     * @param sequenceNumber
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
    public Object generate(String pattern, Class clazz);
    
    /**
     * generates a random string dynamically.
     * {@link DataProvider#generate(String, Class)}
     */
    public Object generate(String pattern);
    
    /* ***************** ADDITIONAL PROPERTY UTILS ************** */
    public void loadConfiguration(final String configName, Configuration cfg)
        throws NoSuchFieldException, IllegalAccessException;
    
    public Set<Attribute> getAttributeSet(final String propertySetName);
    
    /* ************************************************* */
    
    /** free the allocated resources */
    public void dispose();
    
}
