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

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.dbcommon.JNDIUtil;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.operations.SyncOp;


/**
 * Implements the {@link Configuration} interface to provide all the necessary
 * parameters to initialize the JDBC Connector.
 */
public class DatabaseTableConfiguration extends AbstractConfiguration {

    // =======================================================================
    // DatabaseTableConfiguration
    // =======================================================================

    
    
    /**
     * The datasource name is used to connect to database.
     */
    private String datasource;

    /**
     * Return the datasource 
     * @return datasource value
     */
    @ConfigurationProperty(order = 1)
    public String getDatasource() {
        return datasource;
    }

    /**
     * @param value
     */
    public void setDatasource(String value) {
        this.datasource = value;
    }
    
    
    /**
     * The jndiFactory name is used to connect to database.
     */
    private String[] jndiProperties;

    /**
     * Return the jndiFactory 
     * @return jndiFactory value
     */
    @ConfigurationProperty(order = 2)
    public String[] getJndiProperties() {
        return jndiProperties;
    }

    /**
     * @param value
     */
    public void setJndiProperties(String[] value) {
        this.jndiProperties = value;
    }
    
    /**
     * Database connection URL. The url is used to connect to database.
     * Required configuration property, and should be validated
     */
    private String connectionUrl;

    /**
     * Return the connectionUrl 
     * @return url value
     */
    @ConfigurationProperty(order = 4, displayMessageKey="connectionUrl.display", helpMessageKey="connectionUrl.help")
    public String getConnectionUrl() {
        return connectionUrl;
    }

    /**
     * @param value
     */
    public void setConnectionUrl(String value) {
        this.connectionUrl = value;
    }


    /**
     * The Driver class. The driver is located by connector framework to connect to database.
     * Required configuration property, and should be validated
     */
    private String driver;

    /**
     * @return driver value
     */
    @ConfigurationProperty(order = 5)
    public String getDriver() {
        return this.driver;
    }

    /**
     * @param value
     */
    public void setDriver(String value) {
        this.driver = value;
    }

    /**
     * Database Login name. This login name is used to connect to database. The provided login name and password 
     * should have rights to insert/update/delete the rows in the configured identity holder table.
     * Required configuration property, and should be validated
     */
    private String login;

    /**
     * @return login value
     */
    @ConfigurationProperty(order = 6)
    public String getLogin() {
        return this.login;
    }

    /**
     * @param value
     */
    public void setLogin(String value) {
        this.login = value;
    }

    /**
     * Database access Password. This password is used to connect to database. The provided login name and password 
     * should have rights to insert/update/delete the rows in the configured identity holder table.
     * Required configuration property, and should be validated      
     */
    private GuardedString password;

    /**
     * @return password value
     */
    @ConfigurationProperty ( order=7, confidential=true )
    public GuardedString getPassword() {
        return this.password;
    }

    /**
     * @param value
     */
    public void setPassword(GuardedString value) {
        this.password = value;
    }

    /**
     * The new connection validation query. The query can be empty. Then the auto commit true/false 
     * command is applied by default. This can be unsufficient on some database drivers because of caching
     * Then the validation query is required.     
     */
    private String validConnectionQuery;

    /**
     * connection validation query getter
     * @return validConnectionQuery value
     */
    @ConfigurationProperty(order = 8)
    public String getValidConnectionQuery() {
        return this.validConnectionQuery;
    }

    /**
     * Connection validation query setter
     * @param value
     */
    public void setValidConnectionQuery(String value) {
        this.validConnectionQuery = value;
    }

    /**
     * Database Table name. The name of the identity holder table (Integration table). 
     * The quoting of the table name will be taken from NameQuote configuration attribute     
     */
    private String dbTable;

    /**
     * The table name
     * @return the user account table name
     * Please notice, there are used non default message keys
     */
    @ConfigurationProperty(order = 9, required=true, displayMessageKey="usersTable.display", helpMessageKey="usersTable.help")
    public String getDBTable() {
        return this.dbTable;
    }

    /**
     * @param dbTable value
     */
    public void setDBTable(String dbTable) {
        this.dbTable = dbTable;
    }

    /**
     * Key Column, The name of the key column is required
     * This non empty value must be validated
     */
    private String keyColumn;

