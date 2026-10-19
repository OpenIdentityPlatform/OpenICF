/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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
 *
 * Portions Copyrighted 2026 3A Systems, LLC
 */

package org.forgerock.openicf.connectors;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.ResourceException;

import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConnector;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.test.common.TestHelpers;
import org.identityconnectors.test.common.ToListResultsHandler;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 *
 * @author Laszlo Hordos
 */
public class ScriptedCRESTConnectorTest extends RESTTestBase {

    protected static final String TEST_NAME = "CREST";

    protected ConnectorFacade getFacade() {
        return getFacade(ScriptedCRESTConnector.class, TEST_NAME);
    }

    @Test
    public void validate() throws Exception {
        final ConnectorFacade facade = getFacade();
        facade.validate();
    }

    @Test
    public void test() throws Exception {
        final ConnectorFacade facade = getFacade();
        facade.test();
    }

    @Test
    public void testCreate() throws Exception {
        final ConnectorFacade facade = getFacade();
        Set<Attribute> createAttributes = createUserAttributes("John", "Doe");
        createAttributes.add(AttributeBuilder.build("telephoneNumber", "1-555-555-1212"));
        createAttributes.add(AttributeBuilder.buildEnabled(true));
        createAttributes.add(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME, "Group1",
                "Group2"));

        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(createAttributes);

