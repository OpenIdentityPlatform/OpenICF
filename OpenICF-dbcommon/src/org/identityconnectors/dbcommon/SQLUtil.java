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
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
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
     * Convert database type to connector supported set of attribute types
     * @param sqlType #{@link Types}
     * @return a connector supported class
     *
    public static Class<?> getAttributeDataType(int sqlType) {
        switch (sqlType) {
        //Known conversions
        case Types.DECIMAL:
            return BigDecimal.class;
        case Types.DOUBLE:
            return Double.class;
        case Types.FLOAT:
            return Float.class;
        case Types.REAL:
            return Float.class;
        case Types.INTEGER:
            return Integer.class;
        case Types.TINYINT:
            return Byte.class;
        case Types.BLOB:
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
            return byte[].class;
        case Types.BIGINT:
            return BigInteger.class;
        case Types.TIMESTAMP:
        case Types.DATE:
            return Long.class;
        case Types.BIT:
        case Types.BOOLEAN:
            return Boolean.class;
        }
        return String.class;
    }*/
    
    /**
     * Convert database type to connector supported set of attribute types
     * @param cname class name
     * @return a connector supported class
     */
    public static Class<?> getAttributeDataType(String cname) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(cname);
            //Do the conversion
            if (Blob.class.isAssignableFrom(clazz)) {
                // convert to base object..
                clazz = byte[].class;
            } else if (java.util.Date.class.isAssignableFrom(clazz)) {
                clazz = String.class;
            }            
            return clazz;
        } catch (ClassNotFoundException e) {
            throw ConnectorException.wrap(e);
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
            final Object object = resultSet.getObject(i);
            final Attribute attr = convertToAttribute(name, object);  
            ret.add(attr);
        }
        return ret;
    }

    /**
     * Convert a columns database type to attribute 
     * @param name a name of the attribute
     * @param value a value of a attribute
     * @return a attribute
     * @throws SQLException
     */
    static Attribute convertToAttribute(final String name, final Object value) throws SQLException {
        return AttributeBuilder.build(name, convertToSupportedType(value));
    }

    
    /**
     * Convert a columns database type to attribute 
     * @param value a value of a attribute
     * @throws SQLException
     * @return a attribute's supported object
     */
    public static Object convertToSupportedType(final Object value) throws SQLException {
        Object ret = null;
        if (value instanceof Blob) {
            ret = blobConversion((Blob) value);
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
     * Convert attribute types to database types
     * 
     * @param param
     *            the value to convert
     * @param clazzName
     *            expected class name
     * 
     * @return a converted value
     */
    public static Object convertToJDBC(Object param, String clazzName) {
        // check the conversion to is valid
        if (StringUtil.isBlank(clazzName)) {
            return param;
        }

        Class<?> clazz = null;
        try {
            clazz = Class.forName(clazzName);
        } catch (ClassNotFoundException expected) {
            // Could not convert
            final String msg = "Could not convert to class: " + clazz;
            log.ok(expected, msg);
            return param;
        }

        return convertToJDBC(param, clazz);
    }

    /**
     * Convert attribute types to database types
     * 
     * @param param
     *            the value to convert
     * @param expClass
     *            expected class
     * 
     * @return a converted value
     */
    public static Object convertToJDBC(Object param, Class<?> expClass) {
        // check the param null value
        if (param == null) {
            return param;
        }
        
        // check the conversion to is valid
        if (expClass == null) {
            return param;
        }        

        // Date, Timestamps conversion support for now
        if (expClass.equals(java.sql.Timestamp.class)) {
            return string2Timestamp((String) param);
        } else if (expClass.equals(java.sql.Date.class)) {
            return sring2Date((String) param);
        } else if (expClass.equals(java.sql.Time.class)) {
            return string2Time((String) param);
        } else if (expClass.equals(java.util.Date.class)) {
            return string2UtilDate((String) param);
        }
        return param;

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
    public static java.sql.Date sring2Date(String param) {
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
    private static java.sql.Timestamp string2Timestamp(String param) {
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
    protected static byte[] blobConversion(Blob blobValue) throws SQLException {
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
     * @throws SQLException an exception in statement
     */
    public static void setParams(final PreparedStatement statement, final List<Object> params) throws SQLException {
        if(statement == null || params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            final int idx = i + 1;
            setParam(statement, idx, params.get(i));
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
    static void setParams(final CallableStatement statement, final List<Object> params) throws SQLException {
        //The same as for prepared statements
        setParams( (PreparedStatement) statement, params);
    }    

    /**
     * Set the statement parameter
     * <p> It is ready for overloading if necessary</p>
     * @param stmt a <CODE>PreparedStatement</CODE> to set the params
     * @param idx an index of the parameter
     * @param val a parameter Value
     * @throws SQLException a SQL exception 
     */
    public static void setParam(final PreparedStatement stmt, final int idx, Object val) throws SQLException {
        // Guarded string conversion
        if (val instanceof GuardedString) {
            setGuardedStringParam(stmt, idx, (GuardedString) val);
        } else {
          stmt.setObject(idx, val);
        }       
    }

    /**
     * The helper guardedString bind method
     * @param stmt to bind to
     * @param idx index of the object
     * @param guard a <CODE>GuardedString</CODE> parameter
     * @throws SQLException
     */
    private static void setGuardedStringParam(final PreparedStatement stmt, final int idx, GuardedString guard)
            throws SQLException {
        try {
            guard.access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    try {
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
            setParams(st, Arrays.asList(params));
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
            setParams(st, Arrays.asList(params));
            return st.executeUpdate();
        }
        finally{
            closeQuietly(st);
        }
    }
    
    
    
}

