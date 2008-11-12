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

import org.identityconnectors.contract.data.DataProvider;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;


/**
 * Base class of all contract tests.
 * 
 * @author Zdenek Louzensky
 */
public abstract class AbstractSimpleTest {

    private static DataProvider _dataProvider;
    
    protected ConnectorFacade _connFacade;

    /**
     * Dispose the test environment, do the cleanup.
     * Disposal done once after all test methods in a class are invoked.
     */
    @AfterClass
    public static void disposeOnce() {
        _dataProvider.dispose();       
        _dataProvider = null;        
    }
    
    /**
     * Initialize the environment needed to run the test
     */
    @Before
    public void init() {        
        _connFacade = ConnectorHelper.createConnectorFacade(getDataProvider());       
    }

    /**
     * Dispose the test environment, do the cleanup
     */
    @After
    public void dispose() {
        _connFacade = null;
    }

    /**
     * Ask the subclass for the {@link APIOperation}.
     */
    public abstract Class<? extends APIOperation> getAPIOperation();
    
    //=================================================================
    // Helper methods
    //=================================================================
    /**
     * Gets preconfigured {@link DataProvider} instance
     * @return {@link DataProvider}
     */
    public synchronized static DataProvider getDataProvider() {
        if (_dataProvider == null) {
            _dataProvider = ConnectorHelper.createDataProvider();
        }
        return _dataProvider;
    }
    
    /**
     * Always need a {@link ConnectorFacade}.
     */
    public ConnectorFacade getConnectorFacade() {
        return _connFacade;
    }
    
    /**
     * Gets OperationOptions suitable for specified operation.
     * Should be used in all tests requiring OperationOptions unless it's special case.
     * @return {@link OperationOptions}
     */
    public OperationOptions getOperationOptionsByOp(Class<? extends APIOperation> clazz) {
        return null;
    }
}
