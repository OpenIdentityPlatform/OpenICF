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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConnector;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.ConnectorFacade;
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
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
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

    @Test(enabled = false)
    public void testAction() throws Exception {
        final ConnectorFacade facade = getFacade();
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.setScriptLanguage("crest");
        builder.setScriptText("UNKNOWN");
        Object response =  facade.runScriptOnResource(builder.build(), null);
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
