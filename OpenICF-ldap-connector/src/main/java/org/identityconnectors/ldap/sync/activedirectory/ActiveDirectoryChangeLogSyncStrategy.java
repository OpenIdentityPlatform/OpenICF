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
package org.identityconnectors.ldap.sync.activedirectory;

import static org.identityconnectors.framework.common.objects.ObjectClassUtil.createSpecialName;
import static org.identityconnectors.ldap.ADLdapUtil.objectGUIDtoString;
import static org.identityconnectors.ldap.ADLdapUtil.fetchGroupMembersByRange;
import static org.identityconnectors.ldap.LdapConstants.OBJECTCLASS_ATTR;
import static org.identityconnectors.ldap.LdapUtil.getObjectClassFilter;
import static org.identityconnectors.ldap.LdapUtil.buildMemberIdAttribute;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import static org.identityconnectors.ldap.LdapUtil.guessObjectClass;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.BasicControl;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.identityconnectors.ldap.ADLdapUtil;
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConstants;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.LdapSearchResultsHandler;
import org.identityconnectors.ldap.search.SimplePagedSearchStrategy;
import org.identityconnectors.ldap.sync.LdapSyncStrategy;
import org.identityconnectors.ldap.ADGroupType;

/**
 * An implementation of the sync operation based on the Update Sequence Numbers
 * in Active Directory.
 */
public class ActiveDirectoryChangeLogSyncStrategy implements LdapSyncStrategy {

    private static final String DELETE_CTRL = "1.2.840.113556.1.4.417";
    private static final String DELETED_PREFIX = "cn=deleted objects,";
    private static final String NAMING_CTX_ATTR = "defaultNamingContext";
    private static final String USN_CHANGED_ATTR = "uSNChanged";
    private static final String USN_CREATED_ATTR = "uSNCreated";
    private static final String HCU_CHANGED_ATTR = "highestCommittedUSN";
    private static final String DIRSYNC_EVENTS_OBJCLASS = createSpecialName("DIRSYNC_EVENTS");
    private static final Log logger = Log.getLog(ActiveDirectoryChangeLogSyncStrategy.class);
    private final LdapConnection conn;
    private final ObjectClass oclass;

    public ActiveDirectoryChangeLogSyncStrategy(LdapConnection conn, ObjectClass oclass) {
        this.conn = conn;
        this.oclass = oclass;
    }

    public SyncToken getLatestSyncToken() {
        if (oclass.is(DIRSYNC_EVENTS_OBJCLASS)) {
            return new SyncToken(getDirSyncCookie());
        }
        return new SyncToken(gethighestCommittedUSN());
    }

