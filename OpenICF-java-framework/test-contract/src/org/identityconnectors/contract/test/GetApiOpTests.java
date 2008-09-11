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

import static org.junit.Assert.assertNotNull;

import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


/**
 * Contract test of {@link GetApiOp} 
 */
@RunWith(Parameterized.class)
public class GetApiOpTests extends ObjectClassRunner {
    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(GetApiOpTests.class);
    private static final String TEST_NAME = "Get";

    public GetApiOpTests(ObjectClass oclass) {
        super(oclass);
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return GetApiOp.class;
    }

    /**
     * {@inheritDoc}      
     */
    @Override
    public void testRun() {
        ConnectorObject obj = null;
        Uid uid = null;
        
        try {
            Set<Attribute> requestedAttributes = ConnectorHelper.getAttributes(
                    getDataProvider(), getObjectClassInfo(), getTestName(), 0, true);
            // object class is always supported
            uid = getConnectorFacade().create(getSupportedObjectClass(),
                    requestedAttributes, getOperationOptionsByOp(CreateApiOp.class));
            assertNotNull(
                    "Unable to perform get test because object to be get cannot be created",
                    uid);

            // retrieve by uid
            obj = getConnectorFacade().getObject(getObjectClass(), uid, getOperationOptionsByOp(GetApiOp.class));
            assertNotNull("Unable to get object by uid", obj);

            ConnectorHelper.checkObject(getObjectClassInfo(), obj, requestedAttributes);

            // retrieve by name
            Name name = obj.getName();
            obj = ConnectorHelper.findObjectByName(getConnectorFacade(), getObjectClass(),
                    name.getNameValue(), getOperationOptionsByOp(SearchApiOp.class));
            assertNotNull("Unable to get object by name", obj);

            ConnectorHelper.checkObject(getObjectClassInfo(), obj, requestedAttributes);

            // get by other attributes???

        } finally {
            // finally ... get rid of the object
            ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid, false,
                    getOperationOptionsByOp(DeleteApiOp.class));
        }
    }
    
    /**
     * {@inheritDoc}     
     */
    @Override
    public String getTestName() {
        return TEST_NAME;
    }

}
