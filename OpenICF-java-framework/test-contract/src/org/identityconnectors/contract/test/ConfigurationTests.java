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
package org.identityconnectors.contract.test;

import java.util.Arrays;
import java.util.List;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.data.DataProvider;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.spi.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link Configuration} of the Connector Under Test
 * 
 * @author Tomas Knappek
 */
public final class ConfigurationTests {

    private static final Log LOG = Log.getLog(ConfigurationTests.class);
    private static final List <String> ALLOWED_TYPES = Arrays.asList(new String[] {
        "java.lang.String", 
        "[Ljava.lang.String",
        "java.lang.Boolean",
        "[Ljava.lang.Boolean",
        "java.lang.Integer",
        "[Ljava.lang.Integer",
        "java.lang.Long",
        "[Ljava.lang.Long",
        "java.lang.Float",
        "[Ljava.lang.Float",
        "java.lang.Double",
        "[Ljava.lang.Double",
        "java.net.URI",
        "[Ljava.net.URI",
        "org.identityconnectors.common.security.GuardedString"
        });
    
    
    private ConfigurationProperties _configProperties = null;

    /**
     * Initialize the unit test
     */
    @Before
    public void init() throws Exception {        
        ConnectorHelper connHelper = new ConnectorHelper();
        DataProvider dataProvider = connHelper.createDataProvider();
        _configProperties = connHelper.getConfigurationProperties(dataProvider);                
    }

    /**
     * Free up the resources
     */
    @After
    public void dispose() {
        _configProperties = null;
    }

    /**
     * Unit test for checking if the {@link Configuration} property type is supported
     */
    @Test
    public void testPropertiesType() throws Exception {

        assertNotNull(_configProperties);
        
        List<String> propertyNames = _configProperties.getPropertyNames();
        assertNotNull(propertyNames);
        
        //go through the properties and check the type
        for (String propertyName : propertyNames) {
            ConfigurationProperty property =  _configProperties.getProperty(propertyName);
            assertNotNull(property);
            
            String type = property.getType().getName();
            LOG.ok("Property: ''{0}'' type ''{1}''", property.getName(), type);
            assertTrue("Type " + type + " not allowed in configuration!", ALLOWED_TYPES.contains(type));
        }
    }
}
