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
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
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
        // TODO
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
        Uid uid = null;
        try {
            ObjectClass occ = getObjectClass();
            ObjectClassInfo ocii = getObjectClassInfo();
            
            ObjectClassInfo oci = getObjectClassInfo();

            // create a new user
            Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(
                    getDataProvider(), oci, getTestName(), 0, true, false);
            // should throw UnsupportedObjectClass if not supported
            uid = getConnectorFacade().create(getObjectClass(), attrs,
                    getOperationOptionsByOp(CreateApiOp.class));

            // get the user to make sure it exists now
            ConnectorObject obj = getConnectorFacade().getObject(
                    getObjectClass(), uid,
                    getOperationOptionsByOp(GetApiOp.class));

            assertNotNull("Unable to retrieve newly created object", obj);

            // check: non readable attributes should not be returned by default
            for (Attribute attr : obj.getAttributes()) {
                if (!ConnectorHelper.isReadable(oci, attr)) {
                    String msg = String
                            .format(
                                    "Non-readable attribute should not be returned by default: %s",
                                    attr.getName());
                    assertTrue(msg, !ConnectorHelper.isReturnedByDefault(oci,
                            attr));
                }
            }
        } finally {
            if (uid != null) {
                // delete the object
                getConnectorFacade().delete(getSupportedObjectClass(), uid,
                        getOperationOptionsByOp(DeleteApiOp.class));
            }
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

            Uid uid = null;
            try {
                ObjectClassInfo oci = getObjectClassInfo();

                // create a new user
                Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(
                        getDataProvider(), oci, getTestName(), 0, true, false);
                // should throw UnsupportedObjectClass if not supported
                uid = getConnectorFacade().create(getObjectClass(), attrs,
                        getOperationOptionsByOp(CreateApiOp.class));

                /*
                 * ************ GetApiOp ************
                 */
                // get the user to make sure it exists now
                ConnectorObject obj = null;
                switch (apiOp) {
                case GET:
                    obj = getConnectorFacade().getObject(getObjectClass(), uid,
                            getOperationOptionsByOp(GetApiOp.class));
                    break;// GET

                case SEARCH:
                    Filter fltUid = FilterBuilder.equalTo(AttributeBuilder
                            .build(Uid.NAME, uid));

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

                // case SYNC:
                // TODO uncomment and support sync
                // break;// SYNC
                }

                assertNotNull("Unable to retrieve newly created object", obj);

                // Check if attribute set contains non-returned by default
                // Attributes.
                for (Attribute attr : obj.getAttributes()) {
                    String msg = String
                            .format(
                                    "Attribute %s returned. However it is _not_ returned by default.",
                                    attr.getName());
                    assertTrue(msg, ConnectorHelper.isReturnedByDefault(oci,
                            attr) == false);
                }
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
}

enum ApiOperations {
    SEARCH(SearchApiOp.class), GET(GetApiOp.class)/*
                                                     * , SYNC(SyncApiOp.class)
                                                     * TODO uncomment and
                                                     * support sync
                                                     */;

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