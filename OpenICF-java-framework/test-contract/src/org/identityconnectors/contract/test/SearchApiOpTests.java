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

import static org.junit.Assert.*;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Contract test of {@link SearchApiOp} 
 */
@RunWith(Parameterized.class)
public class SearchApiOpTests extends ObjectClassRunner {
    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(SearchApiOpTests.class);
    private static final String TEST_NAME = "Search";
    
    public SearchApiOpTests(ObjectClass oclass) {
        super(oclass);
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return SearchApiOp.class;
    }
    
    /**
     * {@inheritDoc}      
     */
    @Override
    public void testRun() {
        Uid uid = null;
        List<Uid> uids = new ArrayList<Uid>();
        List<Set<Attribute>> attrs = new ArrayList<Set<Attribute>>();
        ConnectorObject coFound = null;
        final int recordCount = 10;


        try {
            // obtain objects stored in connector resource, before test inserts
            // own test data
            // should throw if object class is not supported and test ends
            List<ConnectorObject> coBeforeTest = ConnectorHelper.search(getConnectorFacade(),
                    getObjectClass(), null, getOperationOptionsByOp(SearchApiOp.class));
            
            //prepare the data
            for (int i = 0; i < recordCount; i++) {
                //create objects
                Set<Attribute> attr = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), i, true, false);

                Uid luid = getConnectorFacade().create(getSupportedObjectClass(), attr, getOperationOptionsByOp(CreateApiOp.class));
                assertNotNull("Create returned null uid.", luid);
                attrs.add(attr);
                uids.add(luid);
            }

            //search by id 
            uid = uids.get(0);
            Filter fltUid = FilterBuilder.equalTo(uid);
            List<ConnectorObject> coObjects = ConnectorHelper.search(getConnectorFacade(),
                    getObjectClass(), fltUid, getOperationOptionsByOp(SearchApiOp.class));
            assertTrue("Search filter by uid failed, expected to return one object, but returned "
                    + coObjects.size(), coObjects.size() == 1);
            coFound = coObjects.get(0);
            ConnectorHelper.checkObject(getObjectClassInfo(), coFound, attrs.get(0));

            //get name
            Attribute attName = coFound.getAttributeByName(Name.NAME);
            assertTrue("Special attribute NAME is expected to have exactly one value.", attName
                    .getValue().size() == 1);
            String attNameValue = attName.getValue().get(0).toString();

            //search by name
            coFound = ConnectorHelper.findObjectByName(getConnectorFacade(), getObjectClass(),
                    attNameValue, getOperationOptionsByOp(SearchApiOp.class));
            ConnectorHelper.checkObject(getObjectClassInfo(), coFound, attrs.get(0));
            
            //search by all non special readable attributes
            Filter fltAllAtts = null;
            for (Attribute attribute : attrs.get(0)) {
                if (!AttributeUtil.isSpecial(attribute) && ConnectorHelper.isReadable(getObjectClassInfo(), attribute)) {                    
                    if (fltAllAtts == null) {
                        fltAllAtts = FilterBuilder.equalTo(attribute);
                    } else {
                        fltAllAtts = FilterBuilder.and(fltAllAtts, FilterBuilder.equalTo(attribute));
                    }                    
                }
            }            
            // skip test when there are no special readable attributes 
            // (results in null filter - tested explicitly)
            if (fltAllAtts != null) {
                coObjects = ConnectorHelper.search(getConnectorFacade(), getObjectClass(), fltAllAtts,
                        getOperationOptionsByOp(SearchApiOp.class));
                assertEquals("Search by all non-special attributes returned " + coObjects.size()
                        + "objects, but expected was 1.", 1, coObjects.size());
                ConnectorHelper.checkObject(getObjectClassInfo(), coFound, attrs.get(0));
            }
            
            //check null filter
            coObjects = ConnectorHelper.search(getConnectorFacade(), getObjectClass(), null, getOperationOptionsByOp(SearchApiOp.class));
            assertTrue("Null-filter search failed, wrong number of objects returned, expected: "
                            + (uids.size() + coBeforeTest.size()) + " but found: "
                            + coObjects.size(), 
                            coObjects.size() == uids.size() + coBeforeTest.size());
            
            List<Uid> tempUids = new ArrayList<Uid>(uids);
            
            for (ConnectorObject cObject : coObjects) {
                // check if the uid is in list of objects created by test
                int idx = uids.indexOf(cObject.getUid());
                if (idx > -1) {
                    // is in list
                    // remove it from temp list
                    assertTrue(tempUids.remove(cObject.getUid()));
                    // compare the attributes
                    ConnectorHelper.checkObject(getObjectClassInfo(), cObject, attrs.get(idx));
                } else {
                    assertTrue(
                            "Object returned by null-filter search is neither in list of objects created by test nor in list of objects that were in connector resource before test.",
                            coBeforeTest.contains(cObject));
                }
            }
            assertTrue("Null-filter search didn't return all created objects by search test.",
                    tempUids.size() == 0);                        
            
   
        } finally {
            // remove objects created by test
            for (Uid deluid : uids) {
                try {
                    ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(),
                            deluid, false, getOperationOptionsByOp(DeleteApiOp.class));
                } catch (Exception e) {
                    // ok
                }
            }
        }
        
    }
    
    /**
     * Test Search without specified OperationOptions attrsToGet which is the default for all other tests.
     */
    @Test
    public void testSearchWithoutAttrsToGet() {
        // run the contract test only if search is supported by tested object class
        if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(),
                getAPIOperation())) {
            Uid uid = null;

            try {
                Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), 0, true, false);

                uid = getConnectorFacade().create(getSupportedObjectClass(), attrs, null);
                assertNotNull("Create returned null uid.", uid);

                // get the user to make sure it exists now
                Filter fltUid = FilterBuilder.equalTo(uid);
                List<ConnectorObject> coObjects = ConnectorHelper.search(getConnectorFacade(),
                        getSupportedObjectClass(), fltUid, null);
                assertTrue(
                        "Search filter by uid with no OperationOptions failed, expected to return one object, but returned "
                                + coObjects.size(), coObjects.size() == 1);

                assertNotNull("Unable to retrieve newly created object", coObjects.get(0));

                // compare requested attributes to retrieved attributes, but
                // don't compare attrs which
                // are not returned by default
                ConnectorHelper.checkObject(getObjectClassInfo(), coObjects.get(0), attrs, false);
            } finally {
                if (uid != null) {
                    // delete the object
                    getConnectorFacade().delete(getSupportedObjectClass(), uid, null);
                }
            }
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testSearchWithoutAttrsToGet'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
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
