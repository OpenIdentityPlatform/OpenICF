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

package org.forgerock.openicf.framework.local;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.identityconnectors.common.ConnectorKeyRange;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.Test;

public class AsyncLocalConnectorInfoManagerTest extends
        AsyncConnectorInfoManagerTestBase<AsyncLocalConnectorInfoManager> {

    private final ConnectorFrameworkFactory internalFrameworkFactory =
            new ConnectorFrameworkFactory();

    protected ConnectorFrameworkFactory getConnectorFrameworkFactory() throws Exception {
        return internalFrameworkFactory;
    }

    protected void setupClass(ITestContext context) throws Exception {
        getConnectorFramework().getLocalManager().addConnectorBundle(
                TstConnector.class.getProtectionDomain().getCodeSource().getLocation());
    }

    protected AsyncLocalConnectorInfoManager getConnectorInfoManager() throws Exception {
        return getConnectorFramework().getLocalManager();
    }

    @Test
    public void testAddConnectorBundle() throws Exception {
        ConnectorFrameworkFactory frameworkFactory = new ConnectorFrameworkFactory();
        final ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework =
                frameworkFactory.acquire();

        Promise<ConnectorInfo, RuntimeException> failedPromise;
        Promise<ConnectorInfo, RuntimeException> failedRangePromise;

        try {
            AsyncLocalConnectorInfoManager manager = connectorFramework.get().getLocalManager();

            failedPromise =
                    manager.findConnectorInfoAsync(new ConnectorKey("NOK", "NOK", "1.5.0.0"));
            failedRangePromise =
                    manager.findConnectorInfoAsync(ConnectorKeyRange.newBuilder().setBundleName(
                            "NOK").setConnectorName("NOK").setBundleVersion("[1.0.0.0,2.0)")
                            .build());

            Promise<ConnectorInfo, RuntimeException> keyPromise =
                    manager.findConnectorInfoAsync(getTestConnectorKey());
            Promise<ConnectorInfo, RuntimeException> keyRangePromise =
                    manager.findConnectorInfoAsync(ConnectorKeyRange.newBuilder().setBundleName(
                            getTestConnectorKey().getBundleName()).setConnectorName(
                            getTestConnectorKey().getConnectorName()).setBundleVersion("[1.0,2.0)")
                            .build());

            manager.addConnectorBundle(TstConnector.class.getProtectionDomain().getCodeSource()
                    .getLocation());

            Assert.assertEquals(manager.getConnectorInfos().size(), 3);
            Assert.assertTrue(keyPromise.isDone());
            keyPromise.thenOnResult(new ResultHandler<ConnectorInfo>() {
                public void handleResult(ConnectorInfo result) {
                    Assert.assertEquals(result.getConnectorKey(), getTestConnectorKey());
                }
            }).thenOnException(new ExceptionHandler<RuntimeException>() {
                public void handleException(RuntimeException error) {
                    Assert.fail("Key search should succeed", error);
                }
            });
            Assert.assertTrue(keyRangePromise.isDone());

            keyRangePromise.thenOnResult(new ResultHandler<ConnectorInfo>() {
                public void handleResult(ConnectorInfo result) {
                    Assert.assertEquals(result.getConnectorKey(), getTestConnectorKey());
                }
            }).thenOnException(new ExceptionHandler<RuntimeException>() {
                public void handleException(RuntimeException error) {
                    Assert.fail("KeyRange search should succeed", error);
                }
            });
        } finally {
            connectorFramework.release();
        }
        Assert.assertNotNull(failedPromise);
        Assert.assertTrue(failedPromise.isDone());
        failedPromise.thenOnException(new ExceptionHandler<RuntimeException>() {
            public void handleException(RuntimeException error) {
                Assert.assertTrue(error instanceof IllegalStateException);
            }
        }).thenOnResult(new ResultHandler<ConnectorInfo>() {
            public void handleResult(ConnectorInfo result) {
                Assert.fail("This should not succeed");
            }
        });

        Assert.assertNotNull(failedRangePromise);
        Assert.assertTrue(failedRangePromise.isDone());
        failedRangePromise.thenOnException(new ExceptionHandler<RuntimeException>() {
            public void handleException(RuntimeException error) {
                Assert.assertTrue(error instanceof IllegalStateException);
            }
        }).thenOnResult(new ResultHandler<ConnectorInfo>() {
            public void handleResult(ConnectorInfo result) {
                Assert.fail("This should not succeed");
            }
        });
    }

    @Test
    public void testConnectorBundleRange() throws Exception {
        ConnectorFrameworkFactory frameworkFactory = new ConnectorFrameworkFactory();
        final ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework =
                frameworkFactory.acquire();

        try {
            AsyncLocalConnectorInfoManager manager = connectorFramework.get().getLocalManager();

            File testBundlesDir = getTestBundlesDir();
            URL bundle10 = IOUtil.makeURL(testBundlesDir, "testbundlev10");
            URL bundle11 = IOUtil.makeURL(testBundlesDir, "testbundlev11");

            Promise<ConnectorInfo, RuntimeException> keyRangePromise =
                    manager.findConnectorInfoAsync(ConnectorKeyRange.newBuilder().setBundleName(
                            "testbundlev2").setConnectorName(
                            "org.identityconnectors.testconnector.TstConnector").setBundleVersion(
                            "[1.0,2.0)").build());

            manager.addConnectorBundle(bundle10);

            keyRangePromise.thenOnResult(new ResultHandler<ConnectorInfo>() {
                public void handleResult(ConnectorInfo result) {
                    Assert.assertEquals(result.getConnectorKey().getBundleVersion(), "1.0.0.0");
                }
            }).thenOnException(new ExceptionHandler<RuntimeException>() {
                public void handleException(RuntimeException error) {
                    error.printStackTrace();
                    Assert.fail("1 KeyRange search should succeed "+ error.toString(), error);
                }
            });

            keyRangePromise.getOrThrowUninterruptibly(10, TimeUnit.SECONDS);
            Assert.assertTrue(keyRangePromise.isDone());

            keyRangePromise =
                    manager.findConnectorInfoAsync(ConnectorKeyRange.newBuilder().setBundleName(
                            "testbundlev2").setConnectorName(
                            "org.identityconnectors.testconnector.TstConnector").setBundleVersion(
                            "[1.0,2.0)").build());


            keyRangePromise.thenOnResult(new ResultHandler<ConnectorInfo>() {
                public void handleResult(ConnectorInfo result) {
                    Assert.assertEquals(result.getConnectorKey().getBundleVersion(), "1.0.0.0");
                }
            }).thenOnException(new ExceptionHandler<RuntimeException>() {
                public void handleException(RuntimeException error) {
                    error.printStackTrace();
                    Assert.fail("2 KeyRange search should succeed "+ error.toString(), error);
                }
            });

            keyRangePromise.getOrThrowUninterruptibly(10, TimeUnit.SECONDS);
            Assert.assertTrue(keyRangePromise.isDone());

            manager.addConnectorBundle(bundle11);

            keyRangePromise =
                    manager.findConnectorInfoAsync(ConnectorKeyRange.newBuilder().setBundleName(
                            "testbundlev2").setConnectorName(
                            "org.identityconnectors.testconnector.TstConnector").setBundleVersion(
                            "[1.0,2.0)").build());


            keyRangePromise.thenOnResult(new ResultHandler<ConnectorInfo>() {
                public void handleResult(ConnectorInfo result) {
                    Assert.assertEquals(result.getConnectorKey().getBundleVersion(), "1.1.0.0");
                }
            }).thenOnException(new ExceptionHandler<RuntimeException>() {
                public void handleException(RuntimeException error) {
                    error.printStackTrace();
                    Assert.fail("3 KeyRange search should succeed "+ error.toString(), error);
                }
            });
            keyRangePromise.getOrThrowUninterruptibly(10, TimeUnit.SECONDS);
            Assert.assertTrue(keyRangePromise.isDone());
        } finally {
            connectorFramework.release();
        }
    }

    protected final File getTestBundlesDir() throws URISyntaxException {
        URL testOutputDirectory = AsyncLocalConnectorInfoManagerTest.class.getResource("/");
        Assert.assertNotNull(testOutputDirectory);
        File testBundlesDir = new File(testOutputDirectory.toURI());
        if (!testBundlesDir.isDirectory()) {
            throw new ConnectorException(testBundlesDir.getPath() + " does not exist");
        }
        return testBundlesDir;
    }
}
