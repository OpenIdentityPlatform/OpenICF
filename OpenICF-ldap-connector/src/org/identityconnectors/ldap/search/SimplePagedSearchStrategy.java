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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.identityconnectors.common.logging.Log;

public class SimplePagedSearchStrategy extends LdapSearchStrategy {

    private static final Log log = Log.getLog(SimplePagedSearchStrategy.class);

    private final int pageSize;

    public SimplePagedSearchStrategy(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public void doSearch(LdapContext initCtx, List<String> baseDNs, String query, SearchControls searchControls, SearchResultsHandler handler, boolean ignoreNonExistingBaseDNs) throws IOException, NamingException {
        log.ok("Searching in {0} with filter {1} and {2}", baseDNs, query, searchControlsToString(searchControls));

        LdapContext ctx = initCtx.newInstance(null);
        try {
            Iterator<String> baseDNIter = baseDNs.iterator();
            boolean proceed = true;

            main: while (baseDNIter.hasNext() && proceed) {
                String baseDN = baseDNIter.next();
                byte[] cookie = null;

                do {
                    ctx.setRequestControls(new Control[] { new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                    NamingEnumeration<SearchResult> results;
                    try {
                        results = ctx.search(baseDN, query, searchControls);
                    } catch (NameNotFoundException e) {
                        if (!ignoreNonExistingBaseDNs) {
                            throw e;
                        }
                        log.warn(e, null);
                        continue main;
                    } catch (InvalidNameException e) {
                        if (!ignoreNonExistingBaseDNs) {
                            throw e;
                        }
                        log.warn(e, null);
                        continue main;
                    }
                    try {
                        while (proceed && results.hasMore()) {
                            proceed = handler.handle(baseDN, results.next());
                        }
                    } finally {
                        results.close();
                    }
                    cookie = getResponseCookie(ctx.getResponseControls());
                } while (cookie != null);
            }
        } finally {
            ctx.close();
        }
    }

    private byte[] getResponseCookie(Control[] controls) {
        if (controls != null) {
            for (Control control : controls) {
                if (control instanceof PagedResultsResponseControl) {
                    PagedResultsResponseControl pagedControl = (PagedResultsResponseControl) control;
                    return pagedControl.getCookie();
                }
            }
        }
        return null;
    }
}
