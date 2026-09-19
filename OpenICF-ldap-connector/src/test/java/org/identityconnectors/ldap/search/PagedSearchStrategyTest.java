/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.identityconnectors.ldap.search;

import java.util.Collections;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

/**
 * The paged-results cookie is round-tripped through the client between
 * search calls, so a tampered or corrupted one must fail clearly rather than
 * with a bare NumberFormatException.
 */
public class PagedSearchStrategyTest {

    @Test(expectedExceptions = ConnectorException.class)
    public void rejectsACookieWithAMalformedContextIndex() throws Exception {
        PagedSearchStrategy strategy = new PagedSearchStrategy(10, "AAAA:not-a-number", 0, null,
                new org.identityconnectors.framework.common.objects.SortKey[0]);
        strategy.doSearch(null, Collections.<String> singletonList("dc=example,dc=com"), "(uid=*)",
                new javax.naming.directory.SearchControls(), null);
    }
}
