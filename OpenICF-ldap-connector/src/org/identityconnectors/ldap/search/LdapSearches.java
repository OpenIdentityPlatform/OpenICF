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
package org.identityconnectors.ldap.search;

import static java.util.Collections.singletonList;
import static org.identityconnectors.ldap.LdapUtil.isUnderContexts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapEntry;

/**
 * Helper methods for searching. The "get" methods throw an exception when
 * nothing is found; the "find" methods return null or an empty result.
 *
 * @author Andrei Badea
 */
public class LdapSearches {

    // TODO: when more than one base DN is specified in the configuration,
    // some searches could be faster by searching the entry under all naming
    // contexts on the server and then checking that the entry is really under one of the
    // configured base DNs.

    private static final Log log = Log.getLog(LdapSearches.class);

    private LdapSearches() {
    }

    /**
     * Finds the DN of the entry corresponding to the given qualified Uid.
     */
    public static String getEntryDN(LdapConnection conn, ObjectClass oclass, Uid uid) {
        log.ok("Searching for object {0} of class {1}", uid.getUidValue(), oclass.getObjectClassValue());

        EqualsFilter filter = (EqualsFilter) FilterBuilder.equalTo(uid);
        LdapFilter ldapFilter = new LdapFilterTranslator(conn.getSchemaMapping(), oclass).createEqualsExpression(filter, false);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("entryDN");

        LdapSearch search = new LdapSearch(conn, oclass, ldapFilter, builder.build());
        ConnectorObject object = search.getSingleResult();
        if (object != null) {
            return AttributeUtil.getStringValue(object.getAttributeByName("entryDN"));
        }
        throw new ConnectorException("Unable to find object " + uid + " of class " + oclass);
    }

    public static List<ConnectorObject> findObjects(LdapConnection conn, ObjectClass oclass, String name) {
        log.ok("Searching for object name {0} of class {1}", name, oclass.getObjectClassValue());

        final List<ConnectorObject> result = new ArrayList<ConnectorObject>();

        EqualsFilter filter = (EqualsFilter) FilterBuilder.equalTo(new Name(name));
        LdapFilter ldapFilter = new LdapFilterTranslator(conn.getSchemaMapping(), oclass).createEqualsExpression(filter, false);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("entryDN");

        LdapSearch search = new LdapSearch(conn, oclass, ldapFilter, builder.build());
        search.execute(new ResultsHandler() {
            public boolean handle(ConnectorObject object) {
                result.add(object);
                return true;
            }
        });
        return result;
    }

    public static ConnectorObject findObject(LdapConnection conn, ObjectClass oclass, LdapFilter filter, String... attrsToGet) {
        log.ok("Searching for object of class {0} with filter {1}", oclass.getObjectClassValue(), filter);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet(attrsToGet);

        LdapSearch search = new LdapSearch(conn, oclass, filter, builder.build());
        return search.getSingleResult();
    }

    public static LdapEntry findEntry(LdapConnection conn, LdapName entryDN, String... ldapAttrsToGet) {
        log.ok("Searching for entry {0}", entryDN);

        final List<LdapEntry> result = new ArrayList<LdapEntry>();
        if (!isUnderContexts(entryDN, conn.getConfiguration().getBaseContextsAsLdapNames())) {
            return null;
        }

        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.OBJECT_SCOPE);
        controls.setReturningAttributes(ldapAttrsToGet);
        LdapInternalSearch search = new LdapInternalSearch(conn, null, singletonList(entryDN.toString()), new DefaultSearchStrategy(true), controls);
        search.execute(new SearchResultsHandler() {
            public boolean handle(String baseDN, SearchResult searchResult) {
                result.add(LdapEntry.create(baseDN, searchResult));
                return false;
            }
        });

        return !result.isEmpty() ? result.get(0) : null;
    }

    public static void findEntries(SearchResultsHandler handler, LdapConnection conn, String filter, String... ldapAttrsToGet) {
        log.ok("Searching for entries matching {0}", filter);

        List<String> baseDNs = Arrays.asList(conn.getConfiguration().getBaseContexts());
        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(ldapAttrsToGet);
        LdapInternalSearch search = new LdapInternalSearch(conn, filter, baseDNs, new DefaultSearchStrategy(false), controls);
        search.execute(handler);
    }
}
