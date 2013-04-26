/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock AS. All Rights Reserved
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.BasicControl;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
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
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConstants;
import static org.identityconnectors.ldap.LdapUtil.buildMemberIdAttribute;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.SearchResultsHandler;
import org.identityconnectors.ldap.search.SimplePagedSearchStrategy;
import org.identityconnectors.ldap.sync.LdapSyncStrategy;

/**
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */
/**
 * An implementation of the sync operation based on the Update Sequence Numbers
 * in Active Directory.
 */
public class ActiveDirectoryChangeLogSyncStrategy implements LdapSyncStrategy {

    private static final String DELETE_CTRL = "1.2.840.113556.1.4.417";
    private static final String DELETED_PREFIX = "cn=deleted objects,";
    private static final String NAMING_CTX_ATTR = "defaultNamingContext";
    private static final String OBJSID_ATTR = "objectSID";
    private static final String USN_CHANGED_ATTR = "uSNChanged";
    private static final String HCU_CHANGED_ATTR = "highestCommittedUSN";
    private static final Log logger = Log.getLog(ActiveDirectoryChangeLogSyncStrategy.class);
    private final LdapConnection conn;
    private final ObjectClass oclass;

    public ActiveDirectoryChangeLogSyncStrategy(LdapConnection conn, ObjectClass oclass) {
        this.conn = conn;
        this.oclass = oclass;
    }

    public SyncToken getLatestSyncToken() {
        return new SyncToken(gethighestCommittedUSN());
    }

    public void sync(SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        // ldapsearch -h host -p 389 -b "ou=test,dc=example,dc=com" -D "cn=administrator,cn=users,dc=example,dc=com" -w xxx "(uSNChanged>=52410)" 
        // We use the uSNchanged attribute to detect changes on entries and newly created entries.
        // Since ICF does not make any difference between CREATE/UPDATE we don't have to deal with uSNCreated.
        // We have to detect deleted entries as well. To do so, we use the filter (isDeleted==TRUE) to detect
        // the tombstones in the cn=delete objects,<defaultNamingContext> container.

        final TreeMap<Integer, SyncDelta> changes = new TreeMap<Integer, SyncDelta>();

        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setDerefLinkFlag(false);

        LdapInternalSearch search = new LdapInternalSearch(conn,
                generateUSNChangedFilter(oclass, token, false),
                Arrays.asList(conn.getConfiguration().getBaseContextsToSynchronize()),
                new SimplePagedSearchStrategy(conn.getConfiguration().getBlockSize()),
                controls);
        try {
            search.execute(new SearchResultsHandler() {
                public boolean handle(String baseDN, SearchResult result) throws NamingException {
                    Attributes attrs = result.getAttributes();
                    NamingEnumeration<? extends javax.naming.directory.Attribute> attrsEnum =  attrs.getAll();
                    Uid uid = conn.getSchemaMapping().createUid(conn.getConfiguration().getUidAttribute(), attrs);
                    // build the object first
                    ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                    cob.setUid(uid);
                    cob.setObjectClass(oclass);
                    cob.setName(result.getNameInNamespace());
                    // Make sure we remove the binaries
                    attrs.remove(LdapConstants.MS_GUID_ATTR);
                    attrs.remove(OBJSID_ATTR);
                    // Set all Attributes
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
                            cob.addAttribute(buildMemberIdAttribute(conn,attr));
                        }
                        if (oclass.equals(ObjectClass.ACCOUNT) && id.equalsIgnoreCase(ADUserAccountControl.MS_USR_ACCT_CTRL_ATTR)){
                                String controls = values.get(0).toString();
                                cob.addAttribute(AttributeBuilder.buildEnabled(!ADUserAccountControl.isAccountDisabled(controls)));
                                cob.addAttribute(AttributeBuilder.buildLockOut(ADUserAccountControl.isAccountLockOut(controls)));
                                cob.addAttribute(AttributeBuilder.buildPasswordExpired(ADUserAccountControl.isPasswordExpired(controls)));
                        }
                    }
                    String usnChanged = attrs.get(USN_CHANGED_ATTR).get().toString();

                    SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                    syncDeltaBuilder.setToken(new SyncToken(usnChanged));
                    syncDeltaBuilder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                    syncDeltaBuilder.setUid(uid);
                    syncDeltaBuilder.setObject(cob.build());

                    changes.put(Integer.parseInt(usnChanged), syncDeltaBuilder.build());
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
                LdapContext context = conn.getInitialContext().newInstance(new Control[]{new BasicControl(DELETE_CTRL)});
                NamingEnumeration<SearchResult> deleted = context.search(DELETED_PREFIX + defaultContext, generateUSNChangedFilter(oclass, token, true), controls);

                while (deleted.hasMore()) {
                    SearchResult entry = deleted.next();
                    Attributes attrs = entry.getAttributes();
                    Uid uid = conn.getSchemaMapping().createUid(conn.getConfiguration().getUidAttribute(), attrs);
                    String usnChanged = attrs.get(USN_CHANGED_ATTR).get().toString();

                    SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                    syncDeltaBuilder.setToken(new SyncToken(usnChanged));
                    syncDeltaBuilder.setDeltaType(SyncDeltaType.DELETE);
                    syncDeltaBuilder.setUid(uid);
                    changes.put(Integer.parseInt(usnChanged), syncDeltaBuilder.build());
                }
            } catch (NamingException e) {
                logger.info(e.getExplanation());
            }
        } else {
            logger.info("The server does not support the control to search for deleted entries");
        }
        // Changes are now ordered in the TreeMap according to usnChanged.
        for (Map.Entry<Integer, SyncDelta> entry : changes.entrySet()) {
            if (!handler.handle(entry.getValue())){
                break;
            }
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
        StringBuilder filter;
        filter = new StringBuilder();

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
            String[] oclasses = conn.getConfiguration().getAccountObjectClasses();
            for (int i = 0; i < oclasses.length; i++) {
                filter.append("(objectClass=");
                filter.append(oclasses[i]);
                filter.append(")");
            }
        } else if (ObjectClass.GROUP.equals(oc)) {
            String[] oclasses = conn.getConfiguration().getGroupObjectClasses();
            for (int i = 0; i < oclasses.length; i++) {
                filter.append("(objectClass=");
                filter.append(oclasses[i]);
                filter.append(")");
            }
        } else { // we use the ObjectClass value as the filter...
            filter.append("(objectClass=");
            filter.append(oc.getObjectClassValue());
            filter.append(")");
        }

        filter.insert(0, "(&");
        filter.append(")");
        return filter.toString();
    }
}
