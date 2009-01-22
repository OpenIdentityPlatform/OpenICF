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
package org.identityconnectors.dbcommon;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.sql.DataSource;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;


/**
 * The SQL helper/util class
 * @version $Revision 1.0$
 * @since 1.0
 */
public final class SQLUtil {
    
    /**
     * Setup logging for the {@link DatabaseTableConnector}.
     */
    static final Log log = Log.getLog(SQLUtil.class);

    /**
     * Timestamp format
     */
    static final SimpleDateFormat TMS_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Time format
     */
    static final SimpleDateFormat TM_FMT = new SimpleDateFormat("hh:mm:ss");

    /**
     * Date format
     */
    static final SimpleDateFormat DT_FMT = new SimpleDateFormat("yyyy-MM-dd");

    
    /**
     * Never allow this to be instantiated.
     */
    private SQLUtil() {
        throw new AssertionError();
    }

    /**
     * Get the connection from the datasource
     * @param datasourceName 
     * @param env propertyHastable 
     * @return the connection get from default jndi context
     */
    public static Connection getDatasourceConnection(final String datasourceName, final Hashtable<?,?> env) {
        try {
            javax.naming.InitialContext ic = getInitialContext(env);
            DataSource ds = (DataSource)ic.lookup(datasourceName);
            return ds.getConnection();
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }   

    /**
     * Get the connection from the dataSource with specified user and password
     * @param datasourceName 
     * @param user DB user 
     * @param password DB password 
     * @param env propertyHastable 
     * @return the connection get from dataSource
     */
    public static Connection getDatasourceConnection(final String datasourceName,final String user,GuardedString password, final Hashtable<?,?> env) {        
        try {
            javax.naming.InitialContext ic = getInitialContext(env);
            final DataSource ds = (DataSource)ic.lookup(datasourceName);
            final Connection[] ret = new Connection[1];
            password.access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    try {
                        ret[0] = ds.getConnection(user,new String(clearChars));
                    } catch (SQLException e) {
                        // checked exception are not allowed in the access method 
                        // Lets use the exception softening pattern
                        throw new RuntimeException(e);
                    }
                }
            });
            return ret[0];
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }
    /**
     * Get the connection from the dataSource with specified user and password
     * @param datasourceName 
     * @param user DB user 
     * @param password DB password 
     * @param env propertyHastable 
     * @return the connection get from dataSource
     */
    public static Connection getDatasourceConnection(final String datasourceName,final String user,GuardedString password) {        
        return getDatasourceConnection(datasourceName, user, password, null);
    }    

    /**
     * Get the initial context method
     * @param env environment hastable is null or empty aware  
     * @return The Context
     * @throws NamingException
     */
    private static javax.naming.InitialContext getInitialContext(final Hashtable<?, ?> env) throws NamingException {
        javax.naming.InitialContext ic = null;
        if(env == null || env.size() == 0) {
            //Default context
            ic = new javax.naming.InitialContext();
        } else {
            ic = new javax.naming.InitialContext(env);
        }
        return ic;
    }
    
    /**
     * Get the connection from the datasource
     * @param datasourceName 
     * @return the connection get from default jndi context
     */
    public static Connection getDatasourceConnection(final String datasourceName) {
        return getDatasourceConnection(datasourceName, null);
    }        
    /**
     * Gets a {@link java.sql.Connection} using the basic driver manager.
     * @param driver jdbc driver name
     * @param url jdbc connection url
     * @param login jdbc login name
     * @param password jdbc password
     * @return a valid connection
     */
    public static Connection getDriverMangerConnection(final String driver, final String url, final String login,
            final GuardedString password) {
        // create the connection base on the configuration..
        final Connection[] ret = new Connection[1];
        try {
            // load the driver class..
            Class.forName(driver);
            // get the database URL..

            // check if there is authentication involved.
            if (StringUtil.isNotBlank(login)) {
                password.access(new GuardedString.Accessor() {
                    public void access(char[] clearChars) {
                        try {
                            ret[0] = DriverManager.getConnection(url, login, new String(clearChars));
                        } catch (SQLException e) {
                            // checked exception are not allowed in the access method 
                            // Lets use the exception softening pattern
                            throw new RuntimeException(e);
                        }
                    }
                });
            } else {
                ret[0] = DriverManager.getConnection(url);
            }
            // turn off auto-commit
            ret[0].setAutoCommit(false);
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
        return ret[0];
    }    
    
    /**
     * Ignores any exception thrown by the {@link Connection} parameter when
     * closed, it also checks for {@code null}.
     * 
     * @param conn
     *            JDBC connection to rollback.
     */
    public static void rollbackQuietly(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.rollback();
            }
        } catch (SQLException expected) {
            //expected
        }
    }  
    
    /**
     * Ignores any exception thrown by the {@link DatabaseConnection} parameter when
     * closed, it also checks for {@code null}.
     * 
     * @param conn
     *            DatabaseConnection to rollback.
     */
    public static void rollbackQuietly(DatabaseConnection conn) {
        if (conn != null) {
            rollbackQuietly(conn.getConnection());
        }
    }       

    /**
     * Ignores any exception thrown by the {@link Connection} parameter when
     * closed, it also checks for {@code null}.
     * 
     * @param conn
     *            JDBC connection to close.
     */
    public static void closeQuietly(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException expected) {
            //expected
        }
    }

    
    /**
     * Ignores any exception thrown by the {@link Connection} parameter when closed, 
     * it also checks for {@code null}.
     * 
     * @param conn
     *            DatabaseConnection to close.
     */
    public static void closeQuietly(DatabaseConnection conn) {
        if (conn != null) {
            closeQuietly(conn.getConnection());
        }
    }
        

    /**
     * Ignores any exception thrown by the {@link Statement#close()} method.
     * 
     * @param stmt
     *            {@link Statement} to close.
     */
    public static void closeQuietly(Statement stmt) {
        try {
            if (stmt != null) {
                stmt.close();
            }
        } catch (SQLException expected) {
            //expected
        }
    }

    /**
     * Closes the {@link ResultSet} and ignores any {@link Exception} that may
     * be thrown by the {@link ResultSet#close()} method.
     * 
     * @param rset
     *            {@link ResultSet} to close quitely.
     */
    public static void closeQuietly(ResultSet rset) {
        try {
            if (rset != null) {
                rset.close();
            }
        } catch (SQLException expected) {
            //expected
        }
    }

    /**
     * Date to String
     * @param value Date value
     * @return String value
     */
    public static String utilDate2String(final java.util.Date value) {
        final String ret = TMS_FMT.format(value);
        return ret;
    }
    
    /**
     * Date to string
     * @param value Date value
     * @return String value
     */
    public static String date2String(final java.sql.Date value) {
        final String ret = DT_FMT.format(value);
        return ret;
    }

    /**
     * Time to String format
     * @param value Time value
     * @return String value
     */
    public static String time2String(final java.sql.Time value) {      
        final String ret = TM_FMT.format(value);
        return ret;
    }

    /**
     * Convert timestamp to string
     * @param value <code>Timestamp</code> 
     * @return the string value
     */
    public static String timestamp2String(final java.sql.Timestamp value) {
        final String ret = TMS_FMT.format(value);
        return ret;
    }    

    /**
     * String to Date
     * @param param String value
     * @return Date value 
     */
    public static java.util.Date string2UtilDate(String param) {
        java.util.Date parsedDate;
        try {
            parsedDate = TMS_FMT.parse(param);
        } catch (ParseException e) {
            final DateFormat dfmt = DateFormat.getDateInstance();
            try {
                parsedDate = dfmt.parse(param);
            } catch (ParseException e1) {
                throw new IllegalArgumentException(e1);
            }
        }
        return new java.util.Date(parsedDate.getTime());
    }

    /**
     * String to Time
     * @param param String
     * @return the Time value
     */
    public static java.sql.Time string2Time(String param) {
        java.util.Date parsedDate;
        try {
            parsedDate = TM_FMT.parse(param);
        } catch (ParseException e) {
            final DateFormat dfmt = DateFormat.getTimeInstance();
            try {
                parsedDate = dfmt.parse(param);
            } catch (ParseException e1) {
                throw new IllegalArgumentException(e1);
            }
        }
        return new java.sql.Time(parsedDate.getTime());
    }

    /**
     * String to Date
     * @param param the String value
     * @return Date value
     */
    public static java.sql.Date string2Date(String param) {
        java.util.Date parsedDate;
        try {
            parsedDate = DT_FMT.parse(param);
        } catch (ParseException e) {
            final DateFormat dfmt = DateFormat.getDateTimeInstance();
            try {
                parsedDate = dfmt.parse(param);
            } catch (ParseException e1) {
                throw new IllegalArgumentException(e1);
            }
        }
        return new java.sql.Date(parsedDate.getTime());
    }

    /**
     * Convert string to Timestamp
     * @param param String value
     * @return Timestamp value
     */
    public static java.sql.Timestamp string2Timestamp(String param) {
        java.util.Date parsedDate;
        try {
            parsedDate = TMS_FMT.parse(param);
        } catch (ParseException e) {
            final DateFormat dfmt = DateFormat.getDateTimeInstance();
            try {
                parsedDate = dfmt.parse(param);
            } catch (ParseException e1) {
                throw new IllegalArgumentException(e1);
            }
        }
        return new java.sql.Timestamp(parsedDate.getTime());
    }


    /**
     * Convert String to boolean
     * @param val string value
     * @return Boolean value
     */
    private static Boolean string2Boolean(String val) {
        return Boolean.parseBoolean(val);
    }
    
    /**
     * The null param vlaue normalizator
     * @param sql
     * @param params list
     * @param out out param list
     * @return the modified string
     */
    public static String normalizeNullValues(final String sql, final List<Object> params, List<Object> out) {
        StringBuilder ret = new StringBuilder();
        int size = (params == null) ? 0 : params.size();
        //extend for extra space
        final String sqlext = sql+" ";
        String[] values = sqlext.split("\\?"); 
        if(values.length != (size + 1 )) throw new IllegalStateException("bind.params.count.not.same");
        for (int i = 0; i < values.length; i++) {
            String string = values[i];
            ret.append(string);
            if(params != null && i < params.size()) {
                if (params.get(i) == null) {
                  ret.append("null");
                } else {
                  ret.append("?");
                  out.add(params.get(i));
                }
            }
        }
        //return sql less the extra space
        return ret.substring(0, ret.length()-1);

    }    

    /**
     * Make a blob conversion
     * 
     * @param blobValue
     *            blob
     * @return a converted value
     * @throws SQLException
     */
    protected static byte[] blob2ByteArray(Blob blobValue) throws SQLException {
        byte[] newValue = null;

        // convert from Blob to byte[]
        InputStream is = blobValue.getBinaryStream();
        try {
            newValue = IOUtil.readInputStreamBytes(is, true);
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }

        return newValue;
    }
    
        
    

    /**
     * <p>
     * This method binds the "?" markers in SQL statement with the parameters given as <i>values</i>. It
     * concentrates the replacement of all params. <code>GuardedString</code> are handled so the password is never
     * visible.
     * </p>
     * 
     * @param statement
     * @param params a <CODE>List</CODE> of the object arguments
     * @param sqlTypes 
     * @throws SQLException an exception in statement
     */
    public static void setParams(final PreparedStatement statement, final List<Object> params, final List<Integer> sqlTypes) throws SQLException {
        if(statement == null || params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            final int idx = i + 1;
            final Object val = params.get(i);
            if(sqlTypes != null && i < sqlTypes.size()) {
                Integer sqlType = sqlTypes.get(i);
                if(sqlType == null) {
                    sqlType = Types.NULL;
                }
                setParam(statement, idx, val, sqlType);
            } else {
                setParam(statement, idx, val, Types.NULL);
            }
        }
    }

    /**
     * <p>
     * This method binds the "?" markers in SQL statement with the parameters given as <i>values</i>. It
     * concentrates the replacement of all params. <code>GuardedString</code> are handled so the password is never
     * visible.
     * </p>
     * 
     * @param statement
     * @param params a <CODE>List</CODE> of the object arguments
     * @param sqlTypes 
     * @throws SQLException an exception in statement
     */
    public static void setParams(final PreparedStatement statement, final List<Object> params) throws SQLException {
        if(statement == null || params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            final int idx = i + 1;
            setParam(statement, idx, params.get(i), Types.NULL);
        }
    }    
    
    /**
     * <p>
     * This method binds the "?" markers in SQL statement with the parameters given as <i>values</i>. It
     * concentrates the replacement of all params. <code>GuardedString</code> are handled so the password is never
     * visible.
     * </p>
     * 
     * @param statement
     * @param params a <CODE>List</CODE> of the object arguments
     * @throws SQLException an exception in statement
     */
    static void setParams(final CallableStatement statement, final List<Object> params, final List<Integer> sqlTypes) throws SQLException {
        //The same as for prepared statements
        setParams( (PreparedStatement) statement, params, sqlTypes);
    }    

    /**
     * Set the statement parameter
     * <p> It is ready for overloading if necessary</p>
     * @param stmt a <CODE>PreparedStatement</CODE> to set the params
     * @param idx an index of the parameter
     * @param val a parameter Value
     * @param sqlType 
     * @throws SQLException a SQL exception 
     */
    public static void setParam(final PreparedStatement stmt, final int idx, Object val, int sqlType) throws SQLException {
        // Guarded string conversion
        if (val instanceof GuardedString) {
            setGuardedStringParam(stmt, idx, (GuardedString) val);
        } else {
          setSQLParam(stmt, idx, val, sqlType);
        }       
    }

    /**
     * Read one row from database result set and convert a columns to attribute set.  
     * @param resultSet database data
     * @return The transformed attribute set
     * @throws SQLException 
     */
    public static Set<Attribute> getAttributeSet(ResultSet resultSet) throws SQLException {
        Assertions.nullCheck(resultSet,"resultSet");
        Set<Attribute> ret = new HashSet<Attribute>();
        final ResultSetMetaData meta = resultSet.getMetaData();
        int count = meta.getColumnCount();
        for (int i = 1; i <= count; i++) {
            final String name = meta.getColumnName(i);
            final int sqlType = meta.getColumnType(i);
            final Object param = getSQLParam(resultSet, i, sqlType);
            final Object value = jdbc2Attribute(param);
            final Attribute attr = AttributeBuilder.build(name, value);
            ret.add(attr);
        }
        return ret;
    }

    /**
     * Retrieve the SQL value from result set
     * @param resultSet the result set
     * @param i index
     * @param sqlType expected SQL type or  Types.NULL for generic
     * @return the object return the retrieved object
     * @throws SQLException any SQL error
     */
    static Object getSQLParam(ResultSet resultSet, int i, final int sqlType) throws SQLException {
        Object object;
        switch (sqlType) {
        //Known conversions
        case Types.NULL:
            object = resultSet.getObject(i);
            break;        
        case Types.DECIMAL:
        case Types.NUMERIC:
                object = resultSet.getBigDecimal(i);
                break;
        case Types.DOUBLE:
//            object = resultSet.getDouble(i); double does not support update to null
            object = resultSet.getObject(i);
            break;
        case Types.FLOAT:
        case Types.REAL:
//            object = resultSet.getFloat(i); float does not support update to null
            object = resultSet.getObject(i);
            break;
        case Types.INTEGER:
        case Types.BIGINT:
//            object = resultSet.getInt(i); int does not support update to null
            object = resultSet.getObject(i);
           break;
        case Types.TINYINT:
            object = resultSet.getByte(i);
            break;
        case Types.BLOB:
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
            object = resultSet.getObject(i);
            break;
        case Types.TIMESTAMP:
            object = resultSet.getTimestamp(i);
            break;
        case Types.DATE:
            object = resultSet.getDate(i);
            break;
        case Types.TIME:
            object = resultSet.getTime(i);
            break;
        case Types.BIT:
        case Types.BOOLEAN:
            object = resultSet.getBoolean(i);
            break;
        default:   
            object = resultSet.getString(i);
        }
        return object;
    }
    
    /**
     * Convert database type to connector supported set of attribute types
     * @param sqlType #{@link Types}
     * @return a connector supported class
     */
    public static Class<?> getSQLAttributeType(int sqlType) {
        switch (sqlType) {
        //Known conversions
        case Types.DECIMAL:
        case Types.NUMERIC:
            return BigDecimal.class;
        case Types.DOUBLE:
            return Double.class;
        case Types.FLOAT:
        case Types.REAL:
            return Float.class;
        case Types.INTEGER:
            return Integer.class;
        case Types.BIGINT:
            return Long.class;
        case Types.TINYINT:
            return Byte.class;
        case Types.BLOB:
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
            return byte[].class;
        case Types.TIMESTAMP:
        case Types.DATE:
        case Types.TIME:
            return String.class;
        case Types.BIT:
        case Types.BOOLEAN:
            return Boolean.class;
        }
        return String.class;
    }
    
    /**
     * Convert database type to connector supported set of attribute types
     * @param stmt 
     * @param idx 
     * @param val 
     * @param sqlType #{@link Types}
     * @throws SQLException 
     */
    public static void setSQLParam(final PreparedStatement stmt, final int idx, Object val, int sqlType) throws SQLException {
        log.info("setStmtParam {0} to value {1} for sqlType {2}", idx, val, sqlType);
        // Handle the null value
        if( val == null ) {
            stmt.setNull(idx, sqlType);
            return;
        }
        switch (sqlType) {
        //Known conversions
        case Types.NULL:
            stmt.setObject(idx, val);
            break;
        case Types.DECIMAL:
        case Types.NUMERIC:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.REAL:
            if(val instanceof BigDecimal) {
                stmt.setBigDecimal(idx, (BigDecimal) val);
            } else if(val instanceof Double) {
                stmt.setDouble(idx, (Double) val);
            } else if(val instanceof Float) {
                stmt.setFloat(idx, (Float) val);
            } else if(val instanceof Double) {
                stmt.setDouble(idx, (Double) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.INTEGER:
        case Types.BIGINT:
            if(val instanceof Integer) {
                stmt.setInt(idx, (Integer) val);
            } else if (val instanceof Long) {
                stmt.setLong(idx, (Long) val);
            } else if (val instanceof BigInteger) {
                stmt.setLong(idx, ((BigInteger) val).longValue());
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.TINYINT:
            if(val instanceof Byte) {
                stmt.setByte(idx, (Byte) val);
            } else if(val instanceof Integer) {
                stmt.setInt(idx, (Integer) val);
            } else if (val instanceof Long) {
                stmt.setLong(idx, (Long) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.BLOB:
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
            if(val instanceof InputStream) {
                stmt.setBinaryStream(idx, (InputStream) val, 10000);
            } else if(val instanceof Blob) {
                stmt.setBlob(idx, (Blob) val);
            } else if(val instanceof byte[]) {
                stmt.setBytes(idx, (byte[]) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.TIMESTAMP:
            if( val instanceof String) {
                stmt.setTimestamp(idx, string2Timestamp((String) val));
            } else if (val instanceof Timestamp) {
                stmt.setTimestamp(idx, (Timestamp) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.DATE:
            if( val instanceof String) {
                stmt.setDate(idx, string2Date((String) val));
            } else if (val instanceof java.sql.Date) {
                stmt.setDate(idx, (java.sql.Date) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.TIME:
            if( val instanceof String) {
                stmt.setTime(idx, string2Time((String) val));
            } else if (val instanceof java.sql.Time) {
                stmt.setTime(idx, (java.sql.Time) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        case Types.BIT:
        case Types.BOOLEAN:
            if( val instanceof String) {
                stmt.setBoolean(idx, string2Boolean((String) val));
            } else if (val instanceof java.sql.Time) {
                stmt.setBoolean(idx, (Boolean) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        default:    
            if( val instanceof String) {
                stmt.setString(idx, (String) val);
            } else {
                stmt.setObject(idx, val);
            }
            break;
        }
    }

    /**
     * Convert a columns database type to attribute 
     * @param value a value of a attribute
     * @param sqlType 
     * @throws SQLException
     * @return a attribute's supported object
     */
    public static Object jdbc2Attribute(final Object value) throws SQLException {
        Object ret = null;
        if (value instanceof Blob) {
            ret = blob2ByteArray((Blob) value);
        } else if (value instanceof BigInteger) {
            ret = ((BigInteger) value).longValue();
        } else if (value instanceof java.sql.Timestamp) {
            ret = timestamp2String((java.sql.Timestamp) value);
        } else if (value instanceof java.sql.Time) {
            ret = time2String((java.sql.Time) value);
        } else if (value instanceof java.sql.Date) {
            ret = date2String((java.sql.Date) value);
        } else if (value instanceof java.util.Date) {
            ret = utilDate2String((java.util.Date) value);
        } else {
            ret = value;
        }
        return ret;
    }   
    
    /**
     * The helper guardedString bind method
     * @param stmt to bind to
     * @param idx index of the object
     * @param guard a <CODE>GuardedString</CODE> parameter
     * @throws SQLException
     */
    public static void setGuardedStringParam(final PreparedStatement stmt, final int idx, GuardedString guard)
            throws SQLException {
        try {
            guard.access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    try {
                        //Never use setString, the DB2 database will fail for secured columns
                        stmt.setObject(idx, new String(clearChars));
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
     * Selects single value (first column) from select.
     * It fetches only first row, does not check whether more rows are returned by select
     * @param conn JDBC connection
     * @param sql Select statement with or without parameters
     * @param params Parameters to use in statement
     * @return first row and first column value 
     * @throws SQLException
     */
    public static Object selectFirstRowFirstValue(Connection conn, String sql, Object ...params) throws SQLException{
        PreparedStatement st = null;
        ResultSet rs = null;
        try{
            st = conn.prepareStatement(sql);
            setParams(st, Arrays.asList(params), null);
            rs = st.executeQuery(sql);
            Object value = null;
            if(rs.next()){
                value = rs.getObject(1);
                return value;
            }
            else{
                throw new IllegalStateException("No row found");
            }
        }
        finally{
            closeQuietly(rs);
            closeQuietly(st);
        }
    }
    
    /**
     * Executes DML sql statement. This can be useful to execute insert/update/delete or some
     * database specific statement in one call
     * @param conn
     * @param sql
     * @param params
     * @return number of rows affected as defined by {@link PreparedStatement#executeUpdate()}
     * @throws SQLException
     */
    public static int executeUpdateStatement(Connection conn, String sql, Object ...params) throws SQLException{
        PreparedStatement st = null;
        try{
            st = conn.prepareStatement(sql);
            setParams(st, Arrays.asList(params), null);
            return st.executeUpdate();
        }
        finally{
            closeQuietly(st);
        }
    }

}

