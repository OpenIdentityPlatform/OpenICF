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

import static org.identityconnectors.databasetable.DatabaseTableConstants.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.dbcommon.DatabaseQueryBuilder;
import org.identityconnectors.dbcommon.FilterWhereBuilder;
import org.identityconnectors.dbcommon.InsertIntoBuilder;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.dbcommon.UpdateSetBuilder;
import org.identityconnectors.dbcommon.DatabaseQueryBuilder.OrderBy;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;



/**
 * The database table {@link DatabaseTableConnector} is a basic, but easy to use
 * {@link DatabaseTableConnector} for accounts in a relational database.
 * <p>
 * It supports create, update, search, and delete operations. It can also be
 * used for pass-thru authentication, although it assumes the password is in
 * clear text in the database.
 * <p>
 * This connector assumes that all account data is stored in a single database
 * table. The delete action is implemented to simply remove the row from the
 * table.
 * <p>
 * 
 * @author Will Droste
 * @author Keith Yarbrough
 * @version $Revision $
 * @since 1.0
 */
@ConnectorClass(
        displayNameKey = "DBTABLE_CONNECTOR",
        configurationClass = DatabaseTableConfiguration.class)
public class DatabaseTableConnector implements PoolableConnector, CreateOp, SearchOp<FilterWhereBuilder>,
        DeleteOp, UpdateOp, SchemaOp, TestOp, AuthenticateOp, SyncOp {

    /**
     * Setup logging for the {@link DatabaseTableConnector}.
     */
    Log log = Log.getLog(DatabaseTableConnector.class);

    /**
     * Place holder for the {@link Connection} passed into the callback
     * {@link ConnectionFactory#setConnection(Connection)}.
     */
    private DatabaseTableConnection conn;

    /**
     * Place holder for the {@link Configuration} passed into the callback
     * {@link DatabaseTableConnector#init(Configuration)}.
     */
    private DatabaseTableConfiguration config;

    /**
     * Schema cache is used. The schema creation need a jdbc query.
     */
    private Schema schema;
    
    /**
     * Default attributes to get, created and cached from the schema
     */
    private Set<String> defaultAttributesToGet;
    
    /**
     * Same of the data types must be converted
     */
    private Map<String, Integer> columnSQLTypes;

    /**
     * Cached value for required columns 
     */
    private Set<String> stringColumnRequired;



    // =======================================================================
    // Initialize/dispose methods..
    // =======================================================================

    public Configuration getConfiguration() {
        return this.config;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     * 
     * @see org.identityconnectors.framework.spi.Connector#init(Configuration)
     */
    public void init(Configuration cfg) {
        this.config = (DatabaseTableConfiguration) cfg;
        this.conn = DatabaseTableConnection.getConnection(this.config);
        this.schema = null;
        this.defaultAttributesToGet = null;
        this.columnSQLTypes = null;
    }

    /**
     * @see org.identityconnectors.framework.spi.PoolableConnector#init(Configuration)
     */
    public void checkAlive() {
        conn.test();
    }
    
    /**
     * The connector connection access method
     * @return connection
     */
    DatabaseTableConnection getConnection() {
        return conn;
    }    

    /**
     * Disposes of the {@link DatabaseTableConnector}'s resources.
     * 
     * @see rg.identityconnectors.framework.spi.PoolableConnector#dispose()
     */
    public void dispose() {
        if ( conn != null ) {
            conn.dispose();
            conn = null;
        }
        this.defaultAttributesToGet = null;
        this.schema = null; 
        this.columnSQLTypes = null;
    }

    /**
     * Creates a row in the database representing an account.
     * 
     * @param obj the {@link ObjectClass} type (must be ACCOUNT )
     * @param attrs attributes to associate with required columns in the row.
     * @param options additional options. Additional options are not supported for this operation. 
     * @return the value that represents the account id for the row. The key
     *         column is used to determine the {@link Uid}.
     * @throws AlreadyExistsException if the user already exists on the target resource
     * @throws ConnectorException if an invalid attribute is specified
     *         
     * @see org.identityconnectors.framework.spi.operations.CreateOp#create(ObjectClass, Set, OperationOptions)
     */
    public Uid create(ObjectClass oclass, Set<Attribute> attrs, OperationOptions options) {
        if(oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED)); 
        }
        
        if(attrs == null || attrs.size() == 0) {
            throw new IllegalArgumentException(config.getMessage(MSG_INVALID_ATTRIBUTE_SET)); 
        }
        //Name must be present in attribute set or must be generated UID set on
        Name name = AttributeUtil.getNameFromAttributes(attrs);        
        if(name == null) {
            throw new IllegalArgumentException(config.getMessage(MSG_NAME_BLANK)); 
        } 
        String uidValue = name.getNameValue();

        final String tblname = config.getTable();
        // start the insert statement
        final InsertIntoBuilder bld = new InsertIntoBuilder();     
       
        log.info("Creating user: {0}", name.getNameValue());

        Set<String> missingRequiredColumns = CollectionUtil.newCaseInsensitiveSet();
        if(config.isEnableEmptyString()) {
            missingRequiredColumns.addAll(getStringColumnReguired()); 
        }
        
        //All attribute names should be in create columns statement 
        for (Attribute attr : attrs) {
            // quoted column name
            final String columnName = getColumnName(attr.getName());
            Object value = AttributeUtil.getSingleValue(attr);
            //Empty String
            if (isToBeEmprty(columnName, value)) {
                value = DatabaseTableConstants.EMPTY_STR;
            } 
            final int sqlType = getColumnType(columnName);
            bld.addBind(quoteName(columnName), new SQLParam(value, sqlType));
            missingRequiredColumns.remove(columnName);
        }
        
        // Bind empty string for not-null columns which are not in attribute set list
        if(config.isEnableEmptyString()) {
            for(String missing : missingRequiredColumns) {
                bld.addBind(quoteName(missing), new SQLParam(null, getColumnType(missing)));                               
            }            
        }
        
        final String SQL_INSERT = "INSERT INTO {0} ( {1} ) VALUES ( {2} )";
        // create the prepared statement..
        final String sql = MessageFormat.format(SQL_INSERT, tblname , bld.getInto(), bld.getValues() );

        PreparedStatement pstmt = null;
        try {
            pstmt = conn.prepareStatement(sql, bld.getParams());
            // execute the SQL statement
            pstmt.execute();
        } catch (SQLException e) {
            log.error(e, "Create user {0} error", name.getNameValue());
            if (throwIt(e.getErrorCode()) ) {            
                SQLUtil.rollbackQuietly(conn);
                throw ConnectorException.wrap(e);
            }            
        } finally {
            // clean up...
            SQLUtil.closeQuietly(pstmt);
        }
        conn.commit();
        log.ok("Created user: {0}", name.getNameValue());
        // create and return the uid..
        return new Uid(uidValue);
    }

    /**
     * Test to throw the exception
     * @param e exception
     * @return
     */
    private boolean throwIt(int errorCode) {
        return config.isRethrowAllSQLExceptions() || errorCode != 0;
    }

    /**
     * Test is value is null and must be emptz
     * @param columnName the column name
     * @param value the value to tests
     * @return true/false
     */
    private boolean isToBeEmprty(final String columnName, Object value) {
        return config.isEnableEmptyString() && getStringColumnReguired().contains(columnName) && value == null;
    }
    
    
    /**
     * Deletes a row from the table.
     * @param oclass the type of object to delete. Only ACCOUNT is supported.
     * @param uid the {@link Uid} of the user to delete
     * @param options additional options. Additional options are not supported for this operation. 
     * @throws UnknownUidException if the specified Uid does not exist on the target resource
     * @throws ConnectorException if a problem occurs with the connection     
     * 
     * @see DeleteOp#delete(ObjectClass, Uid, OperationOptions)
     */
    public void delete(final ObjectClass oclass, final Uid uid, final OperationOptions options) {
        final String SQL_DELETE = "DELETE FROM {0} WHERE {1} = ?";
        PreparedStatement stmt = null;
        // create the SQL string..

        if(oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED)); 
        }
        
        if(uid == null || (uid.getUidValue() == null)) {
            throw new IllegalArgumentException(config.getMessage(MSG_UID_BLANK)); 
        }  
        
        final String tblname = config.getTable();
        final String keycol = quoteName(config.getKeyColumn());        
        final String sql = MessageFormat.format(SQL_DELETE, tblname, keycol);
        try {
            log.info("SQL: {0}", sql);
            // create a prepared call..
            stmt = conn.getConnection().prepareStatement(sql);
            // set object to delete..
            stmt.setString(1, uid.getUidValue());
            // uid to delete..
            log.info("Deleting Uid: {0}", uid.getUidValue());
            if (1 != stmt.executeUpdate()) {
                throw new UnknownUidException();
            }
        } catch (SQLException e) {
            log.error(e, "Delete user {0} error", uid.getUidValue());
            SQLUtil.rollbackQuietly(conn);
            throw ConnectorException.wrap(e);
        } finally {
            // clean up..
            SQLUtil.closeQuietly(stmt);
        }
        conn.commit();
        log.ok("Uid deleted: {0}", uid.getUidValue());
    }

    /**
     * Update the database row w/ the data provided.
     * 
     * @param oclass the {@link ObjectClass} type (must be ACCOUNT )
     * @param attrs attributes. Attributes to associate with writable columns in the row.
     *              All Attributes must be valid according to the schema for this connector.    
     * @param options additional options. Additional options are not supported for this operation. 
     *              
     * @see UpdateOp#update(ObjectClass, Set, OperationOptions)
     */
    public Uid update(ObjectClass oclass, Uid uid, Set<Attribute> attrs, OperationOptions options) {
        final String SQL_TEMPLATE = "UPDATE {0} SET {1} WHERE {2} = ?";
        // create the sql statement..
        
        if (oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED));
        }

        if (attrs == null || attrs.size() == 0) {
            throw new IllegalArgumentException(config.getMessage(MSG_INVALID_ATTRIBUTE_SET));
        }
        
        Uid ret = uid;
        // The update is changing name. The oldUid is a key and the name will become new uid.
        Name name = AttributeUtil.getNameFromAttributes(attrs);
        if(name != null && uid.getUidValue() != name.getNameValue()) {
            ret = new Uid(name.getNameValue());
        }
        UpdateSetBuilder updateSet = new UpdateSetBuilder();
        for (Attribute attribute : attrs) {
            // All attributes needs to be updated except the UID
            if (!attribute.is(Uid.NAME)) {
                final String columnName = getColumnName(attribute.getName());
                Object value = AttributeUtil.getSingleValue(attribute);
                
                // Handle the empty string values
                if (isToBeEmprty(columnName, value)) {
                    value = DatabaseTableConstants.EMPTY_STR;
                }                
                final Integer sqlType = getColumnType(columnName);
                final SQLParam param = new SQLParam(value, sqlType);
                updateSet.addBind(quoteName(columnName), param);
            }
        }
        log.info("Update user {0} to {1}", uid.getUidValue(), ret.getUidValue());
        
        // Format the update query
        final String tblname = config.getTable();
        final String keycol = quoteName(config.getKeyColumn());
        updateSet.addValue(new SQLParam(uid.getUidValue(), getColumnType(config.getKeyColumn())));
        final String sql = MessageFormat.format(SQL_TEMPLATE, tblname ,updateSet.getSQL(), keycol );                
        PreparedStatement stmt = null;
        try {
            // create the prepared statement..
            stmt = conn.prepareStatement(sql, updateSet.getParams());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error(e, "Update user {0} error", name.getNameValue());
            if (throwIt(e.getErrorCode()) ) {            
                SQLUtil.rollbackQuietly(conn);
                throw ConnectorException.wrap(e);
            }
        } finally {
            // clean up..
            SQLUtil.closeQuietly(stmt);
        }
        // commit changes
        conn.commit();
        log.ok("Uid: {0} updated", ret.getUidValue());
        return ret;
    }    
    
    /**
     * Creates a Database Table filter translator.
     * 
     * @param objClass the type of object to delete. Only ACCOUNT is supported.
     * @param options additional options. 
     * 
     * @see FilterTranslator#createFilterTranslator(ObjectClass, OperationOptions )
     */
    public FilterTranslator<FilterWhereBuilder> createFilterTranslator(ObjectClass oclass, OperationOptions options) {
        if(oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED)); 
        }
        return new DatabaseTableFilterTranslator(this, oclass, options);
    }

    /**
     * Search for rows 
     * 
     * @param objClass the type of object to delete. Only ACCOUNT is supported.
     * @param where the SQL query where builder {@link FilterWhereBuilder}. Could be null for all rows.
     * @param handler the SQL query result handler {@link ResultsHandler}. Should not be null
     * @param options additional options. The attributeToGet are supported.  
     * 
     * @see SearchOp#executeQuery(ObjectClass, Object, ResultsHandler, OperationOptions)
     */
    public void executeQuery(ObjectClass oclass, FilterWhereBuilder where, ResultsHandler handler,
            OperationOptions options) {
 
        // Contract tests
        if (oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED));
        }

        if (handler == null) {
            throw new IllegalArgumentException(config.getMessage(MSG_RESULT_HANDLER_NULL));
        }
       
        //Names
        final String tblname = config.getTable();
        final Set<String> columnNamesToGet = resolveColumnNamesToGet(options);
        // For all user query there is no need to replace or quote anything
        final DatabaseQueryBuilder query = new DatabaseQueryBuilder(tblname, columnNamesToGet);
        query.setWhere(where);

        ResultSet result = null;
        PreparedStatement statement = null;
        log.info("executeQuery {0} on {1}", query.getSQL(), oclass);
        try {
            statement = conn.prepareStatement(query);
            result = statement.executeQuery();
            while (result.next()) {
                final Set<Attribute> attributeSet = conn.getAttributeSet(result);
                // create the connector object
                final ConnectorObjectBuilder bld = buildConnectorObject(attributeSet);
                if (!handler.handle(bld.build())) {
                    break;
                }
            }
        } catch (SQLException e) {
            log.error(e, "Query {0} on {1} error", query.getSQL(), oclass);    
            if (throwIt(e.getErrorCode()) ) {            
                throw ConnectorException.wrap(e);
            }             
        } finally {
            SQLUtil.closeQuietly(result);
            SQLUtil.closeQuietly(statement);
        }
    }


    /* (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SyncOp#sync(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.SyncToken, org.identityconnectors.framework.common.objects.SyncResultsHandler, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    public void sync(ObjectClass oclass, SyncToken token, SyncResultsHandler handler, OperationOptions options) {

        // Contract tests    
        if(oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED)); 
        } 
        if (handler == null) {
            throw new IllegalArgumentException(config.getMessage(MSG_RESULT_HANDLER_NULL));
        }
        //check if password column is defined in the config
        if (StringUtil.isBlank(config.getChangeLogColumn())) {
           throw new IllegalArgumentException(config.getMessage(MSG_CHANGELOG_COLUMN_BLANK));
        }                

        
        // Names
        final String tblname = config.getTable();
        final String changeLogColumnName = quoteName(config.getChangeLogColumn());
        final Set<String> columnNames = resolveColumnNamesToGet(options);
        final List<OrderBy> orderBy = new ArrayList<OrderBy>();
        //Add also the token column
        columnNames.add(changeLogColumnName);
        orderBy.add(new OrderBy(changeLogColumnName, true));

        // The first token is not null set the FilterWhereBuilder
        final FilterWhereBuilder where = new FilterWhereBuilder();
        if(token != null) {
            final Integer sqlType = getColumnType(config.getChangeLogColumn());
            where.addBind(changeLogColumnName, ">", new SQLParam(token.getValue(), sqlType));
        }
        final DatabaseQueryBuilder query = new DatabaseQueryBuilder(tblname, columnNames);
        query.setWhere(where);
        query.setOrderBy(orderBy);
        
        log.info("sync {0} on {1}", query.getSQL(), oclass);

        ResultSet result = null;
        PreparedStatement statement = null;
        try {
            statement = conn.prepareStatement(query);
            result = statement.executeQuery();
            while (result.next()) {
                final Set<Attribute> attributeSet = SQLUtil.getAttributeSet(result);
                // create the connector object..
                final SyncDeltaBuilder sdb = buildSyncDelta(attributeSet);
                if (!handler.handle(sdb.build())) {
                    break;
                }
            }
        } catch (SQLException e) {
            log.error(e, "sync {0} on {1} error", query.getSQL(), oclass);
            SQLUtil.rollbackQuietly(conn);
            throw ConnectorException.wrap(e);
        } finally {
            SQLUtil.closeQuietly(result);
            SQLUtil.closeQuietly(statement);
        }        
    }
    
    public SyncToken getLatestSyncToken(ObjectClass oclass) {

        final String SQL_SELECT = "SELECT MAX( {0} ) FROM {1}";
        //check if password column is defined in the config
        if (StringUtil.isBlank(config.getChangeLogColumn())) {
           throw new IllegalArgumentException(config.getMessage(MSG_CHANGELOG_COLUMN_BLANK));
        }                 
        // Format the update query
        final String tblname = config.getTable();
        final String chlogName = quoteName(config.getChangeLogColumn());
        final String sql = MessageFormat.format(SQL_SELECT , chlogName, tblname);
        SyncToken ret = null;
        
        log.info("getLatestSyncToken on {0}", oclass);               
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            // create the prepared statement..
            stmt = conn.getConnection().prepareStatement(sql);
            rset = stmt.executeQuery();
            if (rset.next()) {
            	Object value = rset.getObject(1);
            	if(value != null){
            		ret = new SyncToken(SQLUtil.jdbc2AttributeValue(value));
            	}
            }
            conn.commit();
            log.ok("getLatestSyncToken", ret);
            return ret;
        } catch (SQLException e) {  
            log.error(e, "getLatestSyncToken sql {0} on {1} error", sql, oclass);  
            SQLUtil.rollbackQuietly(conn);
            throw ConnectorException.wrap(e);
        } finally {
            // clean up..
            SQLUtil.closeQuietly(rset);
            SQLUtil.closeQuietly(stmt);
        }
        // commit changes
    }

    // =======================================================================
    // Schema..
    // =======================================================================
    /**
     * {@inheritDoc}
     * 
     * @see SchemaOp#schema()
     */
    public Schema schema() {
        if (schema == null) {
            cacheSchema();
        }
        assert schema != null;
        return schema;
    }

   
    /**
     * Test the configuration and connection
     * @see org.identityconnectors.framework.spi.operations.TestOp#test()
     */
    public void test() {
        conn.test();        
    }
        
    /**
     * Attempts to authenticate the given user/password combination.
     */
    public Uid authenticate(ObjectClass oclass, String username, GuardedString password,
            OperationOptions options) {

        final String SQL_AUTH_QUERY = "SELECT {0} FROM {1} WHERE ( {0} = ? ) AND ( {2} = ? )";
        
        if(oclass == null || (!oclass.equals(ObjectClass.ACCOUNT))) {
            throw new IllegalArgumentException(config.getMessage(MSG_ACCOUNT_OBJECT_CLASS_REQUIRED)); 
        }              
        if(StringUtil.isBlank(config.getPasswordColumn())) {
            throw new UnsupportedOperationException(config.getMessage(MSG_AUTHENTICATE_OP_NOT_SUPPORTED));
        }
        // determine if you can get a connection to the database..
        if (StringUtil.isBlank(username)) {
            throw new IllegalArgumentException(config.getMessage(MSG_USER_BLANK));
         }
        // check that there is a pwd to query..
        if (password == null) {
            throw new IllegalArgumentException(config.getMessage(MSG_PASSWORD_BLANK));
         }        
        //check if password column is defined in the config
        if (StringUtil.isBlank(config.getPasswordColumn())) {
           throw new IllegalArgumentException(config.getMessage(MSG_PWD_COLUMN_BLANK));
        }             

        
        log.info("authenticate user: {0}", username);
        String sql = MessageFormat.format(SQL_AUTH_QUERY, quoteName(config
                .getKeyColumn()), config.getTable(), quoteName(config.getPasswordColumn()));

        final List<SQLParam> values = new ArrayList<SQLParam>();
        values.add( new SQLParam(username, getColumnType(config.getKeyColumn()))); // real username
        values.add( new SQLParam(password)); // real password

        PreparedStatement stmt = null;
        ResultSet result = null;
        //No passwordExpired capability
        try {
            // replace the ? in the SQL_AUTH statement with real data
            stmt = conn.prepareStatement(sql, values);
            result = stmt.executeQuery();
            //No PasswordExpired capability
            if (!result.next()) {
                throw new InvalidCredentialException(config.getMessage(MSG_AUTH_FAILED, username)); 
            }
            final Uid uid = new Uid( result.getString(1));
            log.info("user: {0} authenticated ", username);
            return uid;
        } catch (SQLException e) {
            log.error(e, "user: {0} authentication failed ", username);
            SQLUtil.rollbackQuietly(conn);
            throw ConnectorException.wrap(e);            
        } finally {
            SQLUtil.closeQuietly(result);
            SQLUtil.closeQuietly(stmt);
        }
    }
    
    /**
     * Used to escape the table or column name.
     * @param value Value to be quoted
     * @return the quoted column name
     */
    public String quoteName(String value) {
        return DatabaseTableSQLUtil.quoteName(config.getQuoting(), value);
    }
        
    
    /**
     * The required type is cached
     * @param columnName
     * @return the className of the column
     */
    public int getColumnType(String columnName) {
        if (columnSQLTypes == null) {
            cacheSchema();
        }
     // no null here :)
        assert columnSQLTypes != null;
        Integer columnType = columnSQLTypes.get(columnName);
        if(columnType == null) {
           // throw new IllegalArgumentException("Invalid column name: "+columnName);
            columnType = Types.NULL;
        }
        return columnType;
    }
    

    /**
     * Convert the attribute name to resource specific columnName
     * 
     * @param attributeName
     * @return the Column Name value
     */
    public String getColumnName(String attributeName) {
        if(Name.NAME.equalsIgnoreCase(attributeName)) {
            return config.getKeyColumn();
        }
        if(Uid.NAME.equalsIgnoreCase(attributeName)) {
            return config.getKeyColumn();
        }
        if(!StringUtil.isBlank(config.getPasswordColumn()) && 
                OperationalAttributes.PASSWORD_NAME.equalsIgnoreCase(attributeName)) {
            return config.getPasswordColumn();
        }
        return attributeName;
    }

    
    /**
     * Cache schema, defaultAtributesToGet, columnClassNamens
     * 
     */
    private void cacheSchema() {
        /*
         * First, compute the account attributes based on the database schema
         */
        final Set<AttributeInfo> attrInfoSet = buildSelectBasedAttributeInfos();

        // Cache the attributes to get
        defaultAttributesToGet = new HashSet<String>();
        for (AttributeInfo info : attrInfoSet) {
            if (info.isReturnedByDefault()) {
                defaultAttributesToGet.add(info.getName());
            }
        }

        /*
         * Add any other operational attributes to the attrInfoSet
         */
        // attrInfoSet.add(OperationalAttributeInfos.ENABLE);
        
        /*
         * Use SchemaBuilder to build the schema. Currently, only ACCOUNT type is supported.
         */
        final SchemaBuilder schemaBld = new SchemaBuilder(getClass());

        final ObjectClassInfoBuilder ociB = new ObjectClassInfoBuilder();
        ociB.setType(ObjectClass.ACCOUNT_NAME);
        ociB.addAllAttributeInfo(attrInfoSet);

        final ObjectClassInfo oci = ociB.build();
        schemaBld.defineObjectClass(oci);

        /*
         * Note: AuthenticateOp, and all the 'SPIOperation'-s are by default added by Reflection API to the Schema.
         * 
         * See for details: SchemaBuilder.defineObjectClass() --> FrameworkUtil.getDefaultSupportedOperations()
         * ReflectionUtil.getAllInterfaces(connector); is the line that *does* acquire the implemented interfaces by the
         * connector class.
         */
        if (StringUtil.isBlank(config.getPasswordColumn())) { // remove the AuthenticateOp
            schemaBld.removeSupportedObjectClass(AuthenticateOp.class, oci);
        }

        if (StringUtil.isBlank(config.getChangeLogColumn())) { // remove the SyncOp
            schemaBld.removeSupportedObjectClass(SyncOp.class, oci);
        }

        schema = schemaBld.build();
    }

    /**
     * Get the schema using a SELECT query.
     * 
     * @return Schema based on a empty SELECT query.
     */
    private Set<AttributeInfo> buildSelectBasedAttributeInfos() {
        /**
         * Template for a empty query to get the columns of the table.
         */
        final String SCHEMA_QUERY = "SELECT * FROM {0} WHERE {1} IS NULL";

        Set<AttributeInfo> attrInfo;
        String sql = MessageFormat.format(SCHEMA_QUERY, config.getTable(), quoteName(config.getKeyColumn()));
        // check out the result etc..
        ResultSet rset = null;
        Statement stmt = null;
        try {
            // create the query..
            stmt = conn.getConnection().createStatement();

            rset = stmt.executeQuery(sql);
            // get the results queued..
            attrInfo = buildAttributeInfoSet(rset);
        } catch (SQLException ex) {
            log.error(ex, "buildSelectBasedAttributeInfo in SQL: {0}",sql);
            SQLUtil.rollbackQuietly(conn);
            throw ConnectorException.wrap(ex);
        } finally {
            SQLUtil.closeQuietly(rset);
            SQLUtil.closeQuietly(stmt);
        }
        return attrInfo;
    }

    /**
     * Return the set of AttributeInfo based on the database query meta-data. 
     * @param rset
     * @return
     * @throws SQLException  
     */
    private Set<AttributeInfo> buildAttributeInfoSet(ResultSet rset) throws SQLException {
        Set<AttributeInfo> attrInfo = new HashSet<AttributeInfo>();
        columnSQLTypes = CollectionUtil.<Integer>newCaseInsensitiveMap();
        stringColumnRequired = CollectionUtil.newCaseInsensitiveSet();
        ResultSetMetaData meta = rset.getMetaData();
        int count = meta.getColumnCount();
        for (int i = 1; i <= count; i++) {
            final String name = meta.getColumnName(i);
            final AttributeInfoBuilder attrBld = new AttributeInfoBuilder();
            final Integer columnType = meta.getColumnType(i);         
            columnSQLTypes.put(name, columnType);
            if (name.equalsIgnoreCase(config.getKeyColumn())) {
                // name attribute
                attrBld.setName(Name.NAME);
                //The generate UID make the Name attribute is nor required
                attrBld.setRequired(true);
                attrInfo.add(attrBld.build());
            } else if (name.equalsIgnoreCase(config.getPasswordColumn())) {
                // Password attribute
                attrInfo.add(OperationalAttributeInfos.PASSWORD);                
            } else if (name.equalsIgnoreCase(config.getChangeLogColumn())) {
                // skip changelog column from the schema. It is not part of the contract
            } else {
                // All other attributed taken from the table
                final Class<?> dataType = conn.getSms().getSQLAttributeType(columnType);
                attrBld.setType(dataType);
                attrBld.setName(name);
                final boolean required = meta.isNullable(i)==ResultSetMetaData.columnNoNulls;
                attrBld.setRequired(required);
                if(required && dataType.isAssignableFrom(String.class)) {
                    stringColumnRequired.add(name);
                }
                attrBld.setReturnedByDefault( isReturnedByDefault(dataType));
                attrInfo.add(attrBld.build());            
            }
        }
        return attrInfo;
    }

    /**
     * Decide if should be returned by default
     * Generally all byte arrays are not returned by default 
     * @param dataType the type of the attribute type
     * @return
     */
    private boolean isReturnedByDefault(final Class<?> dataType) {
        return byte[].class.equals(dataType) ? false : true;
    }    
    
    /**
     * Construct a connector object
     * <p>Taking care about special attributes</p>
     *  
     * @param attributeSet from the database table
     * @return ConnectorObjectBuilder object
     */
    private ConnectorObjectBuilder buildConnectorObject(Set<Attribute> attributeSet) {
        String uidValue = null;
        ConnectorObjectBuilder bld = new ConnectorObjectBuilder();
        for (Attribute attribute : attributeSet) {
            final String columnName = attribute.getName();
            final Object value = AttributeUtil.getSingleValue(attribute);
            // Map the special
            if (columnName.equalsIgnoreCase(config.getKeyColumn())) {
                if (value == null) {
                    String msg = "Name cannot be null.";
                    throw new IllegalArgumentException(msg);
                }
                uidValue = value.toString();
                bld.setName(uidValue);
            } else if (columnName.equalsIgnoreCase(config.getPasswordColumn())) {
                // No Password in the result object
            } else if (columnName.equalsIgnoreCase(config.getChangeLogColumn())) {
            	//No changelogcolumn attribute in the results
            } else {
                // TODO Check his fix is required. It seem to be invalid contract tests
                // if conndition to avoid the: java.lang.AssertionError: Attribute 'Attribute: {Name=AGE, Value=null}'
                // was neither removed nor its value set to null.
                if(value != null) { 
                    bld.addAttribute(AttributeBuilder.build(columnName, value));
                }
            }
        }

        // To be sure that uid and name are present for mysql
        if(uidValue == null) {
            throw new IllegalStateException("The uid value is missing in query");
        }
        // Add Uid attribute to object
        bld.setUid(new Uid(uidValue));
        // only deals w/ accounts..
        bld.setObjectClass(ObjectClass.ACCOUNT);
        return bld;
    }    

    
    /**
     * Construct a connector object
     * <p>Taking care about special attributes</p>
     *  
     * @param attributeSet from the database table
     * @return ConnectorObjectBuilder object
     */
    private SyncDeltaBuilder buildSyncDelta(Set<Attribute> attributeSet) {
        Object token = null;
        SyncDeltaBuilder bld = new SyncDeltaBuilder();
        Attribute attribute = AttributeUtil.find(config.getChangeLogColumn(), 
                attributeSet);
        if ( attribute != null ) {
            token = AttributeUtil.getSingleValue(attribute);
        }
        
        if ( token == null ) {
            token = 0L;
        }
        
        // To be sure that sync token is present
        if(token != null) {
            bld.setToken(new SyncToken(token));
        }  
        
        bld.setObject(buildConnectorObject(attributeSet).build());
        
        // only deals w/ updates
        bld.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
        return bld;
    }       


    /**
     * @param options
     * @return
     */
    private Set<String> resolveColumnNamesToGet(OperationOptions options) {
        Set<String> attributesToGet = getDefaultAttributesToGet();
        if (options != null && options.getAttributesToGet() != null) {
            attributesToGet = CollectionUtil.newSet(options.getAttributesToGet());
            attributesToGet.add(Uid.NAME); // Ensure the Uid colum is there
        } 
        // Replace attributes to quoted columnNames
        Set<String> columnNamesToGet = new HashSet<String>();
        for (String attributeName : attributesToGet) {
            final String columnName = getColumnName(attributeName);
            columnNamesToGet.add( quoteName(columnName));
        }
        return columnNamesToGet;
    }       
    
    /**
     * Get the default Attributes to get
     * @return the Set of default attribute names
     */
    private Set<String> getDefaultAttributesToGet() {
        if (defaultAttributesToGet == null) {
            cacheSchema();
        }
        assert defaultAttributesToGet != null;
        return defaultAttributesToGet;
    }      
    
    /**
     * Get the default Attributes to get
     * @return the Set of default attribute names
     */
    private Set<String> getStringColumnReguired() {
        if (stringColumnRequired == null) {
            cacheSchema();
        }
        assert stringColumnRequired != null;
        return stringColumnRequired;
    }       
    
}
