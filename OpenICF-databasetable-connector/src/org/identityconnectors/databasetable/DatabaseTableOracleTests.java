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
import static org.junit.Assert.*;


import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.contract.data.DataProvider;
import org.identityconnectors.contract.data.GroovyDataProvider;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

/**
 * Attempts to test the Connector with the framework.
 */
public class DatabaseTableOracleTests extends DatabaseTableTestBase{

    static final String ORACLE_CONFIGURATINON = "configurations.oracle";
    static final DataProvider dp = new GroovyDataProvider();
    private static final String TMS = "TMS";   
    
    
    @Override
    protected DatabaseTableConfiguration getConfiguration() throws Exception {
        DatabaseTableConfiguration cfg = new DatabaseTableConfiguration();
        dp.loadConfiguration(ORACLE_CONFIGURATINON, cfg);
        cfg.setConnectorMessages(TestHelpers.createDummyMessages());
        return cfg;
    }    
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getCreateAttributeSet()
     */
    @Override
    protected Set<Attribute> getCreateAttributeSet(DatabaseTableConfiguration cfg) throws Exception {
        Set<Attribute> ret = new HashSet<Attribute>();        
        ret.add(AttributeBuilder.build(Name.NAME, "Test Name"+r.nextInt()));
        if (StringUtil.isNotBlank(cfg.getPasswordColumn())) {
            ret.add(AttributeBuilder.buildPassword(new GuardedString("Test Pasword".toCharArray())));
        } else {
            ret.add(AttributeBuilder.build(PASSWORD, "Test Pasword"));
        }
        ret.add(AttributeBuilder.build(MANAGER, MANAGER));
        ret.add(AttributeBuilder.build(MIDDLENAME, MIDDLENAME));
        ret.add(AttributeBuilder.build(FIRSTNAME, FIRSTNAME));
        ret.add(AttributeBuilder.build(LASTNAME, LASTNAME));
        ret.add(AttributeBuilder.build(EMAIL, "thelongtestemail@somelongorganization.com"));
        ret.add(AttributeBuilder.build(DEPARTMENT, DEPARTMENT));
        ret.add(AttributeBuilder.build(TITLE, TITLE));
        ret.add(AttributeBuilder.build(AGE, new BigDecimal("99999")));
        ret.add(AttributeBuilder.build(SALARY, new BigDecimal("999999.55")));
        ret.add(AttributeBuilder.build(JPEGPHOTO, randomBytes(r, 2000)));
        return ret;
    }

    /* ------------ Skiped tests -------------------- */
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getModifyAttributeSet()
     */
    @Override
    protected Set<Attribute> getModifyAttributeSet(DatabaseTableConfiguration cfg) throws Exception {         
        return getCreateAttributeSet(cfg);
    }         
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getModifyAttributeSet()
     */
    @Override   
    @Test
    public void testCreateCallNotNullEnableEmptyString() throws Exception {
        //skeep this tests, oracle does not support empty string. They are considered as a null
    }
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getModifyAttributeSet()
     */
    @Override       
    @Test
    public void testSyncUsingLongColumn() throws Exception {  
      //The column is not defined in oracle
    }
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getModifyAttributeSet()
     */
    @Override       
    @Test
    public void testSyncFull() throws Exception {
        //The column is not defined in oracle
    }
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableTestBase#getModifyAttributeSet()
     */
    @Override  
    @Test
    public void testSyncIncemental() throws Exception {
        //The column is not defined in oracle
    }

    /**
     * testTimestampColumn operates on the table 'bug17551table'
     * @throws Exception 
     */
    @Test
    public void testTimestampColumnNative() throws Exception {
        log.ok("testCreateCall");
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setTable("BUG17551");
        cfg.setNativeTimestamps(true);
        con = getConnector(cfg);
        deleteAllFromBug(con.getConn());
        Set<Attribute> expected = getTimestampColumnAttributeSet();
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        // attempt to get the record back..
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
        assertTrue("expect 1 connector object", results.size() == 1);
        final ConnectorObject co = results.get(0);
        assertNotNull(co);
        final Set<Attribute> actual = co.getAttributes();
        assertNotNull(actual);
        Attribute tmsAtr = AttributeUtil.find(TMS, actual);
        String timestampTest = AttributeUtil.getStringValue(tmsAtr);
        if (timestampTest == null || timestampTest.indexOf("00005") == -1) {
            fail("    testTimestampColumn1 testcase for bug#17551 failed, expected 00005 in the milli-seconds part, but got timestamp "
                    + timestampTest);
        }
    }
    
    /**
     * testTimestampColumn operates on the table 'bug17551table'
     * @throws Exception 
     */
    @Test    
    public void testTimestampColumnNotNative() throws Exception {
        log.ok("testCreateCall");
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setTable("BUG17551");
        cfg.setNativeTimestamps(false);
        con = getConnector(cfg);
        deleteAllFromBug(con.getConn());
        Set<Attribute> expected = getTimestampColumnAttributeSet();
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        // attempt to get the record back..
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
        assertTrue("expect 1 connector object", results.size() == 1);
        final ConnectorObject co = results.get(0);
        assertNotNull(co);
        final Set<Attribute> actual = co.getAttributes();
        assertNotNull(actual);
        Attribute tmsAtr = AttributeUtil.find(TMS, actual);
        String timestampTest = AttributeUtil.getStringValue(tmsAtr);
        if (timestampTest != null && timestampTest.indexOf(". 50000") == -1) {
            fail("    expected JDBC driver problem, fixed through bug# 17551");
        }
    }
    
    /**
     *    "Login_Id" VARCHAR2(50) NOT NULL, 
     *    "Password" VARCHAR2(50),
     *    "Email" VARCHAR2(50),
     *    "tms" TIMESTAMP
     */    
    private Set<Attribute> getTimestampColumnAttributeSet() throws Exception {
        Set<Attribute> ret = new HashSet<Attribute>();        
        ret.add(AttributeBuilder.build(Name.NAME, "Test Name"+r.nextInt()));
        ret.add(AttributeBuilder.buildPassword(new GuardedString("Test Pasword".toCharArray())));
        ret.add(AttributeBuilder.build(EMAIL, "thelongtestemail@somelongorganization.com"));
        ret.add(AttributeBuilder.build(TMS, "05-DEC-07 10.29.01.000050 PM"));
        return ret;
    }    

    
    /**
     * The class load method
     * @param conn 
     * @throws Exception 
     */
    public void deleteAllFromBug(DatabaseTableConnection conn) throws Exception { 
        // update the last change
        final String SQL_TEMPLATE = "DELETE FROM BUG17551";
        log.ok(SQL_TEMPLATE);
        PreparedStatement ps = null;
        try {
            ps = conn.getConnection().prepareStatement(SQL_TEMPLATE);
            ps.execute();
        } finally {
            SQLUtil.closeQuietly(ps);
        }
        conn.commit();
    }
    
}
