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

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.databasetable.mapping.DefaulStrategy;
import org.identityconnectors.databasetable.mapping.MappingStrategy;
import org.identityconnectors.databasetable.mapping.NativeTimestampsStrategy;
import org.identityconnectors.databasetable.mapping.OracleStrategy;
import org.identityconnectors.databasetable.mapping.ReadStringStrategy;
import org.identityconnectors.databasetable.mapping.ToAttributeStrategy;
import org.identityconnectors.databasetable.mapping.ToJdbcStrategy;
import org.identityconnectors.databasetable.mapping.WriteStringStrategy;
import org.identityconnectors.dbcommon.DatabaseConnection;
import org.identityconnectors.dbcommon.JNDIUtil;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.spi.Configuration;


/**
 * Wraps JDBC connections extends the DatabaseConnection overriding the test method.
 */
public class DatabaseTableConnection extends DatabaseConnection {

    /**
     * Oracle class indicator
     */
    private static final CharSequence ORACLE = "oracle";


    /**
     * Information from the {@link Configuration} can help determine how to test
     * the viability of the {@link Connection}.
     */
    final DatabaseTableConfiguration config;
    

    /**
     * DefaulStrategy is a default jdbc attribute mapping strategy
     */
    private MappingStrategy sms = new ReadStringStrategy(new DefaulStrategy());


    /**
     * Is the connection from oracle database
     */
    private boolean oracleJDBC = false;    

    /**
     * Use the {@link Configuration} passed in to immediately connect to a
     * database. If the {@link Connection} fails a {@link RuntimeException} will
     * be thrown.
     * @param conn 
     *            Connection created in the time of calling the newConnection
     * @param config
     *            Configuration required to obtain a valid connection.
     * @throws RuntimeException
     *             if there is a problem creating a {@link java.sql.Connection}.
     */
    private DatabaseTableConnection(Connection conn, DatabaseTableConfiguration config) {
        super(conn);
        this.config = config;
        createMappingStrategy(conn, config);
        this.oracleJDBC = isUsingOracleJDBC(conn);
    }

    /**
     * Determines if the underlying JDBC {@link java.sql.Connection} is valid.
     *
     * @see org.identityconnectors.framework.spi.Connection#test()
     * @throws RuntimeException
     *             if the underlying JDBC {@link java.sql.Connection} is not
     *             valid otherwise do nothing.
     */
    @Override
    public void test() {
        String sql = config.getValidConnectionQuery();
        // attempt through auto commit..
        if (StringUtil.isBlank(sql)) {
            super.test();
        } else {
            Statement stmt = null;
            try {
                stmt = getConnection().createStatement();
                // valid queries will return a result set...
                if (!stmt.execute(sql)) {
                    // should have thrown if server was down don't get the
                    // ResultSet, we don't want it if we got to this point and
                    // the SQL was not a query, give a hint why we failed
                    throw new ConnectorException(
                            "Query must return a ResultSet: " + sql);
                }
            } catch (Exception ex) {
                // anything, not just SQLException
                // nothing to do, just invalidate the connection
                throw ConnectorException.wrap(ex);
            } finally {
                SQLUtil.closeQuietly(stmt);
            }
        }
    }
    

    /**
     * Indirect call of prepare statement with mapped prepare statement parameters
     * @param sql a <CODE>String</CODE> sql statement definition
     * @param params the bind parameter values
     * @return return a prepared statement
     * @throws SQLException an exception in statement
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final List<SQLParam> params) throws SQLException {
         final PreparedStatement prepareStatement = getConnection().prepareStatement(sql);
        DatabaseTableConnectorSQLUtil.setParams(sms, prepareStatement, params);
        return prepareStatement;
    }

    /**
     * Indirect call of prepareCall statement with mapped callable statement parameters
     * @param sql a <CODE>String</CODE> sql statement definition
     * @param params the bind parameter values
     * @return return a callable statement
     * @throws SQLException an exception in statement
     */
    @Override
    public CallableStatement prepareCall(final String sql, final List<SQLParam> params) throws SQLException {
        final CallableStatement prepareCall = getConnection().prepareCall(sql);
        DatabaseTableConnectorSQLUtil.setParams(sms, prepareCall, params);
        return prepareCall;
    }     

