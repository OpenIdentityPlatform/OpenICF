/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.ldap.sync.timestamps;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.PagedResultsControl;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import static org.identityconnectors.ldap.ADLdapUtil.fetchGroupMembersByRange;
import static org.identityconnectors.ldap.ADLdapUtil.objectGUIDtoString;
import org.identityconnectors.ldap.LdapConnection;
import static org.identityconnectors.ldap.LdapUtil.buildMemberIdAttribute;
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.LdapConnection.ServerType;
import org.identityconnectors.ldap.LdapConstants;
import org.identityconnectors.ldap.search.DefaultSearchStrategy;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.LdapSearchStrategy;
import org.identityconnectors.ldap.search.LdapSearchResultsHandler;
import org.identityconnectors.ldap.search.SimplePagedSearchStrategy;
import org.identityconnectors.ldap.sync.LdapSyncStrategy;

/**
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */
/**
 * An implementation of the sync operation based on the generic timestamps
 * attribute present in any LDAP directory.
 */
public class TimestampsSyncStrategy implements LdapSyncStrategy {

    private final String createTimestamp = "createTimestamp";
    private final String modifyTimestamp = "modifyTimestamp";
    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final ServerType server;
    private static final Log logger = Log.getLog(TimestampsSyncStrategy.class);

    public TimestampsSyncStrategy(LdapConnection conn, ObjectClass oclass) {
        this.conn = conn;
        this.oclass = oclass;
        this.server = conn.getServerType();
    }

    public SyncToken getLatestSyncToken() {
        return new SyncToken(getNowTime());
    }

