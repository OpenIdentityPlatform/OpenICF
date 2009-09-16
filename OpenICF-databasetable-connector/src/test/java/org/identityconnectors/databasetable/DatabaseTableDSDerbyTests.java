/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.databasetable;

import static org.identityconnectors.common.ByteUtil.randomBytes;
import static org.identityconnectors.common.StringUtil.randomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import javax.sql.DataSource;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.databasetable.mapping.MappingStrategy;
import org.identityconnectors.dbcommon.ExpectProxy;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Attempts to test the Connector with the framework.
 */
public class DatabaseTableDSDerbyTests extends DatabaseTableTestBase {

    /**
     * 
     */
    static final String CREATE_RES = "derbyTest.sql";

    /**
     * 
     */
    static final String DB_DIR = "test_db2";


    /**
     * Derby's embedded driver.
     */
    static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
    
    /**
     * Derby's embedded ds.
     */
    static final String TEST_DS="testDS";

    /**
     * URL used to connect to a derby database.
     */
    static final String URL_CONN = "jdbc:derby:"+getDBDirectory().toString();

    /**
     * URL used to connect to a derby database.
     */
    static final String URL_CREATE = URL_CONN+";create=true";

    
    /**
     * URL used to shutdown a derby database.
     */
    static final String URL_SHUTDOWN = URL_CONN+";shutdown=true";

    //The tested table
    static final String DB_TABLE = "Accounts";

    //jndi for datasource
    static final String[] jndiProperties = new String[]{"java.naming.factory.initial=" + MockContextFactory.class.getName()};



    // Setup/Teardown
    /**
     * Creates a temporary database based on a SQL resource file.
     * @throws Exception 
     */
    @BeforeClass
    public static void createDatabase() throws Exception {
        final File f = getDBDirectory();
        // clear out the test database directory..
        IOUtil.delete(f);
        // attempt to create the database in the directory..
        Connection conn = null;
        Statement stmt = null;
        try {
            Class.forName(DRIVER);             
            conn = DriverManager.getConnection(URL_CREATE, "", "");
            // create the database..
            stmt = conn.createStatement();
            final String create = getResourceAsString("derbyTest.sql");
            assertNotNull(create);
            stmt.execute(create);
            conn.commit();
        } finally {
            SQLUtil.closeQuietly(stmt);
            SQLUtil.closeQuietly(conn);
        }
    }

    /**
     * test method
     */
    @AfterClass
    public static void deleteDatabase() {
        try {
            DriverManager.getConnection(URL_SHUTDOWN);
        } catch (Exception ex) {
            //expected
        }
    }
    
    /**
     * Create the test configuration
     * @return the initialized configuration
     */
     @Override
    protected DatabaseTableConfiguration getConfiguration() throws Exception {
        DatabaseTableConfiguration config = new DatabaseTableConfiguration();
        config.setJdbcDriver(DRIVER);
        config.setDatasource(TEST_DS);
        config.setTable(DB_TABLE);
        config.setJndiProperties(jndiProperties);
        config.setChangeLogColumn(CHANGELOG);
        config.setKeyColumn(ACCOUNTID);
        config.setPasswordColumn(PASSWORD);
        config.setConnectorMessages(TestHelpers.createDummyMessages());
        return config;
    }

     /* (non-Javadoc)
      * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getCreateAttributeSet()
      */
     @Override
     protected Set<Attribute> getCreateAttributeSet(DatabaseTableConfiguration cfg) throws Exception {
         Set<Attribute> ret = new HashSet<Attribute>();        
         ret.add(AttributeBuilder.build(Name.NAME, randomString(r, 50)));
         if (StringUtil.isNotBlank(cfg.getPasswordColumn())) {
             ret.add(AttributeBuilder.buildPassword(new GuardedString(randomString(r, 50).toCharArray())));
         } else {
             ret.add(AttributeBuilder.build(PASSWORD, randomString(r, 40)));
         }
         ret.add(AttributeBuilder.build(MANAGER, randomString(r, 15)));
         ret.add(AttributeBuilder.build(MIDDLENAME, randomString(r, 50)));
         ret.add(AttributeBuilder.build(FIRSTNAME, randomString(r, 50)));
         ret.add(AttributeBuilder.build(LASTNAME, randomString(r, 50)));
         ret.add(AttributeBuilder.build(EMAIL, randomString(r, 50)));
         ret.add(AttributeBuilder.build(DEPARTMENT, randomString(r, 50)));
         ret.add(AttributeBuilder.build(TITLE, randomString(r, 50)));
         if(!cfg.getChangeLogColumn().equalsIgnoreCase(AGE)){
             ret.add(AttributeBuilder.build(AGE, r.nextInt(100)));
         }
         if(!cfg.getChangeLogColumn().equalsIgnoreCase(ACCESSED)){
             ret.add(AttributeBuilder.build(ACCESSED, r.nextLong()));
         }
         ret.add(AttributeBuilder.build(SALARY, new BigDecimal("360536.75")));
         ret.add(AttributeBuilder.build(JPEGPHOTO, randomBytes(r, 2000)));
         ret.add(AttributeBuilder.build(OPENTIME, new java.sql.Time(System.currentTimeMillis()).toString()));
         ret.add(AttributeBuilder.build(ACTIVATE, new java.sql.Date(System.currentTimeMillis()).toString()));
         ret.add(AttributeBuilder.build(ENROLLED, new Timestamp(System.currentTimeMillis()).toString()));
         ret.add(AttributeBuilder.build(CHANGED, new Timestamp(System.currentTimeMillis()).toString()));
         if(!cfg.getChangeLogColumn().equalsIgnoreCase(CHANGELOG)){
             ret.add(AttributeBuilder.build(CHANGELOG, new Timestamp(System.currentTimeMillis()).getTime()));
         }
         return ret;
     }

