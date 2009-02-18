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

import static org.junit.Assert.assertEquals;
import junit.framework.Assert;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

/**
 * Attempts to test the Connector with the framework.
 */
public class DatabaseTableConfigurationTests {
   
    /**
     * Derby's embedded driver.
     */
    static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
    static final String USER = "tstUser";
    static final GuardedString PASSWORD = new GuardedString("tstPAssword".toCharArray());
    static final String DBTABLE = "tstTable";
    static final String HOST = "tstHost";
    static final String PORT = "8000";
    static final String DATABASE = "tstDatabase";
    static final String KEYCOLUMN = "tstKeyColumn";
    static final String PASSDCOLUMN = "tstPasswordColumn";
    static final String CHANGELOG = "tstChangelogColumn";
    static final String URL = "jdbc:derby:@tstHost:8000:tstDatabase";
    static final String URLTEMPLATE = "jdbc:derby:@%h:%p:%d";

    /**
    * 
    * @return
    * @throws Exception
    */
    protected DatabaseTableConfiguration getConfiguration() {
        DatabaseTableConfiguration config = new DatabaseTableConfiguration();
        config.setJdbcDriver(DRIVER);
        config.setUser(USER);
        config.setPassword(PASSWORD);
        config.setTable(DBTABLE);
        config.setHost(HOST);
        config.setPort(PORT);
        config.setDatabase(DATABASE);
        config.setKeyColumn(KEYCOLUMN);
        config.setPasswordColumn(PASSDCOLUMN);
        config.setChangeLogColumn(CHANGELOG);
        config.setJdbcUrlTemplate(URLTEMPLATE);
        config.setConnectorMessages(TestHelpers.createDummyMessages());
        return config;
    }    

    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testConfiguration() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        // check defaults..
        config.validate();
    }   
    
    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testFormatUrl() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        // check defaults..
        final String url = config.formatUrlTemplate();
        assertEquals(URL, url);
    }    
    
    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testGetSetTheProperties() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        // check defaults..
        config.setAllNative(true);
        assertEquals(true, config.isAllNative());
        config.setChangeLogColumn("TST");
        assertEquals("TST", config.getChangeLogColumn());
        config.setDatabase("DB");
        assertEquals("DB", config.getDatabase());
        config.setDatasource("DS");
        assertEquals("DS", config.getDatasource());
        config.setEnableEmptyString(true);
        assertEquals(true, config.isEnableEmptyString());
        config.setHost("HS");
        assertEquals("HS", config.getHost());
        config.setJdbcDriver("DRV");
        assertEquals("DRV", config.getJdbcDriver());
        config.setJdbcUrlTemplate("TMP");
        assertEquals("TMP", config.getJdbcUrlTemplate());
        config.setKeyColumn("KEY");
        assertEquals("KEY", config.getKeyColumn());
        config.setNativeTimestamps(true);
        assertEquals(true, config.isNativeTimestamps());
        config.setPasswordColumn("PWC");
        assertEquals("PWC", config.getPasswordColumn());
        config.setPort("80");
        assertEquals("80", config.getPort());
        config.setQuoting("double");
        assertEquals("double", config.getQuoting());
        config.setRethrowAllSQLExceptions(false);
        assertEquals(false, config.isRethrowAllSQLExceptions());
        config.setTable("TB");
        assertEquals("TB", config.getTable());
        config.setUser("USR");
        assertEquals("USR", config.getUser());
        config.setValidConnectionQuery("VALID");
        assertEquals("VALID", config.getValidConnectionQuery());
    }       

    /**
     * test method
     * 
     * @throws Exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationEmptyHost() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        config.setHost("");
        // check defaults..
        config.validate();
    }

    /**
     * test method
     * 
     * @throws Exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationEmptyPort() {
        DatabaseTableConfiguration config = getConfiguration();
        config.setPort("");
        // check defaults..
        config.validate();
    }
        
    /**
     * test method
     * 
     * @throws Exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationEmptyDatabase() {
        DatabaseTableConfiguration config = getConfiguration();
        config.setDatabase("");
        // check defaults..
        config.validate();
        Assert.fail("empty database");
    }   
    
    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testConfigurationDataSource() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        // check defaults..
        config.setDatasource("DS");
        config.validate();
    }    
    
    /**
     * test method
     * @throws Exception 
     */
    @Test
    public void testConfigurationJndi() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        // check defaults..

        config.setDatasource("DS");
        assertEquals("DS", config.getDatasource());
        
        final String[] tstpr = {"a=A","b=B"};
        config.setJndiProperties(tstpr);
        assertEquals(tstpr[0], config.getJndiProperties()[0]);
        assertEquals(tstpr[1], config.getJndiProperties()[1]);

        config.validate();      
    }   
    
    /**
     * test method
     */
    @Test(expected=IllegalArgumentException.class)
    public void testConfigurationInvalidJndi() {
        // attempt to test driver info..
        DatabaseTableConfiguration config = getConfiguration();
        // check defaults..

        config.setDatasource("DS");      
        final String[] tstpr = {"a=A","b"};
        config.setJndiProperties(tstpr);
        config.validate();      
    }
}