    public void sync(SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        if (oclass.is(DIRSYNC_EVENTS_OBJCLASS)) {
            handleEvents(token, handler, options);
        } else {
            // ldapsearch -h host -p 389 -b "ou=test,dc=example,dc=com" -D "cn=administrator,cn=users,dc=example,dc=com" -w xxx "(uSNChanged>=52410)" 
            // We use the uSNchanged attribute to detect changes on entries and newly created entries.
            // We have to detect deleted entries as well. To do so, we use the filter (isDeleted==TRUE) to detect
            // the tombstones in the cn=delete objects,<defaultNamingContext> container.

            final TreeMap<Integer, SyncDelta> changes = new TreeMap<Integer, SyncDelta>();
            final String[] usnChanged = {""};
            String waterMark = gethighestCommittedUSN();
            
            if (token != null && logger.isWarning()) {
                if (Integer.parseInt(token.getValue().toString()) > Integer.parseInt(waterMark)) {
                    //[OPENICF-402] The current SyncToken should never be greater than the highestCommittedUSN on the DC
                    // We log the issue and let the process go
                    logger.warn("The current SyncToken value ({0}) is greater than the highestCommittedUSN value ({1})", token.getValue().toString(), waterMark);
                }
            }

            SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setDerefLinkFlag(false);
            controls.setReturningAttributes(new String[]{"*", ADUserAccountControl.MSDS_USR_ACCT_CTRL_ATTR});

            LdapInternalSearch search = new LdapInternalSearch(conn,
                    generateUSNChangedFilter(oclass, token, false),
                    Arrays.asList(conn.getConfiguration().getBaseContextsToSynchronize()),
                    new SimplePagedSearchStrategy(conn.getConfiguration().getBlockSize()),
                    controls);
            try {
                search.execute(new LdapSearchResultsHandler() {
                    public boolean handle(String baseDN, SearchResult result) throws NamingException {
                        Attributes attrs = result.getAttributes();
                        Uid uid = conn.getSchemaMapping().createUid(conn.getConfiguration().getUidAttribute(), attrs);
                        // build the object first
                        ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                        cob.setUid(uid);
                        if (ObjectClass.ALL.equals(oclass)) {
                            cob.setObjectClass(guessObjectClass(conn, attrs.get(OBJECTCLASS_ATTR)));
                        } else {
                            cob.setObjectClass(oclass);
                        }
                        cob.setName(result.getNameInNamespace());
                        if (attrs.get(LdapConstants.MS_GUID_ATTR) != null) {
                            cob.addAttribute(AttributeBuilder.build(LdapConstants.MS_GUID_ATTR, objectGUIDtoString(attrs.get(LdapConstants.MS_GUID_ATTR))));
                            attrs.remove(LdapConstants.MS_GUID_ATTR);
                        }
                        // Make sure we remove the SID
                        attrs.remove(LdapConstants.MS_SID_ATTR);

                        // Make sure we're not hitting AD large group issue
                        if (ObjectClass.GROUP.equals(oclass)) {
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
                        // Process Account specifics (ENABLE/PASSWORD_EXPIRED/LOCKOUT/accountExpires/pwdLastSet)
                        if (oclass.equals(ObjectClass.ACCOUNT)) {
                            switch (conn.getServerType()) {
                                case MSAD_GC:
                                case MSAD:
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
                                    break;
                                case MSAD_LDS:
                                    if (attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_DISABLED) != null) {
                                        cob.addAttribute(AttributeBuilder.buildEnabled(!Boolean.parseBoolean(attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_DISABLED).get().toString())));
                                    } else if (attrs.get(LdapConstants.MS_DS_USER_PASSWORD_EXPIRED) != null) {
                                        cob.addAttribute(AttributeBuilder.buildPasswordExpired(Boolean.parseBoolean(attrs.get(LdapConstants.MS_DS_USER_PASSWORD_EXPIRED).get().toString())));
                                    } else if (attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_AUTOLOCKED) != null) {
                                        cob.addAttribute(AttributeBuilder.buildLockOut(Boolean.parseBoolean(attrs.get(LdapConstants.MS_DS_USER_ACCOUNT_AUTOLOCKED).get().toString())));
                                    }
                                    break;
                                default:
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
                            cob.addAttribute(AttributeBuilder.build(id, values));
                            if (conn.getConfiguration().isGetGroupMemberId() && oclass.equals(ObjectClass.GROUP) && attr.getID().equalsIgnoreCase("member")) {
                                cob.addAttribute(buildMemberIdAttribute(conn, attr));
                            }
                        }
                        SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                        usnChanged[0] = attrs.get(USN_CHANGED_ATTR).get().toString();
                        if (usnChanged[0].equalsIgnoreCase(attrs.get(USN_CREATED_ATTR).get().toString())) {
                            syncDeltaBuilder.setDeltaType(SyncDeltaType.CREATE);
                        } else {
                            syncDeltaBuilder.setDeltaType(SyncDeltaType.UPDATE);
                        }
                        syncDeltaBuilder.setToken(new SyncToken(usnChanged[0]));
                        syncDeltaBuilder.setUid(uid);
                        syncDeltaBuilder.setObject(cob.build());

                        changes.put(Integer.parseInt(usnChanged[0]), syncDeltaBuilder.build());
                        return true;
                    }
                });
            } catch (ConnectorException e) {
                if (e.getCause() instanceof PartialResultException) {
                    // The default naming context is used on the DC as the baseContextsToSynchronize, hence this PartialResultException.
                    // Let's just silently catch it not to break the sync cycle. It is thrown at the end of the search anyway...
                    logger.warn("Default naming context of the DC is used as baseContextsToSynchronize.\nPartialResultException has been caught");
                } else {
                    throw e;
                }
            }

            // Deletes
            // ldapsearch -J 1.2.840.113556.1.4.417 -h xx -p 389 -b "dc=example,dc=com" -D "cn=administrator,cn=users,dc=example,dc=com" -w xx "&(isDeleted=TRUE)(uSNChanged>=528433)"
            if (conn.supportsControl(DELETE_CTRL)) {
                try {
                    Attributes rootAttrs = conn.getInitialContext().getAttributes("", new String[]{NAMING_CTX_ATTR});
                    String defaultContext = getStringAttrValue(rootAttrs, NAMING_CTX_ATTR);
                    if (defaultContext != null) {
                        LdapContext context = conn.getInitialContext().newInstance(new Control[]{new BasicControl(DELETE_CTRL)});
                        NamingEnumeration<SearchResult> deleted = context.search(DELETED_PREFIX + defaultContext, generateUSNChangedFilter(oclass, token, true), controls);

                        while (deleted.hasMore()) {
                            SearchResult entry = deleted.next();
                            Attributes attrs = entry.getAttributes();
                            Uid uid = conn.getSchemaMapping().createUid(conn.getConfiguration().getUidAttribute(), attrs);
                            usnChanged[0] = attrs.get(USN_CHANGED_ATTR).get().toString();

                            SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                            syncDeltaBuilder.setToken(new SyncToken(usnChanged[0]));
                            syncDeltaBuilder.setDeltaType(SyncDeltaType.DELETE);
                            syncDeltaBuilder.setUid(uid);
                            if (ObjectClass.ALL.equals(oclass)) {
                                syncDeltaBuilder.setObjectClass(guessObjectClass(conn, attrs.get(OBJECTCLASS_ATTR)));
                            } else {
                                syncDeltaBuilder.setObjectClass(oclass);
                            }
                            changes.put(Integer.parseInt(usnChanged[0]), syncDeltaBuilder.build());
                        }
                    } else if (LdapConstants.ServerType.MSAD_LDS.equals(conn.getServerType())) {
                        logger.error("Active Directory Lightweight Directory Services is used but defaultNamingContext has not been set - impossible to detect deleted objects");
                    }
                } catch (NamingException e) {
                    logger.info(e.getExplanation());
                }
            } else {
                logger.info("The server does not support the control to search for deleted entries");
            }
            // Changes are now ordered in the TreeMap according to usnChanged.
            for (Map.Entry<Integer, SyncDelta> entry : changes.entrySet()) {
                if (!handler.handle(entry.getValue())) {
                    break;
                } else {
                    waterMark = entry.getKey().toString();
                }
            }
            // ICF 1.4 now allows us to send the Token even if no entries were actually processed
            ((SyncTokenResultsHandler) handler).handleResult(new SyncToken(waterMark));
        }
    }

    private String gethighestCommittedUSN() {
        String hcUSN = null;
        try {
            Attributes attrs = conn.getInitialContext().getAttributes("", new String[]{HCU_CHANGED_ATTR});
            hcUSN = getStringAttrValue(attrs, HCU_CHANGED_ATTR);
            if (hcUSN == null) {
                String error = "Unable to read the highestCommittedUSN attribute"
                        + "from the rootDSE of Active Directory ";
                throw new ConnectorException(error);
            }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
        return hcUSN;
    }

    private String generateUSNChangedFilter(ObjectClass oc, SyncToken token, boolean isDeleted) {
        StringBuilder filter = new StringBuilder();

        if (token == null) {
            token = this.getLatestSyncToken();
        }

        filter.append("(uSNChanged>=");
        filter.append(Integer.parseInt(token.getValue().toString()) + 1);
        filter.append(")");

        if (isDeleted) {
            filter.append("(isDeleted=TRUE)");
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

        filter.insert(0, "(&");
        filter.append(")");
        return filter.toString();
    }

    private byte[] getDirSyncCookie() {
        try {
            Attributes rootAttrs = conn.getInitialContext().getAttributes("", new String[]{NAMING_CTX_ATTR});
            String defaultContext = getStringAttrValue(rootAttrs, NAMING_CTX_ATTR);

            LdapContext ctx = conn.getInitialContext().newInstance(null);
            String searchFilter = "(|(objectClass=group)(objectclass=user))";

            //Specify the DirSync and DirSyncResponse controls
            byte[] dirSyncCookie = null;
            boolean hasMore = false;
            Control[] rspCtls;
            //Search for objects using the filter
            do {
                ctx.setRequestControls(new Control[]{new DirSyncControl(dirSyncCookie)});
                NamingEnumeration answer = ctx.search(defaultContext, searchFilter, getSearchCtls());
                while (answer.hasMoreElements()) {
                    answer.next();
                }
                answer.close();
                //save the response controls
                if ((rspCtls = ctx.getResponseControls()) != null) {
                    for (Control control : rspCtls) {
                        if (control instanceof DirSyncResponseControl) {
                            DirSyncResponseControl dirSyncControl = (DirSyncResponseControl) control;
                            dirSyncCookie = dirSyncControl.getResponseCookie();
                            hasMore = dirSyncControl.hasMore();
                        }
                    }
                }

            } while (hasMore);
            ctx.close();

            return dirSyncCookie;

        } catch (NamingException ex) {
            logger.error("Problem reading naming context");
        } catch (IOException ex) {
            logger.error("Problem reading cookie");
        }
        return null;
    }

    private void handleEvents(SyncToken token, SyncResultsHandler handler, OperationOptions options) {
        ArrayList<SearchResult> changes = new ArrayList<SearchResult>();
        String searchFilter = "(|(objectClass=group)(objectclass=user))";
        Control[] rspCtls;
        //Specify the DirSync and DirSyncResponse controls
        byte[] dirSyncCookie = (byte[]) token.getValue();
        boolean hasMore = false;

        try {
            Attributes rootAttrs = conn.getInitialContext().getAttributes("", new String[]{NAMING_CTX_ATTR});
            String defaultContext = getStringAttrValue(rootAttrs, NAMING_CTX_ATTR);
            LdapContext ctx = conn.getInitialContext().newInstance(null);

            do {
                ctx.setRequestControls(new Control[]{new DirSyncControl(dirSyncCookie)});
                NamingEnumeration answer = ctx.search(defaultContext, searchFilter, getSearchCtls());
                while (answer.hasMoreElements()) {
                    SearchResult sr = (SearchResult) answer.next();
                    Attributes attrs = sr.getAttributes();
                    String dn = sr.getNameInNamespace();

                    // Group change 
                    if ((attrs.get("member;range=0-0") != null) || (attrs.get("member;range=1-1") != null)) {
                        changes.add(sr);
                    } // User Change
                    else {
                        boolean change = false;
                        // Create, rename or move
                        if (attrs.get("parentGUID") != null) {
                            // Ignore create...
                            if (attrs.get("WhenCreated") == null) {
                                // Process move/rename
                                // What interest us is mainly user getting out of sync scope...
                                // The entry has been either moved or renamed and if now it is not 
                                // in the sync scope anymore, we take it
                                change = isOutOfScope(dn);
                            }
                        }
                        // Account control is part of the change.
                        if (attrs.get(ADUserAccountControl.MS_USR_ACCT_CTRL_ATTR) != null) {
                            change = true;
                        }
                        if (attrs.get(ADUserAccountControl.MSDS_USR_ACCT_CTRL_ATTR) != null) {
                            change = true;
                        }
                        // The modified entry is of interest
                        if (change) {
                            changes.add(sr);
                        }
                    }
                }

                //Save the response control for next round
                if ((rspCtls = ctx.getResponseControls()) != null) {
                    for (Control control : rspCtls) {
                        if (control instanceof DirSyncResponseControl) {
                            DirSyncResponseControl dirSyncControl = (DirSyncResponseControl) control;
                            dirSyncCookie = dirSyncControl.getResponseCookie();
                            hasMore = dirSyncControl.hasMore();
                        }
                    }
                }
                processChanges(handler, changes, new SyncToken(dirSyncCookie));
                ((SyncTokenResultsHandler) handler).handleResult(new SyncToken(dirSyncCookie));
                changes.clear();
            } while (hasMore);

            ctx.close();

        } catch (IOException ex) {
            logger.error("Problem reading cookie");
        } catch (NamingException ex) {
            logger.error("Problem reading naming context");
        }
    }

    private boolean isOutOfScope(String dn) throws InvalidNameException {
        boolean outOfScope = true;
        LdapName ldn = new LdapName(dn);
        for (String context : conn.getConfiguration().getBaseContextsToSynchronize()) {
            LdapName suffix = new LdapName(context);
            if (ldn.startsWith(suffix)) {
                outOfScope = false;
            }
        }
        return outOfScope;
    }

    private void processChanges(SyncResultsHandler handler, ArrayList<SearchResult> changes, SyncToken syncToken) throws NamingException {
        for (SearchResult change : changes) {
            Attributes attrs = change.getAttributes();
            if ((attrs.get("member;range=0-0") != null) || (attrs.get("member;range=1-1") != null)) {
                processGroupChange(handler, change, syncToken);
            } else {
                processUserChange(handler, change, syncToken);
            }
        }
    }

    private void processGroupChange(SyncResultsHandler handler, SearchResult groupChange, SyncToken syncToken) throws NamingException {
        // Now process the changes
        Attributes attrs = groupChange.getAttributes();
        String dn = groupChange.getNameInNamespace();
        String groupGUID = objectGUIDtoString(attrs.get(LdapConstants.MS_GUID_ATTR));
        javax.naming.directory.Attribute memberIn = attrs.get("member;range=1-1");
        javax.naming.directory.Attribute memberOut = attrs.get("member;range=0-0");

        if (memberIn != null) {
            NamingEnumeration enu = memberIn.getAll();
            while (enu.hasMore()) {
                // acount DN
                String memberDn = (String) enu.next();
                Attributes guid = conn.getInitialContext().getAttributes(memberDn, new String[]{LdapConstants.MS_GUID_ATTR});
                String memberGuid = objectGUIDtoString(guid.get(LdapConstants.MS_GUID_ATTR));

                ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                cob.setUid(memberGuid);
                cob.setName(memberDn);
                cob.setObjectClass(oclass);
                cob.addAttribute(AttributeBuilder.build("addedToGroup", dn));
                cob.addAttribute(AttributeBuilder.build("groupGUID", groupGUID));
                SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                syncDeltaBuilder.setToken(syncToken);
                syncDeltaBuilder.setDeltaType(SyncDeltaType.UPDATE);
                syncDeltaBuilder.setUid(new Uid(memberGuid));
                syncDeltaBuilder.setObject(cob.build());
                if (!handler.handle(syncDeltaBuilder.build())) {
                    break;
                }
            }
        }
        if (memberOut != null) {
            NamingEnumeration enu = memberOut.getAll();
            while (enu.hasMore()) {
                // acount DN
                String memberDn = (String) enu.next();
                Attributes guid = conn.getInitialContext().getAttributes(memberDn, new String[]{LdapConstants.MS_GUID_ATTR});
                String memberGuid = objectGUIDtoString(guid.get(LdapConstants.MS_GUID_ATTR));

                ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                cob.setUid(memberGuid);
                cob.setName(memberDn);
                cob.setObjectClass(oclass);
                cob.addAttribute(AttributeBuilder.build("removedFromGroup", dn));
                cob.addAttribute(AttributeBuilder.build("groupGUID", groupGUID));
                SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                syncDeltaBuilder.setToken(syncToken);
                syncDeltaBuilder.setDeltaType(SyncDeltaType.UPDATE);
                syncDeltaBuilder.setUid(new Uid(memberGuid));
                syncDeltaBuilder.setObject(cob.build());
                if (!handler.handle(syncDeltaBuilder.build())) {
                    break;
                }
            }
        }
    }

    private void processUserChange(SyncResultsHandler handler, SearchResult userChange, SyncToken syncToken) throws NamingException {
        Attributes attrs = userChange.getAttributes();
        String dn = userChange.getNameInNamespace();
        String objectGUID = objectGUIDtoString(attrs.get(LdapConstants.MS_GUID_ATTR));

        ConnectorObjectBuilder cob = new ConnectorObjectBuilder();

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
        if (attrs.get("parentGUID") != null) {
            // move/rename that was out of scope
            cob.addAttribute(AttributeBuilder.build("outOfScope", true));
        }
        cob.setUid(objectGUID);
        cob.setName(dn);
        cob.setObjectClass(oclass);
        cob.addAttribute(AttributeBuilder.build(LdapConstants.MS_GUID_ATTR, objectGUID));

        SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
        syncDeltaBuilder.setToken(syncToken);
        syncDeltaBuilder.setDeltaType(SyncDeltaType.UPDATE);
        syncDeltaBuilder.setUid(new Uid(objectGUID));
        syncDeltaBuilder.setObject(cob.build());
        handler.handle(syncDeltaBuilder.build());
    }

    private SearchControls getSearchCtls() {
        SearchControls searchCtls = new SearchControls();
        String returnedAtts[] = {};
        searchCtls.setReturningAttributes(returnedAtts);
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        return searchCtls;
    }

}
