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

import static org.identityconnectors.databasetable.DatabaseTableConstants.MSG_CAN_NOT_READ;
import static org.identityconnectors.databasetable.DatabaseTableConstants.MSG_QUERY_INVALID;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.databasetable.mapping.AttributeConvertor;
import org.identityconnectors.databasetable.mapping.DefaultStrategy;
import org.identityconnectors.databasetable.mapping.JdbcConvertor;
import org.identityconnectors.databasetable.mapping.MappingStrategy;
import org.identityconnectors.databasetable.mapping.NativeTimestampsStrategy;
import org.identityconnectors.databasetable.mapping.StringStrategy;
import org.identityconnectors.dbcommon.DatabaseConnection;
import org.identityconnectors.dbcommon.JNDIUtil;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.spi.Configuration;

/**
 * Wraps JDBC connections extends the DatabaseConnection overriding the test method.
 */
public class DatabaseTableConnection extends DatabaseConnection {

    /**
     * Setup logging for the {@link DatabaseTableConnection}.
     */
    static Log log = Log.getLog(DatabaseTableConnection.class);

    /**
     * Get the instance method
     * 
     * @param config
     *            a {@link DatabaseTableConfiguration} object
     * @return a new {@link DatabaseTableConnection} connection
     */
    static DatabaseTableConnection createDBTableConnection(DatabaseTableConfiguration config) {
        Connection connection = getNativeConnection(config);
        return new DatabaseTableConnection(connection, config);
    }

    /**
     * @param config
     * @return
     */
    private static java.sql.Connection getNativeConnection(DatabaseTableConfiguration config) {
        java.sql.Connection connection;
        final String login = config.getUser();
        final GuardedString password = config.getPassword();
        final String datasource = config.getDatasource();
        if (StringUtil.isNotBlank(datasource)) {
            log.info("Get a new connection using datasource {0}", datasource);
            final String[] jndiProperties = config.getJndiProperties();
            final ConnectorMessages connectorMessages = config.getConnectorMessages();
            final Hashtable<String, String> prop = JNDIUtil.arrayToHashtable(jndiProperties, connectorMessages);
            if (StringUtil.isNotBlank(login) && password != null) {
                connection = SQLUtil.getDatasourceConnection(datasource, login, password, prop);
            } else {
                connection = SQLUtil.getDatasourceConnection(datasource, prop);
            }
            log.ok("The new connection using datasource {0} created", datasource);
        } else {
            final String driver = config.getJdbcDriver();
            final String connectionUrl = config.formatUrlTemplate();
            log.info("Get a new connection using connection url {0} and user {1}", connectionUrl, login);
            connection = SQLUtil.getDriverMangerConnection(driver, connectionUrl, login, password);
            log.ok("The new connection using connection url {0} and user {1} created", connectionUrl, login);
        }

        /* On Oracle enable the synonyms */
        try {
            Class<?> clazz = Class.forName("oracle.jdbc.OracleConnection");
            if (clazz != null && clazz.isAssignableFrom(connection.getClass())) {
                try {
                    final Method getIncludeSynonyms = clazz.getMethod("getIncludeSynonyms");
                    final Object includeSynonyms = getIncludeSynonyms.invoke(connection);
                    log.info("getIncludeSynonyms on ORACLE : {0}", includeSynonyms);
                    if (Boolean.FALSE.equals(includeSynonyms)) {
                        final Method setIncludeSynonyms = clazz.getMethod("setIncludeSynonyms", boolean.class);
                        setIncludeSynonyms.invoke(connection, Boolean.TRUE);
                        log.ok("setIncludeSynonyms to true success");
                    }
                } catch (Exception e) {
                    log.error(e, "setIncludeSynonyms on ORACLE exception");
                }
            }
        } catch (ClassNotFoundException e) {
            //expected
        }

        //Disable auto-commit mode
        try {
            if ( connection.getAutoCommit() ) {
                log.info("setAutoCommit(false)");
                connection.setAutoCommit(false);
            }
        } catch (SQLException expected) {
            //expected
            log.error(expected, "setAutoCommit(false) exception");
        }
        return connection;
    }

    /**
     * DefaultStrategy is a default jdbc attribute mapping strategy
     */
    private MappingStrategy sms = null;

    /**
     * Information from the {@link Configuration} can help determine how to test the viability of the {@link Connection}
     * .
     */
    final DatabaseTableConfiguration config;

