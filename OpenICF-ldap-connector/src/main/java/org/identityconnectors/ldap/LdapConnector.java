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
 * 
 * Portions Copyrighted 2013 Forgerock
 */
package org.identityconnectors.ldap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.naming.NamingException;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;
import org.identityconnectors.ldap.modify.LdapCreate;
import org.identityconnectors.ldap.modify.LdapDelete;
import org.identityconnectors.ldap.modify.LdapUpdate;
import org.identityconnectors.ldap.search.LdapFilter;
import org.identityconnectors.ldap.search.LdapFilterTranslator;
import org.identityconnectors.ldap.search.LdapSearch;
import org.identityconnectors.ldap.sync.activedirectory.ActiveDirectoryChangeLogSyncStrategy;
import org.identityconnectors.ldap.sync.ibm.IBMDSChangeLogSyncStrategy;
import org.identityconnectors.ldap.sync.sunds.SunDSChangeLogSyncStrategy;
import org.identityconnectors.ldap.sync.timestamps.TimestampsSyncStrategy;

@ConnectorClass(configurationClass = LdapConfiguration.class, displayNameKey = "LdapConnector")
public class LdapConnector implements TestOp, PoolableConnector, SchemaOp, SearchOp<LdapFilter>, AuthenticateOp, ResolveUsernameOp, CreateOp, DeleteOp,
        UpdateAttributeValuesOp, SyncOp {

    // XXX groups.

    /**
     * The configuration for this connector instance.
     */
    private LdapConfiguration config;

    /**
     * The connection to the LDAP server.
     */
    private LdapConnection conn;

    public LdapConnector() {
    }

    public Configuration getConfiguration() {
        return config;
    }

    public void init(Configuration cfg) {
        config = (LdapConfiguration) cfg;
        conn = new LdapConnection(config);
    }

    public void dispose() {
        conn.close();
    }

    public void test() {
        List<String> badBC = new ArrayList<String>();
        List<String> badBCS = new ArrayList<String>();
        config.validate();
        conn.test();
        // Need to check that all the base contexts are valid
        for (String context : config.getBaseContexts()) {
            try {
                conn.getInitialContext().getAttributes(context);
            } catch (NamingException e) {
                badBC.add(context);
            }
        }
        for (String context : config.getBaseContextsToSynchronize()) {
            try {
                conn.getInitialContext().getAttributes(context);
            } catch (NamingException e) {
                badBCS.add(context);
            }
        }
        if (!badBC.isEmpty()){
            throw new ConfigurationException("Bad Base Context(s): " + badBC.toString());
        }
        if (!badBCS.isEmpty()){
            throw new ConfigurationException("Bad Base Context(s) to Synchronize: " + badBCS.toString());
        }
        
        
    }

    public void checkAlive() {
        conn.checkAlive();
    }

    public Schema schema() {
        return conn.getSchemaMapping().schema();
    }

    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password, OperationOptions options) {
        return new LdapAuthenticate(conn, objectClass, username, options).authenticate(password);
    }

    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        return new LdapAuthenticate(conn, objectClass, username, options).resolveUsername();
    }

    public FilterTranslator<LdapFilter> createFilterTranslator(ObjectClass oclass, OperationOptions options) {
        return new LdapFilterTranslator(conn.getSchemaMapping(), oclass);
    }

    public void executeQuery(ObjectClass oclass, LdapFilter query, ResultsHandler handler, OperationOptions options) {
        new LdapSearch(conn, oclass, query, options).execute(handler);
    }

    public Uid create(ObjectClass oclass, Set<Attribute> attrs, OperationOptions options) {
        return new LdapCreate(conn, oclass, attrs, options).execute();
    }

    public void delete(ObjectClass oclass, Uid uid, OperationOptions options) {
        new LdapDelete(conn, oclass, uid).execute();
    }

    public Uid update(ObjectClass oclass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        return new LdapUpdate(conn, oclass, uid).update(replaceAttributes);
    }

    public Uid addAttributeValues(ObjectClass oclass, Uid uid, Set<Attribute> valuesToAdd, OperationOptions options) {
        return new LdapUpdate(conn, oclass, uid).addAttributeValues(valuesToAdd);
    }

    public Uid removeAttributeValues(ObjectClass oclass, Uid uid, Set<Attribute> valuesToRemove,
            OperationOptions options) {
        return new LdapUpdate(conn, oclass, uid).removeAttributeValues(valuesToRemove);
    }

    public SyncToken getLatestSyncToken(ObjectClass oclass) {
        if (config.isUseTimestampsForSync()) {
            return new TimestampsSyncStrategy(conn, oclass).getLatestSyncToken();
        } else {
            switch (conn.getServerType()) {
                case UNKNOWN:
                    return new TimestampsSyncStrategy(conn, oclass).getLatestSyncToken();
                case IBM:
                    return new IBMDSChangeLogSyncStrategy(conn, oclass).getLatestSyncToken();
                case MSAD:
                    return new ActiveDirectoryChangeLogSyncStrategy(conn, oclass).getLatestSyncToken();
                case MSAD_GC:
                    return new TimestampsSyncStrategy(conn, oclass).getLatestSyncToken();
                default:
                    return new SunDSChangeLogSyncStrategy(conn, oclass).getLatestSyncToken();
            }
        }
    }

    public void sync(ObjectClass oclass, SyncToken token, SyncResultsHandler handler, OperationOptions options) {
        if (config.isUseTimestampsForSync()) {
            new TimestampsSyncStrategy(conn, oclass).sync(token, handler, options);
        } else {
            switch (conn.getServerType()) {
                case UNKNOWN:
                    new TimestampsSyncStrategy(conn, oclass).sync(token, handler, options);
                    break;
                case IBM:
                    new IBMDSChangeLogSyncStrategy(conn, oclass).sync(token, handler, options);
                    break;
                case MSAD:
                    new ActiveDirectoryChangeLogSyncStrategy(conn, oclass).sync(token, handler, options);
                    break;
                case MSAD_GC:
                    new TimestampsSyncStrategy(conn, oclass).sync(token, handler, options);
                    break;
                default:
                    new SunDSChangeLogSyncStrategy(conn, oclass).sync(token, handler, options);
            }
        }
    }
}
