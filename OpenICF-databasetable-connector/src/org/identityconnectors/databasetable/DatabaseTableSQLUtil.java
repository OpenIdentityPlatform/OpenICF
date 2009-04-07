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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.databasetable.mapping.MappingStrategy;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;


/**
 * The SQL helper/util class
 * @version $Revision 1.0$
 * @since 1.0
 */
public final class DatabaseTableSQLUtil {
    
    /**
     * Setup logging for the {@link DatabaseTableConnector}.
     */
    static final Log log = Log.getLog(DatabaseTableSQLUtil.class);
   
    /**
     * Never allow this to be instantiated.
     */
    private DatabaseTableSQLUtil() {
        throw new AssertionError();
    }
 
    /**
     * <p>
     * This method binds the "?" markers in SQL statement with the parameters given as <i>values</i>. It
     * concentrates the replacement of all params. <code>GuardedString</code> are handled so the password is never
     * visible.
     * </p>
     * @param sms 
     * @param statement
     * @param params a <CODE>List</CODE> of the object arguments
     * @param sqlTypes 
     * @throws SQLException an exception in statement
     */
    public static void setParams(final MappingStrategy sms, final PreparedStatement statement, final List<SQLParam> params) throws SQLException {
        if(statement == null || params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            final int idx = i + 1;
            final SQLParam parm = params.get(i);
            setParam(sms, statement, idx, parm);
        }
    }

    /**
     * <p>
     * This method binds the "?" markers in SQL statement with the parameters given as <i>values</i>. It
     * concentrates the replacement of all params. <code>GuardedString</code> are handled so the password is never
     * visible.
     * </p>
     * @param sms 
     * @param statement
     * @param params a <CODE>List</CODE> of the object arguments
     * @throws SQLException an exception in statement
     */
    public static void setParams(final MappingStrategy sms, final CallableStatement statement, final List<SQLParam> params) throws SQLException {
        //The same as for prepared statements
        setParams(sms, (PreparedStatement) statement, params);
    }    

    /**
     * Set the statement parameter
     * <p> It is ready for overloading if necessary</p>
     * @param sms 
     * @param scs 
     * @param stmt a <CODE>PreparedStatement</CODE> to set the params
     * @param idx an index of the parameter
     * @param parm a parameter Value
     * @throws SQLException a SQL exception 
     */
    static void setParam(final MappingStrategy sms, final PreparedStatement stmt, final int idx, SQLParam parm) throws SQLException {
        // Guarded string conversion
        if (parm.getValue() instanceof GuardedString) {
            setGuardedStringParam(sms, stmt, idx, (GuardedString) parm.getValue());
        } else {
          sms.setSQLParam(stmt, idx, parm);
        }       
    }

    /**
     * Read one row from database result set and convert a columns to attribute set.  
     * @param sms 
     * @param scs 
     * @param resultSet database data
     * @return The transformed attribute set
     * @throws SQLException 
     */
    public static Set<Attribute> getAttributeSet(final MappingStrategy sms, ResultSet resultSet) throws SQLException {
        Assertions.nullCheck(resultSet,"resultSet");
        Set<Attribute> ret = new HashSet<Attribute>();
        final ResultSetMetaData meta = resultSet.getMetaData();
        int count = meta.getColumnCount();
        for (int i = 1; i <= count; i++) {
            final String name = meta.getColumnName(i);
            final int sqlType = meta.getColumnType(i);
            final SQLParam param = sms.getSQLParam(resultSet, i, sqlType);
            final Attribute attr = AttributeBuilder.build(name, param.getValue());
            ret.add(attr);
        }
        return ret;
    }
    
    /**
     * The helper guardedString bind method
     * @param sms 
     * @param stmt to bind to
     * @param idx index of the object
     * @param guard a <CODE>GuardedString</CODE> parameter
     * @throws SQLException
     */
    static void setGuardedStringParam(final MappingStrategy sms, final PreparedStatement stmt, final int idx, GuardedString guard)
            throws SQLException {
        try {
            guard.access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    try {
                        //Never use setString, the DB2 database will fail for secured columns
                        sms.setSQLParam(stmt, idx, new SQLParam(new String(clearChars), Types.VARCHAR));
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
     * Used to escape the table or column name.
     * @param quoting the string double, single, back, brackets
     * @param value Value to be quoted
     * @return the quoted column name
     */
    public static String quoteName(String quoting, String value) {
        StringBuilder bld = new StringBuilder();
        if (StringUtil.isBlank(quoting) || "none".equalsIgnoreCase(quoting)) {
            bld.append(value);
        } else if ("double".equalsIgnoreCase(quoting)) {
            // for SQL Server, MySQL, NOT DB2, NOT Oracle, Postgresql
            bld.append('"').append(value).append('"');
        } else if ("single".equalsIgnoreCase(quoting)) {
            // for DB2, NOT Oracle, NOT SQL Server, NOT MySQL, ...
            bld.append('\'').append(value).append('\'');
        } else if ("back".equalsIgnoreCase(quoting)) {
            // for MySQL, NOT Oracle, NOT DB2, NOT SQL Server, ...
            bld.append('`').append(value).append('`');
        } else if ("brackets".equalsIgnoreCase(quoting)) {
            // MS SQL Server..
            bld.append('[').append(value).append(']');
        } else {
            throw new IllegalArgumentException();
        }
        return bld.toString();
    }
}

