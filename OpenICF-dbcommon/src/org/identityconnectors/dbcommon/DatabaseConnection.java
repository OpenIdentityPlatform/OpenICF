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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
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
     * @return
     */
    public Connection getConnection() {
        return this.conn;
    }

    /**
     * <p>
     * This method replaces the "?" markers in SQL statement with the parameters given as <i>values</i>. It
     * concentrates the replacement of all params. <code>GuardedString</code> are handled so the password is never
     * visible.
     * </p>
     * <p>
     * Note: the implementation directly uses java.sql.PreparedStatement .
     * </p>
     * 
     * @param sql a <CODE>String</CODE> sql statement definition
     * @param params a <CODE>List</CODE> of the object arguments
     * @return return a prepared statement
     * @throws SQLException an exception in statement
     */
    public PreparedStatement prepareStatement(final String sql, final List<Object> params) throws SQLException {
        final PreparedStatement statement = getConnection().prepareStatement(sql);
        if(params == null) {
            return statement;
        }
        for (int i = 0; i < params.size(); i++) {
            final int idx = i + 1;
            Object val = params.get(i);
            // Guarded string conversion
            if (val instanceof GuardedString) {
                setGuardedStringParam(statement, idx, (GuardedString) val);
            } else {
                setParam(statement, idx, val);
            }
        }
        return statement;
    }

    /**
     * Set the statement parameter
     * <p> It is ready for overloading if necessary</p>
     * @param stmt a <CODE>PreparedStatement</CODE> to set the params
     * @param idx an index of the parameter
     * @param param a parameter Value
     * @throws SQLException a SQL exception 
     */
    protected void setParam(final PreparedStatement stmt, final int idx, Object param) throws SQLException {
        stmt.setObject(idx, param);
    }

    /**
     * The helper guardedString bind method
     * @param stmt to bind to
     * @param idx index of the object
     * @param a <CODE>GuardedString</CODE> parameter
     * @throws SQLException
     */
    protected void setGuardedStringParam(final PreparedStatement stmt, final int idx, GuardedString guard)
            throws SQLException {
        try {
            guard.access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    try {
                        setParam(stmt, idx, new String(clearChars));
                    } catch (SQLException e) {
                        // checked exception are not allowed in the access method 
                        // Lets use the exception softening pattern
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (RuntimeException e) {
            // determine if there's a SQLException and re-throw that..
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw e;
        }
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
