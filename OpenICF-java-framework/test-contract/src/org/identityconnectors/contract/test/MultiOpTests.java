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
import java.util.Map;
import java.util.Set;

import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.api.operations.TestApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.api.operations.ValidateApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests which use many APIOperations to do the test scenario
 * 
 * @author Tomas Knappek
 * @author Zdenek Louzensky
 */
@RunWith(Parameterized.class)
public class MultiOpTests extends ObjectClassRunner {

    private static final String TEST_NAME = "Multi";
    private static final String MODIFIED = "modified";

    // this operation should pass ObjectClassRunner#testContract condition
    private Class _apiOp = CreateApiOp.class;
   
    

    /**
     *  Contructor
     */
    public MultiOpTests(ObjectClass oclass) {
        super(oclass);
    }
    


    /**
     * Scenario test - test positive cases. {@inheritDoc} Test assumes that
     * Schema,Create,Search and Delete are supported operations.
     * 
     */
    @Override
    public void testRun() {
        // initial number of objects to be created
        final int recordCount = 10;
        
        List<Uid> uids = new ArrayList<Uid>();
        List<Set<Attribute>> attrs = new ArrayList<Set<Attribute>>();

        // objects stored in connector resource before test
        List<ConnectorObject> coBeforeTest = null;        
        
        // sync variables
        SyncToken token = null;
        List<SyncDelta> deltas = null;
        
        // variable for assert messages
        String msg = null;
        
        try {
            /* SearchApiOp - get objects stored in connector resource before test */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SearchApiOp.class)) {
                // null filter
                coBeforeTest = ConnectorHelper.search(getConnectorFacade(), getObjectClass(), null,
                        getOperationOptionsByOp(SearchApiOp.class));
            }
            
