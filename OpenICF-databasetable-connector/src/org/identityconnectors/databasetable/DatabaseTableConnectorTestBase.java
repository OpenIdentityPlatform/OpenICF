/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
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

import static org.junit.Assert.*;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.test.TestHelpers;
import org.junit.Test;

/**
 * Attempts to test the Connector with the framework.
 */
public abstract class DatabaseTableConnectorTestBase {
   
    // Constants..
    static final String CHANGELOG = "changelog";
    static final String ACCOUNTID = "accountId";
    static final String PASSWORD = "password";
    static final String MANAGER = "manager";
    static final String MIDDLENAME = "middlename";
    static final String FIRSTNAME = "firstname";
    static final String LASTNAME = "lastname";
    static final String EMAIL = "email";
    static final String DEPARTMENT = "department";
    static final String TITLE = "title";
    static final String AGE = "age";
    static final String SALARY = "salary";
    static final String JPEGPHOTO = "jpegphoto";    
    static final String ENROLLED = "enrolled";    
    static final String ACTIVATE = "activate";   
    static final String ACCESSED = "accessed";    
    static final String CHANGED = "changed";
    
    // always seed that same for results..
    static final Random r = new Random(17);    
    /**
     * Create the test configuration
     * @return the initialized configuration 
     */
    protected abstract DatabaseTableConfiguration getConfiguration();
 
    /**
     * Create the test attribute sets
     * @return the initialized attribute set
     */
    protected abstract Set<Attribute>  getCreateAttributeSet(DatabaseTableConfiguration cfg);

    /**
     * Create the test modify attribute set
     * @return the initialized attribute set
     */
    protected abstract  Set<Attribute>  getModifyAttributeSet(DatabaseTableConfiguration cfg);
   