        facade.delete(ObjectClass.ACCOUNT, uid, null);
        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
    }

    @Test
    public void testUpdate() throws Exception {
        final ConnectorFacade facade = getFacade();
        Set<Attribute> createAttributes = createUserAttributes("John", "Doe");
        createAttributes.add(AttributeBuilder.build("telephoneNumber", "1-555-555-1212"));
        createAttributes.add(AttributeBuilder.buildEnabled(true));
        createAttributes.add(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME,
                new ArrayList<String>()));

        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);

        OperationOptionsBuilder builder =
                new OperationOptionsBuilder().setAttributesToGet(PredefinedAttributes.GROUPS_NAME);

        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, builder.build());
        assertThat(co.getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue()).isEmpty();

        facade.update(ObjectClass.ACCOUNT, co.getUid(), CollectionUtil.newSet(AttributeBuilder
                .build(PredefinedAttributes.GROUPS_NAME, "Group1", "Group2")), null);

        co = facade.getObject(ObjectClass.ACCOUNT, uid, builder.build());
        assertThat(co.getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue()).contains(
                "Group1", "Group2");

        facade.delete(ObjectClass.ACCOUNT, uid, null);
        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testUpdateFail() throws Exception {
        final ConnectorFacade facade = getFacade();
        facade.update(ObjectClass.ACCOUNT, new Uid("_NON_EXIST_"), CollectionUtil
                .newSet(AttributeBuilder
                        .build(PredefinedAttributes.GROUPS_NAME, "Group1", "Group2")), null);
    }

    @Test
    public void testQuery() throws Exception {
        final ConnectorFacade facade = getFacade();
        for (int i = 0; i < 100; i++) {
            String username = String.format("TEST%04d", i);
            Set<Attribute> createAttributes = createUserAttributes("John", username);
            facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        }

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setPageSize(10);
        builder.setSortKeys(SortKey.descendingOrder(Name.NAME));

        SearchResult result = null;

        final Set<ConnectorObject> resultSet = new HashSet<ConnectorObject>();
        int pageIndex = 0;

        while ((result =
                facade.search(ObjectClass.ACCOUNT, FilterBuilder.startsWith(AttributeBuilder.build(
                        Name.NAME, "TEST")), new ResultsHandler() {
                    private int index = 101;

                    public boolean handle(ConnectorObject connectorObject) {
                        Integer idx =
                                Integer.parseInt(connectorObject.getName().getNameValue()
                                        .substring(4));
                        Assert.assertTrue(idx < index);
                        index = idx;
                        return resultSet.add(connectorObject);
                    }
                }, builder.build())).getPagedResultsCookie() != null) {

            builder = new OperationOptionsBuilder(builder.build());
            builder.setPagedResultsCookie(result.getPagedResultsCookie());
            Assert.assertEquals(resultSet.size(), 10 * ++pageIndex);
        }
        Assert.assertEquals(pageIndex, 9);
        Assert.assertEquals(resultSet.size(), 100);

    }

    @Test
    public void testFilterTranslator() throws Exception {
        Filter left = startsWith(AttributeBuilder.build("attributeString", "reti"));
        left = and(left, contains(AttributeBuilder.build("attributeString", "pipi")));
        left = and(left, endsWith(AttributeBuilder.build("attributeString", "ter")));

        Filter right = lessThanOrEqualTo(AttributeBuilder.build("attributeInteger", 42));
        right = or(right, lessThan(AttributeBuilder.build("attributeFloat", Float.MAX_VALUE)));
        right =
                or(right, greaterThanOrEqualTo(AttributeBuilder.build("attributeDouble",
                        Double.MIN_VALUE)));
        right = or(right, greaterThan(AttributeBuilder.build("attributeLong", Long.MIN_VALUE)));
        right = and(right, not(equalTo(AttributeBuilder.build("attributeByte", new Byte("33")))));
        left =
                and(left, containsAllValues(AttributeBuilder.build("attributeStringMultivalue",
                        "value1", "value2")));

        ToListResultsHandler handler = new ToListResultsHandler();
        SearchResult result = getFacade().search(new ObjectClass("TEST"), and(left, right), handler, null);
        Assert.assertTrue(handler.getObjects().isEmpty());
        Assert.assertEquals(
                result.getPagedResultsCookie(),
                "((((attributeString sw \"reti\" and attributeString co \"pipi\") and attributeString ew \"ter\") and attributeStringMultivalue ca \"[\"value1\",\"value2\"]\") and ((((attributeInteger le 42 or attributeFloat lt 3.4028235E38) or attributeDouble ge 4.9E-324) or attributeLong gt -9223372036854775808) and ! (attributeByte eq 33)))");
    }

    @Test(enabled = false)
    public void testAction() throws Exception {
        final ConnectorFacade facade = getFacade();
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("crest");
        builder.setScriptText("UNKNOWN");
        Object response =  facade.runScriptOnResource(builder.build(), null);
    }

    @Test
    public void testSearchHandlerRunsOnTheCallingThread() throws Exception {
        final ConnectorFacade facade = getFacade();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, createUserAttributes("John", "Caller0001"), null);
        try {
            final List<Thread> handlerThreads = new ArrayList<Thread>();
            facade.search(ObjectClass.ACCOUNT,
                    FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "caller")),
                    new ResultsHandler() {
                        public boolean handle(ConnectorObject connectorObject) {
                            handlerThreads.add(Thread.currentThread());
                            return true;
                        }
                    }, null);
            Assert.assertFalse(handlerThreads.isEmpty());
            for (Thread handlerThread : handlerThreads) {
                Assert.assertSame(handlerThread, Thread.currentThread());
            }
        } finally {
            facade.delete(ObjectClass.ACCOUNT, uid, null);
        }
    }

    @Test(timeOut = 60000)
    public void testNestedGetObjectFromTheSearchHandlerCompletes() throws Exception {
        // On pre-fix code this test blocks an I/O dispatcher of the class-wide connector pool for
        // good (facades with the same configuration share one pool), so check it red alone.
        final ConnectorFacade facade = getFacade();
        final List<Uid> uids = new ArrayList<Uid>();
        try {
            // More users than I/O dispatchers: new connections are assigned round-robin, so with a
            // new connection per request a nested call eventually lands on the dispatcher that
            // runs the handler. Pooled connections keep the nested calls on one connection off
            // that dispatcher, so this case passes even when the handler runs on an I/O thread;
            // testSearchHandlerRunsOnTheCallingThread is the one that catches that.
            final int count = 2 * Runtime.getRuntime().availableProcessors() + 2;
            for (int i = 0; i < count; i++) {
                uids.add(facade.create(ObjectClass.ACCOUNT,
                        createUserAttributes("John", String.format("Nested%04d", i)), null));
            }
            final AtomicInteger nested = new AtomicInteger();
            facade.search(ObjectClass.ACCOUNT,
                    FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "nested")),
                    new ResultsHandler() {
                        public boolean handle(ConnectorObject connectorObject) {
                            Assert.assertNotNull(facade.getObject(ObjectClass.ACCOUNT,
                                    connectorObject.getUid(), null));
                            nested.incrementAndGet();
                            return true;
                        }
                    }, null);
            Assert.assertEquals(nested.get(), count);
        } finally {
            deleteAll(facade, uids);
        }
    }

    /** Pins kept behaviour: a handler's exception reaches the caller wrapped as before. */
    @Test
    public void testHandlerExceptionReachesTheCallerAsInternalServerError() throws Exception {
        final ConnectorFacade facade = getFacade();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, createUserAttributes("John", "Boom0001"), null);
        final IllegalStateException boom = new IllegalStateException("boom");
        try {
            facade.search(ObjectClass.ACCOUNT,
                    FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "boom")),
                    new ResultsHandler() {
                        public boolean handle(ConnectorObject connectorObject) {
                            throw boom;
                        }
                    }, null);
            Assert.fail("the handler's exception did not reach the caller");
        } catch (RuntimeException e) {
            final ResourceException first = firstResourceException(e);
            Assert.assertTrue(first instanceof InternalServerErrorException, String.valueOf(first));
            Assert.assertTrue(causedBy(first, boom), String.valueOf(first));
        } finally {
            facade.delete(ObjectClass.ACCOUNT, uid, null);
        }
    }

    /** Pins kept behaviour: an HTTP error status reaches the caller as its own ResourceException. */
    @Test
    public void testQueryOfAMissingResourceFailsWithNotFound() throws Exception {
        final ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("Groovy");
        builder.setScriptText("import org.forgerock.json.resource.Requests\n"
                + "import org.forgerock.services.context.RootContext\n"
                + "import org.forgerock.util.query.QueryFilter\n"
                + "connection.query(new RootContext(),\n"
                + "        Requests.newQueryRequest('missing').setQueryFilter(QueryFilter.alwaysTrue()), [])\n");
        try {
            getFacade().runScriptOnConnector(builder.build(), null);
            Assert.fail("the query of a missing resource succeeded");
        } catch (RuntimeException e) {
            final ResourceException first = firstResourceException(e);
            Assert.assertTrue(first instanceof NotFoundException, String.valueOf(first));
        }
    }

    /** Deletes users a test created; see testQuery for why none may be left behind. */
    private static void deleteAll(ConnectorFacade facade, List<Uid> uids) {
        for (Uid uid : uids) {
            facade.delete(ObjectClass.ACCOUNT, uid, null);
        }
    }

    /** The outermost {@link ResourceException} in the cause chain of {@code t}, or null. */
    private static ResourceException firstResourceException(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceException) {
                return (ResourceException) cause;
            }
        }
        return null;
    }

    private static boolean causedBy(Throwable t, Throwable expected) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause == expected) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testSearchStopsWhenTheHandlerReturnsFalse() throws Exception {
        final ConnectorFacade facade = getFacade();
        final List<Uid> uids = new ArrayList<Uid>();
        try {
            for (int i = 0; i < 3; i++) {
                uids.add(facade.create(ObjectClass.ACCOUNT,
                        createUserAttributes("John", String.format("Stop%04d", i)), null));
            }
            final AtomicInteger handled = new AtomicInteger();
            final SearchResult result = facade.search(ObjectClass.ACCOUNT,
                    FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "stop")),
                    new ResultsHandler() {
                        public boolean handle(ConnectorObject connectorObject) {
                            handled.incrementAndGet();
                            return false;
                        }
                    }, new OperationOptionsBuilder().setPageSize(2)
                            .setSortKeys(SortKey.ascendingOrder(Name.NAME)).build());
            Assert.assertEquals(handled.get(), 1);
            Assert.assertNotNull(result);
            // The rest of the response, including the backend's cookie, is left unread.
            Assert.assertNull(result.getPagedResultsCookie());
        } finally {
            deleteAll(facade, uids);
        }
    }

    @Test
    public void testOperationsAfterTheFirstDoNotTakeA401() throws Exception {
        final ConnectorFacade facade = getFacade();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, createUserAttributes("John", "Auth0001"), null);
        try {
            // A fresh client takes one 401, which fills the shared AuthCache.
            Assert.assertNotNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
            requestCounter.reset();
            for (int i = 0; i < 20; i++) {
                Assert.assertNotNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
            }
            Assert.assertEquals(requestCounter.unauthorized(), 0);
            Assert.assertEquals(requestCounter.requests(), 20);
        } finally {
            facade.delete(ObjectClass.ACCOUNT, uid, null);
        }
    }

    @Test
    public void testPreemptiveBasicNeverTakesA401() throws Exception {
        final ConnectorFacade facade = getFacade();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, createUserAttributes("John", "Preempt0001"), null);
        final APIConfiguration config = TestHelpers.createTestConfiguration(ScriptedCRESTConnector.class,
                TestHelpers.getProperties(ScriptedConnectorBase.class, TEST_NAME), "configuration");
        config.getConfigurationProperties().setPropertyValue("defaultAuthMethod", "BASIC_PREEMPTIVE");
        final ConnectorFacade preemptive = ConnectorFacadeFactory.getInstance().newInstance(config);
        try {
            requestCounter.reset();
            // A new client: its first request already carries the credentials.
            for (int i = 0; i < 3; i++) {
                Assert.assertNotNull(preemptive.getObject(ObjectClass.ACCOUNT, uid, null));
            }
            Assert.assertEquals(requestCounter.unauthorized(), 0);
            Assert.assertEquals(requestCounter.requests(), 3);
        } finally {
            ((LocalConnectorFacadeImpl) preemptive).dispose();
            facade.delete(ObjectClass.ACCOUNT, uid, null);
        }
    }

    @Test
    public void testOperationsReuseOneConnection() throws Exception {
        final ConnectorFacade facade = getFacade();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, createUserAttributes("John", "Reuse0001"), null);
        try {
            Assert.assertNotNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
            requestCounter.reset();
            for (int i = 0; i < 20; i++) {
                Assert.assertNotNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
            }
            Assert.assertEquals(requestCounter.requests(), 20);
            Assert.assertEquals(requestCounter.connections(), 1);
        } finally {
            facade.delete(ObjectClass.ACCOUNT, uid, null);
        }
    }

    private Set<Attribute> createUserAttributes(String firstName, String lastName) {
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name(lastName.toLowerCase()));
        createAttributes.add(AttributeBuilder.buildCurrentPassword("Passw0rd".toCharArray()));
        createAttributes.add(AttributeBuilder.build("givenName", firstName));
        createAttributes.add(AttributeBuilder.build("sn", lastName));
        createAttributes.add(AttributeBuilder
                .build("mail", lastName.toLowerCase() + "@example.com"));
        return createAttributes;
    }

}