            /* SyncApiOp - start synchronizing from now */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                // start synchronizing from now
                token = getConnectorFacade().getLatestSyncToken();
            }

            /* CreateApiOp - create initial objects */
            for (int i = 0; i < recordCount; i++) {
                Set<Attribute> attr = ConnectorHelper.getAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), i, true);
                Uid luid = getConnectorFacade().create(getObjectClass(), attr, getOperationOptionsByOp(CreateApiOp.class));
                assertNotNull("Create returned null uid.", luid);
                attrs.add(attr);
                uids.add(luid);
            }            
            
            /* GetApiOp - check that objects were created with attributes as requested */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), GetApiOp.class)) {
                for (int i = 0; i < recordCount; i++) {
                    ConnectorObject obj = getConnectorFacade().getObject(getObjectClass(),
                            uids.get(i), getOperationOptionsByOp(GetApiOp.class));
                    assertNotNull("Unable to retrieve newly created object", obj);

                    ConnectorHelper.checkObject(getObjectClassInfo(), obj, attrs.get(i));
                }
            }

            /* TestApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), TestApiOp.class)) {
                // should NOT throw
                getConnectorFacade().test();
            }

            /* SyncApiOp - check sync of created objects */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                if (SyncApiOpTests.canSyncAfterOp(CreateApiOp.class)) {
                    // sync after create
                    deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(), token,
                            getOperationOptionsByOp(SyncApiOp.class));

                    msg = "Sync after %d creates returned %d deltas.";
                    assertTrue(String.format(msg, recordCount, deltas.size()),
                            deltas.size() == recordCount);

                    // check all deltas
                    for (int i = 0; i < recordCount; i++) {
                        ConnectorHelper.checkSyncDelta(getObjectClassInfo(), deltas.get(i), uids
                                .get(i), attrs.get(i), SyncDeltaType.CREATE, true);
                    }

                    token = deltas.get(recordCount - 1).getToken();
                }
            }

            /* DeleteApiOp - delete one object */
            Uid deleteUid = uids.remove(0);
            attrs.remove(0);
            
            // delete it and check that it was really deleted
            ConnectorHelper.deleteObject(getConnectorFacade(), getObjectClass(), deleteUid, true,
                    getOperationOptionsByOp(DeleteApiOp.class));

            /* SearchApiOp - search with null filter */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SearchApiOp.class)) {
                List<ConnectorObject> coFound = ConnectorHelper.search(getConnectorFacade(),
                        getObjectClass(), null, getOperationOptionsByOp(SearchApiOp.class));
                assertTrue(
                        "Search with null filter returned different count of results. Expected: "
                                + uids.size() + coBeforeTest.size() + ", but returned: "
                                + coFound.size(), coFound.size() == uids.size()
                                + coBeforeTest.size());
                // check all objects
                for (ConnectorObject obj : coFound) {
                    if (uids.contains((obj.getUid()))) {
                        int index = uids.indexOf(obj.getUid());
                        ConnectorHelper.checkObject(getObjectClassInfo(), obj, attrs.get(index));
                    } else {
                        assertTrue("Search with null filter returned unexpected object " + obj,
                                coBeforeTest.contains(obj));
                    }
                }
            }

            /* UpdateApiOp - update one object */
            Uid updateUid = null;
            Set<Attribute> replaceAttributes = null; 
            if (ConnectorHelper.operationSupported(getConnectorFacade(), UpdateApiOp.class)) {
                updateUid = uids.remove(0);
                attrs.remove(0);
                replaceAttributes = ConnectorHelper.getAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), MODIFIED, 0, false, false);
                
                // update only in case there is something to update
                if (replaceAttributes.size() > 0) {                    
                    // Uid must be present in attributes
                    replaceAttributes.add(updateUid);
                    Uid newUid = getConnectorFacade().update(UpdateApiOp.Type.REPLACE,
                            getObjectClass(), replaceAttributes, getOperationOptionsByOp(UpdateApiOp.class));
                    replaceAttributes.remove(updateUid);
                    
                    if (!updateUid.equals(newUid)) {
                        updateUid = newUid;
                    }
                    
                    attrs.add(replaceAttributes);
                    uids.add(updateUid);
                }

                /* SearchApiOp - search with Uid filter */
                if (ConnectorHelper.operationSupported(getConnectorFacade(), SearchApiOp.class)) {
                    // search by Uid
                    Filter fltUid = FilterBuilder.equalTo(updateUid);
                    List<ConnectorObject> coFound = ConnectorHelper.search(getConnectorFacade(), getObjectClass(),
                            fltUid, getOperationOptionsByOp(SearchApiOp.class));
                    assertTrue("Search with Uid filter returned unexpected number of objects. Expected: 1, but returned: "
                                    + coFound.size(), coFound.size() == 1);
                    ConnectorHelper.checkObject(getObjectClassInfo(), coFound.get(0),
                            replaceAttributes);
                }                
            }
            
            /* SyncApiOp - sync after one delete and one possible update */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                if (SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)
                        || SyncApiOpTests.canSyncAfterOp(UpdateApiOp.class)) {
                    
                    deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(),
                            token, getOperationOptionsByOp(SyncApiOp.class));
                    // one deleted, one updated (if existed attributes to
                    // update)
                    assertTrue("Sync returned unexpected number of deltas. Exptected: max 2, but returned: "
                                    + deltas.size(), deltas.size() <= 2);

                    for (int i = 0; i < deltas.size(); i++) {
                        SyncDelta delta = deltas.get(i);
                                                
                        if (i == 0) {
                            if (SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)) {
                                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                        deleteUid, null, SyncDeltaType.DELETE, true);
                            }
                            else if (SyncApiOpTests.canSyncAfterOp(UpdateApiOp.class)) {
                                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                        updateUid, replaceAttributes, SyncDeltaType.UPDATE, true);
                            }
                        }
                        // second must be update
                        else {                            
                            ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                    updateUid, replaceAttributes, SyncDeltaType.UPDATE, true);
                        }
                        
                        // remember last token
                        token = delta.getToken();
                    }                      
                }
            }

            /* ValidateApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), ValidateApiOp.class)) {
                // should NOT throw
                getConnectorFacade().validate();
            }

            /* CreateApiOp - create one last object */
            Set<Attribute> attrs11 = ConnectorHelper.getAttributes(getDataProvider(),
                    getObjectClassInfo(), getTestName(), recordCount + 1, true);            
            Uid createUid = getConnectorFacade().create(getObjectClass(), attrs11, getOperationOptionsByOp(CreateApiOp.class));
            uids.add(createUid);
            attrs.add(attrs11);
            assertNotNull("Create returned null Uid.", createUid);

            /* GetApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), GetApiOp.class)) {
                // get the object to make sure it exist now
                ConnectorObject obj = getConnectorFacade().getObject(getObjectClass(), createUid,
                        getOperationOptionsByOp(GetApiOp.class));
                assertNotNull("Unable to retrieve newly created object", obj);
    
                // compare requested attributes to retrieved attributes
                ConnectorHelper.checkObject(getObjectClassInfo(), obj, attrs11);
            }

            /* DeleteApiOp - delete one object */
            deleteUid = uids.remove(0);
            attrs.remove(0);
            // delete it and check that it was really deleted
            ConnectorHelper.deleteObject(getConnectorFacade(), getObjectClass(), deleteUid, true,
                    getOperationOptionsByOp(DeleteApiOp.class));

            /* SyncApiOp - after delete, create */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                if (SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)
                        || SyncApiOpTests.canSyncAfterOp(CreateApiOp.class)) {
                    deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(), token,
                            getOperationOptionsByOp(SyncApiOp.class));
                    // one deleted, one created
                    assertTrue("Sync returned unexpected number of deltas. Exptected: max 2, but returned: "
                                    + deltas.size(), deltas.size() <= 2);

                    for (int i = 0; i < deltas.size(); i++) {
                        SyncDelta delta = deltas.get(i);
                                                
                        if (i == 0) {
                            if (SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)) {
                                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                        deleteUid, null, SyncDeltaType.DELETE, true);
                            }
                            else if (SyncApiOpTests.canSyncAfterOp(CreateApiOp.class)) {
                                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                        createUid, attrs11, SyncDeltaType.CREATE, true);
                            }
                        }
                        // second must be create
                        else {                            
                            ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                    updateUid, replaceAttributes, SyncDeltaType.CREATE, true);
                        }
                        
                        // remember last token
                        token = delta.getToken();
                    }                    
                }
            }
            
            /* DeleteApiOp - delete all objects */
            for (int i = 0; i < uids.size(); i++) {
                ConnectorHelper.deleteObject(getConnectorFacade(), getObjectClass(), uids.get(i),
                        true, getOperationOptionsByOp(DeleteApiOp.class));
            }

            /* SyncApiOp - all objects were deleted */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)
                    && SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)) {
                deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(), token,
                        getOperationOptionsByOp(SyncApiOp.class))
                        ;
                msg = "Sync returned unexpected number of deltas. Exptected: %d, but returned: %d";
                assertTrue(String.format(msg, uids.size(), deltas.size()), deltas.size() == uids
                        .size());

                for (int i = 0; i < uids.size(); i++) {
                    ConnectorHelper.checkSyncDelta(getObjectClassInfo(), deltas.get(i),
                            uids.get(i), null, SyncDeltaType.DELETE, true);
                }
            }

        } finally {           
            // cleanup
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
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return _apiOp;
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public String getTestName() {
        return TEST_NAME;
    }

    /**
     * Tests ENABLE attribute contract 
     */
    @Test
    public void testEnableOpAttribute() {
        if (isOperationAttributeSupported(OperationalAttributes.ENABLE_NAME)) {

            //check ENABLE for true
            checkEnableOpAttribute(true);

            //check ENABLE for false
            checkEnableOpAttribute(false);
        }
    }

    /**
     * Method to check the ENABLE attribute contract
     * 
     * @param enabled ENABLE state
     */
    private void checkEnableOpAttribute(boolean enabled) {

        Set<Attribute> attrs = null;

        attrs = ConnectorHelper.getAttributes(getDataProvider(), getObjectClassInfo(), getTestName(), 0, true);

        //remove ENABLE if present
        for (Attribute attribute : attrs) {
            if (attribute.is(OperationalAttributes.ENABLE_NAME)) {
                attrs.remove(attribute);

            }
        }

        //add ENABLE
        attrs.add(AttributeBuilder.buildEnabled(enabled));

        Uid uid = null;

        try {
            //create
            uid = getConnectorFacade().create(getSupportedObjectClass(), attrs, null);

            checkEnableAttribute(uid, enabled);

            //remove ENABLE if present           
            for (Attribute attribute : attrs) {
                if (attribute.is(OperationalAttributes.ENABLE_NAME)) {
                    attrs.remove(attribute);
                    break;
                }
            }
            //add ENABLE
            attrs.add(AttributeBuilder.buildEnabled(!enabled));

            //update
            uid = getConnectorFacade().update(UpdateApiOp.Type.REPLACE, getSupportedObjectClass(), attrs, null);

            //check again
            checkEnableAttribute(uid, !enabled);

        } finally {
            ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid, false, null);
        }
    }

    /**
     * Gets the ConnectorObject and check the value of ENABLE attribute is as
     * expected
     * 
     * @param uid Uid to get
     * @param enabled expected ENABLE value
     */
    private void checkEnableAttribute(Uid uid, boolean enabled) {
        //get the object
        ConnectorObject obj = getConnectorFacade().getObject(getSupportedObjectClass(), uid, null);

        //check we have the correct value
        for (Attribute attribute : obj.getAttributes()) {
            if (attribute.is(OperationalAttributes.ENABLE_NAME)) {
                List<Object> vals = attribute.getValue();
                assertTrue(String.format("Operational attribute %s must contain exactly one value.",
                        OperationalAttributes.ENABLE_NAME), vals.size() == 1);
                assertTrue(String.format("Operational attribute %s value type must be Boolean.",
                        OperationalAttributes.ENABLE_NAME), vals.get(0) instanceof Boolean);
                Boolean value = (Boolean) vals.get(0);
                assertTrue(String.format("Operational attribute %s value is different, expected: %s, returned: %s",
                        OperationalAttributes.ENABLE_NAME, enabled, value), value == enabled);
            }
        }
    }
}
