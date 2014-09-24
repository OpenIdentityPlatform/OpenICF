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
 * Portions Copyrighted 2013-2014 ForgeRock AS
 */
package org.identityconnectors.ldap;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
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
     * The login context if SASL-GSSAPI is used for authentication
     */
    private LoginContext loginContext = null;
    /**
     * The connection to the LDAP server.
     */
    private LdapConnection conn;
    
    private enum UpdateType {
        REPLACE, ADD, REMOVE
    }

    public LdapConnector() {
    }

    public Configuration getConfiguration() {
        return config;
    }

    public void init(Configuration cfg) {
        config = (LdapConfiguration) cfg;
        conn = new LdapConnection(config);

        if (LdapConnection.SASL_GSSAPI.equalsIgnoreCase(config.getAuthType())) {
            try {
                loginContext = new LoginContext(LdapConnector.class.getName());
                loginContext.login();

            } catch (LoginException le) {
                throw new ConfigurationException("Authentication attempt failed" + le);
            }
        } else {
        }
    }

    public void dispose() {
        conn.close();
    }

    public void test() {
        if (loginContext != null) {
            Subject.doAs(loginContext.getSubject(), new PrivilegedAction() {
                public Object run() {
                    doTest();
                    return null;
                }
            });
        } else {
            doTest();
        }
    }

    public void checkAlive() {
        if (loginContext != null) {
            Subject.doAs(loginContext.getSubject(), new PrivilegedAction() {
                public Object run() {
                    conn.checkAlive();
                    return null;
                }
            });
        } else {
            conn.checkAlive();
        }
    }

    public Schema schema() {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Schema>() {
                public Schema run() {
                    return conn.getSchemaMapping().schema();
                }
            });
        } else {
            return conn.getSchemaMapping().schema();
        }
    }

    public Uid authenticate(final ObjectClass objectClass, final String username, final GuardedString password, final OperationOptions options) {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Uid>() {
                public Uid run() {
                    return new LdapAuthenticate(conn, objectClass, username, options).authenticate(password);
                }
            });
        } else {
            return new LdapAuthenticate(conn, objectClass, username, options).authenticate(password);
        }
    }

    public Uid resolveUsername(final ObjectClass objectClass, final String username, final OperationOptions options) {
         if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Uid>() {
                public Uid run() {
                    return new LdapAuthenticate(conn, objectClass, username, options).resolveUsername();
                }
            });
        } else {
            return new LdapAuthenticate(conn, objectClass, username, options).resolveUsername();
        }
    }

    public FilterTranslator<LdapFilter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new LdapFilterTranslator(conn.getSchemaMapping(), objectClass);
    }

    public void executeQuery(final ObjectClass objectClass, final LdapFilter query, final ResultsHandler handler, final OperationOptions options) {
        if (objectClass.is(LdapUtil.SERVER_INFO_NAME)) {
            LdapUtil.getServerInfo(conn, handler);
        } else {
            if (loginContext != null) {
                Subject.doAs(loginContext.getSubject(), new PrivilegedAction() {
                    public Object run() {
                        new LdapSearch(conn, objectClass, query, handler, options).execute();
                        return null;
                    }
                });
            } else {
                new LdapSearch(conn, objectClass, query, handler, options).execute();
            }
        }
    }

    public Uid create(final ObjectClass objectClass, final Set<Attribute> attrs, final OperationOptions options) {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Uid>() {
                public Uid run() {
                    return new LdapCreate(conn, objectClass, attrs, options).execute();
                }
            });
        } else {
            return new LdapCreate(conn, objectClass, attrs, options).execute();
        }
    }

    public void delete(final ObjectClass objectClass, final Uid uid, OperationOptions options) {
        if (loginContext != null) {
            Subject.doAs(loginContext.getSubject(), new PrivilegedAction() {
                public Object run() {
                    new LdapDelete(conn, objectClass, uid).execute();
                    return null;
                }
            });
        } else {
            new LdapDelete(conn, objectClass, uid).execute();
        }
    }

    public Uid update(final ObjectClass objectClass, final Uid uid, final Set<Attribute> replaceAttributes, OperationOptions options) {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Uid>() {
                public Uid run() {
                    return new LdapUpdate(conn, objectClass, uid).update(replaceAttributes);
                }
            });
        } else {
            return new LdapUpdate(conn, objectClass, uid).update(replaceAttributes);
        }
    }

    public Uid addAttributeValues(final ObjectClass objectClass, final Uid uid, final Set<Attribute> valuesToAdd, OperationOptions options) {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Uid>() {
                public Uid run() {
                    return new LdapUpdate(conn, objectClass, uid).addAttributeValues(valuesToAdd);
                }
            });
        } else {
            return new LdapUpdate(conn, objectClass, uid).addAttributeValues(valuesToAdd);
        }
    }

    public Uid removeAttributeValues(final ObjectClass objectClass, final Uid uid, final Set<Attribute> valuesToRemove, OperationOptions options) {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<Uid>() {
                public Uid run() {
                    return new LdapUpdate(conn, objectClass, uid).removeAttributeValues(valuesToRemove);
                }
            });
        } else {
            return new LdapUpdate(conn, objectClass, uid).removeAttributeValues(valuesToRemove);
        }
    }

    public SyncToken getLatestSyncToken(final ObjectClass objectClass) {
        if (loginContext != null) {
            return Subject.doAs(loginContext.getSubject(), new PrivilegedAction<SyncToken>() {
                public SyncToken run() {
                    return lastSyncToken(objectClass);
                }
            });
        } else {
            return lastSyncToken(objectClass);
        }
    }

    public void sync(final ObjectClass objectClass, final SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        if (loginContext != null) {
            Subject.doAs(loginContext.getSubject(), new PrivilegedAction() {
                public Object run() {
                    doSync(objectClass, token, handler, options);
                    return null;
                }
            });
        } else {
            doSync(objectClass, token, handler, options);
        }
    }
    
    private SyncToken lastSyncToken(ObjectClass objectClass){
          if (config.isUseTimestampsForSync()) {
            return new TimestampsSyncStrategy(conn, objectClass).getLatestSyncToken();
        } else {
            switch (conn.getServerType()) {
                case UNKNOWN:
                case OPENLDAP:
                case MSAD_GC:
                    return new TimestampsSyncStrategy(conn, objectClass).getLatestSyncToken();
                case IBM:
                    return new IBMDSChangeLogSyncStrategy(conn, objectClass).getLatestSyncToken();
                case MSAD:
                case MSAD_LDS:
                    return new ActiveDirectoryChangeLogSyncStrategy(conn, objectClass).getLatestSyncToken();
                default:
                    return new SunDSChangeLogSyncStrategy(conn, objectClass).getLatestSyncToken();
            }
        }
    }
    
    private void doSync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, OperationOptions options) {
        if (config.isUseTimestampsForSync()) {
            new TimestampsSyncStrategy(conn, objectClass).sync(token, handler, options);
        } else {
            switch (conn.getServerType()) {
                case UNKNOWN:
                case OPENLDAP:
                case MSAD_GC:
                    new TimestampsSyncStrategy(conn, objectClass).sync(token, handler, options);
                    break;
                case IBM:
                    new IBMDSChangeLogSyncStrategy(conn, objectClass).sync(token, handler, options);
                    break;
                case MSAD:
                case MSAD_LDS:
                    new ActiveDirectoryChangeLogSyncStrategy(conn, objectClass).sync(token, handler, options);
                    break;
                default:
                    new SunDSChangeLogSyncStrategy(conn, objectClass).sync(token, handler, options);
            }
        }
    }
    
    private void doTest(){
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
        if (!badBC.isEmpty()) {
            throw new ConfigurationException("Bad Base Context(s): " + badBC.toString());
        }
        if (!badBCS.isEmpty()) {
            throw new ConfigurationException("Bad Base Context(s) to Synchronize: " + badBCS.toString());
        }
    }
}