     /* (non-Javadoc)
      * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getModifyAttributeSet()
      */
     @Override
     protected Set<Attribute> getModifyAttributeSet(DatabaseTableConfiguration cfg) throws Exception {         
         return getCreateAttributeSet(cfg);
     }    
     
     /**
      * Make sure the Create call works..
      * @throws Exception 
      */
     @Test
     public void testCreateClosedConnection() throws Exception {
         log.ok("testCreateClosedConnection");
         DatabaseTableConfiguration cfg = getConfiguration();
         DatabaseTableConnector con = getConnector(cfg);

         Set<Attribute> expected = getCreateAttributeSet(cfg);
         con.create(ObjectClass.ACCOUNT, expected, null);
         // attempt to get the record back..
         
         assertEquals("Connection should be closed", true,
                 con.getConn().getConnection().isClosed());
     }     

    /**
     * For testing purposes we creating connection an not the framework.
     * @throws Exception 
     */
    @Test
    public void testNoZeroSQLExceptions() throws Exception {
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setRethrowAllSQLExceptions(false);
        con = getConnector(cfg);

        final ExpectProxy<MappingStrategy> smse = new ExpectProxy<MappingStrategy>();
        MappingStrategy sms = smse.getProxy(MappingStrategy.class);
        //Schema
        for (int i = 0; i < 15; i++) {
            smse.expectAndReturn("getSQLAttributeType", String.class);
        }
        //Create fail
        smse.expectAndThrow("setSQLParam", new SQLException("test reason", "0", 0));
        //Update fail
        smse.expectAndThrow("setSQLParam", new SQLException("test reason", "0", 0));
        con.getConn().setSms(sms);
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        con.update(ObjectClass.ACCOUNT, uid, expected, null);
        assertTrue("setSQLParam not called", smse.isDone());
    }
    
    /**
     * For testing purposes we creating connection an not the framework.
     * @throws Exception 
     */
    @Test(expected = ConnectorException.class)
    public void testNonZeroSQLExceptions() throws Exception {
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setRethrowAllSQLExceptions(false);
        con = getConnector(cfg);

        final ExpectProxy<MappingStrategy> smse = new ExpectProxy<MappingStrategy>();
        MappingStrategy sms = smse.getProxy(MappingStrategy.class);
        for (int i = 0; i < 15; i++) {
            smse.expectAndReturn("getSQLAttributeType", String.class);
        }
        smse.expectAndThrow("setSQLParam", new SQLException("test reason", "411", 411));
        con.getConn().setSms(sms);
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        con.create(ObjectClass.ACCOUNT, expected, null);
        assertTrue("setSQLParam not called", smse.isDone());
    }    
    
    /**
     * For testing purposes we creating connection an not the framework.
     * @throws Exception 
     */
    @Test(expected = ConnectorException.class)
    public void testRethrowAllSQLExceptions() throws Exception {
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setRethrowAllSQLExceptions(true);
        con = getConnector(cfg);

        final ExpectProxy<MappingStrategy> smse = new ExpectProxy<MappingStrategy>();
        MappingStrategy sms = smse.getProxy(MappingStrategy.class);
        for (int i = 0; i < 15; i++) {
            smse.expectAndReturn("getSQLAttributeType", String.class);
        }
        smse.expectAndThrow("setSQLParam", new SQLException("test reason", "0", 0));
        con.getConn().setSms(sms);
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        con.create(ObjectClass.ACCOUNT, expected, null);
        assertTrue("setSQLParam not called", smse.isDone());
    }    

