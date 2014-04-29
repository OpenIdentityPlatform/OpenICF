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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConnector;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.test.common.PropertyBag;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
public class ScriptedSQLConnectorTest {

    /**
     * Setup logging for the {@link ScriptedSQLConnectorTest}.
     */
    private static final Log logger = Log.getLog(ScriptedSQLConnectorTest.class);

    protected static final String TEST_NAME = "SQL";

    private ConnectorFacade facadeInstance;

    @BeforeClass
    public void startServer() throws Exception {
        Connection con = getConnection();
        Statement stmt = con.createStatement();
        try {
            InputStream in =
                    ScriptedSQLConnectorTest.class.getResourceAsStream("/sql/testDatabase.ddl");
            Assert.assertNotNull(in);
            stmt.execute(IOUtils.toString(in));
        } finally {
            stmt.close();
            con.close();
        }
    }

    @AfterClass
    public void stopServer() throws Exception {
        Connection con = getConnection();
        Statement stmt = con.createStatement();
        try {
            stmt.execute("SHUTDOWN");
        } finally {
            stmt.close();
            con.close();
        }
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
    public void testCreateUpdateDeleteUser() throws Exception {
        final ConnectorFacade facade = getFacade(TEST_NAME);
        Set<Attribute> createAttributes = createUserAttributes(1, "John", "Doe");

        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(createAttributes);

        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        for (Attribute attr : createAttributes) {
            if (attr.is("firstname")){
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
            if (attr.is("description")){
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
        final ObjectClass ORG = new ObjectClass("organization");
        Set<Attribute> createAttributes = createOrgAttributes(1, "ORG#1");

        Uid uid = facade.create(ORG, createAttributes, null);
        ConnectorObject co = facade.getObject(ORG, uid, null);

        assertThat(AttributeUtil.filterUid(co.getAttributes())).containsAll(createAttributes);

        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        for (Attribute attr : createAttributes) {
            if (attr.is("description")){
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

    private Set<Attribute> createUserAttributes(int index, String firstName, String lastName) {
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name(lastName.toLowerCase()));
        createAttributes.add(AttributeBuilder.build("firstname", firstName));
        createAttributes.add(AttributeBuilder.build("lastname", lastName));
        createAttributes.add(AttributeBuilder.build("displayName", firstName + " " + lastName));
        createAttributes.add(AttributeBuilder.build("email", lastName.toLowerCase()
                + "@example.com"));

        createAttributes.add(AttributeBuilder.build("employeeNumber", String.format(
                "072-5570-%04d", index)));
        createAttributes.add(AttributeBuilder.build("employeeType", index % 3 == 0 ? "employee"
                : "contractor"));
        createAttributes.add(AttributeBuilder.build("description", "Sample Description"));
        createAttributes.add(AttributeBuilder.build("mobilePhone", String.format("1-555-555-%04d",
                index)));

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

        Class.forName("org.h2.Driver");
        return DriverManager.getConnection(jdbcUrl, "sa", "sa");
    }

    protected ConnectorFacade getFacade(String environment) {
        if (null == facadeInstance) {
            facadeInstance = createConnectorFacade(ScriptedSQLConnector.class, environment);
        }
        return facadeInstance;
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
