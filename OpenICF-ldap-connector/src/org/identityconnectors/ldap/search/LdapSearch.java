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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.QualifiedUid;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapEntry;

/**
 * A class to perform an LDAP search against a {@link LdapConnection}.
 *
 * @author Andrei Badea
 */
public class LdapSearch {

    private static final Log log = Log.getLog(LdapSearch.class);

    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final OperationOptions options;

    private final Set<String> attrsToGet;
    private final LdapInternalSearch search;

    public LdapSearch(LdapConnection conn, ObjectClass oclass, String query, OperationOptions options) {
        this.conn = conn;
        this.oclass = oclass;
        this.options = options;
        attrsToGet = getAttributesToGet();
        search = new LdapInternalSearch(conn, restrictQueryToObjectClass(query), getBaseDNs(), getSearchControls());
    }

    /**
     * Performs the search and passes the resulting {@link ConnectorObject}s to
     * the given handler.
     *
     * @param handler
     *            the handler.
     * @throws NamingException
     *             if a JNDI exception occurs.
     */
    public final void execute(final ResultsHandler handler) {
        search.execute(new SearchResultsHandler() {
            public boolean handle(String baseDN, SearchResult result) throws NamingException {
                return handler.handle(createConnectorObject(baseDN, result));
            }
        });
    }

    /**
     * Executes the query against all configured base DNs and returns the first
     * {@link ConnectorObject} or {@code null}.
     */
    public final ConnectorObject getSingleResult() {
        final ConnectorObject[] results = new ConnectorObject[] { null };
        search.execute(new SearchResultsHandler() {
            public boolean handle(String baseDN, SearchResult result) throws NamingException {
                results[0] = createConnectorObject(baseDN, result);
                return false;
            }
        });
        return results[0];
    }

    /**
     * Creates a {@link ConnectorObject} based on the given search result. The
     * search result name is expected to be a relative one, thus the {@code
     * baseDN} parameter is needed in order to create the whole entry DN, which
     * is used to compute the connector object's name attribute.
     */
    private ConnectorObject createConnectorObject(String baseDN, SearchResult result) {
        LdapEntry entry = LdapEntry.create(baseDN, result);

        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(oclass);
        builder.setUid(conn.getSchemaMapping().createUid(oclass, entry));
        builder.setName(conn.getSchemaMapping().createName(oclass, entry));

        for (String attrName : attrsToGet) {
            Attribute attribute = conn.getSchemaMapping().createAttribute(oclass, attrName, entry);
            if (attribute != null) {
                builder.addAttribute(attribute);
            }
        }

        return builder.build();
    }

    /**
     * Creates a query whose results will be based on the passed query, but of
     * the object class specified in the {@link #oclass} field.
     */
    private String restrictQueryToObjectClass(String query) {
        StringBuilder builder = new StringBuilder();
        if (query != null) {
            builder.append("(&");
        }
        builder.append("(objectClass=");
        builder.append(conn.getSchemaMapping().getLdapClass(oclass));
        builder.append(')');
        if (query != null) {
            builder.append(query);
            builder.append(')');
        }
        return builder.toString();
    }

    /**
     * Processes the operation options and initializes the {@link baseDNs} and
     * {@link controls} fields accordingly.
     */
    private SearchControls getSearchControls() {
        SearchControls result = LdapInternalSearch.createDefaultSearchControls();

        Set<String> ldapAttrsToGet = conn.getSchemaMapping().getLdapAttributes(oclass, attrsToGet, true);
        result.setReturningAttributes(ldapAttrsToGet.toArray(new String[ldapAttrsToGet.size()]));
        result.setSearchScope(getLdapSearchScope());

        return result;
    }

    private List<String> getBaseDNs() {
        List<String> result;
        QualifiedUid container = options.getContainer();
        String[] opBaseDNs = conn.getOptionsBaseDNs(options);
        if (container != null) {
            result = Collections.singletonList(LdapSearches.findDN(conn, container.getObjectClass(), container.getUid()));
            if (opBaseDNs.length > 0) {
                throw new ConnectorException("Should only specify one of OP_CONTAINER and OP_BASE_DNS");
            }
        } else if (opBaseDNs.length > 0) {
            result = Arrays.asList(opBaseDNs);
        } else {
            result = Arrays.asList(conn.getConfiguration().getBaseDNs());
        }
        assert result != null;
        return result;
    }

    private Set<String> getAttributesToGet() {
        Set<String> result;
        String[] attributesToGet = options.getAttributesToGet();
        if (attributesToGet != null) {
            result = CollectionUtil.newCaseInsensitiveSet();
            result.addAll(Arrays.asList(attributesToGet));
            conn.getSchemaMapping().removeNonReadableAttributes(oclass, result);
            result.add(Name.NAME);
        } else {
            // This should include Name.NAME, so no need to include it explicitly.
            result = conn.getSchemaMapping().getAttributesReturnedByDefault(oclass);
        }
        // Since Uid is not in the schema, but it is required to construct a ConnectorObject.
        result.add(Uid.NAME);
        return result;
    }

    private int getLdapSearchScope() {
        String scope = options.getScope();
        if (OperationOptions.SCOPE_OBJECT.equals(scope)) {
            return SearchControls.OBJECT_SCOPE;
        } else if (OperationOptions.SCOPE_ONE_LEVEL.equals(scope)) {
            return SearchControls.ONELEVEL_SCOPE;
        } else if (OperationOptions.SCOPE_SUBTREE.equals(scope) || scope == null) {
            return SearchControls.SUBTREE_SCOPE;
        } else {
            log.warn("Unknown search scope {0}", scope);
            return SearchControls.SUBTREE_SCOPE;
        }
    }
}
