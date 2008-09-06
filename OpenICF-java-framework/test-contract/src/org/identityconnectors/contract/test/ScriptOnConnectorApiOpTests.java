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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.ScriptOnConnectorApiOp;
import org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.junit.Test;


/**
 * Contract test of {@link ScriptOnConnectorApiOp} operation.
 * 
 * @author Zdenek Louzensky
 */
public class ScriptOnConnectorApiOpTests extends AbstractSimpleTest {

    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(ScriptOnConnectorApiOpTests.class);

    private static final String TEST_NAME="ScriptOnConnector";
    private static final String LANGUAGE_PROP_PREFIX = "language." + TEST_NAME;
    private static final String SCRIPT_PROP_PREFIX = "script." + TEST_NAME;
    private static final String ARGUMENTS_PROP_PREFIX = "arguments." + TEST_NAME;
    private static final String RESULT_PROP_PREFIX = "result." + TEST_NAME;
    
    /**
     * Returns 
     */
    public Class<? extends APIOperation> getAPIOperation() {
        return ScriptOnConnectorApiOp.class;
    }
    
    /**
     * Tests running a script with correct values from property file.
     */
    @Test
    public void testRunScript() {
        // run test only in case operation is supported
        if (getHelper().operationSupported(getConnectorFacade(), getAPIOperation())) {
            try {
                // get test properties - optional
                // if a property is not found test is skipped
                String language = (String) getDataProvider().getTestSuiteAttribute(
                        String.class.getName(), LANGUAGE_PROP_PREFIX);
                String script = (String) getDataProvider().getTestSuiteAttribute(
                        String.class.getName(), SCRIPT_PROP_PREFIX);
                Map<String, Object> arguments = (Map<String, Object>) getDataProvider()
                        .getTestSuiteAttribute(Map.class.getName(), ARGUMENTS_PROP_PREFIX);
                Object expResult = getDataProvider().getTestSuiteAttribute(Object.class.getName(),
                        RESULT_PROP_PREFIX);

                // run the script
                Object result = getConnectorFacade().runScriptOnConnector(
                        new ScriptContext(language, script, arguments),
                        getOperationOptionsByOp(ScriptOnConnectorApiOp.class));

                // check that returned result was expected
                final String MSG = "Script result was unexpected, expected: '%s', returned: '%s'.";
                assertEquals(String.format(MSG, expResult, result), expResult, result);
            } catch (ObjectNotFoundException ex) {
                // ok - properties were not provided - test is skipped
                LOG.info("Test properties not set, skipping the test " + TEST_NAME);
            }
        }
    }

    /**
     * Tests running a script with unknown language.
     */
    @Test
    public void testRunScriptFailUnknownLanguage() {
        // run test only in case operation is supported
        if (getHelper().operationSupported(getConnectorFacade(), getAPIOperation())) {
            try {
                getConnectorFacade().runScriptOnConnector(
                        new ScriptContext("NONEXISTING LANGUAGE", "script",
                                new HashMap<String, Object>()), null);
                fail("Script language is not supported, should throw an exception.");
            } catch (RuntimeException ex) {
                // expected
            }
        }
    }

    /**
     * Tests running a script with empty script text.
     */
    @Test
    public void testRunScriptFailEmptyScriptText() {
        // run test only in case operation is supported
        if (getHelper().operationSupported(getConnectorFacade(), getAPIOperation())) {
            try {
                getConnectorFacade().runScriptOnConnector(
                        new ScriptContext("LANGUAGE", "", new HashMap<String, Object>()), null);
                fail("Script text is empty and script language is not probably supported, should throw an exception.");
            } catch (RuntimeException ex) {
                // expected
            }
        }
    }

}