    /**
     * Use the {@link Configuration} passed in to immediately connect to a database. If the {@link Connection} fails a
     * {@link RuntimeException} will be thrown.
     * 
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
        this.sms = createMappingStrategy(conn, config);
        log.ok("New DatabaseTableConnection for : {0}", config.getUser());
    }

    /**
     * The strategy utility
     * 
     * @param conn
     * @param config
     * @return the created strategy
     */
    public MappingStrategy createMappingStrategy(Connection conn, DatabaseTableConfiguration config) {
        log.info("Create: DefaultStrategy");
        log.info("Append: JdbcConvertor");
        // tail is always convert to jdbc and do the default statement
        MappingStrategy tail = new JdbcConvertor(new DefaultStrategy());
        if (!config.isAllNative()) {
            log.info("Append: StringStrategy");
            // Backward compatibility is to read and write as string all attributes which make sance to read 
            tail = new StringStrategy(tail);
            // Native timestamps will read as timestamp and convert to String
            if (config.isNativeTimestamps()) {
                log.info("Append: NativeTimestampsStrategy");
                tail = new NativeTimestampsStrategy(tail);
            }
        }
        // head is convert all attributes to acceptable type, if they are not already
        log.info("Append: AttributeConvertor");
        return new AttributeConvertor(tail);
    }

    /**
     * Get the Column Values map
     * 
     * @param result
     * @return the result of Column Values map
     * @throws SQLException
     */
    public Map<String, SQLParam> getColumnValues(ResultSet result) throws SQLException {
        return DatabaseTableSQLUtil.getColumnValues(sms, result);
    }

    /**
     * Accessor for the sms property
     * 
     * @return the sms
     */
    public MappingStrategy getSms() {
        return sms;
    }

    /**
     * Indirect call of prepareCall statement with mapped callable statement parameters
     * 
     * @param sql
     *            a <CODE>String</CODE> sql statement definition
     * @param params
     *            the bind parameter values
     * @return return a callable statement
     * @throws SQLException
     *             an exception in statement
     */
    @Override
    public CallableStatement prepareCall(final String sql, final List<SQLParam> params) throws SQLException {
        log.info("Prepare SQL Call : {0}", sql);
        final CallableStatement prepareCall = getConnection().prepareCall(sql);
        DatabaseTableSQLUtil.setParams(sms, prepareCall, params);
        log.ok("SQL Call statement ok");
        return prepareCall;
    }

    /**
     * Indirect call of prepare statement with mapped prepare statement parameters
     * 
     * @param sql
     *            a <CODE>String</CODE> sql statement definition
     * @param params
     *            the bind parameter values
     * @return return a prepared statement
     * @throws SQLException
     *             an exception in statement
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final List<SQLParam> params) throws SQLException {
        log.info("Prepare SQL Statement : {0}", sql);
        final PreparedStatement prepareStatement = getConnection().prepareStatement(sql);
        DatabaseTableSQLUtil.setParams(sms, prepareStatement, params);
        log.ok("SQL Statement ok");
        return prepareStatement;
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
            log.info("valid connection query is empty, test connection using default");
            super.test();
        } else {
            Statement stmt = null;
            try {
                stmt = getConnection().createStatement();
                log.info("test connection using {0}", sql);
                // valid queries will return a result set...
                if (!stmt.execute(sql)) {
                    // should have thrown if server was down don't get the
                    // ResultSet, we don't want it if we got to this point and
                    // the SQL was not a query, give a hint why we failed
                    throw new ConnectorException(config.getMessage(MSG_QUERY_INVALID, sql));                            
                }
                log.ok("connection is valid");                
            } catch (Exception ex) {
                // anything, not just SQLException
                // nothing to do, just invalidate the connection
                throw new ConnectorException(config.getMessage(MSG_CAN_NOT_READ, sql), ex);
            } finally {
                SQLUtil.closeQuietly(stmt);
            }
        }        
    }

    /**
     * Setter for the sms
     * 
     * @param sms
     *            the strategy
     */
    void setSms(MappingStrategy sms) {
        this.sms = sms;
    }
    
    /**
     * Close connection if pooled
     */
    void closeConnection() {
        if( getConnection() != null && StringUtil.isNotBlank(config.getDatasource()) /*&& this.conn.getConnection() instanceof PooledConnection */) {
            log.info("Close the pooled connection");
            dispose();
        }
    }
    /**
     * Create new connection if pooled and taken from the datasource
     * @throws SQLException
     */
    void openConnection() throws SQLException {
        if (getConnection() == null || getConnection().isClosed()) {
            log.info("Get new connection, it is closed");
            setConnection(getNativeConnection(config));
        }
    }    
}