    /**
     * Key Column getter
     * @return keyColumn value
     */
    @ConfigurationProperty(order = 10, required = true)
    public String getKeyColumn() {
        return this.keyColumn;
    }

    /**
     * Key Column setter
     * @param keyColumn value
     */
    public void setKeyColumn(String keyColumn) {
        this.keyColumn = keyColumn;
    }

    /**
     * Password Column. If non empty, password is supported in the schema
     * empty password column means, the password is not supported and also should not be in the schema    
     */
    private String passwordColumn;

    /**
     * Password Column getter
     * 
     * @return passwordColumn value
     */
    @ConfigurationProperty(order = 11)
    public String getPasswordColumn() {
        return this.passwordColumn;
    }

    /**
     * Password Column setter
     * 
     * @param value
     */
    public void setPasswordColumn(String value) {
        this.passwordColumn = value;
    }

    // =======================================================================
    // Password Encrypt Function
    // =======================================================================
    // private String passwordEncrypt;

    // public String getPasswordEncrypt() {
    // return this.passwordEncrypt;
    // }

    // public void setPasswordEncrypt(String value) {
    // this.passwordEncrypt = value;
    // }

    // =======================================================================
    // Password Decrypt Function
    // =======================================================================
    // private String passwordDecrypt;

    // public String getPasswordDecrypt() {
    // return this.passwordDecrypt;
    // }

    // public void setPasswordDecrypt(String value) {
    // this.passwordDecrypt = value;
    // }

    /**
     * How to quote a column in SQL statements. Possible values can be NONE, SINGLE, DOUBLE, BRACKETS, BACKSLASH
     * Inspect the #{@link QuoteType}
     */
    private String nameQuote;

    /**
     * NameQoute getter 
     * @return nameQuote value
     */
    @ConfigurationProperty(order = 12)
    public String getNameQuote() {
        return this.nameQuote;
    }

    /**
     * NameQuote Setter
     * @param value
     */
    public void setNameQuote(String value) {
        this.nameQuote = value;
    }

     /**
     * Generate UID. if true, new Uid (Name) will be generated and Name is not required attribute
     * if false, The uid after create will be name and name is required attribute   
     */
    private Boolean generateUid;

    /**
     * Generate UID getter method
     * @return true/false 
     */
    @ConfigurationProperty(order = 13)
    public Boolean getGenerateUid() {
        return this.generateUid;
    }

    /**
     * Generate UID setter method
     * @param value
     */
    public void setGenerateUid(Boolean value) {
        this.generateUid = value;
    }

    /**
     * Change Log Column (should automatically add ORDER BY)
     * If the value is non empty, the SyncOp should be supported
     * It could be nativeTimestamps.
     */
    private String changeLogColumn;

    /**
     * Log Column is required be SyncOp
     * @return Log Column 
     */
    @ConfigurationProperty(order = 14, operations = SyncOp.class)
    public String getChangeLogColumn() {
        return this.changeLogColumn;
    }

    /**
     * @param value
     */
    public void setChangeLogColumn(String value) {
        this.changeLogColumn = value;
    }

    // =======================================================================
    // DataSource
    // =======================================================================

    // =======================================================================
    // Configuration Interface
    // =======================================================================

    /**
     * Attempt to validate the arguments added to the Configuration.
     * 
     * @see org.identityconnectors.framework.Configuration#validate()
     */
    @Override
    public void validate() {
        // determine if you can get a connection to the database..
        Assertions.blankCheck(getKeyColumn(), "keyColumn");
        // check that there is a table to query..
        Assertions.blankCheck(getDBTable(), "dbTable");
        
        // check that there is not a datasource
        if(StringUtil.isBlank(getDatasource())){ 
            // determine if you can get a connection to the database..
            Assertions.nullCheck(getLogin(), "login");
            // check that there is a table to query..
            Assertions.nullCheck(getPassword(), "password");
            // check that there is a table to query..
            Assertions.blankCheck(getConnectionUrl(), "connectionUrl");    
            // make sure the driver is in the class path..
            Assertions.blankCheck(getDriver(), "driver");
            try {
                Class.forName(getDriver());
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            //Validate the JNDI properties
            JNDIUtil.arrayToHashtable(getJndiProperties(), getConnectorMessages());
        }
    }
}
