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
import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveSet;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValues;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.PagedResultsControl;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.QualifiedUid;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.GroupHelper;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapEntry;
import org.identityconnectors.ldap.LdapPredefinedAttributes;

import com.sun.jndi.ldap.ctl.VirtualListViewControl;

/**
 * A class to perform an LDAP search against a {@link LdapConnection}.
 *
 * @author Andrei Badea
 */
public class LdapSearch {

    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final LdapFilter filter;
    private final OperationOptions options;
    private final GroupHelper groupHelper;

    public LdapSearch(LdapConnection conn, ObjectClass oclass, LdapFilter filter, OperationOptions options) {
        this.conn = conn;
        this.oclass = oclass;
        this.filter = filter;
        this.options = options;

        groupHelper = new GroupHelper(conn);
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
        final Set<String> attrsToGet = getAttributesToGet();
        LdapInternalSearch search = getInternalSearch(attrsToGet);
        search.execute(new SearchResultsHandler() {
            public boolean handle(String baseDN, SearchResult result) throws NamingException {
                return handler.handle(createConnectorObject(baseDN, result, attrsToGet));
            }
        });
    }

    /**
     * Executes the query against all configured base DNs and returns the first
     * {@link ConnectorObject} or {@code null}.
     */
    public final ConnectorObject getSingleResult() {
        final Set<String> attrsToGet = getAttributesToGet();
        final ConnectorObject[] results = new ConnectorObject[] { null };
        LdapInternalSearch search = getInternalSearch(attrsToGet);
        search.execute(new SearchResultsHandler() {
            public boolean handle(String baseDN, SearchResult result) throws NamingException {
                results[0] = createConnectorObject(baseDN, result, attrsToGet);
                return false;
            }
        });
        return results[0];
    }

    private LdapInternalSearch getInternalSearch(Set<String> attrsToGet) {
        // This is a bit tricky. If the LdapFilter has an entry DN,
        // we only need to look at that entry and check whether it matches
        // the native filter. Moreover, when looking at the entry DN
        // we must not throw exceptions if the entry DN does not exist or is
        // not valid -- just as no exceptions are thrown when the native
        // filter doesn't return any values.
        //
        // In the simple case when the LdapFilter has no entryDN, we
        // will just search over our base DNs looking for entries
        // matching the native filter.

        List<String> baseDNs;
        int searchScope;
        boolean ignoreNonExistingBaseDNs;

        String filterEntryDN = filter != null ? filter.getEntryDN() : null;
        if (filterEntryDN != null) {
            baseDNs = singletonList(filterEntryDN);
            searchScope = SearchControls.OBJECT_SCOPE;
            ignoreNonExistingBaseDNs = true;
        } else {
            baseDNs = getBaseDNs();
            searchScope = getLdapSearchScope();
            ignoreNonExistingBaseDNs = false;
        }

        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        Set<String> ldapAttrsToGet = getLdapAttributesToGet(attrsToGet);
        controls.setReturningAttributes(ldapAttrsToGet.toArray(new String[ldapAttrsToGet.size()]));
        controls.setSearchScope(searchScope);

        String nativeFilter = filter != null ? filter.getNativeFilter() : null;
        String userFilter = null;
        if (oclass.equals(ObjectClass.ACCOUNT)) {
            userFilter = conn.getConfiguration().getAccountSearchFilter();
        }
        return new LdapInternalSearch(conn, getSearchFilter(nativeFilter, userFilter), baseDNs, getSearchStrategy(), controls, ignoreNonExistingBaseDNs);
    }

    private Set<String> getLdapAttributesToGet(Set<String> attrsToGet) {
        Set<String> cleanAttrsToGet = newCaseInsensitiveSet();
        cleanAttrsToGet.addAll(attrsToGet);
        cleanAttrsToGet.remove(LdapPredefinedAttributes.LDAP_GROUPS_NAME);
        boolean posixGroups = cleanAttrsToGet.remove(LdapPredefinedAttributes.POSIX_GROUPS_NAME);
        Set<String> result = conn.getSchemaMapping().getLdapAttributes(oclass, cleanAttrsToGet, true);
        if (posixGroups) {
            result.add(GroupHelper.getPosixRefAttribute());
        }
        // For compatibility with the adapter, we do not ask the server for DN attributes,
        // such as entryDN; we compute them ourselves. Some servers might not support such attributes anyway.
        result.removeAll(LdapEntry.ENTRY_DN_ATTRS);
        return result;
    }

