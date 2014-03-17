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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Set;

import org.forgerock.openicf.connectors.groovy.ScriptedConnector;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.test.common.PropertyBag;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ScriptedConnectorTest {

    /**
     * Setup logging for the {@link ScriptedConnectorTest}.
     */
    private static final Log logger = Log.getLog(ScriptedConnectorTest.class);

    protected static final String testCase = "case1";

    private ConnectorFacade facade;

    @Test(expectedExceptions = ConnectorSecurityException.class)
    public void testAuthenticate1() throws Exception {
        getFacade(testCase).authenticate(ObjectClass.ACCOUNT, "TEST1",
                new GuardedString("".toCharArray()), null);
    }

    @Test
    public void testAuthenticate5() throws Exception {
        Assert.assertNotNull(getFacade(testCase).authenticate(ObjectClass.ACCOUNT, "TEST5",
                new GuardedString("Passw0rd".toCharArray()), null));
    }

    @Test
    public void testAuthenticate6() throws Exception {
        Assert.assertNotNull(getFacade(testCase).authenticate(ObjectClass.ACCOUNT, "TEST6",
                new GuardedString("Passw0rd".toCharArray()), null));
    }

    @Test
    public void testCreate() throws Exception {
        Set<Attribute> createAttributes = new HashSet<Attribute>(1);
        createAttributes.add(new Name("1"));
        createAttributes.add(AttributeBuilder.build("email", "mail@example.com"));
        ConnectorFacade facade = getFacade(testCase);
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        Assert.assertNotNull(uid);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);
        Assert.assertEquals(co.getUid(), uid);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testDelete() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        facade.delete(ObjectClass.ACCOUNT, new Uid("NON_EXIST"), null);
    }

    @Test
    public void testResolveUsername() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        Uid uidAfter = facade.resolveUsername(ObjectClass.ACCOUNT, "TEST1", null);
        Assert.assertEquals(uidAfter.getUidValue(), "123");
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void testResolveUsername1() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        facade.resolveUsername(ObjectClass.ACCOUNT, "NON_EXIST", null);
    }

    @Test
    public void testSchema() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        Schema schema = facade.schema();
        Assert.assertNotNull(schema.findObjectClassInfo("__TEST__"));
    }

    @Test
    public void testScriptOnConnector() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("Groovy");
        builder.setScriptText("return uid");
        Uid uid = new Uid("foo", "12345");
        builder.addScriptArgument("uid", uid);
        Assert.assertEquals(facade.runScriptOnConnector(builder.build(), null), uid);
    }

    @Test
    public void testScriptOnResource() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("SHELL");
        builder.setScriptText("test");
        Assert.assertEquals(facade.runScriptOnResource(builder.build(), null), true);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void testScriptOnResourceFail() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("BASH");
        builder.setScriptText("test");
        Assert.assertEquals(facade.runScriptOnResource(builder.build(), null), true);
    }

    @Test
    public void testSearch() throws Exception {
        ConnectorObject co = getFacade(testCase).getObject(ObjectClass.GROUP, new Uid("1"), null);
        Assert.assertNotNull(co);
    }

    @Test
    public void testSearch1() throws Exception {
        ConnectorFacade search = getFacade(testCase);
        List<ConnectorObject> result =
                TestHelpers.searchToList(search, new ObjectClass("__EMPTY__"), null);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testSync() throws Exception {
        ConnectorFacade sync = getFacade(testCase);
        final List<SyncDelta> result = new ArrayList<SyncDelta>();
        SyncToken lastToken = sync.sync(ObjectClass.ACCOUNT, null, new SyncResultsHandler() {
            public boolean handle(SyncDelta delta) {
                return result.add(delta);
            }
        }, null);
        Assert.assertFalse(result.isEmpty());
    }

    @Test(expectedExceptions = MissingResourceException.class)
    public void testTest() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        facade.test();
    }

    @Test(dependsOnMethods = "testCreate")
    public void testUpdate() throws Exception {
        ConnectorFacade facade = getFacade(testCase);
        Set<Attribute> updateAttributes = new HashSet<Attribute>(1);
        updateAttributes.add(AttributeBuilder.build("email", "foo@example.com"));

        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("2"), updateAttributes, null);
        Assert.assertEquals(uid.getUidValue(), "2");
    }

    protected ConnectorFacade getFacade(String environment) {
        if (null == facade) {

            PropertyBag propertyBag =
                    TestHelpers.getProperties(ScriptedConnector.class, environment);

            APIConfiguration impl =
                    TestHelpers.createTestConfiguration(ScriptedConnector.class, propertyBag,
                            "configuration");
            impl.setProducerBufferSize(0);
            impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(
                    false);
            impl.getResultsHandlerConfiguration().setEnableCaseInsensitiveFilter(false);
            impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
            impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);

            facade = ConnectorFacadeFactory.getInstance().newInstance(impl);
        }
        return facade;
    }
}
