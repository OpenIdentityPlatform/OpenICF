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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;


/**
 * Contract test of {@link UpdateApiOp} 
 */
@RunWith(Parameterized.class)
public class UpdateApiOpTests extends ObjectClassRunner {
    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(UpdateApiOpTests.class);
    
    private static final String MODIFIED = "modified";
    private static final String ADDED = "added";
    private static final String TEST_NAME = "Update";

    public UpdateApiOpTests(ObjectClass objectClass) {
        super(objectClass);
    }
    
    /**
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return UpdateApiOp.class;
    }
    
    /**
     * {@inheritDoc}      
     */
    @Override
    public void testRun() {
        ConnectorObject obj = null;
        Uid uid = null;


        try {
            // create an object to update
            uid = ConnectorHelper.createObject(getConnectorFacade(), getDataProvider(),
                    getObjectClassInfo(), getTestName(), 0, getOperationOptionsByOp(CreateApiOp.class));
            assertNotNull("Create returned null Uid.", uid);

            // get by uid
            obj = getConnectorFacade().getObject(getSupportedObjectClass(), uid, getOperationOptionsByOp(GetApiOp.class));
            assertNotNull("Cannot retrieve created object.", obj);

            Set<Attribute> replaceAttributes = ConnectorHelper.getUpdateableAttributes(
                    getDataProvider(), getObjectClassInfo(), getTestName(), MODIFIED, 0, false,
                    false);

            if (replaceAttributes.size() > 0 || !isObjectClassSupported()) {
                // update only in case there is something to update or when object class is not supported
                replaceAttributes.add(uid);

                assertTrue("no update attributes were found", (replaceAttributes.size() > 0));
                Uid newUid = getConnectorFacade().update(UpdateApiOp.Type.REPLACE,
                        getObjectClass(), replaceAttributes, getOperationOptionsByOp(UpdateApiOp.class));

                // Update change of Uid must be propagated to replaceAttributes
                // set
                if (!newUid.equals(uid)) {
                    replaceAttributes.remove(uid);
                    replaceAttributes.add(newUid);
                    uid = newUid;
                }
            }

            // verify the change
            obj = getConnectorFacade().getObject(getSupportedObjectClass(), uid,
                    getOperationOptionsByOp(GetApiOp.class));
            assertNotNull("Cannot retrieve updated object.", obj);
            ConnectorHelper.checkObject(getObjectClassInfo(), obj, replaceAttributes);

            // ADD and DELETE update test:
            // set of *multivalue* attributes with generated values
            Set<Attribute> addDelAttrs = ConnectorHelper.getUpdateableAttributes(getDataProvider(),
                    getObjectClassInfo(), getTestName(), ADDED, 0, false, true);
            if (addDelAttrs.size() > 0) {
                // uid must be present for update
                addDelAttrs.add(uid);
                Uid newUid = getConnectorFacade().update(UpdateApiOp.Type.ADD, getObjectClass(),
                        addDelAttrs, getOperationOptionsByOp(UpdateApiOp.class));

                // Update change of Uid
                if (!newUid.equals(uid)) {
                    replaceAttributes.remove(uid);
                    addDelAttrs.remove(uid);
                    replaceAttributes.add(newUid);
                    addDelAttrs.add(newUid);
                    uid = newUid;
                }

                // verify the change after ADD
                obj = getConnectorFacade().getObject(getSupportedObjectClass(), uid,
                        getOperationOptionsByOp(GetApiOp.class));
                assertNotNull("Cannot retrieve updated object.", obj);
                // don't want to have two same values for UID attribute
                addDelAttrs.remove(uid);
                ConnectorHelper.checkObject(getObjectClassInfo(), obj,
                        mergeAttributeSets(addDelAttrs, replaceAttributes));
                addDelAttrs.add(uid);

                // delete added attribute values
                newUid = getConnectorFacade().update(UpdateApiOp.Type.DELETE, getObjectClass(),
                        addDelAttrs, getOperationOptionsByOp(UpdateApiOp.class));

                // Update change of Uid must be propagated to replaceAttributes
                if (!newUid.equals(uid)) {
                    replaceAttributes.remove(uid);
                    addDelAttrs.remove(uid);
                    replaceAttributes.add(newUid);
                    addDelAttrs.add(newUid);
                    uid = newUid;
                }

                // verify the change after DELETE
                obj = getConnectorFacade().getObject(getSupportedObjectClass(), uid,
                        getOperationOptionsByOp(GetApiOp.class));
                assertNotNull("Cannot retrieve updated object.", obj);
                ConnectorHelper.checkObject(getObjectClassInfo(), obj, replaceAttributes);
            }                        
        } finally {
            if (uid != null) {
                // finally ... get rid of the object
                ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid,
                        false, getOperationOptionsByOp(DeleteApiOp.class));
            }
        }
    }   
    
    /**
     * The test verifies that connector doesn't throw NullPointerException or some other unexpected behavior when passed null as 
     * attribute value. Test passes null values only for non-required non-special updateable attributes.
     */
    @Test
    public void testUpdateToNull() {
        if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(),
                getAPIOperation())) {
            ConnectorObject obj = null;
            Uid uid = null;

            try {
                // create an object to update
                uid = ConnectorHelper.createObject(getConnectorFacade(), getDataProvider(),
                        getObjectClassInfo(), getTestName(), 2, getOperationOptionsByOp(CreateApiOp.class));
                assertNotNull("Create returned null Uid.", uid);

                Set<Attribute> nullAttributes = new HashSet<Attribute>();
                for (AttributeInfo attInfo : getObjectClassInfo().getAttributeInfo()) {
                    if (attInfo.isUpdateable() && !attInfo.isRequired() && !AttributeUtil.isSpecial(attInfo)) {
                        nullAttributes.add(AttributeBuilder.build(attInfo.getName(), (Object) null));
                    }
                }

                if (nullAttributes.size() > 0) {
                    // update only in case there is something to update
                    nullAttributes.add(uid);

                    assertTrue("no update attributes were found", (nullAttributes.size() > 0));
                    
                    try {
                        Uid newUid = getConnectorFacade().update(UpdateApiOp.Type.REPLACE,
                            getObjectClass(), nullAttributes,
                            getOperationOptionsByOp(UpdateApiOp.class));
                        
                        LOG.info("No exception was thrown, attributes should be removed or their values set to null.");
                        
                        // update uid
                        if (!newUid.equals(uid)) {
                            uid = newUid;
                        }
                        
                        // verify the change
                        obj = getConnectorFacade().getObject(getObjectClass(), uid,
                                getOperationOptionsByOp(GetApiOp.class));
                        assertNotNull("Cannot retrieve updated object.", obj);
                        
                        // check that nulled attributes were removed or set to null
                        for (Attribute attribute : nullAttributes) {                            
                            if (ConnectorHelper.isReadable(getObjectClassInfo(), attribute)) {
                                // null in case attribute is not present
                                Attribute checkedAttribute = obj.getAttributeByName(attribute.getName());
                                final String MSG = "Attribute '%s' was neither removed nor its value set to null.";
                                assertTrue(String.format(MSG, attribute), checkedAttribute == null || checkedAttribute.equals(attribute));
                            }
                        }
                    }
                    catch (IllegalArgumentException ex) {
                        // ok, this option is possible in case connector cannot neither remove the attribute entirely
                        // nor set its value to null
                        LOG.info("IllegalArgumentException was thrown.");
                    }                                                            
                }   
                else {
                    LOG.info("No attributes which can be set to null for object class ''{0}''.", getObjectClass());
                }
            } finally {
                if (uid != null) {
                    // finally ... get rid of the object
                    ConnectorHelper.deleteObject(getConnectorFacade(), getObjectClass(), uid,
                            false, getOperationOptionsByOp(DeleteApiOp.class));
                }
            }
        } else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testUpdateToNull'' for object class ''{0}''.",
                    getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }
    
    /**
     * Tests create of two different objects and then update one to the same
     * attributes as the second. Test that updated object did not update uid to the same value as the first object. 
     */
    @Test
    public void testUpdateToSameAttributes() {
        if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), getAPIOperation())) {
            Uid uid1 = null;
            Uid uid2 = null;
            
            try {
                // create two new objects
                Set<Attribute> attrs1 = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), 1, true, false);
                uid1 = getConnectorFacade().create(getSupportedObjectClass(), attrs1, null);
                assertNotNull("Create returned null uid.", uid1);
                
                // get the object to make sure it exist now
                ConnectorObject obj1 = getConnectorFacade().getObject(getSupportedObjectClass(),
                        uid1, getOperationOptionsByOp(GetApiOp.class));
                
                // compare requested attributes to retrieved attributes
                ConnectorHelper.checkObject(getObjectClassInfo(), obj1, attrs1);
                
                Set<Attribute> attrs2 = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), 2, true, false);
                uid2 = getConnectorFacade().create(getSupportedObjectClass(), attrs2, null);
                assertNotNull("Create returned null uid.", uid2);
                
                // get the object to make sure it exist now
                ConnectorObject obj2 = getConnectorFacade().getObject(getSupportedObjectClass(),
                        uid2, getOperationOptionsByOp(GetApiOp.class));
                
                // compare requested attributes to retrieved attributes
                ConnectorHelper.checkObject(getObjectClassInfo(), obj2, attrs2);
                                
                // update second object with updateable attributes of first object
                Set<Attribute> replaceAttributes = new HashSet<Attribute>();
                for (Attribute attr : attrs1) {
                    if (ConnectorHelper.isUpdateable(getObjectClassInfo(), attr)) {
                        replaceAttributes.add(attr);
                    }
                }
                replaceAttributes.add(uid2);
                
                try {
                    Uid newUid = getConnectorFacade().update(UpdateApiOp.Type.REPLACE,
                        getSupportedObjectClass(), replaceAttributes, getOperationOptionsByOp(UpdateApiOp.class));
                
                    if (!uid2.equals(newUid)) {
                        uid2 = newUid;
                    }

                
                    assertFalse("Update returned the same uid when tried to update to the same " +
                		"attributes as another object.", uid1.equals(uid2));
                }
                catch (RuntimeException ex) {
                    // ok - update could throw this in case @@NAME@@ and @@UID@@ are the same attributes
                }
            } finally {
                if (uid1 != null) {
                    // delete the object
                    ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid1,
                            false, getOperationOptionsByOp(DeleteApiOp.class));
                }
                if (uid2 != null) {
                    // delete the object
                    ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid2,
                            false, getOperationOptionsByOp(DeleteApiOp.class));
                }
            }
        }
        else {
            LOG.info("----------------------------------------------------------------------------------------");
            LOG.info("Skipping test ''testUpdateToSameAttributes'' for object class ''{0}''.", getObjectClass());
            LOG.info("----------------------------------------------------------------------------------------");
        }
    }

    @Override
    public String getTestName() {
        return TEST_NAME;
    }
    
    /**
     * Returns new attribute set which contains all attributes from both sets. If attribute with the same name is present
     * in both sets then its values are merged.
     */
    protected static Set<Attribute> mergeAttributeSets(Set<Attribute> attrSet1, Set<Attribute> attrSet2) {
        Set<Attribute> attrs = new HashSet<Attribute>();
        Map<String, Attribute> attrMap2 = new HashMap<String, Attribute>();
        for (Attribute attr : attrSet2) {
            attrMap2.put(attr.getName(), attr);
        }

        for (Attribute attr1 : attrSet1) {
            Attribute attr2 = attrMap2.remove(attr1.getName());
            // if attribute is present in both sets then merge its values
            if (attr2 != null) {
                AttributeBuilder attrBuilder = new AttributeBuilder();
                attrBuilder.setName(attr1.getName());
                attrBuilder.addValue(attr1.getValue());
                attrBuilder.addValue(attr2.getValue());
                attrs.add(attrBuilder.build());
            } else {
                attrs.add(attr1);
            }
        }

        // add remaining attributes from second set
        for (Attribute attr2 : attrMap2.values()) {
            attrs.add(attr2);
        }

        return attrs;
    }
}
