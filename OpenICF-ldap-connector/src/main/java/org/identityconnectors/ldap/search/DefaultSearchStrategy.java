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
 * "Portions Copyrighted 2014 ForgeRock AS"
 */
package org.identityconnectors.ldap.search;

import java.io.IOException;
import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;

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
import javax.naming.ldap.SortControl;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.SortKey;

public class DefaultSearchStrategy extends LdapSearchStrategy {

    private static final Log log = Log.getLog(DefaultSearchStrategy.class);

    private final boolean ignoreNonExistingBaseDNs;
    private final SortKey[] sortKeys;

    public DefaultSearchStrategy(boolean ignoreNonExistingBaseDNs) {
        this.ignoreNonExistingBaseDNs = ignoreNonExistingBaseDNs;
        this.sortKeys = null;
    }

    public DefaultSearchStrategy(boolean ignoreNonExistingBaseDNs, SortKey[] sortKeys) {
        this.ignoreNonExistingBaseDNs = ignoreNonExistingBaseDNs;
        this.sortKeys = sortKeys;
    }

    @Override
    public void doSearch(LdapContext initCtx, List<String> baseDNs, String query, SearchControls searchControls, LdapSearchResultsHandler handler) throws IOException,NamingException {
        log.ok("Searching in {0} with filter {1} and {2}", baseDNs, query, searchControlsToString(searchControls));

        Iterator<String> baseDNIter = baseDNs.iterator();
        boolean proceed = true;
        boolean isSorted = false;
        LdapContext ctx = initCtx;
        
        if (sortKeys != null && sortKeys.length > 0){
            javax.naming.ldap.SortKey[] skis = new javax.naming.ldap.SortKey[sortKeys.length];
            for(int i = 0; i < sortKeys.length; i++){
                skis[i] = new javax.naming.ldap.SortKey(sortKeys[i].getField(),sortKeys[i].isAscendingOrder(),null);
            }
            // We don't want to make this critical... better return unsorted results than nothing.
            ctx = initCtx.newInstance(new Control[]{new SortControl(skis, Control.NONCRITICAL)});
            isSorted = true;
        }

        while (baseDNIter.hasNext() && proceed) {
            String baseDN = baseDNIter.next();

            NamingEnumeration<SearchResult> results;
            try {
                results = ctx.search((escapeDNValueOfJNDIReservedChars(baseDN)), query, searchControls);
            } catch (NameNotFoundException e) {
                if (!ignoreNonExistingBaseDNs) {
                    throw e;
                }
                log.info("Entry {0} does not exist", baseDN);
                continue;
            } catch (InvalidNameException e) {
                if (!ignoreNonExistingBaseDNs) {
                    throw e;
                }
                log.info(e, null);
                continue;
            }
            try {
                while (proceed && results.hasMore()) {
                    proceed = handler.handle(baseDN, results.next());
                }
            } finally {
                results.close();
                if (isSorted){
                    ctx.close();
                }
            }
        }
    }
}
