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

import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.ldap.LdapConnection;

/**
 * A class to perform an LDAP search against a {@link LdapConnection}.
 *
 * @author Andrei Badea
 */
public class LdapInternalSearch {

    private final LdapConnection conn;
    private final String filter;
    private final List<String> baseDNs;
    private final LdapSearchStrategy strategy;
    private final SearchControls controls;
    private final boolean ignoreNonExistingBaseDNs;

    public LdapInternalSearch(LdapConnection conn, String filter, List<String> baseDNs, LdapSearchStrategy strategy, SearchControls controls, boolean ignoreNonExistingBaseDNs) {
        this.conn = conn;
        this.filter = filter;
        this.baseDNs = baseDNs;
        this.strategy = strategy;
        this.controls = controls;
        this.ignoreNonExistingBaseDNs = ignoreNonExistingBaseDNs;
    }

    public void execute(SearchResultsHandler handler) {
        String filter = nullAsAllObjects(this.filter);
        try {
            strategy.doSearch(conn.getInitialContext(), baseDNs, filter, controls, handler, ignoreNonExistingBaseDNs);
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    private static String nullAsAllObjects(String query) {
        return query != null ? query : "(objectClass=*)";
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
