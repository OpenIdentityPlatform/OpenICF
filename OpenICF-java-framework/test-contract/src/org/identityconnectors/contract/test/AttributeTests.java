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
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
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
 * Test that attributes satisfy contract.
 * 
 * @author David Adam
 */
@RunWith(Parameterized.class)
public class AttributeTests extends ObjectClassRunner {

    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(GetApiOpTests.class);
    private static final String TEST_NAME = "Attribute";

    public AttributeTests(ObjectClass oclass) {
        super(oclass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return CreateApiOp.class; // because without create the tests could
        // not be run.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRun() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestName() {
        return TEST_NAME;
    }

    /**
     * <p>
     * Non readable attributes are not returned by default
     * </p>
     * <p>
     * API operations of acquiring attributes: <code>GetApiOp</code>
     * </p>
     */
    @Test
    public void testNonReadable() {
        if (ConnectorHelper.operationSupported(getConnectorFacade(),
                getObjectClass(), CreateApiOp.class)) {
            Uid uid = null;
            try {
                ObjectClass occ = getObjectClass();
                ObjectClassInfo ocii = getObjectClassInfo();

                ObjectClassInfo oci = getObjectClassInfo();

                // create a new user
                Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), oci, getTestName(), 0, true, false);
                // should throw UnsupportedObjectClass if not supported
                uid = getConnectorFacade().create(getSupportedObjectClass(),
                        attrs, getOperationOptionsByOp(CreateApiOp.class));

                // get the user to make sure it exists now
                ConnectorObject obj = getConnectorFacade().getObject(
                        getObjectClass(), uid,
                        getOperationOptionsByOp(GetApiOp.class));

                assertNotNull("Unable to retrieve newly created object", obj);

                // check: non readable attributes should not be returned by
                // default
                for (Attribute attr : obj.getAttributes()) {
                    if (!ConnectorHelper.isReadable(oci, attr)) {
                        String msg = String
                                .format(
                                        "Non-readable attribute should not be returned by default: %s",
                                        attr.getName());
                        assertTrue(msg, !ConnectorHelper.isReturnedByDefault(
                                oci, attr));
                    }
                }
            } finally {
                if (uid != null) {
                    // delete the object
                    getConnectorFacade().delete(getSupportedObjectClass(), uid,
                            getOperationOptionsByOp(DeleteApiOp.class));
                }
            }
        } else {
            LOG
                    .info("----------------------------------------------------------------------------------------");
            LOG
                    .info(
                            "Skipping test ''testNonReadable'' for object class ''{0}''.",
                            getObjectClass());
            LOG
                    .info("----------------------------------------------------------------------------------------");
        }
    }

    /**
     * <p>
     * not returned by default attributes should not be returned, unless
     * specified in attributesToGet ({@link OperationOptions})
     * </p>
     * <p>
     * API operations of acquiring attributes: <code>GetApiOp</code>
     * </p>
     * <ul>
     * <li>{@link GetApiOp}</li>
     * <li>{@link SearchApiOp}</li>
     * <li>{@link SyncApiOp}</li>
     * </ul>
     */
    @Test
    public void testReturnedByDefault() {
        for (ApiOperations apiop : ApiOperations.values()) {
            testReturnedByDefault(apiop);
        }
    }

    /**
     * {@link AttributeTests#testReturnedByDefault()}
     * 
     * @param apiOp
     *            the type of ApiOperation, that shall be tested.
     */
    private void testReturnedByDefault(ApiOperations apiOp) {
        // run the contract test only if <strong>apiOp</strong> APIOperation is
        // supported
        if (ConnectorHelper.operationSupported(getConnectorFacade(),
                getObjectClass(), apiOp.getClazz())) {
            
            // start synchronizing from now
            SyncToken token = null;
            if (apiOp.equals(ApiOperations.SYNC)) { // just for SyncApiOp test
                token = getConnectorFacade().getLatestSyncToken();
            }

            Uid uid = null;
            try {
                ObjectClassInfo oci = getObjectClassInfo();

                // create a new user
                Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), oci, getTestName(), 0, true, false);
                // should throw UnsupportedObjectClass if not supported
                uid = getConnectorFacade().create(getObjectClass(), attrs,
                        getOperationOptionsByOp(CreateApiOp.class));
                assertNotNull("Create returned null uid.", uid);

                /*
                 * ************ GetApiOp ************
                 */
                // get the user to make sure it exists now
                ConnectorObject obj = null;
                switch (apiOp) {
                case GET:
                    /* last _null_ param - no operation option, response contains just attributes returned by default*/
                    obj = getConnectorFacade().getObject(getObjectClass(), uid, null);
                    break;// GET

                case SEARCH:
                    Filter fltUid = FilterBuilder.equalTo(AttributeBuilder
                            .build(Uid.NAME, uid.getUidValue()));

                    assertTrue(fltUid != null);

                    List<ConnectorObject> coObjects = ConnectorHelper.search(
                            getConnectorFacade(), getSupportedObjectClass(),
                            fltUid, null);

                    assertTrue(
                            "Search filter by uid with no OperationOptions failed, expected to return one object, but returned "
                                    + coObjects.size(), coObjects.size() == 1);

                    assertNotNull("Unable to retrieve newly created object",
                            coObjects.get(0));

                    obj = coObjects.get(0);
                    break;// SEARCH

                case SYNC:
                    obj = testSync(uid, token, attrs, oci);
                    break;// SYNC
                }

                assertNotNull("Unable to retrieve newly created object", obj);

                /*
                 * Check if attribute set contains non-returned by default
                 * Attributes. This is specific for AttributeTests
                 */
                checkAttributes(obj, oci);

            } finally {
                if (uid != null) {
                    // delete the created user
                    getConnectorFacade().delete(getSupportedObjectClass(), uid,
                            getOperationOptionsByOp(DeleteApiOp.class));
                }
            }
        } else {
            LOG
                    .info("----------------------------------------------------------------------------------------");
            LOG
                    .info(
                            "Skipping test ''testReturnedByDefault'' for object class ''{0}''.",
                            getObjectClass());
            LOG
                    .info("----------------------------------------------------------------------------------------");
        }
    }

    /** Main checking of "no returned by default" attributes */
    private void checkAttributes(ConnectorObject obj, ObjectClassInfo oci) {
        // Check if attribute set contains non-returned by default
        // Attributes.
        for (Attribute attr : obj.getAttributes()) {
            String msg = String
                    .format(
                            "Attribute %s returned. However it is _not_ returned by default.",
                            attr.getName());
            /*
             * this is a hack that skips control of UID, as it is presently 
             * non returned by default, however it is automatically returned.
             * see discussion in Issue mailing list -- Issue #334
             * future TODO: after joining UID to schema, erase the condition.
             */
            if (!attr.getName().equals(Uid.NAME)) {
                assertTrue(msg, ConnectorHelper.isReturnedByDefault(oci, attr));
            }
        }
    }

    /**
     * test sync
     * 
     * @param token
     *            initialized token
     * @param attrs
     *            newly created attributes
     * @param uid
     *            the newly created object
     * @param oci
     *            object class info
     * @return the connector object that contains the differences.
     */
    private ConnectorObject testSync(Uid uid, SyncToken token,
            Set<Attribute> attrs, ObjectClassInfo oci) {
        List<SyncDelta> deltas = null;
        String msg = null;

        /*
         * CREATE: (was handled in the calling method, result of create is in
         * param uid, cleanup is also in caller method.)
         */

        if (SyncApiOpTests.canSyncAfterOp(CreateApiOp.class)) {
            // sync after create
            deltas = ConnectorHelper.sync(getConnectorFacade(),
                    getObjectClass(), token,
                    getOperationOptionsByOp(SyncApiOp.class));

            // check that returned one delta
            msg = "Sync should have returned one sync delta after creation of one object, but returned: %d";
            assertTrue(String.format(msg, deltas.size()), deltas.size() == 1);

            // check delta
            ConnectorHelper.checkSyncDelta(getObjectClassInfo(), deltas.get(0),
                    uid, attrs, SyncDeltaType.CREATE_OR_UPDATE, true);

            /*
             * check the attributes inside delta This is specific for
             * AttributeTests
             */
            ConnectorObject obj = deltas.get(0).getObject();
            checkAttributes(obj, oci);

            token = deltas.get(0).getToken();
        }

        /* UPDATE: */

        if (ConnectorHelper.operationSupported(getConnectorFacade(),
                UpdateApiOp.class)
                && SyncApiOpTests.canSyncAfterOp(UpdateApiOp.class)) {

            Set<Attribute> replaceAttributes = ConnectorHelper
                    .getUpdateableAttributes(getDataProvider(),
                            getObjectClassInfo(), getTestName(),
                            SyncApiOpTests.MODIFIED, 0, false, false);

            // update only in case there is something to update
            if (replaceAttributes.size() > 0) {
                replaceAttributes.add(uid);

                assertTrue("no update attributes were found",
                        (replaceAttributes.size() > 0));
                Uid newUid = getConnectorFacade().update(
                        UpdateApiOp.Type.REPLACE, getSupportedObjectClass(),
                        replaceAttributes,
                        getOperationOptionsByOp(UpdateApiOp.class));

                // Update change of Uid must be propagated to
                // replaceAttributes
                if (!newUid.equals(uid)) {
                    replaceAttributes.remove(uid);
                    replaceAttributes.add(newUid);
                    uid = newUid;
                }

                // sync after update
                deltas = ConnectorHelper.sync(getConnectorFacade(),
                        getObjectClass(), token,
                        getOperationOptionsByOp(SyncApiOp.class));

                // check that returned one delta
                msg = "Sync should have returned one sync delta after update of one object, but returned: %d";
                assertTrue(String.format(msg, deltas.size()),
                        deltas.size() == 1);

                // check delta
                ConnectorHelper.checkSyncDelta(getObjectClassInfo(), deltas
                        .get(0), uid, replaceAttributes,
                        SyncDeltaType.CREATE_OR_UPDATE, true);

                /*
                 * check the attributes inside delta This is specific for
                 * AttributeTests
                 */
                ConnectorObject obj = deltas.get(0).getObject();
                checkAttributes(obj, oci);

                token = deltas.get(0).getToken();
            }
        }

        /* DELETE: */

        if (SyncApiOpTests.canSyncAfterOp(DeleteApiOp.class)) {
            // delete object
            getConnectorFacade().delete(getObjectClass(), uid,
                    getOperationOptionsByOp(DeleteApiOp.class));

            // sync after delete
            deltas = ConnectorHelper.sync(getConnectorFacade(),
                    getObjectClass(), token,
                    getOperationOptionsByOp(SyncApiOp.class));

            // check that returned one delta
            msg = "Sync should have returned one sync delta after delete of one object, but returned: %d";
            assertTrue(String.format(msg, deltas.size()), deltas.size() == 1);

            // check delta
            ConnectorHelper.checkSyncDelta(getObjectClassInfo(), deltas.get(0),
                    uid, null, SyncDeltaType.DELETE, true);

            /*
             * check the attributes inside delta This is specific for
             * AttributeTests
             */
            ConnectorObject obj = deltas.get(0).getObject();
            checkAttributes(obj, oci);
        }

        return null;
    }
}

/** helper inner class for passing the type of tested operations */
enum ApiOperations {
    SEARCH(SearchApiOp.class), GET(GetApiOp.class), SYNC(SyncApiOp.class);
    private final String s;
    private final Class<? extends APIOperation> clazz;

    private ApiOperations(Class<? extends APIOperation> c) {
        this.s = c.getName();
        this.clazz = c;
    }

    @Override
    public String toString() {
        return s;
    }

    public Class<? extends APIOperation> getClazz() {
        return clazz;
    }
}