    /**
     * Get the instance method
     * 
     * @param config a {@link DatabaseTableConfiguration} object
     * @return a new {@link DatabaseTableConnection} connection
     */
    static DatabaseTableConnection getConnection(DatabaseTableConfiguration config) {
        java.sql.Connection connection;
        final String login = config.getUser();
        final GuardedString password = config.getPassword();
        final String datasource = config.getDatasource();
        if (StringUtil.isNotBlank(datasource)) {
            final String[] jndiProperties = config.getJndiProperties();
            final ConnectorMessages connectorMessages = config.getConnectorMessages();
            final Hashtable<String, String> prop = JNDIUtil.arrayToHashtable(jndiProperties, connectorMessages);                
            if(StringUtil.isNotBlank(login) && password != null) {
                connection = SQLUtil.getDatasourceConnection(datasource, login, password, prop);
            } else {
                connection = SQLUtil.getDatasourceConnection(datasource, prop);
            } 
        } else {
            final String driver = config.getJdbcDriver();
            final String connectionUrl = config.formatUrlTemplate();
            connection = SQLUtil.getDriverMangerConnection(driver, connectionUrl, login, password);
        }
        return new DatabaseTableConnection(connection, config);
    }

    /**
     * Get the attribute set
     * @param result
     * @return the result of attribute set generation
     * @throws SQLException 
     */
    public Set<Attribute> getAttributeSet(ResultSet result) throws SQLException {
       return DatabaseTableConnectorSQLUtil.getAttributeSet(sms, result);
    }
    
    
    /**
     * Test for oracle connection 
     * @param conn a non-null JDBC connection object
     * @return true if the connection is via an Oracle driver which supports getCLOB()
     * on its ResultSet
     * @throws IOException
     */
    public boolean isUsingOracleJDBC(Connection conn){
        
        // This implementation class needs to be from oracle
        if (!conn.getClass().getName().contains(ORACLE)) {
            return false;
        }
        
        final String SELECT_FROM_DUAL = "select 1 from dual";
        boolean ret = false;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(SELECT_FROM_DUAL);
            if (rs.next()) {
                ret = true;
            }
        } catch (Throwable expected) {
            //expected
        } finally {
            SQLUtil.closeQuietly(rs);
            rs = null;
            SQLUtil.closeQuietly(stmt);
            stmt = null;
        }
        return ret;
    }    
    

    /**
     * Accessor for the oracleJDBC property
     * @return the oracleJDBC
     */
    public boolean isOracleJDBC() {
        return oracleJDBC;
    }
    

    /**
     * Accessor for the sms property
     * @return the sms
     */
    public MappingStrategy getSms() {
        return sms;
    }

    /**
     * The strategy utility
     * @param conn
     * @param config
     */
    public void createMappingStrategy(Connection conn, DatabaseTableConfiguration config) {
        
        // tail is always convert to jdbc and do the default statement
        MappingStrategy tail = new ToJdbcStrategy(new DefaulStrategy());
        if(!config.isAllNative()) {
            // Backward compatibility is to read and write as string all attributes which make sance to read 
            tail = new ReadStringStrategy(new WriteStringStrategy(tail));
            
            if(oracleJDBC) {
                tail = new OracleStrategy(tail);
            }
            
            // Native timestamps will read as timestamp and convert to String
            if(config.isNativeTimestamps()) {
                tail = new NativeTimestampsStrategy(tail);
            }
            
            
        }
        // head is convert all attributes to acceptable type, if they are not already
        this.sms = new ToAttributeStrategy(tail);
    }    
}
