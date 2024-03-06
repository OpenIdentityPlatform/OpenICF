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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConnector;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.test.common.PropertyBag;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 *
 * @author Laszlo Hordos
 */
public class ScriptedSQLSampleTest {
    /**
     * Setup logging for the {@link ScriptedSQLConnectorTest}.
     */
    private static final Log logger = Log.getLog(ScriptedSQLConnectorTest.class);

    protected static final String TEST_NAME = "SQL_SAMPLE";
    protected static final ObjectClass ORG = new ObjectClass("organization");

    private ConnectorFacade facadeInstance;

    @BeforeClass
    public void setUp() throws Exception {

        String username =
                TestHelpers.getProperties(ScriptedConnectorBase.class, TEST_NAME)
                        .getStringProperty("configuration.username");
        if ("__configureme__".equals(username)) {
            throw new SkipException("SQL Sample tests are skipped. Create private configuration!");
        }
    }

    @Test
    public void validate() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        facade.validate();
    }

    @Test
    public void testTest() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        facade.test();
    }

    @Test
    public void testSchema() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        Schema schema = facade.schema();
        Assert.assertEquals(schema.getObjectClassInfo().size(), 3);
    }

    @Test
    public void testAuthenticate() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        facade.authenticate(ObjectClass.ACCOUNT, "bob",
                new GuardedString("password1".toCharArray()), null);
    }

    @Test
    public void testCreateUpdateDeleteUser() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        Set<Attribute> expectedAttributes = createUserAttributes(1, "John", "Doe");

        Set<Attribute> createAttributes = new HashSet<Attribute>(expectedAttributes);
        createAttributes.add(AttributeBuilder.build("password", "Passw0rd"));

        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(expectedAttributes);

        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        for (Attribute attr : expectedAttributes) {
            if (attr.is("firstname")) {
                updateAttributes.add(AttributeBuilder.build("firstname", "Johny"));
            } else {
                updateAttributes.add(attr);
            }
        }

        uid = facade.update(ObjectClass.ACCOUNT, co.getUid(), updateAttributes, null);

        co = facade.getObject(ObjectClass.ACCOUNT, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(updateAttributes);

        facade.delete(ObjectClass.ACCOUNT, uid, null);
        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
    }

    @Test
    public void testCreateUpdateDeleteGroup() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        Set<Attribute> createAttributes = createGroupAttributes(1, "GROUP#1");

        Uid uid = facade.create(ObjectClass.GROUP, createAttributes, null);
        ConnectorObject co = facade.getObject(ObjectClass.GROUP, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(createAttributes);

        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        for (Attribute attr : createAttributes) {
            if (attr.is("description")) {
                updateAttributes.add(AttributeBuilder.build("description", "Updated Description"));
            } else {
                updateAttributes.add(attr);
            }
        }

        uid = facade.update(ObjectClass.GROUP, co.getUid(), updateAttributes, null);

        co = facade.getObject(ObjectClass.GROUP, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(updateAttributes);

        facade.delete(ObjectClass.GROUP, uid, null);
        Assert.assertNull(facade.getObject(ObjectClass.GROUP, uid, null));
    }

    @Test
    public void testCreateUpdateDeleteOrg() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);

        Set<Attribute> createAttributes = createOrgAttributes(1, "ORG#1");

        Uid uid = facade.create(ORG, createAttributes, null);
        ConnectorObject co = facade.getObject(ORG, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(createAttributes);

        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        for (Attribute attr : createAttributes) {
            if (attr.is("description")) {
                updateAttributes.add(AttributeBuilder.build("description", "Updated Description"));
            } else {
                updateAttributes.add(attr);
            }
        }

        uid = facade.update(ORG, co.getUid(), updateAttributes, null);

        co = facade.getObject(ORG, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(updateAttributes);

        facade.delete(ORG, uid, null);
        Assert.assertNull(facade.getObject(ORG, uid, null));
    }

    @Test
    public void testSearchUser() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        Filter filter =
                FilterBuilder.or(FilterBuilder
                        .equalTo(AttributeBuilder.build("organization", "HR")), FilterBuilder
                        .equalTo(AttributeBuilder.build("organization", "SUPPORT")));
        assertThat(TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, filter)).hasSize(2);

        assertThat(
                TestHelpers.searchToList(facade, ObjectClass.GROUP, FilterBuilder
                        .lessThan(AttributeBuilder.build("gid", "101")))).hasSize(1);
        assertThat(
                TestHelpers.searchToList(facade, ObjectClass.GROUP, FilterBuilder
                        .lessThanOrEqualTo(AttributeBuilder.build("gid", "101")))).hasSize(2);

        assertThat(
                TestHelpers.searchToList(facade, ORG, FilterBuilder
                        .endsWith(AttributeBuilder.build("description", "organization")))).hasSize(4);

    }

    private Set<Attribute> createUserAttributes(int index, String firstName, String lastName) {
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name(lastName.toLowerCase()));

        createAttributes.add(AttributeBuilder.build("firstname", firstName));
        createAttributes.add(AttributeBuilder.build("lastname", lastName));
        createAttributes.add(AttributeBuilder.build("fullname", firstName + " " + lastName));
        createAttributes.add(AttributeBuilder.build("email", lastName.toLowerCase()
                + "@example.com"));

        createAttributes.add(AttributeBuilder.build("organization", "SALES"));
        return createAttributes;
    }

    private Set<Attribute> createGroupAttributes(int index, String name) {
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name(name.toLowerCase()));
        createAttributes.add(AttributeBuilder.build("gid", String.format("GID%04d", index)));
        createAttributes.add(AttributeBuilder.build("description", "Sample Description"));
        return createAttributes;
    }

    private Set<Attribute> createOrgAttributes(int index, String name) {
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name(name.toLowerCase()));
        createAttributes.add(AttributeBuilder.build("description", String.format(
                "Sample Description of Org %04d", index)));
        return createAttributes;
    }

    private Connection getConnection() throws ClassNotFoundException, SQLException {
        PropertyBag propertyBag = TestHelpers.getProperties(ScriptedConnectorBase.class, TEST_NAME);

        String jdbcUrl = propertyBag.getStringProperty("configuration.url");
        Assert.assertNotNull(jdbcUrl);

        Class.forName(propertyBag.getStringProperty("configuration.driverClassName"));
        return DriverManager.getConnection(jdbcUrl, propertyBag
                .getStringProperty("configuration.username"), propertyBag
                .getStringProperty("configuration.password"));
    }

    protected ConnectorFacade getFacade(String environment) {
        if (null == facadeInstance) {
            facadeInstance = createConnectorFacade(ScriptedSQLConnector.class, environment);
        }
        return facadeInstance;
    }

    @AfterClass
    public synchronized void afterClass() {
        ((LocalConnectorFacadeImpl) facadeInstance).dispose();
        facadeInstance = null;
    }

    @Test
    public void sampleTest() throws Exception {
        Connection con = getConnection();
        Statement stmt = con.createStatement();
        try {
            ResultSet rs = stmt.executeQuery("SELECT id, UID FROM Users");
            while (rs.next()) {
                String name = rs.getString("uid");
                System.out.println(name);
            }
        } finally {
            stmt.close();
  con.close();
        }
    }

}
