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

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


/**
 * Contract test of {@link SyncApiOp}
 */
@RunWith(Parameterized.class)
public class SyncApiOpTests extends ObjectClassRunner {
    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(SyncApiOpTests.class);
    private static final String TEST_NAME = "Sync";

    public SyncApiOpTests(ObjectClass oclass) {
        super(oclass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return SyncApiOp.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRun() throws Exception {
        Uid uid = null;
        Set<Attribute> attrs = null;
        List<SyncDelta> deltas = null;

        try {
            // create record
            attrs = getHelper().getAttributes(getDataProvider(), getObjectClassInfo(),
                    getTestName(), 0, true);
            uid = getConnectorFacade().create(getSupportedObjectClass(), attrs, getOperationOptionsByOp(CreateApiOp.class));
            assertNotNull("Create returned null uid.", uid);

            // use null SyncToken which means first sync for the resource is called
            // should throw RuntimeException when ObjectClass is not supported
            deltas = getHelper().sync(getConnectorFacade(), getObjectClass(), null, getOperationOptionsByOp(SyncApiOp.class));

            // check that returned one delta
            assertTrue("SyncResultsHandler#handle should be called once, but called "
                    + deltas.size() + " times.", deltas.size() == 1);
            // check that Uid is correct
            assertEquals("Sync returned wrong Uid, expected: " + uid + ",got: "
                    + deltas.get(0).getUid(), deltas.get(0).getUid(), uid);
            // check that attributes are correct
            getHelper().checkAttributes(attrs, deltas.get(0).getAttributes());
            // check that operation is CREATE
            assertTrue("Sync returned wrong delta type, expected CREATE, got: "
                    + deltas.get(0).getDeltaType(),
                    deltas.get(0).getDeltaType() == SyncDeltaType.CREATE);
        } finally {
            if (uid != null) {
                // cleanup test data
                getHelper().deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid, false,
                        getOperationOptionsByOp(DeleteApiOp.class));

                List<SyncDelta> delDeltas = null;
                // use SyncToken returned by previous sync call
                delDeltas = getHelper().sync(getConnectorFacade(), getObjectClass(),
                        deltas.get(0).getToken(), getOperationOptionsByOp(SyncApiOp.class));

                // check that returned one delta
                assertTrue("SyncResultsHandler#handle should be called once, but called "
                        + delDeltas.size() + " times.", delDeltas.size() == 1);
                // check that Uid is correct
                assertEquals("Sync returned wrong Uid, expected: " + uid + ",got: "
                        + delDeltas.get(0).getUid(), delDeltas.get(0).getUid(), uid);
                // check that operation is DELETE
                assertTrue("Sync returned wrong delta type, expected DELETE, got: "
                        + delDeltas.get(0).getDeltaType(),
                        delDeltas.get(0).getDeltaType() == SyncDeltaType.DELETE);
            }
        }
    }

    /**
     * Tests sync method with invalid SyncToken, RuntimeException is expected.
     */
    @Test
    public void testSyncFailInvalidSyncToken() {
        // run the test only if operation is supported
        if (getHelper().operationSupported(getConnectorFacade(), getAPIOperation())) {
            try {
                // pass an invalid SyncToken
                // should throw an exception
                getHelper().sync(getConnectorFacade(), getSupportedObjectClass(), new SyncToken("INVALIDTOKEN"), null);

                fail("Invalid token passed to sync, RuntimeException expected.");
            } catch (RuntimeException ex) {
                // ok
            }
        }
    }

    /**
     * Tests sync method with no data created and expects
     * {@link SyncResultsHandler#handle(SyncDelta)} won't be called.
     */
    @Test
    public void testSyncNoExistingDataToSynchronize() {
        // run the test only if operation is supported
        if (getHelper().operationSupported(getConnectorFacade(), getAPIOperation())) {
            // no data created, try to call sync
            final List<SyncDelta> deltas = getHelper().sync(getConnectorFacade(), getSupportedObjectClass(), null, null);
            assertTrue("SyncResultsHandler#handle shouldn't be called when don't exist data to synchronize.", deltas.size()==0);
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
