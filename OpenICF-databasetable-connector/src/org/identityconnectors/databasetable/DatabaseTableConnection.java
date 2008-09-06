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
package org.identityconnectors.databasetable;

import java.sql.Connection;
import java.sql.Statement;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.dbcommon.DatabaseConnection;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.spi.Configuration;


/**
 * Implements the {@link Connection} interface to wrap JDBC connections.
 */
public class DatabaseTableConnection extends DatabaseConnection {

    /**
     * Information from the {@link Configuration} can help determine how to test
     * the viability of the {@link Connection}.
     */
    final DatabaseTableConfiguration config;

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
    public DatabaseTableConnection(Connection conn, DatabaseTableConfiguration config) {
        super(conn);
        this.config = config;
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
}
