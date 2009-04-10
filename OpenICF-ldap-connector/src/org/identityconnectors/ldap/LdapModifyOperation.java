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

import static java.util.Collections.min;
import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.ldap.LdapUtil.addStringAttrValues;
import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.ldap.GroupHelper.GroupMembership;
import org.identityconnectors.ldap.search.LdapSearches;

public abstract class LdapModifyOperation {

    protected final LdapConnection conn;
    protected final GroupHelper groupHelper;

    public LdapModifyOperation(LdapConnection conn) {
        this.conn = conn;
        groupHelper = new GroupHelper(conn);
    }

    protected final static Set<String> getAttributeValues(String attrName, LdapName entryDN, Attributes attrs) {
        Set<String> result = new HashSet<String>();
        if (entryDN != null && !entryDN.isEmpty()) {
            Rdn rdn = entryDN.getRdn(entryDN.size() - 1);
            addStringAttrValues(rdn.toAttributes(), attrName, result);
        }
        Attribute attr = attrs.get(attrName);
        if (attr != null) {
            try {
                NamingEnumeration<?> attrEnum = attr.getAll();
                while (attrEnum.hasMoreElements()) {
                    result.add((String) attrEnum.nextElement());
                }
            } catch (NamingException e) {
                throw new ConnectorException(e);
            }
            return result;
        }
        // If we got here, the attribute was not in the Attributes instance. So if the
        // result is empty, that means the attribute is not present in either
        // the entry DN or the attribute set.
        return result.isEmpty() ? null : result;
    }

    protected final static String getFirstPosixRefAttr(String entryDN, Set<String> posixRefAttrs) {
        if (isEmpty(posixRefAttrs)) {
            throw new ConnectorException("Unable to add entry " + entryDN + " to POSIX groups because it doesn't have an " + GroupHelper.getPosixRefAttribute() + " attribute");
        }
        return min(posixRefAttrs);
    }

    /**
     * Holds the POSIX ref attributes and the respective group
     * memberships. Retrieves them lazily so that they are only
     * retrieved once, when they are needed.
     */
    public final class PosixGroupMember {

        private final String entryDN;

        private LdapEntry entry;
        private Set<String> posixRefAttrs;
        private Set<GroupMembership> posixGroupMemberships;

        public PosixGroupMember(String entryDN) {
            this.entryDN = entryDN;
        }

        public Set<GroupMembership> getPosixGroupMemberships() {
            if (posixGroupMemberships == null) {
                posixGroupMemberships = groupHelper.getPosixGroupMemberships(getPosixRefAttributes());
            }
            return posixGroupMemberships;
        }

        public Set<GroupMembership> getPosixGroupMembershipsByAttrs(Set<String> posixRefAttrs) {
            Set<GroupMembership> result = new HashSet<GroupMembership>();
            for (GroupMembership member : getPosixGroupMemberships()) {
                if (posixRefAttrs.contains(member.getMemberRef())) {
                    result.add(member);
                }
            }
            return result;
        }

        public Set<GroupMembership> getPosixGroupMembershipsByGroups(List<String> groupDNs) {
            Set<LdapName> groupNames = new HashSet<LdapName>();
            for (String groupDN : groupDNs) {
                groupNames.add(quietCreateLdapName(groupDN));
            }
            Set<GroupMembership> result = new HashSet<GroupMembership>();
            for (GroupMembership member : getPosixGroupMemberships()) {
                if (groupNames.contains(quietCreateLdapName(member.getGroupDN()))) {
                    result.add(member);
                }
            }
            return result;
        }

        public Set<String> getPosixRefAttributes() {
            if (posixRefAttrs == null) {
                posixRefAttrs = getAttributeValues(GroupHelper.getPosixRefAttribute(), null, getLdapEntry().getAttributes());
            }
            return posixRefAttrs;
        }

        private LdapEntry getLdapEntry() {
            if (entry == null) {
                entry = LdapSearches.findEntry(conn, quietCreateLdapName(entryDN), GroupHelper.getPosixRefAttribute());
                if (entry == null) {
                    throw new ConnectorException("Entry " + entryDN + " not found");
                }
            }
            return entry;
        }
    }
}
