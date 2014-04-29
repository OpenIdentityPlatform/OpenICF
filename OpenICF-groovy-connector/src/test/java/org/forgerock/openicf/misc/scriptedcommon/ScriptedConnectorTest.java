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
 */

package org.forgerock.openicf.misc.scriptedcommon;

import static org.forgerock.openicf.connectors.RESTTestBase.createConnectorFacade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Set;

import org.forgerock.openicf.connectors.groovy.ScriptedConnector;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.identityconnectors.framework.common.exceptions.PreconditionFailedException;
import org.identityconnectors.framework.common.exceptions.PreconditionRequiredException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ScriptedConnectorTest {

    /**
     * Setup logging for the {@link ScriptedConnectorTest}.
     */
    private static final Log logger = Log.getLog(ScriptedConnectorTest.class);

    protected static final String TEST_NAME = "GROOVY";
    private static final ObjectClass TEST = new ObjectClass("__TEST__");
    private static final ObjectClass SAMPLE = new ObjectClass("__SAMPLE__");

    private ConnectorFacade facade;

    // =======================================================================
    // Authenticate Operation Test
    // =======================================================================

    @Test(expectedExceptions = ConnectorSecurityException.class)
    public void testAuthenticate1() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST1",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    @Test(expectedExceptions = InvalidCredentialException.class)
    public void testAuthenticate2() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST2",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    @Test(expectedExceptions = InvalidPasswordException.class)
    public void testAuthenticate3() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST3",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    @Test(expectedExceptions = PermissionDeniedException.class)
    public void testAuthenticate4() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST4",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    @Test
    public void testAuthenticate5() throws Exception {
        Assert.assertEquals(getFacade(TEST_NAME).authenticate(TEST, "TEST5",
                new GuardedString("Passw0rd".toCharArray()), null).getUidValue(), "TEST5");
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testAuthenticate6() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST6",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    // =======================================================================
    // Create Operation Test
    // =======================================================================

    @Test
    public void testCreate() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("Foo");
        ConnectorFacade facade = getFacade(TEST_NAME);
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        Assert.assertNotNull(uid);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);
        Assert.assertEquals(co.getUid(), uid);
    }

    @Test(expectedExceptions = AlreadyExistsException.class)
    public void testCreateTest1() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST1");
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.create(TEST, createAttributes, null);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void testCreateTest2() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST2");
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.create(TEST, createAttributes, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateTest3() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST3");
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.create(TEST, createAttributes, null);
    }

    @Test(expectedExceptions = RetryableException.class)
    public void testCreateTest4() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST4");
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.create(TEST, createAttributes, null);
    }

    @Test
    public void testCreateTest5() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST5");
        ConnectorFacade facade = getFacade(TEST_NAME);
        Uid uid = facade.create(TEST, createAttributes, null);
        Assert.assertEquals(uid.getUidValue(), "TEST5");
    }

    @Test(expectedExceptions = OperationTimeoutException.class)
    public void testCreateTimeOut() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TIMEOUT");
        getFacade(TEST_NAME).create(TEST, createAttributes, null);
        Assert.fail();
    }

    // =======================================================================
    // Delete Operation Test
    // =======================================================================

    @Test(expectedExceptions = UnknownUidException.class)
    public void testDelete1() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.delete(TEST, new Uid("TEST1"), null);
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testDelete4() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.delete(TEST, new Uid("TEST4"), null);
    }

    @Test(expectedExceptions = PreconditionRequiredException.class)
    public void testDelete5() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.delete(TEST, new Uid("TEST5"), null);
    }

    @Test(expectedExceptions = OperationTimeoutException.class)
    public void testDeleteTimeOut() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TIMEOUT"), null);
        Assert.fail();
    }

    // =======================================================================
    // ResolveUsername Operation Test
    // =======================================================================

    @Test
    public void testResolveUsername() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        Uid uidAfter = facade.resolveUsername(ObjectClass.ACCOUNT, "TEST1", null);
        Assert.assertEquals(uidAfter.getUidValue(), "123");
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testResolveUsername1() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.resolveUsername(ObjectClass.ACCOUNT, "NON_EXIST", null);
    }

    // =======================================================================
    // Schema Operation Test
    // =======================================================================

    @Test
    public void testSchema() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        Schema schema = facade.schema();
        Assert.assertNotNull(schema.findObjectClassInfo("__TEST__"));
    }

    // =======================================================================
    // ScriptOnConnector Operation Test
    // =======================================================================

    @Test
    public void testScriptOnConnector() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("Groovy");
        builder.setScriptText("return uid");
        Uid uid = new Uid("foo", "12345");
        builder.addScriptArgument("uid", uid);
        Assert.assertEquals(facade.runScriptOnConnector(builder.build(), null), uid);
    }

    // =======================================================================
    // ScriptOnResource Operation Test
    // =======================================================================

    @Test
    public void testScriptOnResource() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("SHELL");
        builder.setScriptText("test");
        Assert.assertEquals(facade.runScriptOnResource(builder.build(), null), true);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void testScriptOnResourceFail() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("BASH");
        builder.setScriptText("test");
        Assert.assertEquals(facade.runScriptOnResource(builder.build(), null), true);
    }

    // =======================================================================
    // Search Operation Test
    // =======================================================================

    @Test
    public void testSearch() throws Exception {
        ConnectorObject co = getFacade(TEST_NAME).getObject(SAMPLE, new Uid("1"), null);
        Assert.assertNotNull(co);
    }

    @Test
    public void testSearch1() throws Exception {
        ConnectorFacade search = getFacade(TEST_NAME);
        List<ConnectorObject> result =
                TestHelpers.searchToList(search, new ObjectClass("__EMPTY__"), null);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testSearch2() throws Exception {
        ConnectorFacade search = getFacade(TEST_NAME);
        for (int i = 0; i < 100; i++) {
            Set<Attribute> co = getTestConnectorObject(String.format("TEST%05d", i));
            co.add(AttributeBuilder.build("sortKey", i));
            search.create(ObjectClass.ACCOUNT, co, null);
        }

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setPageSize(10);
        builder.setSortKeys(new SortKey("sortKey", false));
        SearchResult result = null;

        final Set<ConnectorObject> resultSet = new HashSet<ConnectorObject>();
        int pageIndex = 0;

        while ((result =
                search.search(ObjectClass.ACCOUNT, FilterBuilder.startsWith(AttributeBuilder.build(
                        Name.NAME, "TEST")), new ResultsHandler() {
                    private int index = 101;

                    public boolean handle(ConnectorObject connectorObject) {
                        Integer idx =
                                AttributeUtil.getIntegerValue(connectorObject
                                        .getAttributeByName("sortKey"));
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

    // =======================================================================
    // Sync Operation Test
    // =======================================================================

    @Test
    public void testSync() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        final List<SyncDelta> result = new ArrayList<SyncDelta>();

        SyncToken lastToken =
                facade.sync(ObjectClass.ACCOUNT, new SyncToken(0), new SyncResultsHandler() {
                    public boolean handle(SyncDelta delta) {
                        return result.add(delta);
                    }
                }, null);
        Assert.assertEquals(lastToken.getValue(), 1);
        Assert.assertEquals(result.size(), 1);
        SyncDelta delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.CREATE);
        Assert.assertEquals(delta.getObject().getAttributes().size(), 44);

        lastToken = facade.sync(ObjectClass.ACCOUNT, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 2);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.UPDATE);

        lastToken = facade.sync(ObjectClass.ACCOUNT, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 3);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.CREATE_OR_UPDATE);

        lastToken = facade.sync(ObjectClass.ACCOUNT, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 4);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.UPDATE);
        Assert.assertEquals(delta.getPreviousUid().getUidValue(), "001");

        lastToken = facade.sync(ObjectClass.ACCOUNT, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 5);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.DELETE);

        lastToken = facade.sync(ObjectClass.ACCOUNT, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 10);
        Assert.assertTrue(result.isEmpty());

        lastToken = facade.sync(ObjectClass.ACCOUNT, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 17);
        Assert.assertEquals(result.size(), 4);
        result.clear();

        lastToken = facade.sync(ObjectClass.GROUP, new SyncToken(10), new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 16);
        Assert.assertEquals(result.size(), 3);

    }

    @Test
    public void testSyncAll() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        final List<SyncDelta> result = new ArrayList<SyncDelta>();

        SyncToken lastToken =
                facade.sync(ObjectClass.ALL, new SyncToken(0), new SyncResultsHandler() {
                    public boolean handle(SyncDelta delta) {
                        return result.add(delta);
                    }
                }, null);
        Assert.assertEquals(lastToken.getValue(), 17);
        Assert.assertEquals(result.size(), 7);
        int index = 10;
        for (SyncDelta delta : result) {
            Assert.assertEquals(index++, delta.getToken().getValue());
            if (((Integer) delta.getToken().getValue()) % 2 == 0) {
                Assert.assertEquals(delta.getObject().getObjectClass(), ObjectClass.ACCOUNT);
            } else {
                Assert.assertEquals(delta.getObject().getObjectClass(), ObjectClass.GROUP);
            }
        }
    }

    @Test
    public void testSyncSample() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        final List<SyncDelta> result = new ArrayList<SyncDelta>();

        SyncToken lastToken = facade.sync(SAMPLE, new SyncToken(0), new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), "SAMPLE");
        Assert.assertEquals(result.size(), 2);
    }

    @Test
    public void testSyncToken() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);

        SyncToken lastToken = facade.getLatestSyncToken(ObjectClass.ACCOUNT);
        Assert.assertEquals(lastToken.getValue(), 17);
        lastToken = facade.getLatestSyncToken(ObjectClass.GROUP);
        Assert.assertEquals(lastToken.getValue(), 16);
        lastToken = facade.getLatestSyncToken(ObjectClass.ALL);
        Assert.assertEquals(lastToken.getValue(), 17);
        lastToken = facade.getLatestSyncToken(TEST);
        Assert.assertEquals(lastToken.getValue(), 0);
        lastToken = facade.getLatestSyncToken(SAMPLE);
        Assert.assertEquals(lastToken.getValue(), "ANY OBJECT");
    }

    // =======================================================================
    // Test Operation Test
    // =======================================================================

    @Test(expectedExceptions = MissingResourceException.class)
    public void testTest() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.test();
    }

    // =======================================================================
    // Update Operation Test
    // =======================================================================

    @Test(expectedExceptions = UnknownUidException.class)
    public void testDelete() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.delete(ObjectClass.ACCOUNT, new Uid("NON_EXIST"), null);
    }

    @Test(dependsOnMethods = "testCreate")
    public void testUpdate() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("email", "foo@example.com"));

        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("2"), updateAttributes, null);
        Assert.assertEquals(uid.getUidValue(), "2");
    }

    @Test(expectedExceptions = OperationTimeoutException.class)
    public void testUpdateTimeOut() throws Exception {
        getFacade(TEST_NAME).update(TEST, new Uid("TIMEOUT"),
                CollectionUtil.newSet(AttributeBuilder.build("null")), null);
        Assert.fail();
    }

    private Set<Attribute> getTestConnectorObject(String name) {
        Set<Attribute> createAttributes = new HashSet<Attribute>(1);
        createAttributes.add(new Name(name));
        createAttributes.add(AttributeBuilder.build("email", name + "@example.com"));
        return createAttributes;
    }

    protected ConnectorFacade getFacade(String environment) {
        if (null == facade) {
            facade = createConnectorFacade(ScriptedConnector.class, environment);
        }
        return facade;
    }
}
