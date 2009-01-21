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
package org.identityconnectors.contract.test;


import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.TestApiOp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


/**
 * Contract test of {@link TestApiOp}. Positive test for test() is performed
 * everytime connector facade is created and connector supports the operation.
 * Test uses the same configuration as ValidateApiOpTest.
 * 
 * Currently there is not ability in API to test contract in case connection is lost.
 */
@RunWith(Parameterized.class)
public class TestApiOpTests extends AbstractSimpleTest {
    
    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(TestApiOpTests.class);
    
    private int _iterationNumber;
    
    public TestApiOpTests(final int iterationNumber) {
        super();

        _iterationNumber = iterationNumber;
    }
    
    /**
     * Tests test() with configuration that should NOT be correct.
     */
    @Test
    public void testTestFail() {
        // run test only in case operation is supported
        if (ConnectorHelper.operationsSupported(getConnectorFacade(), getAPIOperations())) {
            // create connector with invalid configuration            
            _connFacade = ConnectorHelper.createConnectorFacadeWithWrongConfiguration(getDataProvider(), getIterationNumber());
            try {
                // should throw RuntimeException
                getConnectorFacade().test();
                fail("test() should throw RuntimeException because configuration should be invalid.");
            }
            catch (RuntimeException ex) {
                // expected
            }
        }
        else {
            LOG.info("--------------------------------");
            LOG.info("Skipping test ''testTestFail''.");
            LOG.info("--------------------------------");
        }
    }
    
    /**
     * {@inheritDoc}     
     */
    @Override
    public Set<Class<? extends APIOperation>> getAPIOperations() {
        Set<Class<? extends APIOperation>> s = new HashSet<Class<? extends APIOperation>>();
        // list of required operations by this test:
        s.add(TestApiOp.class);
        return s;
    }

    /**
     * Returns this iteration number.
     * @return Iteration number.
     */
    public int getIterationNumber() {
        return _iterationNumber;
    }

    /**
     * Parameters to be passed to junit test - iteration number.
     * @return List of arrays of parameters for this test.
     */
    @Parameters
    public static List<Object[]> data() {
        Integer i = Integer.parseInt((String)getDataProvider().getTestSuiteAttribute("iterations", "Validate"));

        List<Object[]> list = new ArrayList<Object[]>();
        for (Integer j=1; j<=i; j++) {
            list.add(new Object[] {j});
        }
        
        return list;
    }

}
