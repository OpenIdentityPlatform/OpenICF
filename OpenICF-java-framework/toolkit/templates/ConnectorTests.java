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
package <%= packageName %>;

import java.util.*;

import org.junit.*;
import junit.framework.Assert;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.test.common.TestHelpers;

/**
 * Attempts to test the {@link <%= resourceName %>Connector} with the framework.
 * 
 * @author $userName
 * @version 1.0
 * @since 1.0
 */
public class <%= resourceName %>ConnectorTests { 

    /*
     * Example test properties. On your computer, these are defined in: 
     * <%= System.getProperty("user.home") + "/.connector-" + resourceName.toLowerCase() %>.properties (your "user.home" directory). 
     */
    private static final String HOST = TestHelpers.getProperty("HOST", null);
    private static final String LOGIN = TestHelpers.getProperty("LOGIN", null);
    private static final String PASSWORD = TestHelpers.getProperty("PASSWORD", null);

    //set up logging
    private static final Log log = Log.getLog(<%= resourceName %>ConnectorTests.class);
    
    @BeforeClass
    public static void setUp() { 
        Assert.assertNotNull(HOST);
        Assert.assertNotNull(LOGIN);
        Assert.assertNotNull(PASSWORD);
        
        //
        //other setup work to do before running tests
        //
    }
    
    @AfterClass
    public static void tearDown() {
        //
        //clean up resources
        //
    }
    
    @Test
    public void exampleTest1() {
        log.info("Running Test 1...");
        //You can use TestHelpers to do some of the boilerplate work in running a search
        //TestHelpers.search(theConnector, ObjectClass.ACCOUNT, filter, handler, null);
    }
    
    @Test
    public void exampleTest2() {
        log.info("Running Test 2...");
        //Another example using TestHelpers
        //List<ConnectorObject> results = TestHelpers.searchToList(theConnector, ObjectClass.GROUP, filter);
    }
 
}
