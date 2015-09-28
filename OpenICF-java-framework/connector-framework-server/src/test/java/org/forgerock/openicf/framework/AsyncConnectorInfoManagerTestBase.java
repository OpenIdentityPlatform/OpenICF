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

package org.forgerock.openicf.framework;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConfigurationPropertyChangeListener;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchBuilder;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

@Test
public abstract class AsyncConnectorInfoManagerTestBase<T extends AsyncConnectorInfoManager> {

    public static final ConnectorKey TEST_POOLABLE_STATEFUL_CONNECTOR_KEY = new ConnectorKey(
            "testbundlev1", "1.0.0.0",
            "org.identityconnectors.testconnector.TstStatefulPoolableConnector");
    public static final ConnectorKey TEST_STATEFUL_CONNECTOR_KEY = new ConnectorKey("testbundlev1",
            "1.0.0.0", "org.identityconnectors.testconnector.TstStatefulConnector");
    public static final ConnectorKey TEST_CONNECTOR_KEY = new ConnectorKey("testbundlev1",
            "1.0.0.0", "org.identityconnectors.testconnector.TstConnector");

    public static final String DEFAULT_PASSWORD = "changeit";
    public static final String JSK_PASSWORD = "Passw0rd";

    public static final GuardedString DEFAULT_GUARDED_PASSWORD = new GuardedString(DEFAULT_PASSWORD
            .toCharArray());

    public static final String KEY_HASH = SecurityUtil.computeBase64SHA1Hash(DEFAULT_PASSWORD
            .toCharArray()); // "lmA6bMfENJGlIDbfrVtklXFK32s=";

    protected ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework = null;

