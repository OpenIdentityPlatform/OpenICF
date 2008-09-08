/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.databasetable;

import static org.identityconnectors.common.ByteUtil.randomBytes;
import static org.identityconnectors.common.StringUtil.randomString;
import static org.junit.Assert.*;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.identityconnectors.common.CaseInsensitiveMap;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.EqualsHashCodeBuilder;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.test.TestHelpers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Attempts to test the Connector with the framework.
 */
public class DatabaseConnectorTests {

    /**
     * 
     */
    final static String CREATE_RES = "derbyTest.sql";

    /**
     * 
     */
    final static String DB_DIR = "test_db";

    /**
     * 
     */
    final static String DB_TABLE = "ACCOUNTS";

    /**
     * Derby's embedded driver.
     */
    final static String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

    /**
     * URL used to connect to a derby database.
     */
    final static String URL_TEMPLATE = "jdbc:derby:{0};create=true";

    /**
     * URL used to shutdown a derby database.
     */
    final static String URL_SHUTDOWN = "jdbc:derby:;shutdown=true";

    // Setup/Teardown
    /**
     * Creates a temporary database based on a SQL resource file.
     */
    @BeforeClass
    public static void createDatabase() throws Exception {
        File f = new File(System.getProperty("java.io.tmpdir"), DB_DIR);
        // clear out the test database directory..
        IOUtil.delete(f);
        // attempt to create the database in the directory..
        DatabaseTableConfiguration config = new DatabaseTableConfiguration();
        config.setDriver(DRIVER);
        config.setConnectionUrl(MessageFormat.format(URL_TEMPLATE, f));
        final Connection connection = SQLUtil.
            getDriverMangerConnection(config.getDriver(), 
                                      config.getConnectionUrl(), 
                                      config.getLogin(), 
                                      config.getPassword());
        DatabaseTableConnection con = new DatabaseTableConnection(connection, config);
        Connection conn = con.getConnection();
        // create the database..
        Statement stmt = conn.createStatement();
        String create = getResourceAsString("derbyTest.sql");
        assertNotNull(create);
        stmt.execute(create);
        SQLUtil.closeQuietly(stmt);
        conn.commit();
        SQLUtil.closeQuietly(conn);
    }

    @AfterClass
    public static void deleteDatabase() {
        ConnectorFacadeFactory.getInstance().dispose();

        try {
            DriverManager.getConnection(URL_SHUTDOWN);
        } catch (Exception ex) {
        }
    }

