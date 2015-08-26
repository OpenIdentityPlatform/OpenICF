/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.framework.impl.api.local.operations;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.api.operations.batch.BatchBuilder;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl.ReferenceCounter;
import org.identityconnectors.mockconnector.MockAllOpsConnector;
import org.identityconnectors.mockconnector.MockAllOpsHandleBatchConnector;
import org.identityconnectors.mockconnector.MockAllOpsSyncBatchConnector;
import org.identityconnectors.mockconnector.MockAllOpsTokenBatchConnector;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Tests the use cases for batch implementation:
 *
 * <ul>
 *     <li>Use Case 0 - Connector does not implement batch at all</li>
 *     <li>Use Case 1 - Connector implement batch support fully synchronously</li>
 *     <li>Use Case 2 - Connector implements asynchronous token-based batch</li>
 *     <li>Use Case 3 - Connector implements results callback batch</li>
 * </ul>
 */
public class BatchImplTests {
    private BatchBuilder batch;
    private OperationOptions options;
    private ReferenceCounter referenceCounter = new ReferenceCounter();

    @BeforeTest
    void setupBatchAndOptions() {
        options = new OperationOptions(new HashMap<String, Object>() {{
            put(OperationOptions.OP_FAIL_ON_ERROR, Boolean.TRUE);
        }});
        batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("foo"), options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("foo"), new HashSet<Attribute>(), options);
    }

    @Test
    public void testUseCase0() {
        BatchImpl impl = new BatchImpl(null, new MockAllOpsConnector(), referenceCounter);
        final List<Object> results = new ArrayList<Object>();
        final Subscription sub = impl.executeBatch(batch.build(), new Observer<BatchResult>() {
            public void onCompleted() {
            }

            public void onError(Throwable e) {
                results.add(e);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
            }
        }, options);

        assertEquals(results.size(), 3);
        assertNull(sub);
    }

    @Test
    public void testUseCase1() {
        BatchImpl impl = new BatchImpl(null, new MockAllOpsSyncBatchConnector(), referenceCounter);
        final List<Object> results = new ArrayList<Object>();
        final Subscription sub = impl.executeBatch(batch.build(), new Observer<BatchResult>() {
            public void onCompleted() {
            }

            public void onError(Throwable e) {
                results.add(e);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
            }
        }, options);

        assertEquals(results.size(), 3);
        assertNull(sub.getReturnValue());
    }

    @Test
    public void testUseCase2() {
        BatchImpl impl = new BatchImpl(null, new MockAllOpsTokenBatchConnector(), referenceCounter);
        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean batchComplete = new AtomicBoolean(false);
        final AtomicBoolean batchError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                batchComplete.set(true);
            }

            public void onError(Throwable e) {
                results.add(e);
                batchError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                batchError.set(batchError.get() | batchResult.getError());
                batchComplete.set(batchComplete.get() | batchResult.getComplete());
            }
        };

        Subscription sub = impl.executeBatch(batch.build(), observer, options);

        assertNotEquals(results.size(), 3);
        assertNotNull(sub);
        assertNotNull(sub.getReturnValue());
        assertFalse(sub.isUnsubscribed());

        BatchToken token = (BatchToken) sub.getReturnValue();

        sleep(3000);

        sub = impl.queryBatch(token, observer, options);
        assertEquals(results.size(), 3);
        assertTrue(sub.isUnsubscribed());
        assertTrue(batchComplete.get());
        assertFalse(batchError.get());
    }

    @Test
    public void testUseCase3() {
        BatchImpl impl = new BatchImpl(null, new MockAllOpsHandleBatchConnector(), referenceCounter);
        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean batchComplete = new AtomicBoolean(false);
        final AtomicBoolean batchError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                batchComplete.set(true);
            }

            public void onError(Throwable e) {
                results.add(e);
                batchError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                batchError.set(batchError.get() | batchResult.getError());
                batchComplete.set(batchComplete.get() | batchResult.getComplete());
            }
        };

        Subscription sub = impl.executeBatch(batch.build(), observer, options);

        assertNotEquals(results.size(), 3);
        assertNotNull(sub);
        assertNotNull(sub.getReturnValue());

        sleep(3000);

        assertEquals(results.size(), 3);
        assertTrue(sub.isUnsubscribed());
        assertTrue(batchComplete.get());
        assertFalse(batchError.get());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) { /**/ }
    }
}
