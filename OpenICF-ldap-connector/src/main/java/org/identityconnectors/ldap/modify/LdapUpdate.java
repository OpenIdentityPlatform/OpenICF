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
package org.identityconnectors.ldap.modify;

import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.common.CollectionUtil.newSet;
import static org.identityconnectors.common.CollectionUtil.nullAsEmpty;
import static org.identityconnectors.ldap.LdapUtil.checkedListByFilter;
import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.identityconnectors.common.Pair;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.GroupHelper;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapModifyOperation;
import org.identityconnectors.ldap.LdapConstants;
import org.identityconnectors.ldap.GroupHelper.GroupMembership;
import org.identityconnectors.ldap.GroupHelper.Modification;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute.Accessor;
import org.identityconnectors.ldap.search.LdapSearches;

public class LdapUpdate extends LdapModifyOperation {

    private final ObjectClass oclass;

    private Uid uid;

    public LdapUpdate(LdapConnection conn, ObjectClass oclass, Uid uid) {
        super(conn);
        this.oclass = oclass;
        this.uid = uid;
    }

    public Uid update(Set<Attribute> attrs) {
        String entryDN = LdapSearches.findEntryDN(conn, oclass, uid);
        PosixGroupMember posixMember = new PosixGroupMember(entryDN);

        // Extract the Name attribute if any, to be used to rename the entry later.
        Set<Attribute> updateAttrs = attrs;
        Name newName = (Name) AttributeUtil.find(Name.NAME, attrs);
        String newEntryDN = null;
        if (newName != null) {
            updateAttrs = newSet(attrs);
            updateAttrs.remove(newName);
            newEntryDN = conn.getSchemaMapping().getEntryDN(oclass, newName);
        }

        List<String> ldapGroups = getStringListValue(updateAttrs, LdapConstants.LDAP_GROUPS_NAME);
        List<String> posixGroups = getStringListValue(updateAttrs, LdapConstants.POSIX_GROUPS_NAME);

        Pair<Attributes, GuardedPasswordAttribute> attrToModify = getAttributesToModify(updateAttrs);
        Attributes ldapAttrs = attrToModify.first;

        // If we are removing all POSIX ref attributes, check they are not used
        // in POSIX groups. Note it is OK to update the POSIX ref attribute instead of
        // removing them -- we will update the groups to refer to the new attributes.
        Set<String> newPosixRefAttrs = getAttributeValues(GroupHelper.getPosixRefAttribute(), quietCreateLdapName(newEntryDN != null ? newEntryDN : entryDN), ldapAttrs);
        if (newPosixRefAttrs != null && newPosixRefAttrs.isEmpty()) {
            checkRemovedPosixRefAttrs(posixMember.getPosixRefAttributes(), posixMember.getPosixGroupMemberships());
        }

        // Rename the entry if needed.
        String oldEntryDN = null;
        if (newName != null) {
            if (newPosixRefAttrs != null && conn.getConfiguration().isMaintainPosixGroupMembership() || posixGroups != null) {
                posixMember.getPosixRefAttributes();
            }
            oldEntryDN = entryDN;
            entryDN = conn.getSchemaMapping().rename(oclass, oldEntryDN, newName);
        }

        // Update the attributes.
        modifyAttributes(entryDN, attrToModify, DirContext.REPLACE_ATTRIBUTE);

        // Update the LDAP groups.
        Modification<GroupMembership> ldapGroupMod = new Modification<GroupMembership>();
        if (oldEntryDN != null && conn.getConfiguration().isMaintainLdapGroupMembership()) {
            Set<GroupMembership> members = groupHelper.getLdapGroupMemberships(oldEntryDN);
            ldapGroupMod.removeAll(members);
            for (GroupMembership member : members) {
                ldapGroupMod.add(new GroupMembership(entryDN, member.getGroupDN()));
            }
        }
        if (ldapGroups != null) {
            Set<GroupMembership> members = groupHelper.getLdapGroupMemberships(entryDN);
            ldapGroupMod.removeAll(members);
            ldapGroupMod.clearAdded(); // Since we will be replacing with the new groups.
            for (String ldapGroup : ldapGroups) {
                ldapGroupMod.add(new GroupMembership(entryDN, ldapGroup));
            }
        }
        groupHelper.modifyLdapGroupMemberships(ldapGroupMod);

        // Update the POSIX groups.
        Modification<GroupMembership> posixGroupMod = new Modification<GroupMembership>();
        if (newPosixRefAttrs != null && conn.getConfiguration().isMaintainPosixGroupMembership()) {
            Set<String> removedPosixRefAttrs = new HashSet<String>(posixMember.getPosixRefAttributes());
            removedPosixRefAttrs.removeAll(newPosixRefAttrs);
            Set<GroupMembership> members = posixMember.getPosixGroupMembershipsByAttrs(removedPosixRefAttrs);
            posixGroupMod.removeAll(members);
            if (!members.isEmpty()) {
                String firstPosixRefAttr = getFirstPosixRefAttr(entryDN, newPosixRefAttrs);
                for (GroupMembership member : members) {
                    posixGroupMod.add(new GroupMembership(firstPosixRefAttr, member.getGroupDN()));
                }
            }
        }
        if (posixGroups != null) {
            Set<GroupMembership> members = posixMember.getPosixGroupMemberships();
            posixGroupMod.removeAll(members);
            posixGroupMod.clearAdded(); // Since we will be replacing with the new groups.
            if (!posixGroups.isEmpty()) {
                String firstPosixRefAttr = getFirstPosixRefAttr(entryDN, newPosixRefAttrs);
                for (String posixGroup : posixGroups) {
                    posixGroupMod.add(new GroupMembership(firstPosixRefAttr, posixGroup));
                }
            }
        }
        groupHelper.modifyPosixGroupMemberships(posixGroupMod);

        return conn.getSchemaMapping().createUid(oclass, entryDN);
    }

