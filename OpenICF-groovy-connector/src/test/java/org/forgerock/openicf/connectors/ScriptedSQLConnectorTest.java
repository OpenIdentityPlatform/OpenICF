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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.IOUtils;
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConnector;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.test.common.PropertyBag;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.openicf.connectors.RESTTestBase.createConnectorFacade;

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
            InputStream in = ScriptedSQLConnectorTest.class.getResourceAsStream("/sql/testDatabase.ddl");
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
