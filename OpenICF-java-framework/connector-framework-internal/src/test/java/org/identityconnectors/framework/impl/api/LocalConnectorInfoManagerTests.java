/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.framework.impl.api;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.identityconnectors.common.Version;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorInfoManagerFactory;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.common.FrameworkUtilTestHelpers;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.api.operations.batch.BatchBuilder;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class LocalConnectorInfoManagerTests extends ConnectorInfoManagerTestBase {

    /**
     * Setup logging for the {@link LocalConnectorInfoManagerTests}.
     */
    private static final Log logger = Log.getLog(LocalConnectorInfoManagerTests.class);

    /**
     * Tests that the framework refuses to load a bundle that requests a
     * framework version newer than the one present.
     */
    @Test(priority = -1)
    public void testCheckVersion() throws Exception {
        // The test bundles require framework 1.0, so pretend the framework is
        // older.
        FrameworkUtilTestHelpers.setFrameworkVersion(Version.parse("0.5"));
        try {
            List<URL> urls = getTestBundles();
            Assert.assertFalse(urls.isEmpty());
            ConnectorInfoManagerFactory.getInstance().getLocalManager(urls.get(0));
            Assert.fail();
        } catch (ConfigurationException e) {
            if (!e.getMessage().contains("unrecognized framework version")) {
                Assert.fail();
            }
        }
    }

    /**
     * A bundle entry such as {@code lib/../../x.jar} must not be written
     * outside of the bundle's temporary directory (zip slip).
     */
    @Test
    public void testRejectsBundleEntryEscapingTempDirectory() throws Exception {
        // bundles are expanded into java.io.tmpdir/bundle-<random>/, so the
        // entry below points two levels up, straight into java.io.tmpdir
        String escapedName = "escaped-" + UUID.randomUUID() + ".jar";
        File escaped = new File(System.getProperty("java.io.tmpdir"), escapedName);

        File bundle = File.createTempFile("evil-bundle", ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("ConnectorBundle-FrameworkVersion", "1.0");
        manifest.getMainAttributes().putValue("ConnectorBundle-Name", "evil");
        manifest.getMainAttributes().putValue("ConnectorBundle-Version", "1.0");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(bundle), manifest);
        try {
            // a regular entry first, so that lib/ exists when the escaping
            // entry is expanded and lib/../.. resolves
            out.putNextEntry(new JarEntry("lib/ok.jar"));
            out.write(new byte[] { 0 });
            out.closeEntry();
            out.putNextEntry(new JarEntry("lib/../../" + escapedName));
            out.write(new byte[] { 0 });
            out.closeEntry();
        } finally {
            out.close();
        }
        try {
            ConnectorInfoManagerFactory.getInstance().getLocalManager(bundle.toURI().toURL());
            Assert.fail("Expected the bundle to be refused");
        } catch (ConfigurationException expected) {
            assertFalse(escaped.exists(), "bundle entry written outside of its temp directory: "
                    + escaped);
        } finally {
            // nothing to evict: a bundle that fails to load is never cached,
            // and clearing the local cache here would leave pooled connector
            // instances of the other tests behind with a stale class loader
            escaped.delete();
            bundle.delete();
        }
    }

    /**
     * To be overridden by subclasses to get different ConnectorInfoManagers
     *
     * @return
     * @throws Exception
     */
    @Override
    protected ConnectorInfoManager getConnectorInfoManager() throws Exception {
        List<URL> urls = getTestBundles();
        ConnectorInfoManagerFactory fact = ConnectorInfoManagerFactory.getInstance();
        ConnectorInfoManager manager = fact.getLocalManager(urls.toArray(new URL[0]));
        return manager;
    }

    @Override
    protected void shutdownConnnectorInfoManager() {
        ConnectorFacadeFactory.getInstance().dispose();
        ConnectorInfoManagerFactory.getInstance().clearLocalCache();
    }

    @Test
    public void testBatchUseCase0and1() throws Exception {
        ConnectorInfoManager manager = getConnectorInfoManager();
        ConnectorInfo info =
                findConnectorInfo(manager, "1.0.0.0",
                        "org.identityconnectors.testconnector.TstConnector");

        APIConfiguration api = info.createDefaultAPIConfiguration();
        api.setProducerBufferSize(0);

        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(OperationOptions.OP_FAIL_ON_ERROR, true);
        builder.setOption("TEST_USECASE1", true);
        final OperationOptions options = builder.build();

        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("foo"), options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("foo"), new HashSet<Attribute>(), options);

        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                isComplete.set(true);
            }

            public void onError(Throwable e) {
                hasError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
            }
        };

        Subscription sub = facade.executeBatch(batch.build(), observer, options);
        assertEquals(results.size(), batch.build().size());
        assertTrue(isComplete.get());
        assertFalse(hasError.get());
        assertNull(sub.getReturnValue());
    }

    @Test
    public void testBatchUseCase2() throws Exception {
        ConnectorInfoManager manager = getConnectorInfoManager();
        ConnectorInfo info =
                findConnectorInfo(manager, "1.0.0.0",
                        "org.identityconnectors.testconnector.TstConnector");

        APIConfiguration api = info.createDefaultAPIConfiguration();
        api.setProducerBufferSize(0);

        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(OperationOptions.OP_FAIL_ON_ERROR, true);
        builder.setOption("TEST_USECASE2", true);
        final OperationOptions options = builder.build();

        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("foo"), options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("foo"), new HashSet<Attribute>(), options);

        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                isComplete.set(true);
            }

            public void onError(Throwable e) {
                hasError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
            }
        };

        Subscription sub = facade.executeBatch(batch.build(), observer, options);
        assertEquals(results.size(), 0);
        assertFalse(isComplete.get());
        assertFalse(hasError.get());
        assertNotNull(sub.getReturnValue());

        Thread.sleep(500);
        sub = facade.queryBatch((BatchToken) sub.getReturnValue(), observer, options);

        assertEquals(results.size(), batch.build().size());
        assertTrue(isComplete.get());
        assertFalse(hasError.get());
        assertNull(sub.getReturnValue());
    }

    @Test
    public void testBatchUseCase2Failure() throws Exception {
        ConnectorInfoManager manager = getConnectorInfoManager();
        ConnectorInfo info =
                findConnectorInfo(manager, "1.0.0.0",
                        "org.identityconnectors.testconnector.TstConnector");

        APIConfiguration api = info.createDefaultAPIConfiguration();
        api.setProducerBufferSize(0);

        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(OperationOptions.OP_FAIL_ON_ERROR, true);
        builder.setOption("TEST_USECASE2", true);
        builder.setOption("FAIL_TEST_ITERATION", 1);
        final OperationOptions options = builder.build();

        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("foo"), options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("foo"), new HashSet<Attribute>(), options);

        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                isComplete.set(true);
            }

            public void onError(Throwable e) {
                hasError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
            }
        };

        Subscription sub = facade.executeBatch(batch.build(), observer, options);
        assertEquals(results.size(), 0);
        assertFalse(isComplete.get());
        assertFalse(hasError.get());
        assertNotNull(sub.getReturnValue());

        Thread.sleep(500);
        sub = facade.queryBatch((BatchToken) sub.getReturnValue(), observer, options);

        assertEquals(results.size(), 2);
        assertFalse(isComplete.get());
        assertTrue(hasError.get());
        assertNull(sub.getReturnValue());
    }

    @Test
    public void testBatchUseCase3() throws Exception {
        ConnectorInfoManager manager = getConnectorInfoManager();
        ConnectorInfo info =
                findConnectorInfo(manager, "1.0.0.0",
                        "org.identityconnectors.testconnector.TstConnector");

        APIConfiguration api = info.createDefaultAPIConfiguration();
        api.setProducerBufferSize(0);

        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(OperationOptions.OP_FAIL_ON_ERROR, true);
        builder.setOption("TEST_USECASE3", true);
        final OperationOptions options = builder.build();

        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("foo"), options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("foo"), new HashSet<Attribute>(), options);

        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                isComplete.set(true);
            }

            public void onError(Throwable e) {
                hasError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
            }
        };

        final List<BatchTask> batchTasks = batch.build();
        Subscription sub = facade.executeBatch(batchTasks, observer, options);
        assertNotNull(sub.getReturnValue());

        final long timeout = System.currentTimeMillis() + 3000;
        while (!isComplete.get() && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }

        assertEquals(results.size(), batchTasks.size());
        assertTrue(isComplete.get());
        assertFalse(hasError.get());

        sub = facade.queryBatch((BatchToken) sub.getReturnValue(), observer, options);
        assertNull(sub.getReturnValue());
    }

    @Test
    public void testBatchUseCase3Failure() throws Exception {
        ConnectorInfoManager manager = getConnectorInfoManager();
        ConnectorInfo info =
                findConnectorInfo(manager, "1.0.0.0",
                        "org.identityconnectors.testconnector.TstConnector");

        APIConfiguration api = info.createDefaultAPIConfiguration();
        api.setProducerBufferSize(0);

        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(OperationOptions.OP_FAIL_ON_ERROR, true);
        builder.setOption("TEST_USECASE3", true);
        builder.setOption("FAIL_TEST_ITERATION", 1);
        final OperationOptions options = builder.build();

        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("foo"), options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("foo"), new HashSet<Attribute>(), options);

        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                isComplete.set(true);
            }

            public void onError(Throwable e) {
                hasError.set(true);
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
            }
        };

        Subscription sub = facade.executeBatch(batch.build(), observer, options);
        assertEquals(results.size(), 0);
        assertFalse(isComplete.get());
        assertFalse(hasError.get());
        assertNotNull(sub.getReturnValue());

        Thread.sleep(500);

        assertEquals(results.size(), 2);
        assertFalse(isComplete.get());
        assertTrue(hasError.get());
    }
    
    @Test
    public void testBatchUseCase0and1Handler() throws Exception {
        ConnectorInfoManager manager = getConnectorInfoManager();
        ConnectorInfo info =
                findConnectorInfo(manager, "1.0.0.0",
                        "org.identityconnectors.testconnector.TstConnector");

        APIConfiguration api = info.createDefaultAPIConfiguration();
        api.setProducerBufferSize(0);

        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(OperationOptions.OP_FAIL_ON_ERROR, true);
        builder.setOption("TEST_USECASE1", true);
        final OperationOptions options = builder.build();

        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>(), options);

        final List<Object> results = new ArrayList<Object>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            public void onCompleted() {
                isComplete.set(true);
                assert !hasError.get();
            }

            public void onError(Throwable e) {
                hasError.set(true);
                assert !isComplete.get();
            }

            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
                assert !batchResult.getResultId().equals("1");
            }
        };

        Subscription sub = facade.executeBatch(batch.build(), observer, options);
        assertEquals(results.size(), 2);
        //Batch process is complete but handler failed to receive all
        assertTrue(hasError.get() ^ isComplete.get());
        assertNull(sub.getReturnValue());
        assertTrue(sub.isUnsubscribed());
    }
}
