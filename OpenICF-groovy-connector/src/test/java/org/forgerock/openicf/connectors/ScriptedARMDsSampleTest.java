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

package org.forgerock.openicf.connectors;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.forgerock.openicf.connectors.RESTTestBase.createConnectorFacade;

import java.util.HashSet;
import java.util.Set;

import org.fest.assertions.core.Condition;
import org.forgerock.openicf.connectors.groovy.ScriptedConnector;
import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConnector;
import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConnector;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.test.common.TestHelpers;
import org.identityconnectors.test.common.ToListResultsHandler;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The ScriptedARMDsSampleTest class demonstrates the ARM mbed Device Server API usage
 * via Groovy scripts.
 */
public class ScriptedARMDsSampleTest {

    protected static final String ARMDS_TEST_NAME = "ARM";

    private ConnectorFacade facadeInstance;
    private String domain = null;

    @BeforeClass
    public void setUp() throws Exception {
        String username =
                TestHelpers.getProperties(ScriptedConnectorBase.class, ARMDS_TEST_NAME)
                        .getStringProperty("configuration.username");
        if ("__configureme__".equals(username)) {
            throw new SkipException("REST Sample tests are skipped. Create private configuration!");
        }
    }

    @AfterClass
    public synchronized void afterClass() {
        if (facadeInstance instanceof LocalConnectorFacadeImpl) {
            ((LocalConnectorFacadeImpl) facadeInstance).dispose();
        }
        facadeInstance = null;
    }

    protected ConnectorFacade getFacade() {
        if (null == facadeInstance) {
            facadeInstance = createConnectorFacade(ScriptedRESTConnector.class, ARMDS_TEST_NAME);
        }
        return facadeInstance;
    }

    @Test(enabled = true)
    public void testTest() throws Exception {
        ConnectorFacade facade = getFacade();
        facade.validate();
        facade.test();
    }

    @Test(enabled = false)
    public void testGet() throws Exception {
        ConnectorFacade facade = getFacade();
        ConnectorObject co = facade.getObject(new ObjectClass("user"), new Uid(""), null);
        Assert.assertNotNull(co);
    }

    @Test(enabled = false)
    public void testDelete() throws Exception {
        ConnectorFacade facade = getFacade();
        facade.delete(new ObjectClass("group"), new Uid(""),
                null);
    }

    @Test(enabled = true)
    public void testQueryAll() throws Exception {
        ConnectorFacade facade = getFacade();
        ToListResultsHandler handler = new ToListResultsHandler();
        SearchResult result = facade.search(new ObjectClass("endpoint"), null, handler, null);
        Assert.assertFalse(handler.getObjects().isEmpty());
        handler = new ToListResultsHandler();
        result =
                facade.search(new ObjectClass("endpoint"), FilterBuilder.equalTo(AttributeBuilder
                        .build("status", false)), handler, null);
        Assert.assertFalse(handler.getObjects().isEmpty());
        Assert.assertNotNull(facade.getObject(new ObjectClass("endpoint"), new Uid("switch-node-001"), null));
    }

    @Test(enabled = true)
    public void testQueryGroupsAll() throws Exception {
        ConnectorFacade facade = getFacade();
        ToListResultsHandler handler = new ToListResultsHandler();
        SearchResult result = facade.search( ObjectClass.GROUP, null, handler, null);
        Assert.assertFalse(handler.getObjects().isEmpty());
//        handler = new ToListResultsHandler();
//        result =
//                facade.search(new ObjectClass("user"), FilterBuilder.equalTo(AttributeBuilder
//                        .build("accountEnabled", true)), handler, null);
//        Assert.assertFalse(handler.getObjects().isEmpty());
        Assert.assertNotNull(facade.getObject(ObjectClass.GROUP, new Uid("group-001"), null));
    }

    @Test(enabled = true)
    public void testSchema() throws Exception {
        ConnectorFacade facade = getFacade();
        Schema schema = facade.schema();
        assertThat(schema.getObjectClassInfo()).areExactly(1, new Condition<ObjectClassInfo>() {
            public boolean matches(ObjectClassInfo value) {
                return value.is("endpoint");
            }
        }).areExactly(1, new Condition<ObjectClassInfo>() {
            public boolean matches(ObjectClassInfo value) {
                return value.is(ObjectClass.GROUP_NAME);
            }
        });
    }

    @Test(enabled = false)
    public void testCreateUser() throws Exception {
        final ConnectorFacade facade = getFacade();
        final ObjectClass user = new ObjectClass("user");
        //@formatter:off
        /*       
        {
            "accountEnabled": true,
            "displayName": "Alex Wu",
            "mailNickname": "AlexW",
            "passwordProfile": { "password" : "Test1234", "forceChangePasswordNextLogin": false },
            "userPrincipalName": "Alex@contoso.onmicrosoft.com"
            "immutableId": "6SNZnFLe6jOI3n68hjO5zA=="
        }
        */
        //@formatter:on

        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(AttributeBuilder.build("accountEnabled", true));
        createAttributes.add(AttributeBuilder.build("displayName", "Alex Wu"));
        createAttributes.add(AttributeBuilder.build("mailNickname", "AlexW"));
        createAttributes.add(AttributeBuilder.build("passwordProfile", CollectionUtil.newMap(
                "password", "Test1234", "forceChangePasswordNextLogin", false)));
        createAttributes.add(AttributeBuilder.build("userPrincipalName", "AlexW@" + domain));
        // createAttributes.add(AttributeBuilder.build("immutableId",
        // "6SNZnFLe6jOI3n68hjO5zA=="));
        //
        // createAttributes.add(AttributeBuilder.build("assignedLicenses",
        // CollectionUtil.newMap(
        // "disabledPlans", new ArrayList<String>(), "skuId",
        // "078d2b04-f1bd-4111-bbd4-b4b1b354cef4"),
        // CollectionUtil.newMap("disabledPlans",
        // CollectionUtil.newList("43de0ff5-c92c-492b-9116-175376d08c38",
        // "0feaeb32-d00e-4d66-bd5a-43b5b83db82c"), "skuId",
        // "078d2b04-f1bd-4111-bbd4-b4b1b354cef4")));
        //
        createAttributes.add(AttributeBuilder.build("otherMails", "admin1@" + domain, "admin4@"
                + domain));

        Uid uid = facade.create(user, createAttributes, null);
        Assert.assertNotNull(uid);
        ConnectorObject co = facade.getObject(user, uid, null);
        Assert.assertNotNull(co);

        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.build("telephoneNumber", "555-1234"));
        uid = facade.update(user, uid, updateAttributes, null);
        co = facade.getObject(user, uid, null);
        Assert.assertEquals(AttributeUtil.getStringValue(co.getAttributeByName("telephoneNumber")),
                "555-1234");
        facade.delete(user, uid, null);
    }

    @Test(enabled = false)
    public void testCreateGroup() throws Exception {
        final ConnectorFacade facade = getFacade();

        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("group-001"));

        createAttributes.add(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "OpenICF Connector Test Group"));

        Uid uid = facade.create(ObjectClass.GROUP, createAttributes, null);
        Assert.assertNotNull(uid);
        ConnectorObject co = facade.getObject(ObjectClass.GROUP, uid, null);
        Assert.assertNotNull(co);
        facade.delete(ObjectClass.GROUP, uid, null);
    }
}
