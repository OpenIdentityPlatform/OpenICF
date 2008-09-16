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


import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Map<Uid, Set<Attribute>> coCreatedAll = null;

        // objects stored in connector resource before test
        List<ConnectorObject> coBeforeTest = null;

        // token returned by sync
        SyncToken token = null;

        // initial number of objects to be created
        final int createCount = 10;

        try {
            /* SearchApiOp - get objects stored in connector resource before test */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SearchApiOp.class)) {
                // null filter
                coBeforeTest = ConnectorHelper.search(getConnectorFacade(), getObjectClass(), null,
                        getOperationOptionsByOp(SearchApiOp.class));
            }

            /* CreateApiOp - create initial objects */
            // creates objects
            coCreatedAll = ConnectorHelper.createObjects(getConnectorFacade(),
                    getDataProvider(), getObjectClass(), getObjectClassInfo(), getTestName(),
                    createCount, getOperationOptionsByOp(CreateApiOp.class));
            // check that objects were created with attributes as requested
            final boolean success = ConnectorHelper.checkObjects(getConnectorFacade(),
                    getObjectClass(), getObjectClassInfo(), coCreatedAll,
                    getOperationOptionsByOp(GetApiOp.class));
            assertTrue("Created objects are different than requested.", success);

            /* TestApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), TestApiOp.class)) {
                // should NOT throw
                getConnectorFacade().test();
            }

            /* SyncApiOp - check sync of created objects */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                List<SyncDelta> deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(),
                        token, getOperationOptionsByOp(SyncApiOp.class));
                assertTrue("Sync returned different number of deltas than expected. Expected: "
                        + createCount + ", but returned: " + deltas.size(),
                        deltas.size() == createCount);

                for (SyncDelta delta : deltas) {
                    assertTrue(
                            "Sync returned delta with unexpected type. Expected CREATE, but returned "
                                    + delta.getDeltaType(),
                            delta.getDeltaType() == SyncDeltaType.CREATE);
                    Set<Attribute> expected = coCreatedAll.get(delta.getUid());
                    assertNotNull(expected);
                    Set<Attribute> got = delta.getObject().getAttributes();
                    assertNotNull(got);
                    ConnectorHelper.checkAttributes(expected, got);
                    token = delta.getToken();
                }
            }

            /* DeleteApiOp - delete one object */
            Uid deleteUid = coCreatedAll.keySet().toArray(new Uid[0])[0];
            // deletes it and checks that it was really deleted
            ConnectorHelper.deleteObject(getConnectorFacade(), getObjectClass(), deleteUid, true,
                    getOperationOptionsByOp(DeleteApiOp.class));
            coCreatedAll.remove(deleteUid);

            /* SearchApiOp - search with null filter */
            List<ConnectorObject> coFound = ConnectorHelper.search(getConnectorFacade(),
                    getObjectClass(), null, getOperationOptionsByOp(SearchApiOp.class));
            assertTrue("Search with null filter returned different results count. Expected: "
                    + coCreatedAll.size() + coBeforeTest.size() + ", but returned: "
                    + coFound.size(), coFound.size() == coCreatedAll.size() + coBeforeTest.size());
            // check all objects
            for (ConnectorObject obj : coFound) {
                if (coCreatedAll.containsKey(obj.getUid())) {
                    ConnectorHelper.checkObject(getObjectClassInfo(), obj,
                            coCreatedAll.get(obj.getUid()));
                } else {
                    assertTrue("Search with null filter returned unexpected object.", coBeforeTest
                            .contains(obj));
                }
            }

            /* UpdateApiOp - update one object */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), UpdateApiOp.class)) {
                Uid updateUid = coCreatedAll.keySet().toArray(new Uid[0])[0];
                Set<Attribute> replaceAttributes = ConnectorHelper.getAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), MODIFIED, 0, false, false);
                
                // update only in case there is something to update
                if (replaceAttributes.size() > 0) {
                    
                    // object class is not supported
                    // Uid must be present in attributes
                    replaceAttributes.add(updateUid);
                    Uid newUid = getConnectorFacade().update(UpdateApiOp.Type.REPLACE,
                            getObjectClass(), replaceAttributes, getOperationOptionsByOp(UpdateApiOp.class));
                    replaceAttributes.remove(updateUid);

                    coCreatedAll.remove(updateUid);
                    updateUid = newUid;
                    coCreatedAll.put(newUid, replaceAttributes);
                }

                /* SearchApiOp - search with Uid filter */
                // search by Uid
                Filter fltUid = FilterBuilder.equalTo(updateUid);
                coFound = ConnectorHelper.search(getConnectorFacade(), getObjectClass(), fltUid,
                        getOperationOptionsByOp(SearchApiOp.class));
                assertTrue("Search with Uid filter returned unexpected number of objects. Expected: 1, but returned: "
                                + coFound.size(), coFound.size() == 1);
                ConnectorHelper.checkObject(getObjectClassInfo(), coFound.get(0), replaceAttributes);

                /* SyncApiOp - sync after one delete and one update */
                if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                    List<SyncDelta> deltas = ConnectorHelper.sync(getConnectorFacade(),
                            getObjectClass(), token, getOperationOptionsByOp(SyncApiOp.class));
                    // one deleted, one updated (if existed attributes to update)
                    assertTrue("Sync returned unexpected number of deltas. Exptected: 2, but returned: "
                                    + deltas.size(), deltas.size() <= 2);

                    for (SyncDelta delta : deltas) {
                        if (delta.getDeltaType() == SyncDeltaType.DELETE) {
                            assertTrue("Sync returned unexpected Uid.", delta.getUid().equals(
                                    deleteUid));
                        } else if (delta.getDeltaType() == SyncDeltaType.UPDATE) {
                            // TODO: not sure, maybe the old uid
                            assertTrue("Sync returned unexpected Uid.", delta.getUid().equals(
                                    updateUid));
                            Set<Attribute> expected = replaceAttributes;
                            assertNotNull(expected);
                            Set<Attribute> got = delta.getObject().getAttributes();
                            assertNotNull(got);
                            ConnectorHelper.checkAttributes(expected, got);
                        } else {
                            fail("Sync returned CREATE type, but no objects were created since last sync.");
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

            /* CreateApiOp */
            // create one last object
            Set<Attribute> attrs = ConnectorHelper.getAttributes(getDataProvider(),
                    getObjectClassInfo(), getTestName(), createCount + 1, true);
            Uid createUid = getConnectorFacade().create(getObjectClass(), attrs, getOperationOptionsByOp(CreateApiOp.class));
            assertNotNull("Create returned null Uid.", createUid);

            // get the object to make sure it exist now
            ConnectorObject obj = getConnectorFacade().getObject(getObjectClass(), createUid,
                    getOperationOptionsByOp(GetApiOp.class));
            assertNotNull("Unable to retrieve newly created object", obj);

            // compare requested attributes to retrieved attributes
            ConnectorHelper.checkObject(getObjectClassInfo(), obj, attrs);
            coCreatedAll.put(createUid, attrs);

            /* DeleteApiOp - delete one object */
            deleteUid = coCreatedAll.keySet().toArray(new Uid[0])[0];
            // deletes it and checks that it was really deleted
            ConnectorHelper.deleteObject(getConnectorFacade(), getObjectClass(), deleteUid, true,
                    getOperationOptionsByOp(DeleteApiOp.class));
            coCreatedAll.remove(deleteUid);

            /* SyncApiOp - one delete, one create */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                List<SyncDelta> deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(),
                        token, getOperationOptionsByOp(SyncApiOp.class));
                // one deleted, one created
                assertTrue("Sync returned unexpected number of deltas. Exptected: 2, but returned: "
                                + deltas.size(), deltas.size() == 2);

                for (SyncDelta delta : deltas) {
                    if (delta.getDeltaType() == SyncDeltaType.DELETE) {
                        assertTrue("Sync returned unexpected Uid.", delta.getUid()
                                .equals(deleteUid));
                    } else if (delta.getDeltaType() == SyncDeltaType.CREATE) {
                        // TODO: not sure, maybe the old uid
                        assertTrue("Sync returned unexpected Uid.", delta.getUid()
                                .equals(createUid));
                        Set<Attribute> expected = attrs;
                        assertNotNull(expected);
                        Set<Attribute> got = delta.getObject().getAttributes();
                        assertNotNull(got);
                        ConnectorHelper.checkAttributes(expected, got);
                    } else {
                        fail("Sync returned UPDATE type, but no objects were updated since last sync.");
                    }
                    // remember last token
                    token = delta.getToken();
                }
            }

        } finally {
            if (coCreatedAll != null) {
                // delete all created objects
                ConnectorHelper.deleteObjects(getConnectorFacade(), getObjectClass(),
                        coCreatedAll.keySet(), getOperationOptionsByOp(DeleteApiOp.class));
            }

            /* SyncApiOp - all objects were deleted */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), SyncApiOp.class)) {
                List<SyncDelta> deltas = ConnectorHelper.sync(getConnectorFacade(), getObjectClass(),
                        token, getOperationOptionsByOp(SyncApiOp.class));
                assertTrue("Sync returned unexpected number of deltas. Exptected: 2, but returned: "
                                + deltas.size(), deltas.size() == coCreatedAll.size());

                for (SyncDelta delta : deltas) {
                    assertTrue("Sync returned unexpected type. Expected: DELETE, but returned: "
                            + delta.getDeltaType(), delta.getDeltaType() == SyncDeltaType.DELETE);
                    assertTrue("Sync returned unexpected Uid.", coCreatedAll.containsKey(delta
                            .getUid()));
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
