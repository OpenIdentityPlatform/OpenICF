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

import static org.junit.Assert.*;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
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
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Schema;
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

    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(MultiOpTests.class);
    
    private static final String TEST_NAME = "Multi";
    private static final String MODIFIED = "modified";
    private static final String LOCKOUT_PREFIX = "lockout";
    private static final String SKIP = "skip";

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
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SearchApiOp.class)) {
                // null filter
                coBeforeTest = ConnectorHelper.search(getConnectorFacade(), getObjectClass(), null,
                        getOperationOptionsByOp(SearchApiOp.class));
            }
            
            /* SyncApiOp - start synchronizing from now */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SyncApiOp.class)) {
                // start synchronizing from now
                token = getConnectorFacade().getLatestSyncToken(getObjectClass());
            }

            /* CreateApiOp - create initial objects */
            for (int i = 0; i < recordCount; i++) {
                Set<Attribute> attr = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), i, true, false);
                Uid luid = getConnectorFacade().create(getObjectClass(), attr, getOperationOptionsByOp(CreateApiOp.class));
                assertNotNull("Create returned null uid.", luid);
                attrs.add(attr);
                uids.add(luid);
            }            
            
            /* GetApiOp - check that objects were created with attributes as requested */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), GetApiOp.class)) {
                for (int i = 0; i < recordCount; i++) {
                    ConnectorObject obj = getConnectorFacade().getObject(getObjectClass(),
                            uids.get(i), getOperationOptionsByOp(GetApiOp.class));
                    assertNotNull("Unable to retrieve newly created object", obj);

                    ConnectorHelper.checkObject(getObjectClassInfo(), obj, attrs.get(i));
                }
            }

            /* TestApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), TestApiOp.class)) {
                // should NOT throw
                getConnectorFacade().test();
            }

            /* SyncApiOp - check sync of created objects */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SyncApiOp.class)) {
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
                                .get(i), attrs.get(i), SyncDeltaType.CREATE_OR_UPDATE, true);
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
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SearchApiOp.class)) {
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
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), UpdateApiOp.class)) {
                updateUid = uids.remove(0);
                attrs.remove(0);
                replaceAttributes = ConnectorHelper.getUpdateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), MODIFIED, 0, false, false);
                
                // update only in case there is something to update
                if (replaceAttributes.size() > 0) {                    
                    // Uid must be present in attributes
                    replaceAttributes.add(updateUid);
                    Uid newUid = getConnectorFacade().update(
                            getObjectClass(), 
                            updateUid,
                            AttributeUtil.filterUid(replaceAttributes), getOperationOptionsByOp(UpdateApiOp.class));
                    replaceAttributes.remove(updateUid);
                    
                    if (!updateUid.equals(newUid)) {
                        updateUid = newUid;
                    }
                    
                    attrs.add(replaceAttributes);
                    uids.add(updateUid);
                }

                /* SearchApiOp - search with Uid filter */
                if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SearchApiOp.class)) {
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
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SyncApiOp.class)) {
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
                                        updateUid, replaceAttributes, SyncDeltaType.CREATE_OR_UPDATE, true);
                            }
                        }
                        // second must be update
                        else {                            
                            ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                    updateUid, replaceAttributes, SyncDeltaType.CREATE_OR_UPDATE, true);
                        }
                        
                        // remember last token
                        token = delta.getToken();
                    }                      
                }
            }

            /* ValidateApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), ValidateApiOp.class)) {
                // should NOT throw
                getConnectorFacade().validate();
            }

            /* CreateApiOp - create one last object */
            Set<Attribute> attrs11 = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                    getObjectClassInfo(), getTestName(), recordCount + 1, true, false);     
            Uid createUid = getConnectorFacade().create(getObjectClass(), attrs11, getOperationOptionsByOp(CreateApiOp.class));
            uids.add(createUid);
            attrs.add(attrs11);
            assertNotNull("Create returned null Uid.", createUid);

            /* GetApiOp */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), GetApiOp.class)) {
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

            /* SyncApiOp - after create, delete */
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SyncApiOp.class)) {
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
                            if (SyncApiOpTests.canSyncAfterOp(CreateApiOp.class)) {
                                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                        createUid, attrs11, SyncDeltaType.CREATE_OR_UPDATE, true);
                            }
                            else if (SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)) {
                                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                        deleteUid, null, SyncDeltaType.DELETE, true);
                            }
                        }
                        // second must be create
                        else {                            
                            ConnectorHelper.checkSyncDelta(getObjectClassInfo(), delta,
                                    deleteUid, replaceAttributes, SyncDeltaType.DELETE, true);
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
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), SyncApiOp.class)
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

    /*
     * *****************************
     * OPERATIONAL ATTRIBUTES TESTS:
     * *****************************
     */
    
    /**
     * Tests ENABLE attribute contract 
     */
    @Test
    public void testEnableOpAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.ENABLE_NAME)) {

            // check ENABLE for true
            checkOpAttribute(OperationalAttributes.ENABLE_NAME, true, false, Boolean.class);

            // check ENABLE for false
            checkOpAttribute(OperationalAttributes.ENABLE_NAME, false, true, Boolean.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testEnableOpAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests ENABLE_DATE attribute contract 
     */
    @Test
    public void testEnableDateOpAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.ENABLE_DATE_NAME)) {
            
            // check ENABLE_DATE for "now" and "1.1.1970"
            checkOpAttribute(OperationalAttributes.ENABLE_DATE_NAME, (new Date()).getTime(),
                    (new Date(0)).getTime(), Long.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testEnableDateOpAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests DISABLE_DATE attribute contract 
     */
    @Test
    public void testDisableDateOpAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.DISABLE_DATE_NAME)) {

            // check DISABLE_DATE for "now" and "1.1.1970"
            checkOpAttribute(OperationalAttributes.DISABLE_DATE_NAME, (new Date()).getTime(),
                    (new Date(0)).getTime(), Long.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testDisableDateOpAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests LOCK_OUT attribute contract 
     */
    @Test
    public void testLockOutOpAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.LOCK_OUT_NAME) && canLockOut()) {

         // check ENABLE for true
            checkOpAttribute(OperationalAttributes.LOCK_OUT_NAME, true, false, Boolean.class);

            // check ENABLE for false
            checkOpAttribute(OperationalAttributes.LOCK_OUT_NAME, false, true, Boolean.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testLockOutOpAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests PASSWORD_EXPIRATION_DATE attribute contract 
     */
    @Test
    public void testPasswordExpirationDateOpAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME)) {

            // check PASSWORD_EXPIRATION_DATE for "now" and "1.1.1970"
            checkOpAttribute(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, (new Date()).getTime(),
                    (new Date(0)).getTime(), Long.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testPasswordExpirationDateOpAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests PASSWORD_EXPIRED attribute contract 
     */
    @Test
    public void testPasswordExpiredOpAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.PASSWORD_EXPIRED_NAME)) {

            // check PASSWORD_EXPIRED for true
            checkOpAttribute(OperationalAttributes.PASSWORD_EXPIRED_NAME, true, false, Boolean.class);

            // check PASSWORD_EXPIRED for false
            checkOpAttribute(OperationalAttributes.PASSWORD_EXPIRED_NAME, false, true, Boolean.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testPasswordExpiredOpAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests PASSWORD_CHANGE_INTERVAL attribute contract 
     */
    @Test
    public void testPasswordChangeIntervalPredAttribute() {
        if (isObjectClassSupported()
                && ConnectorHelper.isCRU(getObjectClassInfo(), PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME)) {

            // check PASSWORD_CHANGE_INTERVAL for 120 days and 30 days
            checkOpAttribute(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME, 10368000000L, 2592000000L, Long.class);
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testPasswordChangeIntervalPredAttribute'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }

    /**
     * Method to check the attrName's attribute contract
     * 
     * @param attrName attribute to be checked
     * @param createValue value used for create
     * @param updateValue value used for update
     * @param type expected type of the value
     */
    private void checkOpAttribute(String attrName, Object createValue, Object updateValue, Class<?> type) {

        Set<Attribute> attrs = null;

        attrs = ConnectorHelper.getCreateableAttributes(getDataProvider(), getObjectClassInfo(),
                getTestName(), 0, true, false);

        //remove attrName if present
        for (Attribute attribute : attrs) {
            if (attribute.is(attrName)) {
                attrs.remove(attribute);
                break;
            }
        }

        //add attrName with create value
        attrs.add(AttributeBuilder.build(attrName, createValue));

        Uid uid = null;

        try {
            //create
            uid = getConnectorFacade().create(getSupportedObjectClass(), attrs, null);

            // check value of attribute with create value
            checkAttribute(attrName, uid, createValue, type);

            // clear attrs
            attrs.clear();
            
            //add update value
            attrs.add(AttributeBuilder.build(attrName, updateValue));

            // add uid for update
            attrs.add(uid);
            
            //update
            uid = getConnectorFacade().update(getSupportedObjectClass(), uid,
                    AttributeUtil.filterUid(attrs), null);

            //check again with update value
            checkAttribute(attrName, uid, updateValue, type);

        } finally {
            ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid, false, null);
        }
    }

    /**
     * Gets the ConnectorObject and check the value of attribute is as
     * expected
     * 
     * @param uid Uid of object to get
     * @param expValue expected value of the attribute
     * @param type expected type of the attribute
     */
    private void checkAttribute(String attrName, Uid uid, Object expValue, Class<?> type) {
        //get the object
        ConnectorObject obj = getConnectorFacade().getObject(getSupportedObjectClass(), uid, null);

        //check we have the correct value
        for (Attribute attribute : obj.getAttributes()) {
            if (attribute.is(attrName)) {
                List<Object> vals = attribute.getValue();
                assertTrue(String.format("Operational attribute %s must contain exactly one value.",
                        attrName), vals.size() == 1);
                Object val = vals.get(0);
                assertEquals(String.format(
                        "Operational attribute %s value type must be %s, but is %s.", attrName,
                        type.getSimpleName(), val.getClass().getSimpleName()), type, val.getClass());
                
                assertEquals(String.format("Operational attribute %s value is different, expected: %s, returned: %s",
                        attrName, expValue, val), expValue, val);
            }
        }
    }
    
    /**
     * Tests GROUPS attribute contract
     */
    @Test
    public void testGroupsPredAttribute() {
        final ObjectClassInfo accountInfo = findOInfo(ObjectClass.ACCOUNT);
        final ObjectClassInfo groupInfo = findOInfo(ObjectClass.GROUP);

        // run test only in case ACCOUNT and GROUP are supported and GROUPS is supported for ACCOUNT
        if (accountInfo != null && groupInfo != null
                && ConnectorHelper.isCRU(accountInfo, PredefinedAttributes.GROUPS_NAME)) {

            Uid groupUid1 = null;
            Uid groupUid2 = null;
            Uid accountUid1 = null;
            try {
                // create 1st group
                Set<Attribute> groupAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), groupInfo, getTestName(), 0, true, false);                
                groupUid1 = getConnectorFacade().create(ObjectClass.GROUP, groupAttrs1, null);

                // create an account with GROUPS set to created GROUP
                Set<Attribute> accountAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), accountInfo, getTestName(), 0, true, false);
                for (Attribute attr : accountAttrs1) {
                    if (attr.is(PredefinedAttributes.GROUPS_NAME)) {
                        accountAttrs1.remove(attr);
                        break;
                    }
                }
                accountAttrs1.add(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME,
                        AttributeUtil.getStringValue(groupUid1)));
                
                accountUid1 = getConnectorFacade().create(ObjectClass.ACCOUNT, accountAttrs1, null);

                // get the account to make sure it exists now
                ConnectorObject obj = getConnectorFacade().getObject(ObjectClass.ACCOUNT,
                        accountUid1, getOperationOptionsByOp(GetApiOp.class));

                // check that object was created properly
                ConnectorHelper.checkObject(accountInfo, obj, accountAttrs1);

                // continue test only if update is supported for account and GROUPS is multiValue
                if (ConnectorHelper.operationSupported(getConnectorFacade(), ObjectClass.ACCOUNT,
                        UpdateApiOp.class) && ConnectorHelper.isMultiValue(accountInfo, PredefinedAttributes.GROUPS_NAME)) {
                    // create another group
                    Set<Attribute> groupAttrs2 = ConnectorHelper.getCreateableAttributes(
                            getDataProvider(), groupInfo, getTestName(), 1, true, false);                    
                    groupUid2 = getConnectorFacade().create(ObjectClass.GROUP, groupAttrs2, null);

                    // update account to contain both groups
                    Set<Attribute> accountAttrs2 = new HashSet<Attribute>();
                    accountAttrs2.add(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME,
                    		AttributeUtil.getStringValue(groupUid2)));
                    accountAttrs2.add(accountUid1);
                    accountUid1 = getConnectorFacade().addAttributeValues(
                            ObjectClass.ACCOUNT, accountUid1, AttributeUtil.filterUid(accountAttrs2), null);

                    // get the account to make sure it exists now and values are correct
                    obj = getConnectorFacade().getObject(ObjectClass.ACCOUNT, accountUid1,
                            getOperationOptionsByOp(GetApiOp.class));

                    // check that object was created properly
                    ConnectorHelper.checkObject(accountInfo, obj, UpdateApiOpTests
                            .mergeAttributeSets(accountAttrs1, accountAttrs2));
                }
                
                // ACCOUNTS must be supported for GROUPS to be able to check backward reference
                if (ConnectorHelper.isReadable(groupInfo, PredefinedAttributes.ACCOUNTS_NAME)) {
                    // check that ACCOUNTS is set properly
                    OperationOptionsBuilder builder = new OperationOptionsBuilder();
                    builder.setAttributesToGet(PredefinedAttributes.ACCOUNTS_NAME);
                    ConnectorObject groupObj = getConnectorFacade().getObject(ObjectClass.GROUP,
                            groupUid1, builder.build());
                    assertNotNull("Cannot get group object.", groupObj);
                    Attribute accounts = groupObj.getAttributeByName(PredefinedAttributes.ACCOUNTS_NAME);
                    assertTrue("ACCOUNTS attribute should contain one value, but contains: "
                            + accounts.getValue().size(), accounts.getValue().size() == 1);
                    final String MSG = "ACCOUNTS attribute value is wrong, expected: %s, returned: %s.";
                    assertTrue(String.format(MSG, accountUid1, accounts.getValue().get(0)),
                            accounts.getValue().get(0).equals(accountUid1));
                }

            } finally {
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.GROUP, groupUid1,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.GROUP, groupUid2,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ACCOUNT,
                        accountUid1, false, null);
            }

        } else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testGroupsPredAttribute''.");
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests ORGANIZATIONS attribute contract
     */
    @Test
    public void testOrganizationsPredAttribute() {
        final ObjectClassInfo accountInfo = findOInfo(ObjectClass.ACCOUNT);
        final ObjectClassInfo orgInfo = findOInfo(ObjectClass.ORGANIZATION);

        // run test only in case ACCOUNT and ORGANIZATION are supported and ORGANIZATIONS is supported for ACCOUNT
        if (accountInfo != null && orgInfo != null
                && ConnectorHelper.isCRU(accountInfo, PredefinedAttributes.ORGANIZATION_NAME)) {

            Uid orgUid1 = null;
            Uid orgUid2 = null;
            Uid accountUid1 = null;
            try {
                // create 1st org
                Set<Attribute> orgAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), orgInfo, getTestName(), 0, true, false);                
                orgUid1 = getConnectorFacade().create(ObjectClass.ORGANIZATION, orgAttrs1, null);

                // create an account with ORGANIZATIONS set to created org
                Set<Attribute> accountAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), accountInfo, getTestName(), 0, true, false);
                for (Attribute attr : accountAttrs1) {
                    if (attr.is(PredefinedAttributes.ORGANIZATION_NAME)) {
                        accountAttrs1.remove(attr);
                        break;
                    }
                }
                accountAttrs1.add(AttributeBuilder.build(PredefinedAttributes.ORGANIZATION_NAME,
                		AttributeUtil.getStringValue(orgUid1)));
                
                accountUid1 = getConnectorFacade().create(ObjectClass.ACCOUNT, accountAttrs1, null);

                // get the account to make sure it exists now
                ConnectorObject obj = getConnectorFacade().getObject(ObjectClass.ACCOUNT,
                        accountUid1, getOperationOptionsByOp(GetApiOp.class));

                // check that object was created properly
                ConnectorHelper.checkObject(accountInfo, obj, accountAttrs1);

                // continue test only if update is supported for account and ORGANIZATIONS is multiValue
                if (ConnectorHelper.operationSupported(getConnectorFacade(), ObjectClass.ACCOUNT,
                        UpdateApiOp.class) && ConnectorHelper.isMultiValue(accountInfo, PredefinedAttributes.ORGANIZATION_NAME)) {
                    // create another org
                    Set<Attribute> orgAttrs2 = ConnectorHelper.getCreateableAttributes(
                            getDataProvider(), orgInfo, getTestName(), 1, true, false);                    
                    orgUid2 = getConnectorFacade().create(ObjectClass.ORGANIZATION, orgAttrs2, null);

                    // update account to contain both orgs
                    Set<Attribute> accountAttrs2 = new HashSet<Attribute>();
                    accountAttrs2.add(AttributeBuilder.build(PredefinedAttributes.ORGANIZATION_NAME,
                    		AttributeUtil.getStringValue(orgUid2)));
                    accountAttrs2.add(accountUid1);
                    accountUid1 = getConnectorFacade().addAttributeValues(
                            ObjectClass.ACCOUNT, accountUid1, AttributeUtil.filterUid(accountAttrs2), null);

                    // get the account to make sure it exists now and values are correct
                    obj = getConnectorFacade().getObject(ObjectClass.ACCOUNT, accountUid1,
                            getOperationOptionsByOp(GetApiOp.class));

                    // check that object was created properly
                    ConnectorHelper.checkObject(accountInfo, obj, UpdateApiOpTests
                            .mergeAttributeSets(accountAttrs1, accountAttrs2));
                }
                
                // ACCOUNTS must be supported for ORGANIZATIONS to be able to check backward reference
                if (ConnectorHelper.isReadable(orgInfo, PredefinedAttributes.ACCOUNTS_NAME)) {
                    // check that ACCOUNTS is set properly
                    OperationOptionsBuilder builder = new OperationOptionsBuilder();
                    builder.setAttributesToGet(PredefinedAttributes.ACCOUNTS_NAME);
                    ConnectorObject orgObj = getConnectorFacade().getObject(ObjectClass.ORGANIZATION,
                            orgUid1, builder.build());
                    assertNotNull("Cannot get organization object.", orgObj);
                    Attribute accounts = orgObj.getAttributeByName(PredefinedAttributes.ACCOUNTS_NAME);
                    assertTrue("ACCOUNTS attribute should contain one value, but contains: "
                            + accounts.getValue().size(), accounts.getValue().size() == 1);
                    final String MSG = "ACCOUNTS attribute value is wrong, expected: %s, returned: %s.";
                    assertTrue(String.format(MSG, accountUid1, accounts.getValue().get(0)),
                            accounts.getValue().get(0).equals(accountUid1));
                }

            } finally {
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ORGANIZATION, orgUid1,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ORGANIZATION, orgUid2,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ACCOUNT,
                        accountUid1, false, null);
            }

        } else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testOrganizationsPredAttribute''.");
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests ACCOUNTS attribute contract for object class organization
     */
    @Test
    public void testAccountsPredAttributeOrg() {
        final ObjectClassInfo accountInfo = findOInfo(ObjectClass.ACCOUNT);
        final ObjectClassInfo orgInfo = findOInfo(ObjectClass.ORGANIZATION);

        // run test only in case ACCOUNT and ORGANIZATION are supported and ACCOUNTS is supported for ORGANIZATIONS
        if (accountInfo != null && orgInfo != null
                && ConnectorHelper.isCRU(accountInfo, PredefinedAttributes.ACCOUNTS_NAME)) {

            Uid accountUid1 = null;
            Uid accountUid2 = null;
            Uid orgUid1 = null;
            try {
                // create 1st account
                Set<Attribute> accountAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), accountInfo, getTestName(), 0, true, false);                
                accountUid1 = getConnectorFacade().create(ObjectClass.ACCOUNT, accountAttrs1, null);

                // create an org with ACCOUNTS set to created account
                Set<Attribute> orgAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), orgInfo, getTestName(), 0, true, false);
                for (Attribute attr : orgAttrs1) {
                    if (attr.is(PredefinedAttributes.ACCOUNTS_NAME)) {
                        orgAttrs1.remove(attr);
                        break;
                    }
                }
                orgAttrs1.add(AttributeBuilder.build(PredefinedAttributes.ACCOUNTS_NAME,
                		AttributeUtil.getStringValue(accountUid1)));
                
                orgUid1 = getConnectorFacade().create(ObjectClass.ORGANIZATION, orgAttrs1, null);

                // get the org to make sure it exists now
                ConnectorObject obj = getConnectorFacade().getObject(ObjectClass.ORGANIZATION,
                        orgUid1, getOperationOptionsByOp(GetApiOp.class));

                // check that object was created properly
                ConnectorHelper.checkObject(orgInfo, obj, orgAttrs1);

                // continue test only if update is supported for org and ACCOUNTS is multiValue
                if (ConnectorHelper.operationSupported(getConnectorFacade(), ObjectClass.ORGANIZATION,
                        UpdateApiOp.class) && ConnectorHelper.isMultiValue(orgInfo, PredefinedAttributes.ACCOUNTS_NAME)) {
                    // create another account
                    Set<Attribute> accountAttrs2 = ConnectorHelper.getCreateableAttributes(
                            getDataProvider(), accountInfo, getTestName(), 1, true, false);                    
                    accountUid2 = getConnectorFacade().create(ObjectClass.ACCOUNT, accountAttrs2, null);

                    // update org to contain both accounts
                    Set<Attribute> orgAttrs2 = new HashSet<Attribute>();
                    orgAttrs2.add(AttributeBuilder.build(PredefinedAttributes.ACCOUNTS_NAME,
                    		AttributeUtil.getStringValue(accountUid2)));
                    orgAttrs2.add(orgUid1);
                    orgUid1 = getConnectorFacade().addAttributeValues(
                            ObjectClass.ORGANIZATION, orgUid1, AttributeUtil.filterUid(orgAttrs2), null);

                    // get the org to make sure it exists now and values are correct
                    obj = getConnectorFacade().getObject(ObjectClass.ORGANIZATION, orgUid1,
                            getOperationOptionsByOp(GetApiOp.class));

                    // check that object was created properly
                    ConnectorHelper.checkObject(orgInfo, obj, UpdateApiOpTests
                            .mergeAttributeSets(orgAttrs1, orgAttrs2));
                }
                
                // ORGANIZATION must be supported for ACCOUNTS to be able to check backward reference
                if (ConnectorHelper.isReadable(accountInfo, PredefinedAttributes.ORGANIZATION_NAME)) {
                    // check that ORGANIZATION is set properly
                    OperationOptionsBuilder builder = new OperationOptionsBuilder();
                    builder.setAttributesToGet(PredefinedAttributes.ORGANIZATION_NAME);
                    ConnectorObject accObj = getConnectorFacade().getObject(ObjectClass.ACCOUNT,
                            accountUid1, builder.build());
                    assertNotNull("Cannot get account object.", accObj);
                    Attribute organizations = accObj.getAttributeByName(PredefinedAttributes.ORGANIZATION_NAME);
                    assertTrue("ORGANIZATIONS attribute should contain one value, but contains: "
                            + organizations.getValue().size(), organizations.getValue().size() == 1);
                    final String MSG = "ORGANIZATIONS attribute value is wrong, expected: %s, returned: %s.";
                    assertTrue(String.format(MSG, orgUid1, organizations.getValue().get(0)),
                            organizations.getValue().get(0).equals(orgUid1));
                }

            } finally {
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ACCOUNT, accountUid1,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ACCOUNT, accountUid2,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ORGANIZATION,
                        orgUid1, false, null);
            }

        } else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testAccountsPredAttributeOrg''.");
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests ACCOUNTS attribute contract for object class group
     */
    @Test
    public void testAccountsPredAttributeGroup() {
        final ObjectClassInfo accountInfo = findOInfo(ObjectClass.ACCOUNT);
        final ObjectClassInfo groupInfo = findOInfo(ObjectClass.GROUP);

        // run test only in case ACCOUNT and GROUP are supported and ACCOUNTS is supported for GROUPS
        if (accountInfo != null && groupInfo != null
                && ConnectorHelper.isCRU(accountInfo, PredefinedAttributes.ACCOUNTS_NAME)) {

            Uid accountUid1 = null;
            Uid accountUid2 = null;
            Uid groupUid1 = null;
            try {
                // create 1st account
                Set<Attribute> accountAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), accountInfo, getTestName(), 0, true, false);                
                accountUid1 = getConnectorFacade().create(ObjectClass.ACCOUNT, accountAttrs1, null);

                // create an org with ACCOUNTS set to created account
                Set<Attribute> groupAttrs1 = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), groupInfo, getTestName(), 0, true, false);
                for (Attribute attr : groupAttrs1) {
                    if (attr.is(PredefinedAttributes.ACCOUNTS_NAME)) {
                        groupAttrs1.remove(attr);
                        break;
                    }
                }
                groupAttrs1.add(AttributeBuilder.build(PredefinedAttributes.ACCOUNTS_NAME,
                		AttributeUtil.getStringValue(accountUid1)));
                
                groupUid1 = getConnectorFacade().create(ObjectClass.GROUP, groupAttrs1, null);

                // get the org to make sure it exists now
                ConnectorObject obj = getConnectorFacade().getObject(ObjectClass.GROUP,
                        groupUid1, getOperationOptionsByOp(GetApiOp.class));

                // check that object was created properly
                ConnectorHelper.checkObject(groupInfo, obj, groupAttrs1);

                // continue test only if update is supported for group and ACCOUNTS is multiValue
                if (ConnectorHelper.operationSupported(getConnectorFacade(), ObjectClass.GROUP,
                        UpdateApiOp.class) && ConnectorHelper.isMultiValue(groupInfo, PredefinedAttributes.ACCOUNTS_NAME)) {
                    // create another account
                    Set<Attribute> accountAttrs2 = ConnectorHelper.getCreateableAttributes(
                            getDataProvider(), accountInfo, getTestName(), 1, true, false);                    
                    accountUid2 = getConnectorFacade().create(ObjectClass.ACCOUNT, accountAttrs2, null);

                    // update group to contain both accounts
                    Set<Attribute> groupAttrs2 = new HashSet<Attribute>();
                    groupAttrs2.add(AttributeBuilder.build(PredefinedAttributes.ACCOUNTS_NAME,
                    		AttributeUtil.getStringValue(accountUid2)));
                    groupAttrs2.add(groupUid1);
                    groupUid1 = getConnectorFacade().addAttributeValues(
                            ObjectClass.GROUP, groupUid1, AttributeUtil.filterUid(groupAttrs2), null);

                    // get the org to make sure it exists now and values are correct
                    obj = getConnectorFacade().getObject(ObjectClass.GROUP, groupUid1,
                            getOperationOptionsByOp(GetApiOp.class));

                    // check that object was created properly
                    ConnectorHelper.checkObject(groupInfo, obj, UpdateApiOpTests
                            .mergeAttributeSets(groupAttrs1, groupAttrs2));
                }

            } finally {
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ACCOUNT, accountUid1,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.ACCOUNT, accountUid2,
                        false, null);
                ConnectorHelper.deleteObject(getConnectorFacade(), ObjectClass.GROUP,
                        groupUid1, false, null);
            }

        } else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testAccountsPredAttributeGroup''.");
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Returns ObjectClassInfo stored in connector schema for object class.
     */
    private ObjectClassInfo findOInfo(ObjectClass oclass) {
        Schema schema = getConnectorFacade().schema();
        for (ObjectClassInfo oinfo : schema.getObjectClassInfo()) {
            if (oinfo.is(oclass.getObjectClassValue())) {
                return oinfo;
            }
        }
        
        return null;
    }
    
    /**
     * <p>
     * Returns true if tests are configured to lockout tests
     * {@link MultiOpTests#testLockOutOpAttribute()}.
     * </p>
     * 
     * <p>
     * Returns true if tests are configured to test connector's lockout
     * operation. Some connectors implement lockout but are capable
     * to unlock but not lock.
     * </p>
     */
    private static boolean canLockOut() {
        // by default it's supposed that case insensitive search is disabled.
        Boolean canLockout = true;
        try {
            canLockout = !(Boolean) getDataProvider().getTestSuiteAttribute(
                    SKIP + "." + LOCKOUT_PREFIX,
                    TEST_NAME);

        } catch (ObjectNotFoundException ex) {
            // exceptions is throw in case property definition is not found
            // ok -- indicates enabling the property
        }

        return canLockout;
    }
}
