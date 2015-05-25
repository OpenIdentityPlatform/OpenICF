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
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.FailureHandler;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
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

    @Test
    public void testRequiredConnectorInfo() throws Exception {
        ReferenceCountedObject<ConnectorFramework>.Reference framework =
                ConnectorFrameworkFactory.DEFAULT_FACTORY.acquire();
        try {

            AsyncConnectorInfoManager manager = getConnectorInfoManager();
            Assert.assertNotNull(manager);

            ConnectorInfo c =
                    manager.findConnectorInfoAsync(TEST_CONNECTOR_KEY).getOrThrowUninterruptibly(5,
                            TimeUnit.MINUTES);
            Assert.assertNotNull(c);

            Assert.assertNotNull(getConnectorInfoManager().findConnectorInfoAsync(
                    TEST_STATEFUL_CONNECTOR_KEY).getOrThrowUninterruptibly(30, TimeUnit.SECONDS));

            Assert.assertNotNull(getConnectorInfoManager().findConnectorInfoAsync(
                    TEST_POOLABLE_STATEFUL_CONNECTOR_KEY).getOrThrowUninterruptibly(30,
                    TimeUnit.SECONDS));

            for (ConnectorInfo ci : manager.getConnectorInfos()) {
                Reporter.log(String.valueOf(ci.getConnectorKey()), true);
            }
        } finally {
            framework.release();
        }
    }

    @Test
    public void testValidate() throws Exception {

        Promise<ConnectorInfo, RuntimeException> keyPromise =
                getConnectorInfoManager().findConnectorInfoAsync(TEST_STATEFUL_CONNECTOR_KEY);

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

        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, new Uid("1"), optionsBuilder.build());
        Assert.assertNull(co);

        ScriptContextBuilder contextBuilder = new ScriptContextBuilder().setScriptLanguage("JavaScript").setScriptText("function foo() {return arg; }\nfoo();").addScriptArgument("arg", "test");
        
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

        ScriptContextBuilder contextBuilder = new ScriptContextBuilder().setScriptLanguage("JavaScript").setScriptText("function foo() {return arg; }\nfoo();").addScriptArgument("arg", "test");

        Object o = facade.runScriptOnConnector(contextBuilder.build(), null);
        Assert.assertEquals(o, "test");
        o = facade.runScriptOnResource(contextBuilder.build(), null);
        Assert.assertEquals(o, "test");
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
                                subscription.unsubscribe();
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
        
        latch.await(25, TimeUnit.MINUTES);
        if (null != assertionError.get()) {
            throw assertionError.get();
        }

        final CountDownLatch syncLatch = new CountDownLatch(1);
        handler.getObjects().clear();
        
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
                                        }, null);

                        subscriber.add(new rx.Subscription() {
                            public void unsubscribe() {
                                subscription.unsubscribe();
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
                    } catch (Exception  e){
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
    }

    protected ConnectorFacade getConnectorFacade() throws Exception {
        return getConnectorFacade(false, false);
    }

    protected ConnectorFacade getConnectorFacade(final boolean caseIgnore,
            final boolean returnNullTest) throws Exception {
        return getConnectorInfoManager().findConnectorInfoAsync(TEST_STATEFUL_CONNECTOR_KEY).then(
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