    /**
     * For testing purposes we creating connection an not the framework.
     * @throws Exception 
     */
    @Test
    public void testSchema() throws Exception {
        DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        // check if this works..
        Schema schema = con.schema();
        checkSchema(schema);
    }
    
    
       
    /**
     * check validity of the schema
     * 
     * @param schema
     *            the schema to be checked
     * @throws Exception 
     */
    void checkSchema(Schema schema) throws Exception {
        // Schema should not be null
        assertNotNull(schema);
        Set<ObjectClassInfo> objectInfos = schema.getObjectClassInfo();
        assertNotNull(objectInfos);
        assertEquals(1, objectInfos.size());
        // get the fields from the test account
        final Set<Attribute> attributeSet = getCreateAttributeSet(getConfiguration());
        final Map<String,Attribute> expected = AttributeUtil.toMap(attributeSet);
        final Set<String> keys = CollectionUtil.newCaseInsensitiveSet();
        keys.addAll(expected.keySet());

        // iterate through ObjectClassInfo Set
        for (ObjectClassInfo objectInfo : objectInfos) {
            assertNotNull(objectInfo);
            // the object class has to ACCOUNT_NAME
            assertTrue(objectInfo.is(ObjectClass.ACCOUNT_NAME));

            // iterate through AttributeInfo Set
            for (AttributeInfo attInfo : objectInfo.getAttributeInfo()) {
                assertNotNull(attInfo);
                String fieldName = attInfo.getName();
                if(fieldName.equalsIgnoreCase(CHANGELOG)){
                    keys.remove(fieldName);
                    continue;
                }
                assertTrue("Field:" + fieldName + " doesn't exist", keys.contains(fieldName));
                keys.remove(fieldName);
                Attribute fa = expected.get(fieldName); 
                assertNotNull("Field:" + fieldName + "  was duplicated", fa);
                Object field = AttributeUtil.getSingleValue(fa);
                Class<?> valueClass = field.getClass();
                assertEquals("field: " + fieldName, valueClass, attInfo.getType());
            }
            // all the attribute has to be removed
            assertEquals("There are missing attributes which were not included in the schema:"+keys, 0, keys.size());
        }
    }    
    

    /**
     * Test creating of the connector object, searching using UID and delete
     * @throws Exception 
     * @throws SQLException 
     */
    @Test
    public void testGetLatestSyncToken() throws Exception {
        final String SQL_TEMPLATE = "UPDATE Accounts SET changelog = ? WHERE accountId = ?";
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        deleteAllFromAccounts(con.getConn());
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);
        final Long changelog = 9999999999999L; //Some really big value

        // update the last change
        PreparedStatement ps = null;
        DatabaseTableConnection conn = null;
        try {
            conn = DatabaseTableConnection.createDBTableConnection(getConfiguration());

            List<SQLParam> values = new ArrayList<SQLParam>();
            values.add(new SQLParam("changelog", changelog, Types.INTEGER));
            values.add(new SQLParam("accountId", uid.getUidValue(), Types.VARCHAR));
            ps = conn.prepareStatement(SQL_TEMPLATE, values);
            ps.execute();
            conn.commit();
        } finally {
            SQLUtil.closeQuietly(ps);
            SQLUtil.closeQuietly(conn);
        }
        // attempt to find the newly created object..
        final SyncToken latestSyncToken = con.getLatestSyncToken(ObjectClass.ACCOUNT);
        assertNotNull(latestSyncToken);
        final Object actual = latestSyncToken.getValue();
        assertEquals(changelog, actual);
    }     
    
    static String getResourceAsString(String res) {
        return IOUtil.getResourceAsString(DatabaseTableDSDerbyTests.class, res);
    }    

    /**
     * Context is set in jndiProperties
     */
    public static class MockContextFactory implements InitialContextFactory {

        @SuppressWarnings("unchecked")
        public Context getInitialContext(Hashtable environment) throws NamingException {
            Context context = (Context) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[] { Context.class }, new ContextIH());
            return context;
        }
    }
   
    /**
     *  MockContextFactory create the ContextIH
     *  The looup method will return DataSourceIH
     */
    static class ContextIH implements InvocationHandler {

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("lookup")) {
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { DataSource.class },
                        new DataSourceIH());
            }
            return null;
        }
    }

    /**
     * ContextIH create DataSourceIH
     * The getConnection method will return ConnectionIH
     */
    static class DataSourceIH implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getConnection")) {
                return DriverManager.getConnection(URL_CONN, "", "");
            }
            throw new IllegalArgumentException("DataSource, invalid method:"+method.getName());            
        }
    }      

    /**
     * The dir acces method
     * @return the file see {@link File}
     */
    static File getDBDirectory() {
        return new File(System.getProperty("java.io.tmpdir"), DB_DIR);
    }
        
}
