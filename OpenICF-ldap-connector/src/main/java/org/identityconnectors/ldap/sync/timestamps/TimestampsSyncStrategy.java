/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
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

import static org.identityconnectors.ldap.ADLdapUtil.fetchGroupMembersByRange;
import static org.identityconnectors.ldap.ADLdapUtil.objectGUIDtoString;
import static org.identityconnectors.ldap.LdapUtil.buildMemberIdAttribute;
import static org.identityconnectors.ldap.LdapConstants.OBJECTCLASS_ATTR;
import static org.identityconnectors.ldap.LdapUtil.getObjectClassFilter;
import static org.identityconnectors.ldap.LdapUtil.guessObjectClass;

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
import org.identityconnectors.ldap.ADGroupType;
import org.identityconnectors.ldap.ADLdapUtil;
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConstants.ServerType;
import org.identityconnectors.ldap.LdapConstants;
import org.identityconnectors.ldap.LdapEntry;
import org.identityconnectors.ldap.search.DefaultSearchStrategy;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.LdapSearchStrategy;
import org.identityconnectors.ldap.search.LdapSearchResultsHandler;
import org.identityconnectors.ldap.search.SimplePagedSearchStrategy;
import org.identityconnectors.ldap.sync.LdapSyncStrategy;

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
        LdapSearchStrategy strategy;
        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setDerefLinkFlag(false);
        if (ADLdapUtil.isServerMSADFamily(server)) {
            controls.setReturningAttributes(getAttributesToGet(new String[]{createTimestamp, modifyTimestamp,
                ADUserAccountControl.MSDS_USR_ACCT_CTRL_ATTR,
                conn.getConfiguration().getUidAttribute()}, options.getAttributesToGet()));
        } else {
            controls.setReturningAttributes(getAttributesToGet(new String[]{createTimestamp, modifyTimestamp,
                conn.getConfiguration().getUidAttribute()}, options.getAttributesToGet()));
        }

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
                    LdapEntry entry = LdapEntry.create(baseDN, result);
                    Attributes attrs = result.getAttributes();
                    Uid uid = conn.getSchemaMapping().createUid(oclass, entry);
                    // build the object first
                    ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                    cob.setUid(uid);
                    if (ObjectClass.ALL.equals(oclass)) {
                        cob.setObjectClass(guessObjectClass(conn, attrs.get(OBJECTCLASS_ATTR)));
                    } else {
                        cob.setObjectClass(oclass);
                    }
                    cob.setName(result.getNameInNamespace());

                    // Let's process AD specifics...
                    if (ADLdapUtil.isServerMSADFamily(server)) {
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
                                if (attrs.get(ADUserAccountControl.MS_USR_ACCT_CTRL_ATTR) != null) {
                                    String uac = attrs.get(ADUserAccountControl.MS_USR_ACCT_CTRL_ATTR).get().toString();
                                    cob.addAttribute(AttributeBuilder.buildEnabled(!ADUserAccountControl.isAccountDisabled(uac)));
                                    cob.addAttribute(AttributeBuilder.build(ADUserAccountControl.DONT_EXPIRE_PASSWORD_NAME, ADUserAccountControl.isDontExpirePassword(uac)));
                                    cob.addAttribute(AttributeBuilder.build(ADUserAccountControl.PASSWORD_NOTREQD_NAME, ADUserAccountControl.isPasswordNotReq(uac)));
                                    cob.addAttribute(AttributeBuilder.build(ADUserAccountControl.SMARTCARD_REQUIRED_NAME, ADUserAccountControl.isSmartCardRequired(uac)));
                                }
                                if (attrs.get(ADUserAccountControl.MSDS_USR_ACCT_CTRL_ATTR) != null) {
                                    String uac2 = attrs.get(ADUserAccountControl.MSDS_USR_ACCT_CTRL_ATTR).get().toString();
                                    cob.addAttribute(AttributeBuilder.buildLockOut(ADUserAccountControl.isAccountLockOut(uac2)));
                                    cob.addAttribute(AttributeBuilder.buildPasswordExpired(ADUserAccountControl.isPasswordExpired(uac2)));
                                }
                            }
                            if (attrs.get(ADUserAccountControl.ACCOUNT_EXPIRES) != null) {
                                cob.addAttribute(ADLdapUtil.convertMSEpochToISO8601(attrs.get(ADUserAccountControl.ACCOUNT_EXPIRES)));
                                attrs.remove(ADUserAccountControl.ACCOUNT_EXPIRES);
                            }
                            if (attrs.get(ADUserAccountControl.PWD_LAST_SET) != null) {
                                cob.addAttribute(ADLdapUtil.convertMSEpochToISO8601(attrs.get(ADUserAccountControl.PWD_LAST_SET)));
                                attrs.remove(ADUserAccountControl.PWD_LAST_SET);
                            }
                            if (attrs.get(ADUserAccountControl.LAST_LOGON) != null) {
                                cob.addAttribute(ADLdapUtil.convertMSEpochToISO8601(attrs.get(ADUserAccountControl.LAST_LOGON)));
                                attrs.remove(ADUserAccountControl.LAST_LOGON);
                            }
                            if (attrs.get(ADUserAccountControl.LOCKOUT_TIME) != null) {
                                cob.addAttribute(ADLdapUtil.convertMSEpochToISO8601(attrs.get(ADUserAccountControl.LOCKOUT_TIME)));
                                attrs.remove(ADUserAccountControl.LOCKOUT_TIME);
                            }
                        }
                        if (ObjectClass.GROUP.equals(oclass)) {
                            // Make sure we're not hitting AD large group issue
                            // see: http://msdn.microsoft.com/en-us/library/ms817827.aspx
                            if (attrs.get("member;range=0-1499") != null) {
                                // we're in the limitation
                                Attribute range = AttributeBuilder.build("member", fetchGroupMembersByRange(conn, result));
                                cob.addAttribute(range);
                                if (conn.getConfiguration().isGetGroupMemberId()) {
                                    cob.addAttribute(buildMemberIdAttribute(conn, range));
                                }
                                attrs.remove("member;range=0-1499");
                                attrs.remove("member");
                            }
                            try {
                                if (attrs.get(ADGroupType.GROUPTYPE) != null) {
                                    String groupType = attrs.get(ADGroupType.GROUPTYPE).get().toString();
                                    cob.addAttribute(AttributeBuilder.build(ADGroupType.GROUP_SCOPE_NAME, ADGroupType.getScope(groupType)));
                                    cob.addAttribute(AttributeBuilder.build(ADGroupType.GROUP_TYPE_NAME, ADGroupType.getType(groupType)));
                                }
                            } catch (NamingException e) {
                                logger.warn(e, "Can't read groupType attribute: " + e.getExplanation());
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
            ((SyncTokenResultsHandler) handler).handleResult(new SyncToken(now));
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
            filter.append(getObjectClassFilter(conn.getConfiguration().getAccountObjectClasses()));
            if (conn.getConfiguration().getAccountSynchronizationFilter() != null) {
                filter.append(conn.getConfiguration().getAccountSynchronizationFilter());
            }
        } else if (ObjectClass.GROUP.equals(oc)) {
            filter.append(getObjectClassFilter(conn.getConfiguration().getGroupObjectClasses()));
            if (conn.getConfiguration().getGroupSynchronizationFilter() != null) {
                filter.append(conn.getConfiguration().getGroupSynchronizationFilter());
            }
        } else if (ObjectClass.ALL.equals(oc)) {
            filter.append(getObjectClassFilter(conn.getConfiguration().getObjectClassesToSynchronize()));
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
        logger.info("Using timestamp filter {0}", filter.toString());
        return filter.toString();
    }
    
    static String[] getAttributesToGet(String[]... attrsLists) {
    int len = 0;
    for (String[] attrs : attrsLists) {
        len += attrs.length;
    }
    String[] attrsToGet = new String[len];
    int idx = 0;
    for (String[] attrs : attrsLists) {
        for (String attr : attrs) {
            attrsToGet[idx] = attr;
            idx++;
        }
    }
    return attrsToGet;
}
}
