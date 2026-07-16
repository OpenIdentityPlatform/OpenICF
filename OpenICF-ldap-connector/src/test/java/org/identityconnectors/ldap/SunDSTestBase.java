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
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.ldap;

import static java.util.Collections.reverse;
import static java.util.Collections.sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;

import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.ldap.search.DefaultSearchStrategy;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.LdapSearchResultsHandler;
import org.identityconnectors.test.common.TestHelpers;

/**
 * Base for the tests written against Sun DSEE, run against the embedded OpenDJ instance.
 *
 * OpenDJ serves the change log these tests read and supports the VLV searches they make, so it
 * stands in for the Sun DSEE they were pointed at through sunds.* properties, which nobody can
 * supply any more.
 *
 * The tests here empty their base context and repopulate it, so they get one of their own rather
 * than the contexts the other tests read. Whatever they leave behind is cleared up anyway: the
 * server stops when the class ends, and the next start puts the data back as it was.
 */
public class SunDSTestBase extends LdapConnectorTestBase {

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    public static LdapConfiguration newConfiguration() {
        LdapConfiguration config = new LdapConfiguration();
        config.setConnectorMessages(TestHelpers.createDummyMessages());
        config.setHost("localhost");
        config.setPort(PORT);
        config.setPrincipal(ADMIN_DN);
        config.setCredentials(ADMIN_PASSWORD);
        config.setBaseContexts(SMALL_COMPANY_DN);
        config.setUidAttribute("entryDN");
        config.setReadSchema(false); // To be compatible with IdM.
        config.validate();
        return config;
    }

    public static void cleanupBaseContext(LdapConnection conn) throws NamingException {
        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        LdapInternalSearch search = new LdapInternalSearch(conn, null, Arrays.asList(conn.getConfiguration().getBaseContexts()),
                new DefaultSearchStrategy(false), controls);
        final List<LdapName> entryDNs = new ArrayList<LdapName>();
        search.execute(new LdapSearchResultsHandler() {
            public boolean handle(String baseDN, SearchResult result) throws NamingException {
                entryDNs.add(LdapEntry.create(baseDN, result).getDN());
                return true;
            }
        });
        entryDNs.removeAll(conn.getConfiguration().getBaseContextsAsLdapNames());
        sort(entryDNs);
        reverse(entryDNs); // Cf. LdapName.compareTo().
        for (LdapName entryDN : entryDNs) {
            conn.getInitialContext().destroySubcontext(entryDN);
        }
    }

    public static ConnectorFacade newFacade(LdapConfiguration cfg) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(LdapConnector.class, cfg);
        return factory.newInstance(impl);
    }
}