    /**
     * Creates a {@link ConnectorObject} based on the given search result. The
     * search result name is expected to be a relative one, thus the {@code
     * baseDN} parameter is needed in order to create the whole entry DN, which
     * is used to compute the connector object's name attribute.
     */
    private ConnectorObject createConnectorObject(String baseDN, SearchResult result, Set<String> attrsToGet) {
        LdapEntry entry = LdapEntry.create(baseDN, result);

        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(oclass);
        builder.setUid(conn.getSchemaMapping().createUid(oclass, entry));
        builder.setName(conn.getSchemaMapping().createName(oclass, entry));

        for (String attrName : attrsToGet) {
            Attribute attribute = null;
            if (LdapPredefinedAttributes.isLdapGroups(attrName)) {
                List<String> ldapGroups = groupHelper.getLdapGroups(entry.getDN().toString());
                attribute = AttributeBuilder.build(LdapPredefinedAttributes.LDAP_GROUPS_NAME, ldapGroups);
            } else if (LdapPredefinedAttributes.isPosixGroups(attrName)) {
                Set<String> posixRefAttrs = getStringAttrValues(entry.getAttributes(), GroupHelper.getPosixRefAttribute());
                List<String> posixGroups = groupHelper.getPosixGroups(posixRefAttrs);
                attribute = AttributeBuilder.build(LdapPredefinedAttributes.POSIX_GROUPS_NAME, posixGroups);
            } else {
                attribute = conn.getSchemaMapping().createAttribute(oclass, attrName, entry);
            }
            if (attribute != null) {
                builder.addAttribute(attribute);
            }
        }

        return builder.build();
    }

    /**
     * Creates the final search filter. It will be composed of an optional native filter, an optional
     * user filter, and the filters for all LDAP object classes for the searched ObjectClass.
     */
    private String getSearchFilter(String nativeFilter, String userFilter) {
        StringBuilder builder = new StringBuilder();
        Set<String> ldapClasses = conn.getSchemaMapping().getLdapClasses(oclass);
        boolean and = userFilter != null || ldapClasses.size() > 1 || nativeFilter != null;
        if (and) {
            builder.append("(&");
        }
        if (userFilter != null) {
            boolean enclose = userFilter.length() > 0 && userFilter.charAt(0) != '(';
            if (enclose) {
                builder.append('(');
            }
            builder.append(userFilter);
            if (enclose) {
                builder.append(')');
            }
        }
        for (String ldapClass : ldapClasses) {
            builder.append("(objectClass=");
            builder.append(ldapClass);
            builder.append(')');
        }
        if (nativeFilter != null) {
            builder.append(nativeFilter);
        }
        if (and) {
            builder.append(')');
        }
        return builder.toString();
    }

    private List<String> getBaseDNs() {
        List<String> result;
        QualifiedUid container = options.getContainer();
        String[] opBaseDNs = conn.getOptionsBaseDNs(options);
        if (container != null) {
            if (opBaseDNs.length > 0) {
                throw new ConnectorException("Should only specify one of OP_CONTAINER and OP_BASE_DNS");
            }
            result = singletonList(LdapSearches.findDN(conn, container));
        } else if (opBaseDNs.length > 0) {
            result = Arrays.asList(opBaseDNs);
        } else {
            result = Arrays.asList(conn.getConfiguration().getBaseContexts());
        }
        assert result != null;
        return result;
    }

    private LdapSearchStrategy getSearchStrategy() {
        LdapSearchStrategy strategy;
        if (ObjectClass.ACCOUNT.equals(oclass)) {
            // Only consider paged strategies for accounts, just as the adapter does.

            int pageSize = conn.getConfiguration().getBlockCount();
            boolean useBlocks = conn.getConfiguration().isUseBlocks();
            boolean usePagedResultsControl = conn.getConfiguration().isUsePagedResultControl();

            if (useBlocks && !usePagedResultsControl && conn.supportsControl(VirtualListViewControl.OID)) {
                // TODO: VLV index strategy.
                strategy = new SimplePagedSearchStrategy(pageSize);
            } else if (useBlocks && conn.supportsControl(PagedResultsControl.OID)) {
                strategy = new SimplePagedSearchStrategy(pageSize);
            } else {
                strategy = new DefaultSearchStrategy();
            }
        } else {
            strategy = new DefaultSearchStrategy();
        }
        return strategy;
    }

    private Set<String> getAttributesToGet() {
        Set<String> result;
        String[] attributesToGet = options.getAttributesToGet();
        if (attributesToGet != null) {
            result = newCaseInsensitiveSet();
            result.addAll(Arrays.asList(attributesToGet));
            removeNonReadableAttributes(result);
            result.add(Name.NAME);
        } else {
            // This should include Name.NAME, so no need to include it explicitly.
            result = conn.getSchemaMapping().getAttributesReturnedByDefault(oclass);
        }
        // Since Uid is not in the schema, but it is required to construct a ConnectorObject.
        result.add(Uid.NAME);
        return result;
    }

    private void removeNonReadableAttributes(Set<String> attributes) {
        // Since the groups attributes are fake attributes, we don't want to
        // send them to LdapSchemaMapping. This, for example, avoid an (unlikely)
        // conflict with a custom attribute defined in the server schema.
        boolean ldapGroups = attributes.remove(LdapPredefinedAttributes.LDAP_GROUPS_NAME);
        boolean posixGroups = attributes.remove(LdapPredefinedAttributes.POSIX_GROUPS_NAME);
        conn.getSchemaMapping().removeNonReadableAttributes(oclass, attributes);
        if (ldapGroups) {
            attributes.add(LdapPredefinedAttributes.LDAP_GROUPS_NAME);
        }
        if (posixGroups) {
            attributes.add(LdapPredefinedAttributes.POSIX_GROUPS_NAME);
        }
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
            throw new IllegalArgumentException("Invalid search scope " + scope);
        }
    }
}
