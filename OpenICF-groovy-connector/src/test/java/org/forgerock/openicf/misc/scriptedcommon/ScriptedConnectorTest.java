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

import static org.fest.assertions.api.Assertions.*;
import static org.forgerock.openicf.connectors.RESTTestBase.createConnectorFacade;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.*;
import static org.identityconnectors.framework.common.objects.filter.FilterBuilder.not;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

import org.forgerock.openicf.connectors.groovy.ScriptedConnector;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
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
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
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
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.test.common.TestHelpers;
import org.identityconnectors.test.common.ToListResultsHandler;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import groovy.json.JsonSlurper;

public class ScriptedConnectorTest {

    /**
     * Setup logging for the {@link ScriptedConnectorTest}.
     */
    private static final Log logger = Log.getLog(ScriptedConnectorTest.class);

    protected static final String TEST_NAME = "GROOVY";
    private static final ObjectClass TEST = new ObjectClass("__TEST__");
    private static final ObjectClass SAMPLE = new ObjectClass("__SAMPLE__");
    private static final ObjectClass UNKNOWN = new ObjectClass("__UNKNOWN__");

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

    @Test(expectedExceptions = PasswordExpiredException.class)
    public void testAuthenticate5() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST5",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testAuthenticate7() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TEST7",
                new GuardedString("Passw0rd".toCharArray()), null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testAuthenticateUnsupportedObjectClass() throws Exception {
        Assert.assertEquals(getFacade(TEST_NAME).authenticate(UNKNOWN, "TESTOK1",
                new GuardedString("Passw0rd".toCharArray()), null).getUidValue(), "TESTOK1");
    }

    @Test
    public void testAuthenticateOK() throws Exception {
        Assert.assertEquals(getFacade(TEST_NAME).authenticate(TEST, "TESTOK1",
                new GuardedString("Passw0rd".toCharArray()), null).getUidValue(), "TESTOK1");
    }

    @Test
    public void testAuthenticateEmpty() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TESTOK2", new GuardedString("".toCharArray()),
                null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void testAuthenticateNotEmpty() throws Exception {
        getFacade(TEST_NAME).authenticate(TEST, "TESTOK2",
                new GuardedString("NOT_EMPTY".toCharArray()), null);
    }

    // =======================================================================
    // Create Operation Test
    // =======================================================================

    @Test
    public void testCreate() throws Exception {
        createTestUser("Foo");
    }

    @Test(expectedExceptions = AlreadyExistsException.class)
    public void testCreate1() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST1");
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.create(TEST, createAttributes, null);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void testCreate2() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST2");
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.create(TEST, createAttributes, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreate3() throws Exception {
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

    @Test
    public void testCreateTestRunAs() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST5");
        ConnectorFacade facade = getFacade(TEST_NAME);
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setRunAsUser("admin");
        builder.setRunWithPassword(new GuardedString("Passw0rd".toCharArray()));
        Uid uid = facade.create(TEST, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "TEST5");
    }

    @Test(expectedExceptions = ConnectorSecurityException.class)
    public void testCreateTestRunAsFailed() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST5");
        ConnectorFacade facade = getFacade(TEST_NAME);
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setRunAsUser("admin");
        builder.setRunWithPassword(new GuardedString("_FAKE_".toCharArray()));
        Uid uid = facade.create(TEST, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "TEST5");
    }

    @Test(expectedExceptions = OperationTimeoutException.class)
    public void testCreateTimeOut() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TIMEOUT");
        getFacade(TEST_NAME).create(TEST, createAttributes, null);
        Assert.fail();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCreateUnsupportedObjectClass() throws Exception {
        Set<Attribute> createAttributes = getTestConnectorObject("TEST5");
        getFacade(TEST_NAME).create(UNKNOWN, createAttributes, null);
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

    @Test(expectedExceptions = ConfigurationException.class)
    public void testDeleteCEException() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TESTEX_CE"), null);
        Assert.fail();
    }

