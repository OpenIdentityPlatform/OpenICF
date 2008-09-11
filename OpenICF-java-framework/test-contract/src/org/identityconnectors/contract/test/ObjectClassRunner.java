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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;


import java.util.Iterator;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Simple base class that will run through all the {@link ObjectClass}s.
 */
public abstract class ObjectClassRunner extends AbstractSimpleTest {

    private final ObjectClass _objectClass;
    private ObjectClassInfo _objectClassInfo;
    private ObjectClass _supportedObjectClass;    
    private boolean _ocSupported;
    

    /**
     * Base class for running {@link ObjectClass} across a test.
     */
    public ObjectClassRunner(ObjectClass oclass) {
        _objectClass = oclass;
    }
    
    /**
     * Initialize the environment needed to run the test
     */
    @Before
    public void init() {
        super.init();                        
        Set<ObjectClassInfo> oinfos = getSchema().getObjectClassInfo();
        for (Iterator<ObjectClassInfo> it = oinfos.iterator(); it.hasNext();) {
            _objectClassInfo = it.next();
            _supportedObjectClass = ConnectorHelper.getObjectClassFromObjectClassInfo(_objectClassInfo);
            if (_objectClassInfo.getType().equals(getObjectClass().getObjectClassValue())){                
                _ocSupported = true;
                break;
            }


        }
    }
    
    /**
     * Dispose the test environment, do the cleanup
     */
    @After
    public void dispose() {        
        _objectClassInfo = null;
        super.dispose();
    }
    
    /**
     * Main contract test entry point, it calls {@link #testRun()} method
     * in configured number of iterations, runs the iteration only if the 
     * operation is supported by the connector
     */
    @Test
    public void testContract() {
        //run the contract test for supported operation only
        if (ConnectorHelper.operationSupported(getConnectorFacade(), getAPIOperation())) {
            try {
                testRun();
                if (!isObjectClassSupported()) {
                    //should throw RuntimeException
                    fail("ObjectClass " + getObjectClass() + " is not supported, must" +
                            " throw RuntimeException");
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                if (isObjectClassSupported()) {
                    e.printStackTrace();
                    fail("Unexpected RuntimeException thrown: " + e);
                }
            }

        }
    }

    
    /**
     * This method will be called configured number of times
     */
    public abstract void testRun();
    
    /**
     * Return all the base {@link ObjectClass}s.
     */
    @Parameters
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][] { { ObjectClass.ACCOUNT },
                { ObjectClass.GROUP }, { ObjectClass.ORGANIZATION },
                { ObjectClass.PERSON }, { new ObjectClass("NONEXISTING") } });
    }

    /**
     * Returns object class which is currently tested.
     */
    public ObjectClass getObjectClass() {
        return _objectClass;
    }
    
    /**
     * Always returns supported object class by connector operation.
     * If currently tested object class is supported then is returned otherwise not.
     * @return
     */
    public ObjectClass getSupportedObjectClass() {
        return _supportedObjectClass;
    }

    /**
     * Ask the subclass for the {@link APIOperation}.
     */
    public abstract Class<? extends APIOperation> getAPIOperation();
    
    //=================================================================
    // Helper methods
    //=================================================================

    /**
     * Need a schema 
     */
    public Schema getSchema() {
        return getConnectorFacade().schema();
    }
    
    /**
     * Gets Test name
     * @return Test Name
     */
    public abstract String getTestName();
    
    /**
     * Gets {@link ObjectClassInfo} for object class returned by {@link ObjectClassRunner#getSupportedObjectClass}.
     * 
     * @return {@link ObjectClassInfo}
     */
    public ObjectClassInfo getObjectClassInfo() {
        return _objectClassInfo;
    }

    /**
     * Identifier which tells if the tested ObjectClass (get by {@link ObjectClassRunner#getObjectClass() }
     * is supported by connector or not, supported means that the ObjectClass is included in the Schema
     * @return
     */
    public boolean isObjectClassSupported() {
        return _ocSupported;
    }
    
    /**
     * Gets supported {@link OperationOptionInfo}s by the operation
     * 
     * @return {@link Set<OperationOptionInfo>} set of supported options
     */
    public Set<OperationOptionInfo> getOperationOptions() {
        return getSchema().getSupportedOptionsByOperation(getAPIOperation());
    }
    
    /**
     * Checks wheter supplied OperationAttribute name is supported by the connector
     * for current ObjectClass
     * 
     * @param name OperationAttribute name
     * @return true if the OpereationAttribute is supported, false otherwise
     */
    public boolean isOperationAttributeSupported(String name) {
        if (isObjectClassSupported()) {
            ObjectClassInfo oinfo = getObjectClassInfo();            
            for (AttributeInfo ainfo : oinfo.getAttributeInfo()) {
				return ainfo.is(name) && ainfo.isReadable()
						&& ainfo.isCreateable() && ainfo.isUpdateable();
			}
        }
        return false;
    }
    

    /**
     * {@inheritDoc}
     */
    @Override
    public OperationOptions getOperationOptionsByOp(Class<? extends APIOperation> clazz) {
        if (clazz.equals(SearchApiOp.class) || clazz.equals(GetApiOp.class)) {
            // all object class attributes as attrsToGet
            Collection<String> attrNames = new ArrayList<String>();
            for (AttributeInfo attrInfo : getObjectClassInfo().getAttributeInfo()) {
                attrNames.add(attrInfo.getName());
            }
            
            OperationOptionsBuilder opOptionsBuilder = new OperationOptionsBuilder();
            opOptionsBuilder.setAttributesToGet(attrNames);
            OperationOptions attrsToGet = opOptionsBuilder.build();
            
            return attrsToGet;
        }
        
        return super.getOperationOptionsByOp(clazz);
    }        

}
