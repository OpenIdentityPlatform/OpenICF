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

import static org.identityconnectors.common.StringUtil.isNotBlank;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortResponseControl;

import org.identityconnectors.common.logging.Log;

import com.sun.jndi.ldap.ctl.VirtualListViewControl;
import com.sun.jndi.ldap.ctl.VirtualListViewResponseControl;

public class VlvIndexSearchStrategy extends LdapSearchStrategy {

    private static Log log;

    private final String vlvIndexAttr;
    private final int blockSize;

    private int index;
    private int lastListSize;
    private byte[] cookie;

    static synchronized void setLog(Log log) {
        VlvIndexSearchStrategy.log = log;
    }

    synchronized static Log getLog() {
        if (log == null) {
            log = Log.getLog(VlvIndexSearchStrategy.class);
        }
        return log;
    }

    public VlvIndexSearchStrategy(String vlvSortAttr, int blockSize) {
        this.vlvIndexAttr = isNotBlank(vlvSortAttr) ? vlvSortAttr : "uid";
        this.blockSize = blockSize;
    }

    @Override
    public void doSearch(LdapContext initCtx, List<String> baseDNs, String query, SearchControls searchControls, SearchResultsHandler handler) throws IOException, NamingException {
        getLog().ok("Searching in {0} with filter {1} and {2}", baseDNs, query, searchControlsToString(searchControls));

        Iterator<String> baseDNIter = baseDNs.iterator();
        boolean proceed = true;

        LdapContext ctx = initCtx.newInstance(null);
        try {
            while (baseDNIter.hasNext() && proceed) {
                proceed = searchBaseDN(ctx, baseDNIter.next(), query, searchControls, handler);
            }
        } finally {
            ctx.close();
        }
    }

    private boolean searchBaseDN(LdapContext ctx, String baseDN, String query, SearchControls searchControls, SearchResultsHandler handler) throws IOException, NamingException {
        getLog().ok("Searching in {0}", baseDN);
        
        index = 1;
        lastListSize = 0;
        cookie = null;

        String lastResultName = null;

        for (;;) {
            SortControl sortControl = new SortControl(vlvIndexAttr, Control.CRITICAL);
            
            int afterCount = blockSize - 1;
            VirtualListViewControl vlvControl = new VirtualListViewControl(index, lastListSize, 0, afterCount, Control.CRITICAL);
            vlvControl.setContextID(cookie);
            
            getLog().ok("New search: target = {0}, afterCount = {1}", index, afterCount);
            ctx.setRequestControls(new Control[] { sortControl, vlvControl });

            // Need to process the response controls, which are available after
            // all results have been processed, before sending anything to the caller
            // (because processing the response controls might throw exceptions that
            // invalidate anything we might have sent otherwise).
            // So storing the results before actually sending them to the handler.
            List<SearchResult> resultList = new ArrayList<SearchResult>(blockSize);

            NamingEnumeration<SearchResult> results = ctx.search(baseDN, query, searchControls);
            try {
                while (results.hasMore()) {
                    SearchResult result = results.next();

                    boolean overlap = false;
                    if (lastResultName != null) {
                        if (lastResultName.equals(result.getName())) {
                            getLog().warn("Working around rounding error overlap at index " + index);
                            overlap = true;
                        }
                        lastResultName = null;
                    }

                    if (!overlap) {
                        resultList.add(result);
                    }
                }
            } finally {
                results.close();
            }

            processResponseControls(ctx.getResponseControls());

            SearchResult result = null;
            Iterator<SearchResult> resultIter = resultList.iterator();
            while (resultIter.hasNext()) {
                result = resultIter.next();
                index++;
                if (!handler.handle(baseDN, result)) {
                    return false;
                }
            }
            if (result != null) {
                lastResultName = result.getName();
            }

            if (index > lastListSize) {
                break;
            }

            // DSEE seems to only have a single VLV index (although it claims to support more).
            // It returns at the server content count the sum of sizes of all indexes,
            // but it only returns the entries in the base context we are asking for.
            // So, in this case, index will never reach lastListSize. To avoid an infinite loop,
            // ending search if we received no results in the last iteration.
            if (resultList.isEmpty()) {
                getLog().warn("Ending search because received no results");
                break;
            }
        }
        return true;
    }

    private void processResponseControls(Control[] controls) throws NamingException {
        if (controls != null) {
            for (Control control : controls) {
                if (control instanceof SortResponseControl) {
                    SortResponseControl sortControl = (SortResponseControl) control;
                    if (!sortControl.isSorted() || (sortControl.getResultCode() != 0)) {
                        throw sortControl.getException();
                    }
                }
                if (control instanceof VirtualListViewResponseControl) {
                    VirtualListViewResponseControl vlvControl = (VirtualListViewResponseControl) control;
                    if (vlvControl.getResultCode() == 0) {
                        lastListSize = vlvControl.getListSize();
                        cookie = vlvControl.getContextID();
                        getLog().ok("Response control: lastListSize = {0}", lastListSize);
                    } else {
                        throw vlvControl.getException();
                    }
                }
            }
        }
    }
}
