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

import static org.identityconnectors.common.ByteUtil.randomBytes;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.contract.data.DataProvider;
import org.identityconnectors.contract.data.GroovyDataProvider;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

/**
 * Attempts to test the Connector with the framework.
 */
public class DatabaseTableConnectorOracleTests extends DatabaseTableConnectorTestBase{

    static final String ORACLE_CONFIGURATINON = "configurations.oracle";
    static final DataProvider dp = new GroovyDataProvider();   
    
    
    @Override
    protected DatabaseTableConfiguration getConfiguration() throws Exception {
        DatabaseTableConfiguration cfg = new DatabaseTableConfiguration();
        dp.loadConfiguration(ORACLE_CONFIGURATINON, cfg);
        cfg.setConnectorMessages(TestHelpers.createDummyMessages());
        return cfg;
    }    
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableConnectorTestBase#getCreateAttributeSet()
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
     * @see org.identityconnectors.databasetable.DatabaseTableConnectorTestBase#getModifyAttributeSet()
     */
    @Override
    protected Set<Attribute> getModifyAttributeSet(DatabaseTableConfiguration cfg) throws Exception {         
        return getCreateAttributeSet(cfg);
    }         
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableConnectorTestBase#getModifyAttributeSet()
     */
    @Override   
    @Test
    public void testCreateCallNotNullEnableEmptyString() throws Exception {
        //skeep this tests, oracle does not support empty string. They are considered as a null
    }
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableConnectorTestBase#getModifyAttributeSet()
     */
    @Override       
    @Test
    public void testSyncUsingLongColumn() throws Exception {  
      //The column is not defined in oracle
    }
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableConnectorTestBase#getModifyAttributeSet()
     */
    @Override       
    @Test
    public void testSyncFull() throws Exception {
        //The column is not defined in oracle
    }
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.DatabaseTableConnectorTestBase#getModifyAttributeSet()
     */
    @Override  
    @Test
    public void testSyncIncemental() throws Exception {
        //The column is not defined in oracle
    }

    /**
     * doTestTimestampColumn1 , doTestTimestampColumn2, doTestTsColAcctIter1 and 
     * doTestTsColAcctIter2 operates on the table 'bug17551table', which was created 
     * using the following SQL automatically.
     *
     * CREATE TABLE bug17551table (
     *              Login_Id VARCHAR(50) NOT NULL, Password VARCHAR(50),
     *              Email VARCHAR(50), time_stamp TIMESTAMP 
     * )
     * @throws Exception 
     */
    public void setupBug17551Table() throws Exception {
        String dropTableSql = "DROP TABLE bug17551table";
        String createTableSql = "CREATE TABLE bug17551table (Login_Id VARCHAR(50) NOT NULL, Password VARCHAR(50), Email VARCHAR(50), time_stamp TIMESTAMP)";

        java.sql.Connection con = null;
        java.sql.Statement stmt = null;

        try {
            con = DatabaseTableConnection.getConnection(getConfiguration()).getConnection();
            stmt = con.createStatement();
            try {
                stmt.execute(dropTableSql);
            }
            catch (java.sql.SQLException sex) {
                //expected
            }
            stmt.execute(createTableSql);
        }
        finally {
            stmt.close();
            stmt = null;
            con.close();
            con = null;
        }
    }

}