    public void sync(SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        // ldapsearch -h host -p 389 -b "dc=example,dc=com" -D "cn=administrator,cn=users,dc=example,dc=com" -w xxx "whenchanged>=20130214130642.0Z"
        // on AD
        // ldapsearch -h host -p 389 -b 'dc=example,dc=com' -S modifytimestamp -D 'cn=directory manager' -w xxx "createTimestamp>=20120424080554Z"
        // on other directories

        final String now = getNowTime();
        LdapSearchStrategy strategy = null;
        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setDerefLinkFlag(false);
        controls.setReturningAttributes(new String[]{"*", createTimestamp, modifyTimestamp,conn.getConfiguration().getUidAttribute()});
        
        if (conn.getConfiguration().isUseBlocks() && conn.supportsControl(PagedResultsControl.OID)) {
            strategy = new SimplePagedSearchStrategy(conn.getConfiguration().getBlockSize());
        } else {
            strategy = new DefaultSearchStrategy(false);
        }

        LdapInternalSearch search = new LdapInternalSearch(conn,
                generateFilter(oclass, token),
                Arrays.asList(conn.getConfiguration().getBaseContextsToSynchronize()),
                strategy, controls);

        try {
            search.execute(new LdapSearchResultsHandler() {
                public boolean handle(String baseDN, SearchResult result) throws NamingException {
                    Attributes attrs = result.getAttributes();
                    Uid uid = conn.getSchemaMapping().createUid(conn.getConfiguration().getUidAttribute(), attrs);
                    // build the object first
                    ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                    cob.setUid(uid);
                    cob.setObjectClass(oclass);
                    cob.setName(result.getNameInNamespace());

                    // Let's process AD specifics...
                    if (ServerType.MSAD_GC.equals(server) || ServerType.MSAD.equals(server) || ServerType.MSAD_LDS.equals(server)) {
                        if (ObjectClass.ACCOUNT.equals(oclass)) {
                            if (ServerType.MSAD_LDS.equals(server)) {
                                if (attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_DISABLED) != null) {
                                    cob.addAttribute(AttributeBuilder.buildEnabled(!Boolean.parseBoolean(attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_DISABLED).get().toString())));
                                } else if (attrs.get(LdapConstants.MS_DS_USER_PASSWORD_EXPIRED) != null) {
                                    cob.addAttribute(AttributeBuilder.buildPasswordExpired(Boolean.parseBoolean(attrs.get(LdapConstants.MS_DS_USER_PASSWORD_EXPIRED).get().toString())));
                                } else if (attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_AUTOLOCKED) != null) {
                                    cob.addAttribute(AttributeBuilder.buildLockOut(Boolean.parseBoolean(attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_AUTOLOCKED).get().toString())));
                                }
                            } else {
                                javax.naming.directory.Attribute uac = attrs.get(ADUserAccountControl.MS_USR_ACCT_CTRL_ATTR);
                                if (uac != null) {
                                    String controls = uac.get().toString();
                                    cob.addAttribute(AttributeBuilder.buildEnabled(!ADUserAccountControl.isAccountDisabled(controls)));
                                    cob.addAttribute(AttributeBuilder.buildLockOut(ADUserAccountControl.isAccountLockOut(controls)));
                                    cob.addAttribute(AttributeBuilder.buildPasswordExpired(ADUserAccountControl.isPasswordExpired(controls)));
                                }
                            }
                        }
                        if (ObjectClass.GROUP.equals(oclass)) {
                            // Make sure we're not hitting AD large group issue
                            // see: http://msdn.microsoft.com/en-us/library/ms817827.aspx
                            if (attrs.get("member;range=0-1499") != null) {
                                // we're in the limitation
                                Attribute range = AttributeBuilder.build("member", fetchGroupMembersByRange(conn, result));
                                cob.addAttribute(range);
                                if (conn.getConfiguration().isGetGroupMemberId()){
                                    cob.addAttribute(buildMemberIdAttribute(conn, range));
                                }
                                attrs.remove("member;range=0-1499");
                                attrs.remove("member");
                            }
                        }
                        javax.naming.directory.Attribute guid = attrs.get(LdapConstants.MS_GUID_ATTR);
                        if (guid != null) {
                            cob.addAttribute(AttributeBuilder.build(LdapConstants.MS_GUID_ATTR, objectGUIDtoString(guid)));
                            attrs.remove(LdapConstants.MS_GUID_ATTR);
                        }
                    }
                    // Set all Attributes
                    NamingEnumeration<? extends javax.naming.directory.Attribute> attrsEnum = attrs.getAll();
                    while (attrsEnum.hasMore()) {
                        javax.naming.directory.Attribute attr = attrsEnum.next();
                        String id = attr.getID();
                        NamingEnumeration vals = attr.getAll();
                        ArrayList values = new ArrayList();
                        while (vals.hasMore()) {
                            values.add(vals.next());
                        }
                        if (conn.getConfiguration().isGetGroupMemberId() && ObjectClass.GROUP.equals(oclass) 
                                && id.equalsIgnoreCase(conn.getConfiguration().getGroupMemberAttribute())) {
                            cob.addAttribute(buildMemberIdAttribute(conn, attr));
                        }
                        cob.addAttribute(AttributeBuilder.build(id, values));
                    }

                    SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                    syncDeltaBuilder.setToken(new SyncToken(now));
                    syncDeltaBuilder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                    syncDeltaBuilder.setUid(uid);
                    syncDeltaBuilder.setObject(cob.build());

                    return handler.handle(syncDeltaBuilder.build());
                }
            });
            // ICF 1.4 now allows us to send the Token even if no entries were actually processed
            ((SyncTokenResultsHandler)handler).handleResult(new SyncToken(now));
        } catch (ConnectorException e) {
            if (e.getCause() instanceof PartialResultException) {
                logger.warn("PartialResultException has been caught");
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private String getNowTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        switch (server) {
            case MSAD_GC:
            case MSAD:
            case MSAD_LDS:
                return sdf.format(new Date()) + ".0Z";
            default:
                return sdf.format(new Date()) + "Z";
        }
    }

    private String generateFilter(ObjectClass oc, SyncToken token) {
        StringBuilder filter;
        filter = new StringBuilder();

        if (token == null) {
            token = this.getLatestSyncToken();
        }
        if (ObjectClass.ACCOUNT.equals(oc)) {
            String[] oclasses = conn.getConfiguration().getAccountObjectClasses();
            for (int i = 0; i < oclasses.length; i++) {
                filter.append("(objectClass=");
                filter.append(oclasses[i]);
                filter.append(")");
            }
            if (conn.getConfiguration().getAccountSynchronizationFilter() != null){
                filter.append(conn.getConfiguration().getAccountSynchronizationFilter());
            }
        } else if (ObjectClass.GROUP.equals(oc)) {
            String[] oclasses = conn.getConfiguration().getGroupObjectClasses();
            for (int i = 0; i < oclasses.length; i++) {
                filter.append("(objectClass=");
                filter.append(oclasses[i]);
                filter.append(")");
            }
            if (conn.getConfiguration().getGroupSynchronizationFilter() != null){
                filter.append(conn.getConfiguration().getGroupSynchronizationFilter());
            }
        } else { // we use the ObjectClass value as the filter...
            filter.append("(objectClass=");
            filter.append(oc.getObjectClassValue());
            filter.append(")");
        }

        filter.append("(|(");
        filter.append(modifyTimestamp);
        filter.append(">=");
        filter.append(token.getValue().toString());
        filter.append(")(");
        filter.append(createTimestamp);
        filter.append(">=");
        filter.append(token.getValue().toString());
        filter.append("))");
        filter.insert(0, "(&");
        filter.append(")");
        logger.info("Using timestamp filter {0}",filter.toString());
        return filter.toString();
    }
}