    /**
     * The class load method
     * @param conn 
     * @throws Exception 
     */
    public void deleteAllFromAccounts(DatabaseTableConnection conn) throws Exception { 
        // update the last change
        final String SQL_TEMPLATE = "DELETE FROM ACCOUNTS";
        PreparedStatement ps = null;
        try {
            ps = conn.getConnection().prepareStatement(SQL_TEMPLATE);
            ps.execute();
        } finally {
            SQLUtil.closeQuietly(ps);
        }
        conn.commit();
    }
    
    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testConfiguration() throws Exception {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        config.validate();      
    }   


    
    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testTestMethod() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        con.test();
    }  
    
 

    /**
     * For testing purposes we creating connection an not the framework.
     * @throws Exception 
     */
    @Test(expected = ConnectorException.class)
    public void testInvalidConnectionQuery() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setValidConnectionQuery("INVALID");
        final DatabaseTableConnector con = getConnector(cfg);        
        con.test();
    }


    /**
     * Make sure the Create call works..
     * @throws Exception 
     */
    @Test
    public void testCreateCall() throws Exception {
        DatabaseTableConfiguration cfg = getConfiguration();
        DatabaseTableConnector con = getConnector(cfg);
        deleteAllFromAccounts(con.getConnection());
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        // attempt to get the record back..
        List<ConnectorObject> results = TestHelpers
                .searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
        assertTrue("expect 1 connector object", results.size() == 1);
        final ConnectorObject co = results.get(0);
        assertNotNull(co);
        final Set<Attribute> actual = co.getAttributes();
        assertNotNull(actual);
        attributeSetsEquals(con.schema(), expected, actual);
    }


    /**
     * Make sure the Create call works..
     * @throws Exception 
     */
    @Test(expected=ConnectorException.class)
    public void testCreateCallNotNull() throws Exception {
        DatabaseTableConnector c = null;
        DatabaseTableConfiguration cfg = getConfiguration();
        try {
            c = new DatabaseTableConnector();
            c.init(cfg);
            Set<Attribute> expected = getCreateAttributeSet(cfg);
            // create modified attribute set
            Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
            chMap.put("firstname", AttributeBuilder.build(FIRSTNAME, (String) null));
            final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());            
            c.create(ObjectClass.ACCOUNT, changeSet, null);
        } finally {
            if (c != null) {
                c.dispose();
            }            
        }
    }
    
    /**
     * Make sure the Create call works..
     * @throws Exception 
     */
    @Test
    public void testCreateCallNotNullEnableEmptyString() throws Exception {
        DatabaseTableConnector c = null;
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setEnableEmptyString(true);
        try {
            c = new DatabaseTableConnector();
            c.init(cfg);
            Set<Attribute> expected = getCreateAttributeSet(cfg);
            // create modified attribute set
            Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
            chMap.put(FIRSTNAME, AttributeBuilder.build(FIRSTNAME, (String) null));
            chMap.put(LASTNAME, AttributeBuilder.build(LASTNAME, (String) null));
            final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());            
            Uid uid = c.create(ObjectClass.ACCOUNT, changeSet, null);
            // attempt to get the record back..
            List<ConnectorObject> results = TestHelpers
                    .searchToList(c, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
            assertTrue("expect 1 connector object", results.size() == 1);
            final ConnectorObject co = results.get(0);
            assertNotNull(co);
            final Set<Attribute> actual = co.getAttributes();
            assertNotNull(actual);
            attributeSetsEquals(c.schema(), changeSet, actual, FIRSTNAME, LASTNAME);
        } finally {
            if (c != null) {
                c.dispose();
            }            
        }
    }    
    
    /**
     * Make sure the Create call works..
     * @throws Exception 
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateUnsuported() throws Exception {
        DatabaseTableConnector c = null;
        DatabaseTableConfiguration cfg = getConfiguration();
        try {
            c = new DatabaseTableConnector();
            c.init(cfg);
            ObjectClass objClass = new ObjectClass("NOTSUPPORTED");
            c.create(objClass, getCreateAttributeSet(cfg), null);
        } finally {
            if (c != null) {
                c.dispose();
            }
        }
    }

    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testCreateWithName() throws Exception {        
        DatabaseTableConfiguration cfg = getConfiguration();
        final Set<Attribute> attributes = getCreateAttributeSet(cfg);
        final DatabaseTableConnector con = getConnector(cfg);
        Name name = AttributeUtil.getNameFromAttributes(attributes);
        final Uid uid = con.create(ObjectClass.ACCOUNT, attributes, null);
        assertNotNull(uid);
        assertEquals(name.getNameValue(), uid.getUidValue());
    }
  


    /**
     * Test creating of the connector object, searching using UID and delete
     * @throws Exception 
     */
    @Test
    public void testCreateAndDelete() throws Exception {
        final String ERR1 = "Could not find new object.";
        final String ERR2 = "Found object that should not be there.";
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        try {
            System.out.println("Uid: " + uid);
            // attempt to find the newly created object..
            List<ConnectorObject> list = TestHelpers.
                searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
            assertTrue(ERR1, list.size()==1);

            //Test the created attributes are equal the searched
            final ConnectorObject co = list.get(0);
            assertNotNull(co);
            final Set<Attribute> actual = co.getAttributes();
            assertNotNull(actual);
            attributeSetsEquals(con.schema(), expected, actual);
        } finally {
            // attempt to delete the object..
            con.delete(ObjectClass.ACCOUNT, uid, null);
            // attempt to find it again to make sure
            // it actually deleted the object..
            // attempt to find the newly created object..
            List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
            assertTrue(ERR2, list.size()==0);
            try {
                // now attempt to delete an object that is not there..
                con.delete(ObjectClass.ACCOUNT, uid, null);
                fail("Should have thrown an execption.");
            } catch (UnknownUidException exp) {
                // should get here..
            }
        }
    }

    /**
     * Test creating of the connector object, searching using UID and delete
     * @throws Exception 
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteUnsupported() throws Exception {
        final String ERR1 = "Could not find new object.";
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        try {
            System.out.println("Uid: " + uid);
            // attempt to find the newly created object..
            List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
            assertTrue(ERR1, list.size()==1);
        } finally {
            // attempt to delete the object..
            ObjectClass objc = new ObjectClass("UNSUPPORTED");
            con.delete(objc, uid, null);
        }
    }

    /**
     * Test creating of the connector object, searching using UID and update
     * @throws Exception 
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUpdateUnsupported() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertTrue(list.size()==1);

        // create updated connector object
        Set<Attribute> changeSet = getModifyAttributeSet(cfg);
        ObjectClass objClass = new ObjectClass("NOTSUPPORTED");
        con.update(objClass, uid ,changeSet, null);
    }

    /**
     * Test creating of the connector object, searching using UID and update
     * @throws Exception 
     */
    @Test
    public void testUpdateNull() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertTrue(list.size()==1);

        // create updated connector object
        Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
        chMap.put(SALARY, AttributeBuilder.build(SALARY, (Integer) null));
        // do the update
        final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());
        con.update(ObjectClass.ACCOUNT, uid, changeSet, null);
        
        // retrieve the object
        List<ConnectorObject> list2 = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertNotNull(list2);
        assertTrue(list2.size()==1);  
        final Set<Attribute> actual = list2.get(0).getAttributes();
        attributeSetsEquals(con.schema(), changeSet, actual, SALARY);
    }
    
    /**
     * Test creating of the connector object, searching using UID and update
     * 
     * @throws Exception
     */
    @Test
    public void testCreateAndUpdate() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertTrue(list.size()==1);

        // create updated connector object
        final Set<Attribute> changeSet = getModifyAttributeSet(cfg);
        uid = con.update(ObjectClass.ACCOUNT, uid, changeSet, null);
        
        // retrieve the object
        List<ConnectorObject> list2 = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertNotNull(list2);
        assertTrue(list2.size()==1);  
        final Set<Attribute> actual = list2.get(0).getAttributes();
        attributeSetsEquals(con.schema(), changeSet, actual);
    }
    
    /**
     * Test method for
     * {@link org.identityconnectors.databasetable.DatabaseTableConnector#authenticate(username, password, options)}.
     * Test creating of the connector object, searching using UID and update
     * @throws Exception 
     */
    @Test
    public void testAuthenticateOriginal() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertTrue(list.size()==1);
        
        // check if authenticate operation is present (it should)
        Schema schema = con.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        assertTrue(oci.size() >= 1); 

        // this should not throw any RuntimeException, on invalid authentication
        final Name name = AttributeUtil.getNameFromAttributes(expected);
        final GuardedString passwordValue = AttributeUtil.getPasswordValue(expected);
        final Uid auid = con.authenticate(ObjectClass.ACCOUNT, name.getNameValue(), passwordValue, null);
        assertEquals(uid, auid);

        // cleanup (should not throw any exception.)
        con.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method for
     * {@link org.identityconnectors.databasetable.DatabaseTableConnector#authenticate(username, password, options)}.
     * Test creating of the connector object, searching using UID and update
     * 
     * In this case the user that we query is not in the database at all.
     * @throws Exception 
     */
    @Test(expected = InvalidCredentialException.class)
    public void testAuthenticateWrongOriginal() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        // this should throw InvalidCredentials exception, as we query a
        // non-existing user
        con.authenticate(ObjectClass.ACCOUNT, "NON", new GuardedString("MOM".toCharArray()),
                null);
    }

    /**
     * Test method for
     * {@link org.identityconnectors.databasetable.DatabaseTableConnector#authenticate(username, password, options)}.
     * @throws Exception 
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testNoPassColumnAutenticate() throws Exception {

        final DatabaseTableConfiguration cfg = getConfiguration();
        // Erasing password column from the configuration (it will be no longer treated as special attribute).
        cfg.setPasswordColumn(null);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        final DatabaseTableConnector con = getConnector(cfg);

        // note: toAttributeSet(false), where false means, password will not be
        // treated as special attribute.
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        // check if authenticate operation is present (it should NOT!)
        Schema schema = con.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        assertTrue(oci.size() == 0);

        // authentication should not be allowed -- will throw an
        // IllegalArgumentException
        // this should not throw any RuntimeException, on invalid authentication
        final Name name = AttributeUtil.getNameFromAttributes(expected);
        final GuardedString passwordValue = AttributeUtil.getPasswordValue(expected);
        con.authenticate(ObjectClass.ACCOUNT, name.getNameValue(), passwordValue, null);

        // cleanup (should not throw any exception.)
        con.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method
     * @throws Exception 
     */
    @Test
    public void testSearchByName() throws Exception {
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        assertTrue(list.size()==1);
        ConnectorObject actual = list.get(0);
        assertNotNull(actual);
        attributeSetsEquals(con.schema(), expected, actual.getAttributes());

    }

    /**
     * Test method to issue #238
     * @throws Exception
     */
    @Test
    public void testSearchWithNullPassword() throws Exception {
        final String SQL_TEMPLATE = "UPDATE {0} SET password = null WHERE {1} = ?";
        final DatabaseTableConfiguration cfg = getConfiguration();
        final String sql = MessageFormat.format(SQL_TEMPLATE, cfg.getTable(), cfg.getKeyColumn());
        final DatabaseTableConnector con = getConnector(cfg);
        
        PreparedStatement ps = null;
        DatabaseTableConnection conn = null;
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        try {
            Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);

            //set password to null
            //expected.setPassword((String) null);
            conn = con.getConnection();

            List<SQLParam> values = new ArrayList<SQLParam>();
            values.add(new SQLParam(uid.getUidValue(), Types.VARCHAR));
            ps = conn.prepareStatement(sql, values);
            ps.execute();
            conn.commit();

            // attempt to get the record back..
            List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder
                    .equalTo(uid));
            assertTrue("expect 1 connector object", results.size() == 1);
            final Set<Attribute> attributes = results.get(0).getAttributes();
            attributeSetsEquals(con.schema(), expected, attributes);
        } finally {
            SQLUtil.closeQuietly(ps);
            SQLUtil.closeQuietly(conn);

            if (con != null) {
                con.dispose();
            }
        }
    }

    /**
     * Test method, issue #186
     * @throws Exception 
     */
    @Test
    public void testSearchByNameAttributesToGet() throws Exception {
        // create connector
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        
        // attempt to get the record back..
        OperationOptionsBuilder opOption = new OperationOptionsBuilder();
        opOption.setAttributesToGet(FIRSTNAME, LASTNAME, MANAGER);
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder
                .equalTo(uid),opOption.build());
        assertTrue("expect 1 connector object", results.size() == 1);        

        final ConnectorObject co = results.get(0);

        
        assertEquals(uid.getUidValue(), co.getUid().getUidValue());
        assertEquals(uid.getUidValue(), co.getName().getNameValue());
        
        Set<Attribute> actual = co.getAttributes();
        assertNotNull(actual);
        assertNull(AttributeUtil.find(AGE, actual));
        assertNull(AttributeUtil.find(DEPARTMENT, actual));
        assertNull(AttributeUtil.find(EMAIL, actual));
        assertNotNull(AttributeUtil.find(FIRSTNAME, actual));
        assertNotNull(AttributeUtil.find(LASTNAME, actual));
        assertNotNull(AttributeUtil.find(MANAGER, actual));
        assertNull(AttributeUtil.find(MIDDLENAME, actual));
        assertNull(AttributeUtil.find(SALARY, actual));
        assertNull(AttributeUtil.find(TITLE, actual));
        assertNull(AttributeUtil.find(JPEGPHOTO, actual));        
        assertNull(AttributeUtil.find(CHANGED, actual));       
    }

    /**
     * Test method, issue #186
     * @throws Exception 
     */
    @Test
    public void testSearchByNameAttributesToGetExtended() throws Exception {
        // create connector
        final DatabaseTableConfiguration cfg = getConfiguration();
        final DatabaseTableConnector con = getConnector(cfg);
        deleteAllFromAccounts(con.getConnection());
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        assertNotNull(uid);

        
        // attempt to get the record back..
        OperationOptionsBuilder opOption = new OperationOptionsBuilder();
        opOption.setAttributesToGet(FIRSTNAME, LASTNAME, MANAGER, JPEGPHOTO);
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder
                .equalTo(uid),opOption.build());
        assertTrue("expect 1 connector object", results.size() == 1);        

        final ConnectorObject co = results.get(0);

        assertEquals(uid.getUidValue(), co.getUid().getUidValue());
        assertEquals(uid.getUidValue(), co.getName().getNameValue());
        
        Set<Attribute> actual = co.getAttributes();
        assertNotNull(actual);        
        assertNull(AttributeUtil.find(AGE, actual));
        assertNull(AttributeUtil.find(DEPARTMENT, actual));
        assertNull(AttributeUtil.find(EMAIL, actual));
        assertNotNull(AttributeUtil.find(FIRSTNAME, actual));
        assertNotNull(AttributeUtil.find(LASTNAME, actual));
        assertNotNull(AttributeUtil.find(MANAGER, actual));
        assertNull(AttributeUtil.find(MIDDLENAME, actual));
        assertNull(AttributeUtil.find(SALARY, actual));
        assertNull(AttributeUtil.find(TITLE, actual));
        assertNotNull(AttributeUtil.find(JPEGPHOTO, actual));
        assertEquals(AttributeUtil.find(JPEGPHOTO, expected), AttributeUtil.find(JPEGPHOTO, actual));                
    }
        
    // Helper Methods/Classes

    /**
     * @param cfg
     * @return
     */
    protected DatabaseTableConnector getConnector(DatabaseTableConfiguration cfg) {
        final DatabaseTableConnector con = new DatabaseTableConnector();
        con.init(cfg);
        return con;
    }    

    
    
    /**
     * @param expected
     * @param actual
     */
    protected void attributeSetsEquals(final Schema schema, Set<Attribute> expected, Set<Attribute> actual, String ... ignore) {
        attributeSetsEquals(schema, AttributeUtil.toMap(expected), AttributeUtil.toMap(actual), ignore);              
    }    
    
     /**
     * @param expected
     * @param actual
     */
    protected void attributeSetsEquals(final Schema schema, final Map<String, Attribute> expMap, final Map<String, Attribute> actMap, String ... ignore) {
        final Set<String> ignoreSet = new HashSet<String>(Arrays.asList(ignore));
        if(schema != null ) {
            final ObjectClassInfo oci = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
            final Set<AttributeInfo> ais = oci.getAttributeInfo();
            for (AttributeInfo ai : ais) {
                //ignore not returned by default
                if (!ai.isReturnedByDefault()) {
                    ignoreSet.add(ai.getName());
                }
                //ignore not readable attributes
                if (!ai.isReadable()) {
                    ignoreSet.add(ai.getName());
                }
            }
        }
        
        Set<String> names = CollectionUtil.newCaseInsensitiveSet();
        names.addAll(expMap.keySet());
        names.addAll(actMap.keySet());
        names.removeAll(ignoreSet);
        names.remove(Uid.NAME);
        int missing = 0; 
        List<String> mis = new ArrayList<String>();
        List<String> extra = new ArrayList<String>();        
        for (String attrName : names) {
            final Attribute expAttr = expMap.get(attrName);
            final Attribute actAttr = actMap.get(attrName);
            if(expAttr != null && actAttr != null ) {
                final Object expValue = AttributeUtil.getSingleValue(expAttr);
                final Object actValue = AttributeUtil.getSingleValue(actAttr);
                assertEquals(attrName, expValue, actValue);
            } else {
                missing = missing + 1;
                if(expAttr != null) {
                    mis.add(expAttr.getName());
                }
                if(actAttr != null) {
                    extra.add(actAttr.getName());                    
                }
            }
        }
        assertEquals("missing attriburtes extra "+extra+" , missing "+mis, 0, missing);                   
    }       

    protected static class FindUidSyncHandler implements SyncResultsHandler {
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