    public static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } catch (IOException e) {
            // IGNORE
        } finally {
            if (null != socket)
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return -1;
    }

    public static RemoteWSFrameworkConnectionInfo buildRemoteWSFrameworkConnectionInfo(
            boolean isSecure, int port, String contextPath) {
        String path = StringUtil.isBlank(contextPath) ? "/openicf" : contextPath + "/openicf";
        return RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                URI.create((isSecure ? "wss" : "ws") + "://127.0.0.1:" + port + path))
                .setPrincipal(isSecure ? "secure" : "plain").setPassword(DEFAULT_GUARDED_PASSWORD)
                .build();
    }

    public static RemoteWSFrameworkConnectionInfo buildRemoteProxyFrameworkConnectionInfo(
            boolean isSecure, int port, int proxyPort, String contextPath) {
        String path = StringUtil.isBlank(contextPath) ? "/openicf" : contextPath + "/openicf";
        return RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                URI.create((isSecure ? "wss" : "ws") + "://127.0.0.1:" + port + path))
                .setPrincipal("proxy").setPassword(DEFAULT_GUARDED_PASSWORD).setProxyHost(
                        "127.0.0.1").setProxyPort(proxyPort).build();
    }

    @BeforeTest
    public void beforeTest(ITestContext context) throws Exception {
        Reporter.log("Setup Test:" + getClass());
        setupTest(context);
    }

    @AfterTest
    public void afterTest(ITestContext context) throws Exception {
        Reporter.log("Shutdown Test:" + getClass());
        shutdownTest(context);
    }

    protected void setupTest(ITestContext context) throws Exception {
    }

    protected void shutdownTest(ITestContext context) throws Exception {
    }

    @BeforeClass
    public void beforeClass(ITestContext context) throws Exception {
        connectorFramework = getConnectorFrameworkFactory().acquire();
        setupClass(context);
    }

    @AfterClass
    public void afterClass(ITestContext context) throws Exception {
        shutdownClass(context);
        connectorFramework.release();
    }

    protected void setupClass(ITestContext context) throws Exception {
    }

    protected void shutdownClass(ITestContext context) throws Exception {
    }

    public ConnectorFramework getConnectorFramework() {
        return connectorFramework.get();
    }

    protected abstract ConnectorFrameworkFactory getConnectorFrameworkFactory() throws Exception;

    protected abstract T getConnectorInfoManager() throws Exception;

    
    public ConnectorKey getTestConnectorKey(){
        return TEST_CONNECTOR_KEY;
    }

    public ConnectorKey getTestStatefulConnectorKey(){
        return TEST_STATEFUL_CONNECTOR_KEY;
    }

    public ConnectorKey getTestPoolableStatefulConnectorKey(){
        return TEST_POOLABLE_STATEFUL_CONNECTOR_KEY;
    }
    
    @Test
    public void testRequiredConnectorInfo() throws Exception {

            AsyncConnectorInfoManager manager = getConnectorInfoManager();
            Assert.assertNotNull(manager);

            ConnectorInfo c =
                    manager.findConnectorInfoAsync(getTestConnectorKey()).getOrThrowUninterruptibly(5,
                            TimeUnit.MINUTES);
            Assert.assertNotNull(c);

            Assert.assertNotNull(manager.findConnectorInfoAsync(
                    getTestStatefulConnectorKey()).getOrThrowUninterruptibly(30, TimeUnit.SECONDS));

            Assert.assertNotNull(manager.findConnectorInfoAsync(
                    getTestPoolableStatefulConnectorKey()).getOrThrowUninterruptibly(30,
                    TimeUnit.SECONDS));

            for (ConnectorInfo ci : manager.getConnectorInfos()) {
                Reporter.log(String.valueOf(ci.getConnectorKey()), true);
            }
    }

    @Test
    public void testValidate() throws Exception {

        Promise<ConnectorInfo, RuntimeException> keyPromise =
                getConnectorInfoManager().findConnectorInfoAsync(getTestStatefulConnectorKey());

        ConnectorInfo info = keyPromise.getOrThrowUninterruptibly(30, TimeUnit.SECONDS);

        APIConfiguration api = info.createDefaultAPIConfiguration();

        ConfigurationProperties props = api.getConfigurationProperties();
        ConfigurationProperty property = props.getProperty("failValidation");
        property.setValue(false);
        ConnectorFacade facade = getConnectorFramework().newInstance(api);
        facade.validate();
        property.setValue(true);
        facade = getConnectorFramework().newInstance(api);
        // validate and also test that locale is propagated
        // properly
        try {
            CurrentLocale.set(new Locale("en"));
            facade.validate();

            fail("exception expected");
        } catch (ConnectorException e) {
            assertThat(e).hasMessage("validation failed en");
        } finally {
            CurrentLocale.clear();
        }
        // validate and also test that locale is propagated
        // properly
        try {
            CurrentLocale.set(new Locale("es"));
            facade.validate();

            fail("exception expected");
        } catch (ConnectorException e) {
            assertThat(e).hasMessage("validation failed es");
        } finally {
            CurrentLocale.clear();
        }
        // call test and also test that locale is propagated
        // properly
        try {
            CurrentLocale.set(new Locale("en"));
            facade.test();

            fail("exception expected");
        } catch (ConnectorException e) {
            assertThat(e).hasMessage("test failed en");
        } finally {
            CurrentLocale.clear();
        }
    }

    @Test
    public void testConfigurationUpdate() throws Exception {
        ConnectorInfo[] infos =
                new ConnectorInfo[] {
                    getConnectorInfoManager().findConnectorInfoAsync(getTestStatefulConnectorKey())
                            .get(5, TimeUnit.MINUTES),
                    getConnectorInfoManager().findConnectorInfoAsync(
                            getTestPoolableStatefulConnectorKey()).get(5, TimeUnit.MINUTES) };
        for (ConnectorInfo info : infos) {
            APIConfiguration api = info.createDefaultAPIConfiguration();

            ConfigurationProperties props = api.getConfigurationProperties();
            props.getProperty("randomString").setValue(StringUtil.randomString());
            api.setProducerBufferSize(0);

            final AtomicReference<List<ConfigurationProperty>> current =
                    new AtomicReference<List<ConfigurationProperty>>();
            api.setChangeListener(new ConfigurationPropertyChangeListener() {
                public void configurationPropertyChange(List<ConfigurationProperty> changes) {
                    current.set(changes);
                }
            });
            
            ConnectorFacade facade = getConnectorFramework().newInstance(api);

            ScriptContextBuilder builder = new ScriptContextBuilder();
            builder.setScriptLanguage("GROOVY");

            builder.setScriptText("connector.update()");
            facade.runScriptOnConnector(builder.build(), null);

            for (int i = 0; (i < 5 && null == current.get()); i++) {
                Thread.sleep(1000);
            }
            assertNotNull(current.get());
            assertEquals(current.get().size(), 1);
            assertEquals(current.get().get(0).getValue(), "change");
        }
    }

    @Test
    public void testNullOperations() throws Exception {
        final ConnectorFacade facade = getConnectorFacade(true, true);
        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        facade.test();
        Assert.assertNull(facade.schema());

        Uid uid =
                facade.create(ObjectClass.ACCOUNT, CollectionUtil.newSet(new Name("CREATE_01"),
                        AttributeBuilder.buildPassword("Passw0rd".toCharArray())), optionsBuilder
                        .build());
        Assert.assertNull(uid);

        Uid resolvedUid =
                facade.resolveUsername(ObjectClass.ACCOUNT, "CREATE_01", optionsBuilder.build());
        Assert.assertNull(resolvedUid);

        Uid authenticatedUid =
                facade.authenticate(ObjectClass.ACCOUNT, "CREATE_01", new GuardedString("Passw0rd"
                        .toCharArray()), optionsBuilder.build());
        Assert.assertNull(authenticatedUid);

        SyncToken token = facade.getLatestSyncToken(ObjectClass.ACCOUNT);
        Assert.assertNull(token);

        SyncToken lastToken =
                facade.sync(ObjectClass.ACCOUNT, new SyncToken(-1), new SyncResultsHandler() {

                    public boolean handle(SyncDelta delta) {
                        return true;
                    }
                }, optionsBuilder.build());

        Assert.assertNull(lastToken);

        SearchResult searchResult = facade.search(ObjectClass.ACCOUNT, null, new ResultsHandler() {
            public boolean handle(ConnectorObject connectorObject) {
                return true;
            }
        }, optionsBuilder.build());

        Assert.assertNull(searchResult);

        Uid updatedUid =
                facade.update(ObjectClass.ACCOUNT, new Uid("1"), CollectionUtil
                        .newSet(AttributeBuilder.buildLockOut(true)), optionsBuilder.build());
        Assert.assertNull(updatedUid);

        ConnectorObject co =
                facade.getObject(ObjectClass.ACCOUNT, new Uid("1"), optionsBuilder.build());
        Assert.assertNull(co);
    }

    @Test
    public void testNullScriptOperations() throws Exception {
        final ConnectorFacade facade = getConnectorFacade(true, true);
        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        facade.test();

        ScriptContextBuilder contextBuilder =
                new ScriptContextBuilder().setScriptLanguage("JavaScript").setScriptText(
                        "function foo() {return arg; }\nfoo();").addScriptArgument("arg", "test");

        Object o = facade.runScriptOnConnector(contextBuilder.build(), optionsBuilder.build());
        Assert.assertEquals(o, "test");
        o = facade.runScriptOnResource(contextBuilder.build(), optionsBuilder.build());
        Assert.assertNull(o);
    }

    @Test
    public void testOperations() throws Exception {
        ConnectorFacade facade = getConnectorFacade();
        facade.test();
        Assert.assertNotNull(facade.schema());

        Uid uid1 =
                facade.create(ObjectClass.ACCOUNT, CollectionUtil.newSet(new Name("CREATE_01"),
                        AttributeBuilder.buildPassword("Passw0rd".toCharArray())), null);
        Assert.assertNotNull(uid1);

        Uid uid2 =
                facade.create(ObjectClass.ACCOUNT, CollectionUtil.newSet(new Name("CREATE_02"),
                        AttributeBuilder.buildPassword("Password".toCharArray())), null);

        Assert.assertNotEquals(uid1, uid2);

        Uid resolvedUid = facade.resolveUsername(ObjectClass.ACCOUNT, "CREATE_01", null);
        assertEquals(uid1, resolvedUid);

        Uid authenticatedUid =
                facade.authenticate(ObjectClass.ACCOUNT, "CREATE_01", new GuardedString("Passw0rd"
                        .toCharArray()), null);
        assertEquals(uid1, authenticatedUid);

        try {
            facade.authenticate(ObjectClass.ACCOUNT, "CREATE_01", new GuardedString("wrongPassw0rd"
                    .toCharArray()), null);
            Assert.fail("This should fail");
        } catch (RuntimeException e) {
            assertThat(e).hasMessage("Invalid Password");
        }

        SyncToken token = facade.getLatestSyncToken(ObjectClass.ACCOUNT);
        Assert.assertEquals(token.getValue(), 2);

        final List<SyncDelta> changes = new ArrayList<SyncDelta>();

        SyncToken lastToken =
                facade.sync(ObjectClass.ACCOUNT, new SyncToken(-1), new SyncResultsHandler() {
                    Integer index = null;

                    public boolean handle(SyncDelta delta) {
                        Integer previous = index;
                        index = (Integer) delta.getToken().getValue();
                        if (null != previous) {
                            Assert.assertTrue(previous < index);
                        }
                        changes.add(delta);
                        return true;
                    }
                }, null);

        Assert.assertEquals(changes.size(), 2);
        Assert.assertEquals(facade.getObject(ObjectClass.ACCOUNT, uid1, null).getUid(), uid1);
        Assert.assertEquals(token, lastToken);

        ToListResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.ACCOUNT, FilterBuilder.or(FilterBuilder.equalTo(new Name(
                "CREATE_02")), FilterBuilder.startsWith(new Name("CREATE"))), handler, null);
        Assert.assertEquals(handler.getObjects().size(), 2);

        handler = new ToListResultsHandler();
        facade.search(ObjectClass.ACCOUNT, null, handler, null);
        Assert.assertEquals(handler.getObjects().size(), 2);

        Uid updatedUid =
                facade.update(ObjectClass.ACCOUNT, uid1, CollectionUtil.newSet(AttributeBuilder
                        .buildLockOut(true)), null);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, updatedUid, null);
        Assert.assertTrue(AttributeUtil.isLockedOut(co));

        facade.delete(ObjectClass.ACCOUNT, updatedUid, null);
        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, updatedUid, null));
    }

    @Test
    public void testScriptOperations() throws Exception {
        ConnectorFacade facade = getConnectorFacade();
        facade.test();
        ScriptContextBuilder contextBuilder =
                new ScriptContextBuilder().setScriptLanguage("JavaScript").setScriptText(
                        "function foo() {return arg; }\nfoo();").addScriptArgument("arg", "test");

        Object o = facade.runScriptOnConnector(contextBuilder.build(), null);
        Assert.assertEquals(o, "test");
        o = facade.runScriptOnResource(contextBuilder.build(), null);
        Assert.assertEquals(o, "test");
    }

    @Test
    public void testSerialBatch() throws Throwable {
        ConnectorFacade facade = getConnectorFacade();

        OperationOptions options = new OperationOptionsBuilder()
                .setOption(OperationOptions.OP_FAIL_ON_ERROR, true)
                .setOption("TEST_USECASE1", true)
                .build();

        runBatch(facade, options);
    }

    @Test
    public void testSerialBatchWithError() throws Throwable {
        ConnectorFacade facade = getConnectorFacade();

        OperationOptions options = new OperationOptionsBuilder()
                .setOption(OperationOptions.OP_FAIL_ON_ERROR, true)
                .setOption("TEST_USECASE1", true)
                .setOption("FAIL_TEST_ITERATION", 3)
                .build();

        runBatch(facade, options);
    }

    @Test
    public void testSynchronousBatch() throws Throwable {
        ConnectorFacade facade = getConnectorFacade();

        OperationOptions options = new OperationOptionsBuilder()
                .setOption(OperationOptions.OP_FAIL_ON_ERROR, true)
                .setOption("TEST_USECASE2", true)
                .build();

        runBatch(facade, options);
    }

    @Test
    public void testSynchronousBatchWithError() throws Throwable {
        ConnectorFacade facade = getConnectorFacade();

        OperationOptions options = new OperationOptionsBuilder()
                .setOption(OperationOptions.OP_FAIL_ON_ERROR, true)
                .setOption("TEST_USECASE2", true)
                .setOption("FAIL_TEST_ITERATION", 3)
                .build();

        runBatch(facade, options);
    }

    @Test
    public void testAsynchronousBatch() throws Throwable {
        ConnectorFacade facade = getConnectorFacade();

        OperationOptions options = new OperationOptionsBuilder()
                .setOption(OperationOptions.OP_FAIL_ON_ERROR, true)
                .setOption("TEST_USECASE3", true)
                .build();

        runBatch(facade, options);
    }

    @Test
    public void testAsynchronousBatchWithError() throws Throwable {
        ConnectorFacade facade = getConnectorFacade();

        OperationOptions options = new OperationOptionsBuilder()
                .setOption(OperationOptions.OP_FAIL_ON_ERROR, true)
                .setOption("TEST_USECASE3", true)
                .setOption("FAIL_TEST_ITERATION", 3)
                .build();

        runBatch(facade, options);
    }

    private void runBatch(ConnectorFacade facade, OperationOptions options) throws Throwable {
        final List<BatchTask> batchTasks = new ArrayList<BatchTask>(newTestBatch(options));
        batchTasks.addAll(newTestBatch(options));
        batchTasks.addAll(newTestBatch(options));
        batchTasks.addAll(newTestBatch(options));
        final List<BatchResult> results = new ArrayList<BatchResult>();
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean hasError = new AtomicBoolean(false);
        final AtomicBoolean hasException = new AtomicBoolean(false);

        Observer<BatchResult> observer = new Observer<BatchResult>() {
            @Override
            public void onCompleted() {
                isComplete.set(true);
            }

            @Override
            public void onError(Throwable e) {
                hasException.set(true);
            }

            @Override
            public void onNext(BatchResult batchResult) {
                results.add(batchResult);
                if (batchResult.getError()) {
                    hasError.set(true);
                }
            }
        };

        Subscription sub = facade.executeBatch(batchTasks, observer, options);

        BatchToken batchToken = (BatchToken) sub.getReturnValue();

        if (batchToken.isQueryRequired() && batchToken.getTokens().size() > 0) {
            // Wait for completion flag
            long timeout = new Date().getTime() + 3000;
            while (!isComplete.get() && new Date().getTime() < timeout) {
                Thread.sleep(200);
            }
            assertTrue(isComplete.get());

            // Reset completion flag and query for results
            isComplete.set(false);
            timeout = new Date().getTime() + 3000;
            boolean done = !batchToken.isQueryRequired();
            while (!done) {
                try {
                    sub = facade.queryBatch(batchToken, observer, options);
                } catch (Exception e) {
                    if (!hasError.get()) {
                        throw e;
                    }
                }
                batchToken = (BatchToken) sub.getReturnValue();

                done = (isComplete.get() && results.size() == batchTasks.size()) ||
                        (options.getFailOnError() && hasError.get()) ||
                        new Date().getTime() >= timeout;
                if (!done) {
                    Thread.sleep(200);
                }
            }
        }

        int expectedSize = options.getOptions().containsKey("FAIL_TEST_ITERATION")
                ? (Integer) options.getOptions().get("FAIL_TEST_ITERATION")
                : batchTasks.size();

        if (batchToken.hasAsynchronousResults() && !isComplete.get()) {
            long timeout = new Date().getTime() + 3000;
            while (!isComplete.get() && new Date().getTime() < timeout) {
                Thread.sleep(200);
            }
        }
        sub.close();

        assertEquals(results.size(), expectedSize);

        // Wait for completion flag
        long timeout = new Date().getTime() + 3000;
        while (!isComplete.get() && new Date().getTime() < timeout) {
            Thread.sleep(200);
        }
        assertTrue(isComplete.get());

        if (options.getOptions().containsKey("FAIL_TEST_ITERATION")) {
            assertTrue(hasError.get());
        } else {
            assertFalse(hasError.get());
        }
        assertTrue(sub.isUnsubscribed());
        assertFalse(hasException.get());
    }

    private List<BatchTask> newTestBatch(OperationOptions options) {
        final BatchBuilder batch = new BatchBuilder();
        batch.addCreateOp(ObjectClass.ACCOUNT, new HashSet<Attribute>() {{
            add(new Name(UUID.randomUUID().toString()));
        }}, options);
        batch.addUpdateAddOp(ObjectClass.ACCOUNT, new Uid("0"), new HashSet<Attribute>(), options);
        batch.addDeleteOp(ObjectClass.ACCOUNT, new Uid("0"), options);
        return batch.build();
    }

    @Test
    public void testSubscriptionOperation() throws Throwable {
        final ConnectorFacade facade = getConnectorFacade();
        final ToListResultsHandler handler = new ToListResultsHandler();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> assertionError = new AtomicReference<Throwable>(null);

        Observable<ConnectorObject> connectorObjectObservable =
                Observable.create(new Observable.OnSubscribe<ConnectorObject>() {
                    public void call(final Subscriber<? super ConnectorObject> subscriber) {

                        final Subscription subscription =
                                facade.subscribe(ObjectClass.ACCOUNT, null,
                                        new Observer<ConnectorObject>() {
                                            public void onCompleted() {
                                                subscriber.onCompleted();
                                            }

                                            public void onError(Throwable e) {
                                                subscriber.onError(e);
                                            }

                                            public void onNext(ConnectorObject connectorObject) {
                                                subscriber.onNext(connectorObject);
                                            }
                                        }, null);

                        subscriber.add(new rx.Subscription() {
                            public void unsubscribe() {
                                subscription.close();
                            }

                            public boolean isUnsubscribed() {
                                return subscription.isUnsubscribed();
                            }
                        });
                    }
                });
        final rx.Subscription[] subscription = new rx.Subscription[1];
        subscription[0] = connectorObjectObservable.subscribe(new Action1<ConnectorObject>() {
            public void call(ConnectorObject connectorObject) {
                Reporter.log("Connector Event received:" + connectorObject.getUid(), true);
                handler.handle(connectorObject);
            }
        }, new Action1<Throwable>() {
            public void call(Throwable throwable) {
                try {
                    Assert.assertEquals(handler.getObjects().size(), 10, "Uncompleted subscription");
                } catch (final Exception t) {
                    assertionError.set(t);
                } finally {
                    latch.countDown();
                }
            }
        });

        latch.await(5, TimeUnit.MINUTES);
        if (null != assertionError.get()) {
            throw assertionError.get();
        }

        if (facade instanceof LocalConnectorFacadeImpl){
            LocalConnectorFacadeImpl localConnectorFacade = (LocalConnectorFacadeImpl) facade;
            Assert.assertTrue(localConnectorFacade.isUnusedFor(1, TimeUnit.NANOSECONDS));
        }
        
        final CountDownLatch syncLatch = new CountDownLatch(1);
        handler.getObjects().clear();

        
        final OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        optionsBuilder.setOption("eventCount", 1500);
        Observable<SyncDelta> syncDeltaObservable =
                Observable.create(new Observable.OnSubscribe<SyncDelta>() {
                    public void call(final Subscriber<? super SyncDelta> subscriber) {
                        final Subscription subscription =
                                facade.subscribe(ObjectClass.ACCOUNT, null,
                                        new Observer<SyncDelta>() {
                                            public void onCompleted() {
                                                subscriber.onCompleted();
                                            }

                                            public void onError(Throwable e) {
                                                subscriber.onError(e);
                                            }

                                            public void onNext(SyncDelta syncDelta) {
                                                subscriber.onNext(syncDelta);
                                            }
                                        }, optionsBuilder.build());

                        subscriber.add(new rx.Subscription() {
                            public void unsubscribe() {
                                subscription.close();
                            }

                            public boolean isUnsubscribed() {
                                return subscription.isUnsubscribed();
                            }
                        });
                    }
                });

        subscription[0] = syncDeltaObservable.subscribe(new Action1<SyncDelta>() {
            public void call(SyncDelta delta) {
                Reporter.log("Sync Event received:" + delta.getToken(), true);
                handler.handle(delta.getObject());
                if (((Integer) delta.getToken().getValue()) > 2) {
                    try {
                        subscription[0].unsubscribe();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    syncLatch.countDown();
                }
            }
        }, new Action1<Throwable>() {
            public void call(Throwable throwable) {
                try {
                    assertionError.set(throwable);
                } catch (final Exception t) {
                    assertionError.set(t);
                } finally {
                    latch.countDown();
                }
            }
        });
        syncLatch.await(25, TimeUnit.SECONDS);
        if (null != assertionError.get()) {
            throw assertionError.get();
        }
        for (int i = 0; i < 5 && !(handler.getObjects().size() > 2); i++) {
            Reporter.log("Wait for result handler thread to complete: " + i, true);
            Thread.sleep(200); // Wait to complete all other threads
        }
        Assert.assertTrue(handler.getObjects().size() < 10 && handler.getObjects().size() > 2);
        if (facade instanceof LocalConnectorFacadeImpl){
            LocalConnectorFacadeImpl localConnectorFacade = (LocalConnectorFacadeImpl) facade;
            Assert.assertTrue(localConnectorFacade.isUnusedFor(1, TimeUnit.NANOSECONDS));
        }
    }

    protected ConnectorFacade getConnectorFacade() throws Exception {
        return getConnectorFacade(false, false);
    }

    protected ConnectorFacade getConnectorFacade(final boolean caseIgnore,
            final boolean returnNullTest) throws Exception {
        return getConnectorInfoManager().findConnectorInfoAsync(getTestStatefulConnectorKey()).then(
                new Function<ConnectorInfo, ConnectorFacade, RuntimeException>() {
                    public ConnectorFacade apply(ConnectorInfo info) throws RuntimeException {
                        APIConfiguration api = info.createDefaultAPIConfiguration();
                        ConfigurationProperties props = api.getConfigurationProperties();

                        props.getProperty("randomString").setValue(StringUtil.randomString());
                        props.getProperty("caseIgnore").setValue(caseIgnore);
                        props.getProperty("returnNullTest").setValue(returnNullTest);
                        props.getProperty("failValidation").setValue(false);
                        props.getProperty("testObjectClass").setValue(
                                new String[] { ObjectClass.ACCOUNT_NAME, ObjectClass.GROUP_NAME });

                        api.setProducerBufferSize(0);
                        return getConnectorFramework().newInstance(api);
                    }
                }).getOrThrowUninterruptibly();
    }

    public static final class ToListResultsHandler implements ResultsHandler {

        private final List<ConnectorObject> connectorObjects = new ArrayList<ConnectorObject>();

        public boolean handle(ConnectorObject object) {
            connectorObjects.add(object);
            return true;
        }

        public List<ConnectorObject> getObjects() {
            return connectorObjects;
        }

    }

}