    @Test(expectedExceptions = ConnectionBrokenException.class)
    public void testDeleteCBException() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TESTEX_CB"), null);
        Assert.fail();
    }

    @Test(expectedExceptions = ConnectionFailedException.class)
    public void testDeleteCFException() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TESTEX_CF"), null);
        Assert.fail();
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void testDeleteCException() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TESTEX_C"), null);
        Assert.fail();
    }

    @Test(expectedExceptions = ConnectorIOException.class)
    public void testDeleteCIOException() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TESTEX_CIO"), null);
        Assert.fail();
    }

    @Test(expectedExceptions = OperationTimeoutException.class)
    public void testDeleteOTException() throws Exception {
        getFacade(TEST_NAME).delete(TEST, new Uid("TESTEX_OT"), null);
        Assert.fail();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testDeleteUnsupportedObjectClass() throws Exception {
        getFacade(TEST_NAME).delete(UNKNOWN, new Uid("001"), null);
    }

    // =======================================================================
    // ResolveUsername Operation Test
    // =======================================================================

    @Test
    public void testResolveUsername1() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        Uid uidAfter = facade.resolveUsername(ObjectClass.ACCOUNT, "TESTOK1", null);
        Assert.assertEquals(uidAfter.getUidValue(), "123");
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testResolveUsername2() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.resolveUsername(ObjectClass.ACCOUNT, "NON_EXIST", null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testResolveUsername3() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.resolveUsername(UNKNOWN, "NON_EXIST", null);
    }

    // =======================================================================
    // Schema Operation Test
    // =======================================================================

    @Test
    public void testSchema1() throws Exception {
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

    @Test(expectedExceptions = ConnectorException.class,
            expectedExceptionsMessageRegExp = "No argument has arrived.")
    public void testScriptOnConnectorArgFail() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("Groovy");
        builder.setScriptText("try{return loginName}catch(MissingPropertyException e){throw new Exception(\"No argument has arrived.\")}");
        facade.runScriptOnConnector(builder.build(), null);
    }

    // =======================================================================
    // ScriptOnResource Operation Test
    // =======================================================================

    @Test
    public void testScriptOnResource() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("Groovy");
        builder.setScriptText("return arg");
        builder.addScriptArgument("arg01", true);
        builder.addScriptArgument("arg02", "String");
        assertThat((Map) facade.runScriptOnResource(builder.build(), null)).contains(
                entry("arg01", true), entry("arg02", "String"));

    }

    @Test(expectedExceptions = ConnectorException.class,
            expectedExceptionsMessageRegExp = "No argument has arrived.")
    public void testScriptOnResourceArgFail() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("Groovy");
        builder.setScriptText("try{return loginName}catch(MissingPropertyException e){throw new Exception(\"No argument has arrived.\")}");
        facade.runScriptOnResource(builder.build(), null);
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
    // Get Operation Test
    // =======================================================================

    @Test
    public void testGet() throws Exception {
        ConnectorObject co = getFacade(TEST_NAME).getObject(TEST, new Uid("ANY"), null);
        Assert.assertEquals(co.getUid().getUidValue(), "ANY");
        Assert.assertEquals(co.getName().getNameValue(), "ANY");
        Assert.assertEquals(co.getObjectClass(), TEST);

        assertThat(co.getAttributeByName("attributeString").getValue()).describedAs(
                "attributeString").containsOnly("retipipiter");
        assertThat(co.getAttributeByName("attributeStringMultivalue").getValue()).describedAs(
                "attributeStringMultivalue").containsOnly("value1", "value2");

        assertThat(co.getAttributeByName("attributelongp").getValue())
                .describedAs("attributelongp").containsOnly(11l);
        assertThat(co.getAttributeByName("attributelongpMultivalue").getValue()).describedAs(
                "attributelongpMultivalue").containsOnly(12l, 13l);

        assertThat(co.getAttributeByName("attributeLong").getValue()).describedAs("attributeLong")
                .containsOnly(14l);
        assertThat(co.getAttributeByName("attributeLongMultivalue").getValue()).describedAs(
                "attributeLongMultivalue").containsOnly(15l, 16l);

        assertThat(co.getAttributeByName("attributechar").getValue()).describedAs("attributechar")
                .containsOnly('a');
        assertThat(co.getAttributeByName("attributecharMultivalue").getValue()).describedAs(
                "attributecharMultivalue").containsOnly('b', 'c');

        assertThat(co.getAttributeByName("attributeCharacter").getValue()).describedAs(
                "attributeCharacter").containsOnly('d');
        assertThat(co.getAttributeByName("attributeCharacterMultivalue").getValue()).describedAs(
                "attributeCharacterMultivalue").containsOnly('e', 'f');

        assertThat(co.getAttributeByName("attributedoublep").getValue()).describedAs(
                "attributedoublep").containsOnly(Double.MIN_NORMAL);
        assertThat(co.getAttributeByName("attributedoublepMultivalue").getValue()).describedAs(
                "attributedoublepMultivalue").containsOnly(Double.MIN_VALUE, Double.MAX_VALUE);

        assertThat(co.getAttributeByName("attributeDouble").getValue()).describedAs(
                "attributeDouble").containsOnly(17D);
        assertThat(co.getAttributeByName("attributeDoubleMultivalue").getValue()).describedAs(
                "attributeDoubleMultivalue").containsOnly(18D, 19D);

        assertThat(co.getAttributeByName("attributefloatp").getValue()).describedAs(
                "attributefloatp").containsOnly(20F);
        assertThat(co.getAttributeByName("attributefloatpMultivalue").getValue()).describedAs(
                "attributefloatpMultivalue").containsOnly(21F, 22F);

        assertThat(co.getAttributeByName("attributeFloat").getValue())
                .describedAs("attributeFloat").containsOnly(23F);
        assertThat(co.getAttributeByName("attributeFloatMultivalue").getValue()).describedAs(
                "attributeFloatMultivalue").containsOnly(24F, 25F);

        assertThat(co.getAttributeByName("attributeint").getValue()).describedAs("attributeint")
                .containsOnly(26);
        assertThat(co.getAttributeByName("attributeintMultivalue").getValue()).describedAs(
                "attributeintMultivalue").containsOnly(27, 28);

        assertThat(co.getAttributeByName("attributeInteger").getValue()).describedAs(
                "attributeInteger").containsOnly(29);
        assertThat(co.getAttributeByName("attributeIntegerMultivalue").getValue()).describedAs(
                "attributeIntegerMultivalue").containsOnly(30, 31);

        assertThat(co.getAttributeByName("attributebooleanp").getValue()).describedAs(
                "attributebooleanp").containsOnly(true);
        assertThat(co.getAttributeByName("attributebooleanpMultivalue").getValue()).describedAs(
                "attributebooleanpMultivalue").containsOnly(true, false);

        assertThat(co.getAttributeByName("attributeBoolean").getValue()).describedAs(
                "attributeBoolean").containsOnly(false);
        assertThat(co.getAttributeByName("attributeBooleanMultivalue").getValue()).describedAs(
                "attributeBooleanMultivalue").containsOnly(true, false);

        assertThat(co.getAttributeByName("attributebytep").getValue())
                .describedAs("attributebytep").containsOnly((byte) 48);
        assertThat(co.getAttributeByName("attributebytepMultivalue").getValue()).describedAs(
                "attributebytepMultivalue").containsOnly((byte) 49, (byte) 50);

        assertThat(co.getAttributeByName("attributeByte").getValue()).describedAs("attributeByte")
                .containsOnly((byte) 51);
        assertThat(co.getAttributeByName("attributeByteMultivalue").getValue()).describedAs(
                "attributeByteMultivalue").containsOnly((byte) 52, (byte) 53);

        assertThat(co.getAttributeByName("attributeByteArray").getValue()).describedAs(
                "attributeByteArray").containsOnly(
                new Object[] { "array".getBytes(Charset.forName("UTF-8")) });
        assertThat(co.getAttributeByName("attributeByteArrayMultivalue").getValue()).describedAs(
                "attributeByteArrayMultivalue").containsOnly(
                "item1".getBytes(Charset.forName("UTF-8")),
                "item2".getBytes(Charset.forName("UTF-8")));

        assertThat(co.getAttributeByName("attributeBigDecimal").getValue()).describedAs(
                "attributeBigDecimal").containsOnly(BigDecimal.ONE);
        assertThat(co.getAttributeByName("attributeBigDecimalMultivalue").getValue()).describedAs(
                "attributeBigDecimalMultivalue").containsOnly(BigDecimal.ZERO, BigDecimal.TEN);

        assertThat(co.getAttributeByName("attributeBigInteger").getValue()).describedAs(
                "attributeBigInteger").containsOnly(BigInteger.ONE);
        assertThat(co.getAttributeByName("attributeBigIntegerMultivalue").getValue()).describedAs(
                "attributeBigIntegerMultivalue").containsOnly(BigInteger.ZERO, BigInteger.TEN);

        assertThat(co.getAttributeByName("attributeGuardedByteArray").getValue()).describedAs(
                "attributeGuardedByteArray").containsOnly(
                new GuardedByteArray("array".getBytes(Charset.forName("UTF-8"))));
        assertThat(co.getAttributeByName("attributeGuardedByteArrayMultivalue").getValue())
                .describedAs("attributeGuardedByteArrayMultivalue").containsOnly(
                        new GuardedByteArray("item1".getBytes(Charset.forName("UTF-8"))),
                        new GuardedByteArray("item2".getBytes(Charset.forName("UTF-8"))));

        assertThat(co.getAttributeByName("attributeGuardedString").getValue()).describedAs(
                "attributeGuardedString").containsOnly(new GuardedString("secret".toCharArray()));
        assertThat(co.getAttributeByName("attributeGuardedStringMultivalue").getValue())
                .describedAs("attributeGuardedStringMultivalue").containsOnly(
                        new GuardedString("secret1".toCharArray()),
                        new GuardedString("secret2".toCharArray()));

        assertThat(co.getAttributeByName("attributeMap").getValue()).describedAs("attributeMap")
                .containsOnly(createAssertMap(42));
        assertThat(co.getAttributeByName("attributeMapMultivalue").getValue()).describedAs(
                "attributeMapMultivalue").containsOnly(createAssertMap(42), createAssertMap(43));

    }

    private Map<String, Object> createAssertMap(int n) {
        Map<String, Object> result = new HashMap<String, Object>(6);
        result.put("string", "String");
        result.put("number", n);
        result.put("trueOrFalse", true);
        result.put("nullValue", null);
        result.put("collection", Arrays.asList("item1", "item2"));
        Map<String, Object> o = new HashMap<String, Object>(2);
        o.put("key1", "value1");
        o.put("key2", "value2");
        result.put("object", o);
        return result;
    }

    // =======================================================================
    // Search Operation Test
    // =======================================================================

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

        ToListResultsHandler handler = new ToListResultsHandler();
        SearchResult result = getFacade(TEST_NAME).search(TEST, and(left, right), handler, null);
        Assert.assertEquals(handler.getObjects().size(), 10);
        Assert.assertNotNull(result.getPagedResultsCookie());

        Map queryMap = (Map) new JsonSlurper().parseText(result.getPagedResultsCookie());
        assertThat(queryMap).contains(entry("operation", "AND"));

        left =
                and(left, containsAllValues(AttributeBuilder.build("attributeStringMultivalue",
                        "value1", "value2")));

        try {
            handler = new ToListResultsHandler();
            getFacade(TEST_NAME).search(TEST, and(left, right), handler, null);
            fail("containsAllValues should raise exception if not implemented");
        } catch (UnsupportedOperationException e) {
            /* expected */
            Assert.assertEquals(e.getMessage(),
                    "ContainsAllValuesFilter transformation is not supported");
        }
    }

    @Test
    public void testSearch() throws Exception {
        ConnectorObject co = getFacade(TEST_NAME).getObject(SAMPLE, new Uid("1"), null);
        Assert.assertNotNull(co);
    }

    @Test
    public void testSearchAttributes() throws Exception {
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("attributeString", "attributeMap");
        getFacade(TEST_NAME).search(TEST, null, new ResultsHandler() {
            public boolean handle(ConnectorObject connectorObject) {
                Assert.assertEquals(connectorObject.getAttributes().size(), 4);
                return true;
            }
        }, builder.build());
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
            Set<Attribute> co = getTestConnectorObject(String.format("TESTS%05d", i));
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
                search.search(ObjectClass.ACCOUNT, startsWith(AttributeBuilder.build(Name.NAME,
                        "TESTS")), new ResultsHandler() {
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

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSearchUnsupportedObjectClass() throws Exception {
        getFacade(TEST_NAME).getObject(UNKNOWN, new Uid("1"), null);
    }

    // =======================================================================
    // Sync Operation Test
    // =======================================================================

    @DataProvider
    public Object[][] SyncObjectClassProvider() {
        return new Object[][] { { ObjectClass.ACCOUNT }, { TEST } };
    }

    @Test(dataProvider = "SyncObjectClassProvider")
    public void testSyncNull(ObjectClass objectClass) throws Exception {
        final List<SyncDelta> result = new ArrayList<SyncDelta>();
        SyncToken lastToken =
                getFacade(TEST_NAME).sync(objectClass, new SyncToken(5), new SyncResultsHandler() {
                    public boolean handle(SyncDelta delta) {
                        return result.add(delta);
                    }
                }, null);
        Assert.assertEquals(lastToken.getValue(), 10);
        Assert.assertTrue(result.isEmpty());
    }

    @Test(dataProvider = "SyncObjectClassProvider")
    public void testSync(ObjectClass objectClass) throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        final List<SyncDelta> result = new ArrayList<SyncDelta>();

        SyncToken lastToken = facade.sync(objectClass, new SyncToken(0), new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 1);
        Assert.assertEquals(result.size(), 1);
        SyncDelta delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.CREATE);
        Assert.assertEquals(delta.getObject().getAttributes().size(), 44);

        lastToken = facade.sync(objectClass, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 2);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.UPDATE);

        lastToken = facade.sync(objectClass, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 3);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.CREATE_OR_UPDATE);

        lastToken = facade.sync(objectClass, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 4);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.UPDATE);
        Assert.assertEquals(delta.getPreviousUid().getUidValue(), "001");

        lastToken = facade.sync(objectClass, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 5);
        Assert.assertEquals(result.size(), 1);
        delta = result.remove(0);
        Assert.assertEquals(delta.getDeltaType(), SyncDeltaType.DELETE);

        lastToken = facade.sync(objectClass, lastToken, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertEquals(lastToken.getValue(), 10);
        Assert.assertTrue(result.isEmpty());

        lastToken = facade.sync(objectClass, lastToken, new SyncResultsHandler() {
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

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSyncUnsupportedObjectClass() throws Exception {
        getFacade(TEST_NAME).sync(UNKNOWN, new SyncToken(0), new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return true;
            }
        }, null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSyncTokenUnsupportedObjectClass() throws Exception {
        getFacade(TEST_NAME).getLatestSyncToken(UNKNOWN);
    }

    // =======================================================================
    // Test Operation Test
    // =======================================================================

    @Test
    public void validate() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        facade.validate();
    }

    @Test(expectedExceptions = MissingResourceException.class)
    public void testTest() throws Exception {
        ConnectorFacade facade = getFacade(TEST_NAME);
        facade.test();
    }

    // =======================================================================
    // Update Operation Test
    // =======================================================================

    @Test
    public void testUpdate() throws Exception {
        Uid uid = createTestUser("TESTOK01");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("email", "foo@example.com"));

        uid = getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, uid, updateAttributes, null);
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testUpdateMVCCNOK1() throws Exception {
        Uid uid = createTestUser("TESTMVCCNOK01");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("email", "foo@example.com"));
        Assert.assertNotNull(uid.getRevision());
        getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, new Uid(uid.getUidValue(), "NOK"),
                updateAttributes, null);
    }

    @Test(expectedExceptions = PreconditionRequiredException.class)
    public void testUpdateMVCCNOK2() throws Exception {
        Uid uid = createTestUser("TESTMVCCNOK02");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(new Name("TEST"));
        updateAttributes.add(AttributeBuilder.build("email", "foo@example.com"));
        Assert.assertNotNull(uid.getRevision());
        getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, new Uid(uid.getUidValue()),
                updateAttributes, null);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class,
            expectedExceptionsMessageRegExp = "Expecting non null value")
    public void testUpdateFailEmpty() throws Exception {
        Uid uid = createTestUser("FAIL01");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("email"));

        uid = getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, uid, updateAttributes, null);
        fail("Connector operation should fail");
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class,
            expectedExceptionsMessageRegExp = "Expecting Boolean value")
    public void testUpdateFailType() throws Exception {
        Uid uid = createTestUser("FAIL02");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("active", "true"));

        uid = getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, uid, updateAttributes, null);
        fail("Connector operation should fail");
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class,
            expectedExceptionsMessageRegExp = "Expecting single value")
    public void testUpdateFailMulti() throws Exception {
        Uid uid = createTestUser("FAIL03");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("userName", "name1", "name2"));

        uid = getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, uid, updateAttributes, null);
        fail("Connector operation should fail");
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class,
            expectedExceptionsMessageRegExp = "Try update non modifiable attribute")
    public void testUpdateFailReadOnly() throws Exception {
        Uid uid = createTestUser("FAIL04");
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("lastModified", "newValue"));

        uid = getFacade(TEST_NAME).update(ObjectClass.ACCOUNT, uid, updateAttributes, null);
        fail("Connector operation should fail");
    }

    @Test(expectedExceptions = OperationTimeoutException.class)
    public void testUpdateTimeOut() throws Exception {
        getFacade(TEST_NAME).update(TEST, new Uid("TIMEOUT"),
                CollectionUtil.newSet(AttributeBuilder.build("null")), null);
        Assert.fail();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testUpdateUnsupportedObjectClass() throws Exception {
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("email", "foo@example.com"));
        getFacade(TEST_NAME).update(UNKNOWN, new Uid("TESTOK1"), updateAttributes, null);
    }

    protected Uid createTestUser(String username) {
        Set<Attribute> createAttributes = getTestConnectorObject(username);
        ConnectorFacade facade = getFacade(TEST_NAME);
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        Assert.assertNotNull(uid);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);
        Assert.assertEquals(co.getUid(), uid);
        return uid;
    }

    protected Set<Attribute> getTestConnectorObject(String name) {
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name(name));
        createAttributes.add(AttributeBuilder.build("userName", name));
        createAttributes.add(AttributeBuilder.build("email", name + "@example.com"));
        createAttributes.add(AttributeBuilder.buildEnabled(true));
        createAttributes.add(AttributeBuilder.build("firstName", "John"));
        createAttributes.add(AttributeBuilder.build("sn", name.toUpperCase()));
        createAttributes.add(AttributeBuilder.buildPassword("Passw0rd".toCharArray()));
        createAttributes.add(AttributeBuilder
                .build(PredefinedAttributes.DESCRIPTION, "Description"));
        createAttributes.add(AttributeBuilder.build("groups", "group1", "group2"));

        return createAttributes;
    }

    protected ConnectorFacade getFacade(String environment) {
        if (null == facade) {
            facade = createConnectorFacade(ScriptedConnector.class, environment);
        }
        return facade;
    }

    @AfterClass
    public synchronized void afterClass() {
        ((LocalConnectorFacadeImpl) facade).dispose();
        facade = null;
    }
}
