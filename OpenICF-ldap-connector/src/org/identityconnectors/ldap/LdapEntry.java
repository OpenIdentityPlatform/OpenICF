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
package org.identityconnectors.ldap;

import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public abstract class LdapEntry {

    public static final Set<String> ENTRY_DN_ATTRS;

    static {
        Set<String> set = CollectionUtil.newCaseInsensitiveSet();
        set.add("entryDN");
        // These two are used throughout the adapter.
        set.add("dn");
        set.add("distinguishedName");
        ENTRY_DN_ATTRS = Collections.unmodifiableSet(set);
    }

    public static LdapEntry create(String baseDN, SearchResult result) {
        return new SearchResultBased(baseDN, result);
    }

    public static LdapEntry create(String entryDN, Attributes attributes) {
        return new Simple(entryDN, attributes);
    }

    public static boolean isDNAttribute(String attrID) {
        return ENTRY_DN_ATTRS.contains(attrID);
    }

    public abstract Attributes getAttributes();

    public abstract LdapName getDN();

    private static LdapName join(String name, String baseDN) {
        LdapName result = quietCreateLdapName(cleanNameToParse(name));
        if (baseDN != null) {
            LdapName contextName = quietCreateLdapName(baseDN);
            try {
                result.addAll(0, contextName);
            } catch (InvalidNameException e) {
                throw new ConnectorException(e);
            }
        }
        return result;
    }

    // Copied from adapter, but not clear why it is needed.
    private static String cleanNameToParse(String name) {
        String nameToParse = name;

        // Remove any extra leading double quote.
        if (nameToParse.startsWith("\"")) {
            nameToParse = nameToParse.substring(1, nameToParse.length());
        }

        // Remove any extra trailing double quote.
        if (nameToParse.endsWith("\"")) {
            nameToParse = nameToParse.substring(0, nameToParse.length() - 1);
        }

        return nameToParse;
    }

    private static final class SearchResultBased extends LdapEntry {

        private final String baseDN;
        private final SearchResult result;

        private Attributes attributes;
        private LdapName dn;

        public SearchResultBased(String baseDN, SearchResult result) {
            assert result != null;

            this.baseDN = baseDN;
            this.result = result;
        }

        @Override
        public Attributes getAttributes() {
            if (attributes == null) {
                attributes = new DNAttributes(this, result.getAttributes());
            }
            return attributes;
        }

        @Override
        public LdapName getDN() {
            if (dn == null) {
                if (result.isRelative()) {
                    dn = join(result.getName(), baseDN);
                } else {
                    // XXX probably need to filter the starting part of the URL.
                    dn = join(result.getName(), null);
                }
            }
            return dn;
        }
    }

    private static final class Simple extends LdapEntry {

        private final String entryDN;
        private final Attributes attributes;

        private LdapName dn;

        public Simple(String entryDN, Attributes attributes) {
            assert entryDN != null;
            assert attributes != null;

            this.entryDN = entryDN;
            this.attributes = new DNAttributes(this, attributes);
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }

        @Override
        public LdapName getDN() {
            if (dn == null) {
                dn = join(entryDN, null);
            }
            return dn;
        }
    }

    private final static class DNAttributes extends AppendingAttributes {

        private static final long serialVersionUID = 1L;

        private final LdapEntry ldapEntry;
        private final Map<String, Attribute> dnAttributes = CollectionUtil.newCaseInsensitiveMap();

        public DNAttributes(LdapEntry ldapEntry, Attributes delegate) {
            super(delegate);
            this.ldapEntry = ldapEntry;
        }

        @Override
        public Object clone() {
            return new DNAttributes(ldapEntry, (Attributes) delegate.clone());
        }

        @Override
        protected Attribute getAttributeToAppend(String attrID) {
            if (ENTRY_DN_ATTRS.contains(attrID)) {
                Attribute result = dnAttributes.get(attrID);
                if (result == null) {
                    result = new BasicAttribute(attrID, ldapEntry.getDN().toString());
                    dnAttributes.put(attrID, result);
                }
                return result;
            }
            return null;
        }

        @Override
        protected Set<String> getAttributeIDsToAppend() {
            return ENTRY_DN_ATTRS;
        }
    }
}