    // Tests
    @Test
    public void testProperties() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = new DatabaseTableConfiguration();
        // check defaults..
        assertNull(config.getConnectionUrl());
        assertNull(config.getDriver());
        assertNull(config.getLogin());
        assertNull(config.getPassword());
        assertNull(config.getValidConnectionQuery());
        assertNull(config.getDBTable());
        // assertNull(config.getPasswordColumn());
        // assertNull(config.getPasswordDecrypt());
        // assertNull(config.getPasswordEncrypt());
        // check each to make sure they work..
        config.setDriver(DRIVER);
        assertEquals(DRIVER, config.getDriver());

    }

    /**
     * For testing purposes we creating connection an not the framework.
     */
    @Test
    public void testSchema() {
        DatabaseTableConnector cn = null;
        DatabaseTableConfiguration cfg = newConfiguration();
        try {
            // initialize the connector..
            cn = new DatabaseTableConnector();
            cn.init(cfg);
            // check if this works..
            Schema schema = cn.schema();
            System.out.println(schema.toString());
            checkSchema(schema);
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (cn != null) {
                cn.dispose();
            }
        }
    }

    /**
     * For testing purposes we creating connection an not the framework.
     */
    @Test(expected = ConnectorException.class)
    public void testInvalidConnectionQuery() {

        final DatabaseTableConfiguration cfg = newConfiguration();
        cfg.setValidConnectionQuery("INVSLID");
        ConnectorFacade facade = getFacade(cfg);

        TestAccount tst = TestAccount.createTestAccount();
        Uid uid = facade.create(ObjectClass.ACCOUNT, tst.toAttributeSet(), null);
    }


    /**
     * Make sure the Create call works..
     */
    @Test
    public void testCreateCall() {
        DatabaseTableConnector c = null;
        DatabaseTableConfiguration cfg = newConfiguration();
        try {
            c = new DatabaseTableConnector();
            c.init(cfg);
            TestAccount expected = TestAccount.createTestAccount();
            Uid uid = c.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);
            // attempt to get the record back..
            List<ConnectorObject> results = TestHelpers
                    .searchToList(c, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
            assertTrue("expect 1 connector object", results.size() == 1);
            TestAccount actual = TestAccount.fromAttributeSet(results.get(0).getAttributes());
            accountEquals(expected, actual);
        } finally {
            if (c != null) {
                c.dispose();
            }            
        }
    }

    /**
     * Make sure the Create call works..
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateUnsuported() {
        DatabaseTableConnector c = null;
        DatabaseTableConfiguration cfg = newConfiguration();
        try {
            c = new DatabaseTableConnector();
            c.init(cfg);
            TestAccount tst = TestAccount.createTestAccount();
            ObjectClass objClass = new ObjectClass("NOTSUPPORTED");
            c.create(objClass, tst.toAttributeSet(), null);
        } finally {
            if (c != null) {
                c.dispose();
            }
        }
    }

    @Test
    public void testCreateWithName() {
        ConnectorFacade facade = getFacade();
        TestAccount tst = TestAccount.createTestAccount();
        Set<Attribute> attributes = tst.toAttributeSet();

        Name name = AttributeUtil.getNameFromAttributes(attributes);
        final Uid uid = facade.create(ObjectClass.ACCOUNT, attributes, null);
        assertNotNull(uid);
        assertEquals(name.getNameValue(), uid.getUidValue());
    }

    /**
     * Test retrieving the schema using the API.
     */
    @Test
    public void testSchemaApi() {
        Schema schema = getFacade().schema();
        System.out.println(schema.toString());
        checkSchema(schema);
    }

    /**
     * Test creating of the connector object, searching using UID and delete
     */
    @Test
    public void testCreateAndDelete() {
        final String ERR1 = "Could not find new object.";
        final String ERR2 = "Found object that should not be there.";
        ConnectorFacade facade = getFacade();
        TestAccount expected = TestAccount.createTestAccount();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);
        try {
            System.out.println("Uid: " + uid);
            FindUidObjectHandler handler = new FindUidObjectHandler(uid);
            // attempt to find the newly created object..
            facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
            assertTrue(ERR1, handler.found);

            //Test the created attributes are equal the searched
            assertNotNull(handler.connectorObject);
            TestAccount actual = TestAccount.fromAttributeSet(handler.connectorObject.getAttributes());
            accountEquals(expected, actual);
        } finally {
            // attempt to delete the object..
            facade.delete(ObjectClass.ACCOUNT, uid, null);
            // attempt to find it again to make sure
            // it actually deleted the object..
            FindUidObjectHandler handler = new FindUidObjectHandler(uid);
            // attempt to find the newly created object..
            facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
            assertFalse(ERR2, handler.found);
            try {
                // now attempt to delete an object that is not there..
                facade.delete(ObjectClass.ACCOUNT, uid, null);
                fail("Should have thrown an execption.");
            } catch (UnknownUidException exp) {
                // should get here..
            }
        }
    }

    /**
     * Test creating of the connector object, searching using UID and delete
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteUnsupported() {
        final String ERR1 = "Could not find new object.";
        ConnectorFacade facade = getFacade();
        TestAccount tst = new TestAccount();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, tst.toAttributeSet(), null);
        try {
            System.out.println("Uid: " + uid);
            FindUidObjectHandler handler = new FindUidObjectHandler(uid);
            // attempt to find the newly created object..
            facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
            assertTrue(ERR1, handler.found);
        } finally {
            // attempt to delete the object..
            ObjectClass objc = new ObjectClass("UNSUPPORTED");
            facade.delete(objc, uid, null);
        }
    }

    /**
     * Test creating of the connector object, searching using UID and update
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUpdateUnsupported() {
        ConnectorFacade facade = getFacade();
        TestAccount tst = TestAccount.createTestAccount();

        // create the object
        final Uid uid = facade.create(ObjectClass.ACCOUNT, tst.toAttributeSet(), null);
        assertNotNull(uid);

        // retrieve the object
        FindUidObjectHandler handler = new FindUidObjectHandler(uid);
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
        assertNotNull(handler.connectorObject);

        // create updated connector object
        ConnectorObjectBuilder coBuilder = new ConnectorObjectBuilder();
        coBuilder.setName(handler.connectorObject.getName());
        coBuilder.setUid(uid);
        coBuilder.setObjectClass(ObjectClass.ACCOUNT);
        String strUpdate = " !@#$%^&*()\"'changed";
        int strUpdateLen = strUpdate.length();
        tst.setAge(tst.getAge() + 10);
        tst.setDepartment(tst.getDepartment().substring(strUpdateLen) + strUpdate);
        tst.setEmail(tst.getEmail().substring(strUpdateLen) + strUpdate);
        tst.setFirstName(tst.getFirstName().substring(strUpdateLen) + strUpdate);
        tst.setJpegPhoto(randomBytes(1000));
        tst.setLastName(tst.getLastName().substring(strUpdateLen) + strUpdate);
        tst.setManager(" !@#$%^&*()\"'");
        tst.setMiddleName(tst.getMiddleName().substring(strUpdateLen) + strUpdate);
        tst.setPassword(tst.getPassword().substring(strUpdateLen) + strUpdate);
        tst.setSalary(new BigDecimal(1000));
        tst.setTitle(tst.getTitle().substring(strUpdateLen) + strUpdate);
        for (Attribute attribute : tst.toAttributeSet()) {
            coBuilder.addAttribute(attribute);
        }
        ConnectorObject coBeforeUpdate = coBuilder.build();

        // do the update
        Set<Attribute> changeSet = CollectionUtil.newSet(coBeforeUpdate.getAttributes());
        changeSet.remove(coBeforeUpdate.getName());
        ObjectClass objClass = new ObjectClass("NOTSUPPORTED");
        facade.update(UpdateApiOp.Type.REPLACE, objClass, changeSet, null);
    }

    /**
     * Test creating of the connector object, searching using UID and update
     */
    @Test
    public void testCreateAndUpdate() {
        ConnectorFacade facade = getFacade();
        TestAccount tst = TestAccount.createTestAccount();

        // create the object
        final Uid uid = facade.create(ObjectClass.ACCOUNT, tst.toAttributeSet(), null);
        assertNotNull(uid);

        // retrieve the object
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        // **test only**
        FindUidObjectHandler handler = new FindUidObjectHandler(uid);
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
        assertNotNull(handler.connectorObject);

        // create updated connector object
        ConnectorObjectBuilder coBuilder = new ConnectorObjectBuilder();
        coBuilder.setName(handler.connectorObject.getName());
        coBuilder.setUid(uid);
        coBuilder.setObjectClass(ObjectClass.ACCOUNT);
        String strUpdate = " !@#$%^&*()\"'changed";
        int strUpdateLen = strUpdate.length();
        tst.setAge(tst.getAge() + 10);
        tst.setDepartment(tst.getDepartment().substring(strUpdateLen) + strUpdate);
        tst.setEmail(tst.getEmail().substring(strUpdateLen) + strUpdate);
        tst.setFirstName(tst.getFirstName().substring(strUpdateLen) + strUpdate);
        tst.setJpegPhoto(randomBytes(1000));
        tst.setLastName(tst.getLastName().substring(strUpdateLen) + strUpdate);
        tst.setManager(" !@#$%^&*()\"'");
        tst.setMiddleName(tst.getMiddleName().substring(strUpdateLen) + strUpdate);
        tst.setPassword(tst.getPassword().substring(strUpdateLen) + strUpdate);
        tst.setSalary(new BigDecimal(1000));
        tst.setTitle(tst.getTitle().substring(strUpdateLen) + strUpdate);
        for (Attribute attribute : tst.toAttributeSet()) {
            coBuilder.addAttribute(attribute);
        }
        ConnectorObject coBeforeUpdate = coBuilder.build();

        // do the update
        Set<Attribute> changeSet = CollectionUtil.newSet(coBeforeUpdate.getAttributes());
        changeSet.remove(coBeforeUpdate.getName());

        final Uid uidUpdate = facade.update(UpdateApiOp.Type.REPLACE, ObjectClass.ACCOUNT, changeSet, null);

        // uids should be the same
        assertEquals(uid.getUidValue(), uidUpdate.getUidValue());

        // retrieve the updated object
        FindUidObjectHandler handlerUpdate = new FindUidObjectHandler(uid);
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handlerUpdate, null);
        assertNotNull(handlerUpdate.connectorObject);

        // assert the connector object are equal
        ConnectorObject coAfterUpdate = handlerUpdate.connectorObject;
        assertEquals(coBeforeUpdate.getUid().getValue(), coAfterUpdate.getUid().getValue());
        assertEquals(coBeforeUpdate.getName().getNameValue(), coAfterUpdate.getName().getNameValue());

        TestAccount expected = TestAccount.fromAttributeSet(coBeforeUpdate.getAttributes());
        TestAccount actual = TestAccount.fromAttributeSet(coAfterUpdate.getAttributes());
        accountEquals(expected, actual);
    }
    
    /**
     * Test creating of the connector object, searching using UID and delete
     */
    @Test
    public void testSyncFull() {
        final String ERR1 = "Could not find new object.";
        final String ERR2 = "Found object that should not be there.";
        ConnectorFacade facade = getFacade();
        TestAccount expected = TestAccount.createTestAccount();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);
        try {
            System.out.println("Uid: " + uid);
            FindUidSyncHandler handler = new FindUidSyncHandler(uid);
            // attempt to find the newly created object..
            facade.sync(ObjectClass.ACCOUNT, null , handler, null);
            assertTrue(ERR1, handler.found);
            assertEquals(0L, handler.token.getValue());
            // assertEquals(expected, handler.deltaType); // not definned till now 

            //Test the created attributes are equal the searched
            assertNotNull(handler.attributes);
            TestAccount actual = TestAccount.fromAttributeSet(handler.attributes);
            accountEquals(expected, actual);
        } finally {
            // attempt to delete the object..
            facade.delete(ObjectClass.ACCOUNT, uid, null);
            // attempt to find it again to make sure
            // it actually deleted the object..
            FindUidObjectHandler handler = new FindUidObjectHandler(uid);
            // attempt to find the newly created object..
            facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
            assertFalse(ERR2, handler.found);
            try {
                // now attempt to delete an object that is not there..
                facade.delete(ObjectClass.ACCOUNT, uid, null);
                fail("Should have thrown an execption.");
            } catch (UnknownUidException exp) {
                // should get here..
            }
        }
    }    

    /**
     * Test creating of the connector object, searching using UID and delete
     * @throws SQLException 
     */
    @Test
    public void testSyncPartOld() throws SQLException {
        final String ERR1 = "Could not find new object.";
        final String SQL_TEMPLATE = "UPDATE Accounts SET changed = ? WHERE accountId = ?";
        
        ConnectorFacade facade = getFacade();
        TestAccount expected = TestAccount.createTestAccount();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);

        // update the last change
        DatabaseTableConnector connector = null;
        PreparedStatement ps = null;
        DatabaseTableConnection conn = null;
        try {
            connector = new DatabaseTableConnector();
            connector.init(newConfiguration());
            conn = connector.getConnection();

            List<Object> values = new ArrayList<Object>();
            final Timestamp changed = new Timestamp(System.currentTimeMillis() - 1000);
            expected.setChanged(changed);
            values.add(changed);
            values.add(uid.getUidValue());
            ps = conn.prepareStatement(SQL_TEMPLATE, values);
            ps.execute();
            conn.commit();
        } finally {
            if (conn != null)
                SQLUtil.closeQuietly(conn.getConnection());
            SQLUtil.closeQuietly(ps);
            if (connector != null) {
                connector.dispose();
            }
        }
        System.out.println("Uid: " + uid);
        FindUidSyncHandler handler = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        facade.sync(ObjectClass.ACCOUNT, new SyncToken(System.currentTimeMillis()), handler, null);
        assertFalse(ERR1, handler.found);
        // Test the created attributes are equal the searched      
    }      
    
    /**
     * Test creating of the connector object, searching using UID and delete
     * @throws SQLException 
     */
    @Test
    public void testSyncPartNew() throws SQLException {
        final String ERR1 = "Could not find new object.";
        final String SQL_TEMPLATE = "UPDATE Accounts SET changed = ? WHERE accountId = ?";
        
        ConnectorFacade facade = getFacade();
        TestAccount expected = TestAccount.createTestAccount();
        final Uid uid = facade.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);

        // update the last change
        DatabaseTableConnector connector = null;
        PreparedStatement ps = null;
        DatabaseTableConnection conn = null;
        try {
            connector = new DatabaseTableConnector();
            connector.init(newConfiguration());
            conn = connector.getConnection();

            List<Object> values = new ArrayList<Object>();
            final Timestamp changed = new Timestamp(System.currentTimeMillis());
            expected.setChanged(changed);
            values.add(changed);
            values.add(uid.getUidValue());
            ps = conn.prepareStatement(SQL_TEMPLATE, values);
            ps.execute();
            conn.commit();
        } finally {
            if (conn != null)
                SQLUtil.closeQuietly(conn.getConnection());
            SQLUtil.closeQuietly(ps);
            if (connector != null) {
                connector.dispose();
            }
        }
        System.out.println("Uid: " + uid);
        FindUidSyncHandler handler = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        facade.sync(ObjectClass.ACCOUNT, new SyncToken(System.currentTimeMillis() - 1000), handler, null);
        assertTrue(ERR1, handler.found);
        // Test the created attributes are equal the searched
        assertNotNull(handler.attributes);
        TestAccount actual = TestAccount.fromAttributeSet(handler.attributes);
        accountEquals(expected, actual);        
    }      
    
    
    
    @Test
    public void testQuoting() throws Exception {
        final Map<String, Pair<String, String>> data = new HashMap<String, Pair<String, String>>();
        data.put("none", new Pair<String, String>("afklj", "afklj"));
        data.put("double", new Pair<String, String>("123jd", "\"123jd\""));
        data.put("single", new Pair<String, String>("di3nfd", "'di3nfd'"));
        data.put("back", new Pair<String, String>("fadfk3", "`fadfk3`"));
        data.put("brackets", new Pair<String, String>("fadlkfj", "[fadlkfj]"));
        for (Map.Entry<String, Pair<String, String>> entry : data.entrySet()) {
            DatabaseTableConfiguration config = new DatabaseTableConfiguration();
            config.setNameQuote(entry.getKey());
            String actual = config.quoteName(entry.getValue().first);
            assertEquals(entry.getValue().second, actual);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidQuoting() {
        // test the exception case..
        DatabaseTableConfiguration config = new DatabaseTableConfiguration();
        config.setNameQuote("fadsfd");
        config.quoteName("anything");
    }

    /**
     * Test method for
     * {@link org.identityconnectors.databasetable.DatabaseTableConnector#authenticate(username, password, options)}.
     * Test creating of the connector object, searching using UID and update
     */
    @Test
    public void testAuthenticateOriginal() {

        ConnectorFacade facade = getFacade();
        assertNotNull(facade);

        TestAccount testAccount = TestAccount.createTestAccount();

        // create the object representing a new account
        final Uid uid = facade.create(ObjectClass.ACCOUNT, testAccount.toAttributeSet(), null);
        assertNotNull(uid);
        
        // check if authenticate operation is present (it should)
        Schema schema = facade.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        assertTrue(oci.size() >= 1); 

        // this should not throw any RuntimeException, on invalid authentication
        facade.authenticate(testAccount.getAccountId(), new GuardedString(testAccount.getPassword().toCharArray()),
                null);

        // cleanup (should not throw any exception.)
        facade.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method for
     * {@link org.identityconnectors.databasetable.DatabaseTableConnector#authenticate(username, password, options)}.
     * Test creating of the connector object, searching using UID and update
     * 
     * In this case the user that we query is not in the database at all.
     */
    @Test(expected = InvalidCredentialException.class)
    public void testAuthenticateWrongOriginal() {
        // create a random account, but do not insert into the database
        TestAccount testAccount = TestAccount.createTestAccount();

        ConnectorFacade facade = getFacade();
        assertNotNull(facade);

        // this should throw InvalidCredentials exception, as we query a
        // non-existing user
        facade.authenticate(testAccount.getAccountId(), new GuardedString(testAccount.getPassword().toCharArray()),
                null);
    }

    /**
     * Test method for
     * {@link org.identityconnectors.databasetable.DatabaseTableConnector#authenticate(username, password, options)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNoPassColumnAutenticate() {

        DatabaseTableConfiguration cfg = newConfiguration();
        /*
         * Erasing password column from the configuration (it will be no longer
         * treated as special attribute).
         */
        cfg.setPasswordColumn(null);

        ConnectorFacade facade = getFacade(cfg);
        assertNotNull(facade);

        // create the object representing a new account
        TestAccount testAccount = TestAccount.createTestAccount();
        // note: toAttributeSet(false), where false means, password will not be
        // treated as special attribute.
        final Uid uid = facade.create(ObjectClass.ACCOUNT, testAccount.toAttributeSet(false), null);
        assertNotNull(uid);

        // check if authenticate operation is present (it should NOT!)
        Schema schema = facade.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        assertTrue(oci.size() == 0);

        // authentication should not be allowed -- will throw an
        // IllegalArgumentException
        facade.authenticate(testAccount.getAccountId(), new GuardedString(testAccount.getPassword().toCharArray()),
                null);

        // cleanup (should not throw any exception.)
        facade.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method
     */
    @Test
    public void testSearchByName() {
        // create object
        ConnectorFacade facade = getFacade();
        TestAccount expected = TestAccount.createTestAccount();

        // create the object
        final Uid uid = facade.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);
        assertNotNull(uid);

        // retrieve the object
        FindUidObjectHandler handler = new FindUidObjectHandler(uid);
        Filter nameFilter = FilterBuilder.equalTo(new Name(uid.getUidValue()));
        facade.search(ObjectClass.ACCOUNT, nameFilter, handler, null);
        ConnectorObject coFound = handler.connectorObject;
        assertNotNull(coFound);
        TestAccount actual = TestAccount.fromAttributeSet(coFound.getAttributes());
        accountEquals(expected, actual);

    }

    /**
     * Test method to issue #238
     * @throws SQLException
     */
    @Test
    public void testSearchWithNullPassword() throws SQLException {
        final String SQL_TEMPLATE = "UPDATE {0} SET password = null WHERE {1} = ?";

        final DatabaseTableConfiguration cfg = newConfiguration();
        final String sql = MessageFormat.format(SQL_TEMPLATE, cfg.getDBTable(), cfg.getKeyColumn());

        // create object
        TestAccount expected = TestAccount.createTestAccount();
        DatabaseTableConnector connector = null;
        PreparedStatement ps = null;
        DatabaseTableConnection conn = null;
        try {
            connector = new DatabaseTableConnector();
            connector.init(cfg);
            Uid uid = connector.create(ObjectClass.ACCOUNT, expected.toAttributeSet(), null);

            //set password to null
            expected.setPassword((String) null);
            conn = connector.getConnection();

            List<Object> values = new ArrayList<Object>();
            values.add(uid.getUidValue());
            ps = conn.prepareStatement(sql, values);
            ps.execute();
            conn.commit();

            // attempt to get the record back..
            List<ConnectorObject> results = TestHelpers.searchToList(connector, ObjectClass.ACCOUNT, FilterBuilder
                    .equalTo(uid));
            assertTrue("expect 1 connector object", results.size() == 1);
            TestAccount actual = TestAccount.fromAttributeSet(results.get(0).getAttributes());
            accountEquals(expected, actual);
        } finally {
            if(conn != null) SQLUtil.closeQuietly(conn.getConnection());
            SQLUtil.closeQuietly(ps);

            if (connector != null) {
                connector.dispose();
            }
        }
    }

    /**
     * Test method, issue #186
     */
    @Test
    public void testSearchByNameAttributesToGet() {
        // create object
        ConnectorFacade facade = getFacade();
        TestAccount tst = TestAccount.createTestAccount();

        // create the object
        final Uid uid = facade.create(ObjectClass.ACCOUNT, tst.toAttributeSet(), null);
        assertNotNull(uid);

        // retrieve the object
        FindUidObjectHandler handler = new FindUidObjectHandler(uid);
        Filter nameFilter = FilterBuilder.equalTo(new Name(uid.getUidValue()));
        OperationOptionsBuilder opOption = new OperationOptionsBuilder();
        opOption.setAttributesToGet(TestAccount.FIRSTNAME, TestAccount.LASTNAME, TestAccount.MANAGER);

        facade.search(ObjectClass.ACCOUNT, nameFilter, handler, opOption.build());
        ConnectorObject coFound = handler.connectorObject;
        assertNotNull(coFound);
        assertEquals(uid.getUidValue(), coFound.getUid().getUidValue());
        assertEquals(uid.getUidValue(), coFound.getName().getNameValue());
        assertNull(AttributeUtil.find(TestAccount.AGE, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.DEPARTMENT, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.EMAIL, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.FIRSTNAME, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.LASTNAME, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.MANAGER, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.MIDDLENAME, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.SALARY, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.TITLE, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.JPEGPHOTO, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.CHANGED, coFound.getAttributes()));
    }

    /**
     * Test method, issue #186
     */
    @Test
    public void testSearchByNameAttributesToGetExtended() {
        // create object
        ConnectorFacade facade = getFacade();
        TestAccount tst = TestAccount.createTestAccount();

        // create the object
        final Uid uid = facade.create(ObjectClass.ACCOUNT, tst.toAttributeSet(), null);
        assertNotNull(uid);

        // retrieve the object
        FindUidObjectHandler handler = new FindUidObjectHandler(uid);
        Filter nameFilter = FilterBuilder.equalTo(new Name(uid.getUidValue()));
        OperationOptionsBuilder opOption = new OperationOptionsBuilder();
        opOption.setAttributesToGet(TestAccount.FIRSTNAME, TestAccount.LASTNAME, TestAccount.MANAGER,
                TestAccount.JPEGPHOTO);

        facade.search(ObjectClass.ACCOUNT, nameFilter, handler, opOption.build());
        ConnectorObject coFound = handler.connectorObject;
        assertNotNull(coFound);
        assertEquals(uid.getUidValue(), coFound.getUid().getUidValue());
        assertEquals(uid.getUidValue(), coFound.getName().getNameValue());
        assertNull(AttributeUtil.find(TestAccount.AGE, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.DEPARTMENT, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.EMAIL, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.FIRSTNAME, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.LASTNAME, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.MANAGER, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.MIDDLENAME, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.SALARY, coFound.getAttributes()));
        assertNull(AttributeUtil.find(TestAccount.TITLE, coFound.getAttributes()));
        assertNotNull(AttributeUtil.find(TestAccount.JPEGPHOTO, coFound.getAttributes()));
    }

    // Helper Methods/Classes

    /**
     * @param expected
     * @param actual
     */
    private void accountEquals(TestAccount expected, TestAccount actual) {
        assertEquals("getAccountId", expected.getAccountId(), actual.getAccountId());
        assertEquals("getAge", expected.getAge(), actual.getAge());
        assertEquals("getDepartment", expected.getDepartment(), actual.getDepartment());
        assertEquals("getEmail", expected.getEmail(), actual.getEmail());
        assertEquals("getFirstName", expected.getFirstName(), actual.getFirstName());        
        assertNull("getJpegPhoto", actual.getJpegPhoto());
        assertEquals("getLastName", expected.getLastName(), actual.getLastName());
        assertEquals("getManager", expected.getManager(), actual.getManager());
        assertEquals("getMiddleName", expected.getMiddleName(), actual.getMiddleName());
        assertNull("getPassword", actual.getPassword());
        assertEquals("getSalary", expected.getSalary().doubleValue(), actual.getSalary().doubleValue(), 0.01d);
        assertEquals("getTitle", expected.getTitle(), actual.getTitle());
        assertEquals("getEnrolled", expected.getEnrolled(), actual.getEnrolled());
    }

    /**
     * check validity of the schema
     * 
     * @param schema
     *            the schema to be checked
     */
    private void checkSchema(Schema schema) {
        // Schema should not be null
        assertNotNull(schema);
        Set<ObjectClassInfo> objectInfos = schema.getObjectClassInfo();
        assertNotNull(objectInfos);
        assertEquals(1, objectInfos.size());
        // get the fields from the test account
        final Set<Attribute> attributeSet = TestAccount.createTestAccount().toAttributeSet();
        final Map<String, Attribute> expected = AttributeUtil.toMap(attributeSet);
        final Set<String> keys = CollectionUtil.newSet(expected.keySet());

        // iterate through ObjectClassInfo Set
        for (ObjectClassInfo objectInfo : objectInfos) {
            assertNotNull(objectInfo);
            // the object class has to ACCOUNT_NAME
            assertEquals(ObjectClass.ACCOUNT_NAME, objectInfo.getType());

            // iterate through AttributeInfo Set
            for (AttributeInfo attInfo : objectInfo.getAttributeInfo()) {
                assertNotNull(attInfo);
                String fieldName = attInfo.getName();
                assertTrue("Field:" + fieldName + " doesn't exist", keys.contains(fieldName));
                keys.remove(fieldName);
                Attribute fa = expected.get(fieldName); 
                assertNotNull("Field:" + fieldName + "  was duplicated", fa);
                Object field = AttributeUtil.getSingleValue(fa);
                Class<?> expClass = field.getClass();
                assertEquals("field: " + fieldName, expClass, attInfo.getType());
            }
            // all the attribute has to be removed
            assertEquals("There are missing attributes which were not included in the schema ", 0, keys.size());
        }
    }
    
    private ConnectorFacade getFacade() {
        return getFacade(newConfiguration());
    }

    private ConnectorFacade getFacade(DatabaseTableConfiguration cfg) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(DatabaseTableConnector.class, cfg);
        return factory.newInstance(impl);
    }

    /**
     * @return
     */
    public DatabaseTableConfiguration newConfiguration() {
        DatabaseTableConfiguration config = new DatabaseTableConfiguration();
        config.setDriver(DRIVER);
        config.setDBTable(DB_TABLE);
        config.setKeyColumn(TestAccount.ACCOUNTID);
        config.setPasswordColumn(TestAccount.PASSWORD);
        config.setConnectionUrl(getConnectionUrl());
        config.setGenerateUid(true);
        config.setChangeLogColumn(TestAccount.CHANGED);
        return config;
    }

    public File getDBDirectory() {
        return new File(System.getProperty("java.io.tmpdir"), DB_DIR);
    }

    public String getConnectionUrl() {
        return MessageFormat.format(URL_TEMPLATE, getDBDirectory());
    }

    static String getResourceAsString(String res) {
        return IOUtil.getResourceAsString(DatabaseConnectorTests.class, res);
    }    
    
    /**
     * Test object 
     * @version $Revision 1.0$
     * @since 1.0
     */
    public static class TestAccount {

        // always seed that same for results..
        private static final Random r = new Random(17);

        // Constants..
        private static final String ACCOUNTID = "accountId";

        private static final String PASSWORD = "password";

        private static final String MANAGER = "manager";

        private static final String MIDDLENAME = "middlename";

        private static final String FIRSTNAME = "firstname";

        private static final String LASTNAME = "lastname";

        private static final String EMAIL = "email";

        private static final String DEPARTMENT = "department";

        private static final String TITLE = "title";

        private static final String AGE = "age";

        private static final String SALARY = "salary";

        private static final String JPEGPHOTO = "jpegphoto";
        
        private static final String ENROLLED = "enrolled";
        
        private static final String CHANGED = "changed";

        // Fields..  
        private String accountId;

        private String password;

        private String manager;

        private String middleName;

        private String firstName;

        private String lastName;

        private String email;

        private String title;

        private String department;

        private Integer age;

        private BigDecimal salary;

        private byte[] jpegphoto;

        private Date enrolled;
        
        private Timestamp changed;

        public TestAccount() {
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public void setPassword(GuardedString password) {
            this.password = getPlainPassword(password);
        }

        private String getPlainPassword(GuardedString password) {
            final StringBuffer buf = new StringBuffer();
            password.access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    buf.append(clearChars);
                }
            });
            return buf.toString();
        }

        public String getManager() {
            return manager;
        }

        public void setManager(String manager) {
            this.manager = manager;
        }

        public String getMiddleName() {
            return middleName;
        }

        public void setMiddleName(String middleName) {
            this.middleName = middleName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public BigDecimal getSalary() {
            return salary;
        }

        public void setSalary(BigDecimal salary) {
            this.salary = salary;
        }

        public byte[] getJpegPhoto() {
            return jpegphoto;
        }

        public void setJpegPhoto(byte[] jpegphoto) {
            this.jpegphoto = jpegphoto;
        }


        public Date getEnrolled() {
            return enrolled;
        }

        public void setEnrolled(Date enrolled) {
            this.enrolled = enrolled;
        }
        
        public Timestamp getChanged() {
            return changed;
        }

        public void setChanged(Timestamp changed) {
            this.changed = changed;
        }

        public String getTitle() {
            return this.title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        /**
         * Create a random test account fully populated..
         * @return an account
         */
        public static TestAccount createTestAccount() {
            TestAccount testAccount = new TestAccount();
            testAccount.setAccountId(randomString(r, 50));
            testAccount.setAge(r.nextInt(100));
            testAccount.setDepartment(randomString(r, 50));
            testAccount.setEmail(randomString(r, 50));
            testAccount.setFirstName(randomString(r, 50));
            testAccount.setJpegPhoto(randomBytes(r, 2000));
            testAccount.setLastName(randomString(r, 50));
            testAccount.setManager(randomString(r, 15));
            testAccount.setMiddleName(randomString(r, 50));
            testAccount.setPassword(randomString(r, 40));
            testAccount.setSalary(new BigDecimal(r.nextDouble()));
            testAccount.setTitle(randomString(r, 50));
            testAccount.setEnrolled(new Date(System.currentTimeMillis()));
            testAccount.setChanged(new Timestamp(System.currentTimeMillis()));
            return testAccount;
        }        

        /**
         * The TestAccount factory method
         * @param attributes connector attributes
         * @return s test account
         */
        public static TestAccount fromAttributeSet(Set<Attribute> attributes) {
            TestAccount ret = new TestAccount();
            for (Attribute attr : attributes) {
                String name = attr.getName();
                if (Name.NAME.equalsIgnoreCase(name)) {
                    ret.setAccountId(AttributeUtil.getStringValue(attr));
                } else if (DEPARTMENT.equalsIgnoreCase(name)) {
                    ret.setDepartment(AttributeUtil.getStringValue(attr));
                } else if (EMAIL.equalsIgnoreCase(name)) {
                    ret.setEmail(AttributeUtil.getStringValue(attr));
                } else if (FIRSTNAME.equalsIgnoreCase(name)) {
                    ret.setFirstName(AttributeUtil.getStringValue(attr));
                } else if (LASTNAME.equalsIgnoreCase(name)) {
                    ret.setLastName(AttributeUtil.getStringValue(attr));
                } else if (MANAGER.equalsIgnoreCase(name)) {
                    ret.setManager(AttributeUtil.getStringValue(attr));
                } else if (MIDDLENAME.equalsIgnoreCase(name)) {
                    ret.setMiddleName(AttributeUtil.getStringValue(attr));
                } else if (OperationalAttributes.PASSWORD_NAME.equalsIgnoreCase(name)) {
                    ret.setPassword(AttributeUtil.getGuardedStringValue(attr));
                } else if (PASSWORD.equalsIgnoreCase(name)) {
                    ret.setPassword(AttributeUtil.getStringValue(attr));
                } else if (TITLE.equalsIgnoreCase(name)) {
                    ret.setTitle(AttributeUtil.getStringValue(attr));
                } else if (AGE.equalsIgnoreCase(name)) {
                    ret.setAge(AttributeUtil.getIntegerValue(attr));
                } else if (SALARY.equalsIgnoreCase(name)) {
                    ret.setSalary(AttributeUtil.getBigDecimalValue(attr));
                } else if (ENROLLED.equalsIgnoreCase(name)) {
                    ret.setEnrolled(AttributeUtil.getDateValue(attr));
                } else if (CHANGED.equalsIgnoreCase(name)) {
                    ret.setChanged(new Timestamp(AttributeUtil.getLongValue(attr)));
                }
            }
            return ret;
        }

        public Set<Attribute> toAttributeSet() {
            return toAttributeSet(true);
        }
        

        /**
         * @param passwdColDefined
         *            if the attribute set should include password
         */
        public Set<Attribute> toAttributeSet(boolean passwdColDefined) {
            Set<Attribute> ret = new HashSet<Attribute>();
            ret.add(AttributeBuilder.build(Name.NAME, getAccountId()));

            if (passwdColDefined) {
                if (getPassword() != null) {
                    ret.add(AttributeBuilder.buildPassword(new GuardedString(getPassword().toCharArray())));
                }
            } else {
                ret.add(AttributeBuilder.build(PASSWORD, getPassword()));
            }

            ret.add(AttributeBuilder.build(MANAGER, getManager()));
            ret.add(AttributeBuilder.build(MIDDLENAME, getMiddleName()));
            ret.add(AttributeBuilder.build(FIRSTNAME, getFirstName()));
            ret.add(AttributeBuilder.build(LASTNAME, getLastName()));
            ret.add(AttributeBuilder.build(EMAIL, getEmail()));
            ret.add(AttributeBuilder.build(DEPARTMENT, getDepartment()));
            ret.add(AttributeBuilder.build(TITLE, getTitle()));
            ret.add(AttributeBuilder.build(AGE, getAge()));
            ret.add(AttributeBuilder.build(SALARY, getSalary()));
            ret.add(AttributeBuilder.build(JPEGPHOTO, getJpegPhoto()));
            ret.add(AttributeBuilder.build(ENROLLED, getEnrolled().getTime()));
            return ret;
        }        

        EqualsHashCodeBuilder getEqualsHashCodeBuilder() {
            EqualsHashCodeBuilder bld = new EqualsHashCodeBuilder();
            bld.appendBean(this);
            return bld;
        }

        @Override
        public String toString() {
            return toAttributeSet().toString();
        }

        @Override
        public int hashCode() {
            return getEqualsHashCodeBuilder().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            boolean ret = false;
            if (obj instanceof TestAccount) {
                TestAccount tstObj = (TestAccount) obj;
                EqualsHashCodeBuilder thisEq = getEqualsHashCodeBuilder();
                ret = thisEq.equals(tstObj.getEqualsHashCodeBuilder());
            }
            return ret;
        }
    }

    static class TrueFilter implements Filter {
        public boolean accept(ConnectorObject obj) {
            return true;
        }
    }

    
    static class FindUidObjectHandler implements ResultsHandler {
        /**
         * Determines if found..
         */
        public boolean found = false;

        /**
         * The handled object
         */
        public ConnectorObject connectorObject = null;

        /**
         * Uid to find.
         */
        public final Uid uid;

        /**
         * @param uid
         */
        public FindUidObjectHandler(Uid uid) {
            this.uid = uid;
        }

        public boolean handle(ConnectorObject obj) {
            System.out.println("Object: " + obj);
            if (obj.getUid().equals(uid)) {
                found = true;
                this.connectorObject = obj;
                return false; // find, do not continue
            }
            return true;
        }
    }

    static class FindUidSyncHandler implements SyncResultsHandler {
        /**
         * Determines if found..
         */
        public boolean found = false;

        /**
         * Uid to find.
         */
        public final Uid uid;

        /**
         * 
         */
        public SyncDeltaType deltaType;

        /**
         * Sync token to find
         */
        public SyncToken token;

        /**
         * Attribute set to find
         */
        public Set<Attribute> attributes = null;
        
        /**
         * @param uid
         */
        public FindUidSyncHandler(Uid uid) {
            this.uid = uid;
        }

        /* (non-Javadoc)
         * @see org.identityconnectors.framework.common.objects.SyncResultsHandler#handle(org.identityconnectors.framework.common.objects.SyncDelta)
         */
        public boolean handle(SyncDelta delta) {
            System.out.println("SyncDeltat: " + delta);
            if (delta.getUid().equals(uid)) {
                found = true;
                this.attributes = delta.getObject().getAttributes();
                this.deltaType = delta.getDeltaType();
                this.token = delta.getToken();
                return false;
            }
            return true;
        }
    }    
}
