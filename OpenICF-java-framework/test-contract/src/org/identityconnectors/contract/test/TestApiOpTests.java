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


import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.TestApiOp;
import org.identityconnectors.framework.common.objects.ObjectClass;
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
        if (ConnectorHelper.operationSupported(getConnectorFacade(), getAPIOperation())) {
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
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return TestApiOp.class;
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
        Integer i = Integer.parseInt((String)getDataProvider().getTestSuiteAttribute(Integer.class.getName(), "iterations.Validate"));

        List<Object[]> list = new ArrayList<Object[]>();
        for (Integer j=1; j<=i; j++) {
            list.add(new Object[] {j});
        }
        
        return list;
    }

}