    public Uid addAttributeValues(Set<Attribute> attrs) {
        String entryDN = LdapSearches.findEntryDN(conn, oclass, uid);
        PosixGroupMember posixMember = new PosixGroupMember(entryDN);

        Pair<Attributes, GuardedPasswordAttribute> attrsToModify = getAttributesToModify(attrs);
        modifyAttributes(entryDN, attrsToModify, DirContext.ADD_ATTRIBUTE);

        List<String> ldapGroups = getStringListValue(attrs, LdapConstants.LDAP_GROUPS_NAME);
        if (!isEmpty(ldapGroups)) {
            groupHelper.addLdapGroupMemberships(entryDN, ldapGroups);
        }

        List<String> posixGroups = getStringListValue(attrs, LdapConstants.POSIX_GROUPS_NAME);
        if (!isEmpty(posixGroups)) {
            Set<String> posixRefAttrs = posixMember.getPosixRefAttributes();
            String posixRefAttr = getFirstPosixRefAttr(entryDN, posixRefAttrs);
            groupHelper.addPosixGroupMemberships(posixRefAttr, posixGroups);
        }

        return uid;
    }

    public Uid removeAttributeValues(Set<Attribute> attrs) {
        String entryDN = LdapSearches.findEntryDN(conn, oclass, uid);
        PosixGroupMember posixMember = new PosixGroupMember(entryDN);

        Pair<Attributes, GuardedPasswordAttribute> attrsToModify = getAttributesToModify(attrs);
        Attributes ldapAttrs = attrsToModify.first;

        Set<String> removedPosixRefAttrs = getAttributeValues(GroupHelper.getPosixRefAttribute(), null, ldapAttrs);
        if (!isEmpty(removedPosixRefAttrs)) {
            checkRemovedPosixRefAttrs(removedPosixRefAttrs, posixMember.getPosixGroupMemberships());
        }

        modifyAttributes(entryDN, attrsToModify, DirContext.REMOVE_ATTRIBUTE);

        List<String> ldapGroups = getStringListValue(attrs, LdapConstants.LDAP_GROUPS_NAME);
        if (!isEmpty(ldapGroups)) {
            groupHelper.removeLdapGroupMemberships(entryDN, ldapGroups);
        }

        List<String> posixGroups = getStringListValue(attrs, LdapConstants.POSIX_GROUPS_NAME);
        if (!isEmpty(posixGroups)) {
            Set<GroupMembership> members = posixMember.getPosixGroupMembershipsByGroups(posixGroups);
            groupHelper.removePosixGroupMemberships(members);
        }

        return uid;
    }

