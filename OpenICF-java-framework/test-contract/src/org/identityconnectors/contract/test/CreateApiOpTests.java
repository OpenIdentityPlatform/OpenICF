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

import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Contract test of {@link CreateApiOp} operation.
 */
@RunWith(Parameterized.class)
public class CreateApiOpTests extends ObjectClassRunner {
    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(CreateApiOpTests.class);
    private static final String TEST_NAME = "Create";

    public CreateApiOpTests(ObjectClass oclass) {
        super(oclass);
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return CreateApiOp.class;
    }
        
    /**
     * {@inheritDoc}
     */
    @Override
    public void testRun() throws ObjectNotFoundException {
        
        Uid uid = null;
        
        try {

            Set<Attribute> attrs = getHelper().getAttributes(getDataProvider(),
                    getObjectClassInfo(), getTestName(), 0, true);
            
            // should throw UnsupportedObjectClass if not supported
            uid = getConnectorFacade().create(getObjectClass(), attrs,
                    getOperationOptionsByOp(CreateApiOp.class));            

            // get the user to make sure it exists now
            ConnectorObject obj = getConnectorFacade().getObject(getObjectClass(), uid,
                    getOperationOptionsByOp(GetApiOp.class));

            assertNotNull("Unable to retrieve newly created object", obj);

            // compare requested attributes to retrieved attributes
            getHelper().checkObject(getObjectClassInfo(), obj, attrs);
        } finally {
            if (uid != null) {
                // delete the object
                getConnectorFacade().delete(getSupportedObjectClass(), uid,
                        getOperationOptionsByOp(DeleteApiOp.class));
            }
        }
    }
    
    /**
     * Tests create method with invalid Attribute, RuntimeException is expected
     */
    @Test(expected=java.lang.RuntimeException.class)
    public void testCreateFailUnsupportedAttribute() {
        
        //create not supported Attribute Set
        Set<Attribute> attrs = null;
        attrs.add(AttributeBuilder.build("NONEXISTINGATTRIBUTE"));
        
        //do the create call
        //note - the ObjectClassInfo is always supported
        getConnectorFacade().create(getSupportedObjectClass(), attrs, null);
    }
    
    /**
     * Tests create twice with the same attributes. It should return different
     * Uids.
     */
    @Test
    public void testCreateWithSameAttributes() throws Exception {
        if (getHelper().operationSupported(getConnectorFacade(), getAPIOperation())) {
            Uid uid1 = null;
            Uid uid2 = null;

            try {

                Set<Attribute> attrs = getHelper().getAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), 1, true);

                // ObjectClassInfo is always supported
                uid1 = getConnectorFacade().create(getSupportedObjectClass(), attrs,
                        getOperationOptionsByOp(CreateApiOp.class));

                // get the object to make sure it exist now
                ConnectorObject obj1 = getConnectorFacade().getObject(getSupportedObjectClass(),
                        uid1, getOperationOptionsByOp(GetApiOp.class));
                assertNotNull("Unable to retrieve newly created object", obj1);

                // compare requested attributes to retrieved attributes
                getHelper().checkObject(getObjectClassInfo(), obj1, attrs);

                /* SECOND CREATE: */

                // should return different uid or throw
                uid2 = getConnectorFacade().create(getSupportedObjectClass(), attrs, getOperationOptionsByOp(CreateApiOp.class));
                assertFalse("Create returned the same Uid as by previous create.", uid1
                        .equals(uid2));

                // get the object to make sure it exist now
                ConnectorObject obj2 = getConnectorFacade().getObject(getSupportedObjectClass(),
                        uid2, getOperationOptionsByOp(GetApiOp.class));
                assertNotNull("Unable to retrieve newly created object", obj2);

                // compare requested attributes to retrieved attributes
                getHelper().checkObject(getObjectClassInfo(), obj2, attrs);
            } catch (RuntimeException ex) {
                // ok - second create could throw this exception
            } finally {
                if (uid1 != null) {
                    // delete the object
                    getHelper().deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid1,
                            false, getOperationOptionsByOp(DeleteApiOp.class));
                }
                if (uid2 != null) {
                    // delete the object
                    getHelper().deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid2,
                            false, getOperationOptionsByOp(DeleteApiOp.class));
                }
            }
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
