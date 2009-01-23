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
package org.identityconnectors.ldap.search;

import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.PagedResultsControl;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.ldap.LdapConnection;

import com.sun.jndi.ldap.ctl.VirtualListViewControl;

/**
 * A class to perform an LDAP search against a {@link LdapConnection}.
 *
 * @author Andrei Badea
 */
public class LdapInternalSearch {

    private final LdapConnection conn;
    private final String query;
    private final List<String> baseDNs;
    private final SearchControls controls;

    public LdapInternalSearch(LdapConnection conn, String query, List<String> baseDNs, SearchControls controls) {
        this.conn = conn;
        this.query = query;
        this.baseDNs = baseDNs;
        this.controls = controls;
    }

    public LdapInternalSearch(LdapConnection conn, String query, List<String> baseDNs, int searchScope, String... attrsToGet) {
        this(conn, query, baseDNs, createSearchControls(searchScope, attrsToGet));
    }

    public void execute(SearchResultsHandler handler) {
        String query = getQuery();
        int pageSize = conn.getConfiguration().getPageSize();
        boolean pagedSearchEnabled = conn.getConfiguration().isPagedSearchEnabled();
        boolean simplePagedSearchPreferred = conn.getConfiguration().isSimplePagedSearchPreferred();

        LdapSearchStrategy strategy;
        if (pagedSearchEnabled && !simplePagedSearchPreferred && conn.supportsControl(VirtualListViewControl.OID)) {
            // TODO: VLV index strategy.
            strategy = null;
        } else if (pagedSearchEnabled && conn.supportsControl(PagedResultsControl.OID)) {
            strategy = new SimplePagedSearchStrategy(conn.getInitialContext(), baseDNs, query, controls, pageSize);
        } else {
            strategy = new DefaultSearchStrategy(conn.getInitialContext(), baseDNs, query, controls);
        }

        try {
            strategy.doSearch(handler);
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    private String getQuery() {
        return query != null ? query : "(objectClass=*)";
    }

    private static SearchControls createSearchControls(int scope, String... attributesToGet) {
        SearchControls result = createDefaultSearchControls();
        result.setSearchScope(scope);
        result.setReturningAttributes(attributesToGet);
        return result;
    }

    public static SearchControls createDefaultSearchControls() {
        SearchControls result = new SearchControls();
        result.setCountLimit(0);
        // Setting true to be consistent with the adapter. However, the
        // comment in the adapter that this flag causes the referrals to be
        // followed is wrong. Cf. http://java.sun.com/products/jndi/tutorial/ldap/misc/aliases.html.
        result.setDerefLinkFlag(true);
        result.setReturningObjFlag(false);
        result.setTimeLimit(0);
        return result;
    }
}
