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
package org.identityconnectors.dbcommon;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.spi.Configuration;


/**
 * Implements the {@link org.identityconnectors.common.pooling.Connection} interface to wrap JDBC connections.
 * <p>Define a protected {@link #getDriverMangerConnection()} method for instantiating the connection. 
 * This method can be overloaded to create a more connector specific connection if needed </p>
 * 
 * @version $Revision $
 * @since 1.0
 */
public class DatabaseConnection  {
    /**
     * Setup logging for the {@link DatabaseConnection}.
     */
    private static final Log log = Log.getLog(DatabaseConnection.class);

    /**
     * Internal JDBC connection.
     */
    private Connection conn = null;
    
    /**
     * Test constructor 
     */
    DatabaseConnection() {
        //No body
    }    
    
    /**
     * Use the {@link Configuration} passed in to immediately connect to a database. If the {@link Connection} fails a
     * {@link RuntimeException} will be thrown.
     * @param conn a connection
     * 
     * @throws RuntimeException
     *             if there is a problem creating a {@link java.sql.Connection}.
     */
    public DatabaseConnection(Connection conn) {
        this.conn = conn;
    }

    /**
     * Closes the internal {@link java.sql.Connection}.
     * 
     * @see org.identityconnectors.framework.Connection#dispose()
     */
    public void dispose() {
        SQLUtil.closeQuietly(conn);
    }

    /**
     * Determines if the underlying JDBC {@link java.sql.Connection} is valid.
     * 
     * @see org.identityconnectors.framework.spi.Connection#test()
     * @throws RuntimeException
     *             if the underlying JDBC {@link java.sql.Connection} is not valid otherwise do nothing.
     */
    public void test() {
        try {
            // setAutoCommit() requires a connection to the server
            // in most cases. But some drivers may cache the autoCommit
            // value and only connect to the server if the value changes.
            // (namely DB2). So we have to actually change the value twice
            // and then set it back to the original value if the connection
            // is still valid. setAutoCommit() is very quick so 2 round
            // trips shouldn't be that bad.

            // This has the BAD side effect of actually causing preceding
            // partial transactions to be committed at this point. Also,
            // PostgreSQL apparently caches BOTH operations, so this still
            // does not work against that DB.
            getConnection().setAutoCommit(!getConnection().getAutoCommit());
            getConnection().setAutoCommit(!getConnection().getAutoCommit());
        } catch (Exception e) {
            // anything, not just SQLException
            // if the connection is not valid anymore,
            // a new one will be created, so there is no
            // need to set auto commit back to its original value
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Get the internal JDBC connection.
     * 
     * @return the connection
     */
    public Connection getConnection() {
        return this.conn;
    }

    /**
     * Indirect call of prepare statement
     * @param sql a <CODE>String</CODE> sql statement definition
     * @param params the bind parameter values
     * @return return a prepared statement
     * @throws SQLException an exception in statement
     */
    public PreparedStatement prepareStatement(final String sql, final List<Object> params) throws SQLException {
        final List<Object> out = new ArrayList<Object>();
        final String nomalized = SQLUtil.normalizeNullValues(sql, params, out);
        final PreparedStatement prepareStatement = getConnection().prepareStatement(nomalized);
        SQLUtil.setParams(prepareStatement, out);
        return prepareStatement;
    }
    
    
    /**
     * Indirect call of prepare statement
     * @param query DatabaseQueryBuilder query
     * @return return a prepared statement
     * @throws SQLException an exception in statement
     */
    public PreparedStatement prepareStatement(DatabaseQueryBuilder query) throws SQLException {
        return prepareStatement(query.getSQL(), query.getParams());
    }
        
    
    /**
     * Indirect call of prepareCall
     * @param sql a <CODE>String</CODE> sql statement definition
     * @param params the bind parameter values
     * @return return a callable statement
     * @throws SQLException an exception in statement
     */
    public CallableStatement prepareCall(final String sql, final List<Object> params) throws SQLException {
        final List<Object> out = new ArrayList<Object>();
        final String nomalized = SQLUtil.normalizeNullValues(sql, params, out);
        final CallableStatement prepareCall = getConnection().prepareCall(nomalized);
        SQLUtil.setParams(prepareCall, out);
        return prepareCall;
    }    

    /**
     * TODO: Plan to support DataSource in the near future. The advantage is the administrator can configure the
     * resource and it can use its own connection pooling.
     * 
     * @return
     */
    static Connection getDataSourceConnection() {
        return null;
    }
    
    
    /**
     * commit transaction
     */
    public void commit() {
        try {
            getConnection().commit();
        } catch (SQLException e) {
            SQLUtil.rollbackQuietly(getConnection());
            log.error(e, "error in commit");
            throw ConnectorException.wrap(e);
        }
    }
}