    private void checkRemovedPosixRefAttrs(Set<String> removedPosixRefAttrs, Set<GroupMembership> memberships) {
        for (GroupMembership membership : memberships) {
            if (removedPosixRefAttrs.contains(membership.getMemberRef())) {
                throw new ConnectorException(conn.format("cannotRemoveBecausePosixMember", GroupHelper.getPosixRefAttribute()));
            }
        }
    }

    private Pair<Attributes, GuardedPasswordAttribute> getAttributesToModify(Set<Attribute> attrs) {
        BasicAttributes ldapAttrs = new BasicAttributes();
        GuardedPasswordAttribute pwdAttr = null;
        for (Attribute attr : attrs) {
            javax.naming.directory.Attribute ldapAttr = null;
            if (attr.is(Uid.NAME)) {
                throw new IllegalArgumentException("Unable to modify an object's uid");
            } else if (attr.is(Name.NAME)) {
                // Such a change would have been handled in update() above.
                throw new IllegalArgumentException("Unable to modify an object's name");
            } else if (LdapConstants.isLdapGroups(attr.getName())) {
                // Handled elsewhere.
            } else if (LdapConstants.isPosixGroups(attr.getName())) {
                // Handled elsewhere.
            } else if (attr.is(OperationalAttributes.PASSWORD_NAME)) {
                pwdAttr = conn.getSchemaMapping().encodePassword(oclass, attr);
            } else {
                ldapAttr = conn.getSchemaMapping().encodeAttribute(oclass, attr);
            }
            if (ldapAttr != null) {
                javax.naming.directory.Attribute existingAttr = ldapAttrs.get(ldapAttr.getID());
                if (existingAttr != null) {
                    try {
                        NamingEnumeration<?> all = ldapAttr.getAll();
                        while (all.hasMoreElements()) {
                            existingAttr.add(all.nextElement());
                        }
                    } catch (NamingException e) {
                        throw new ConnectorException(e);
                    }
                } else {
                    ldapAttrs.put(ldapAttr);
                }
            }
        }
        return new Pair<Attributes, GuardedPasswordAttribute>(ldapAttrs, pwdAttr);
    }

    private void modifyAttributes(final String entryDN, Pair<Attributes, GuardedPasswordAttribute> attrs, final int ldapModifyOp) {
        final List<ModificationItem> modItems = new ArrayList<ModificationItem>(attrs.first.size());
        NamingEnumeration<? extends javax.naming.directory.Attribute> attrEnum = attrs.first.getAll();
        while (attrEnum.hasMoreElements()) {
            modItems.add(new ModificationItem(ldapModifyOp, attrEnum.nextElement()));
        }

        if (attrs.second != null) {
            attrs.second.access(new Accessor() {
                public void access(javax.naming.directory.Attribute passwordAttr) {
                    // Do not add the password to the result Attributes because
                    // it is a guarded value.
                    hashPassword(passwordAttr, entryDN);
                    modItems.add(new ModificationItem(ldapModifyOp, passwordAttr));
                    modifyAttributes(entryDN, modItems);
                }
            });
        } else {
            modifyAttributes(entryDN, modItems);
        }
    }

    private void modifyAttributes(String entryDN, List<ModificationItem> modItems) {
        try {
            conn.getInitialContext().modifyAttributes(entryDN, modItems.toArray(new ModificationItem[modItems.size()]));
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    private List<String> getStringListValue(Set<Attribute> attrs, String attrName) {
        Attribute attr = AttributeUtil.find(attrName, attrs);
        if (attr != null) {
            return checkedListByFilter(nullAsEmpty(attr.getValue()), String.class);
        }
        return null;
    }
}
