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
 * 
 * "Portions Copyrighted 2013-2015 Forgerock AS"
 */
package org.identityconnectors.ldap.modify;

import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.common.CollectionUtil.newSet;
import static org.identityconnectors.common.CollectionUtil.nullAsEmpty;
import static org.identityconnectors.ldap.LdapUtil.checkedListByFilter;
import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;
import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;
import static org.identityconnectors.ldap.LdapUtil.normalizeLdapString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.naming.NameAlreadyBoundException;
import java.text.ParseException;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapContext;

import org.identityconnectors.common.Pair;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.ADGroupType;
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.GroupHelper;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapModifyOperation;
import org.identityconnectors.ldap.LdapConstants;
import org.identityconnectors.ldap.GroupHelper.GroupMembership;
import org.identityconnectors.ldap.GroupHelper.Modification;
import org.identityconnectors.ldap.LdapAuthenticate;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute.Accessor;
import org.identityconnectors.ldap.search.LdapSearches;

public class LdapUpdate extends LdapModifyOperation {

    private final ObjectClass oclass;
    private final OperationOptions options;
    private final Uid uid;

    private static final Log logger = Log.getLog(LdapUpdate.class);

    public LdapUpdate(LdapConnection conn, ObjectClass oclass, Uid uid, OperationOptions options) {
        super(conn);
        this.oclass = oclass;
        this.uid = uid;
        this.options = options;
    }

    public Uid update(Set<Attribute> attrs) {
        String entryDN = escapeDNValueOfJNDIReservedChars(LdapSearches.getEntryDN(conn, oclass, uid));
        PosixGroupMember posixMember = new PosixGroupMember(entryDN);
        LdapContext runAsContext = null;

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

        Pair<Attributes, Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>> attrToModify = getAttributesToModify(updateAttrs);
        Attributes ldapAttrs = attrToModify.first;

        // If we are removing all POSIX ref attributes, check they are not used
        // in POSIX groups. Note it is OK to update the POSIX ref attribute instead of
        // removing them -- we will update the groups to refer to the new attributes.
        Set<String> newPosixRefAttrs = getAttributeValues(GroupHelper.getPosixRefAttribute(), quietCreateLdapName(newEntryDN != null ? newEntryDN : entryDN), ldapAttrs);
        if (newPosixRefAttrs != null && newPosixRefAttrs.isEmpty()) {
            checkRemovedPosixRefAttrs(posixMember.getPosixRefAttributes(), posixMember.getPosixGroupMemberships());
        }

        if (StringUtil.isNotBlank(options.getRunAsUser())) {
            String dn = new LdapAuthenticate(conn, oclass, options.getRunAsUser(), options).getDn();
            runAsContext = conn.getRunAsContext(dn, options.getRunWithPassword());
        }

        try {
            // Rename the entry if needed.
            String oldEntryDN = null;
            if ((newName != null) && (!normalizeLdapString(entryDN).equalsIgnoreCase(normalizeLdapString(newEntryDN)))) {
                if (newPosixRefAttrs != null && conn.getConfiguration().isMaintainPosixGroupMembership() || posixGroups != null) {
                    posixMember.getPosixRefAttributes();
                }
                oldEntryDN = entryDN;
                if (runAsContext == null) {
                    conn.getInitialContext().rename(oldEntryDN, newEntryDN);
                } else {
                    runAsContext.rename(oldEntryDN, newEntryDN);
                }
                entryDN = newEntryDN;
            }
            // Update the attributes.
            modifyAttributes(entryDN, attrToModify, DirContext.REPLACE_ATTRIBUTE, runAsContext);

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
            groupHelper.modifyLdapGroupMemberships(ldapGroupMod, runAsContext);

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
            groupHelper.modifyPosixGroupMemberships(posixGroupMod, runAsContext);
        } catch (NameAlreadyBoundException e) {
            throw new AlreadyExistsException(e);
        } catch (NamingException e) {
            throw new ConnectorException(e);
        } finally {
            if (runAsContext != null) {
                try {
                    runAsContext.close();
                } catch (NamingException e) {
                }
            }
        }

        return conn.getSchemaMapping().createUid(oclass, entryDN);
    }

    public Uid addAttributeValues(Set<Attribute> attrs) {
        String entryDN = LdapSearches.findEntryDN(conn, oclass, uid);
        PosixGroupMember posixMember = new PosixGroupMember(entryDN);
        LdapContext runAsContext = null;

        if (StringUtil.isNotBlank(options.getRunAsUser())) {
            String dn = new LdapAuthenticate(conn, oclass, options.getRunAsUser(), options).getDn();
            runAsContext = conn.getRunAsContext(dn, options.getRunWithPassword());
        }

        Pair<Attributes, Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>> attrsToModify = getAttributesToModify(attrs);
        modifyAttributes(entryDN, attrsToModify, DirContext.ADD_ATTRIBUTE, runAsContext);

        List<String> ldapGroups = getStringListValue(attrs, LdapConstants.LDAP_GROUPS_NAME);
        if (!isEmpty(ldapGroups)) {
            groupHelper.addLdapGroupMemberships(entryDN, ldapGroups, runAsContext);
        }

        List<String> posixGroups = getStringListValue(attrs, LdapConstants.POSIX_GROUPS_NAME);
        if (!isEmpty(posixGroups)) {
            Set<String> posixRefAttrs = posixMember.getPosixRefAttributes();
            String posixRefAttr = getFirstPosixRefAttr(entryDN, posixRefAttrs);
            groupHelper.addPosixGroupMemberships(posixRefAttr, posixGroups, runAsContext);
        }

        return uid;
    }

    public Uid removeAttributeValues(Set<Attribute> attrs) {
        String entryDN = LdapSearches.findEntryDN(conn, oclass, uid);
        PosixGroupMember posixMember = new PosixGroupMember(entryDN);
        LdapContext runAsContext = null;

        if (StringUtil.isNotBlank(options.getRunAsUser())) {
            String dn = new LdapAuthenticate(conn, oclass, options.getRunAsUser(), options).getDn();
            runAsContext = conn.getRunAsContext(dn, options.getRunWithPassword());
        }

        Pair<Attributes, Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>> attrsToModify = getAttributesToModify(attrs);
        Attributes ldapAttrs = attrsToModify.first;

        Set<String> removedPosixRefAttrs = getAttributeValues(GroupHelper.getPosixRefAttribute(), null, ldapAttrs);
        if (!isEmpty(removedPosixRefAttrs)) {
            checkRemovedPosixRefAttrs(removedPosixRefAttrs, posixMember.getPosixGroupMemberships());
        }

        modifyAttributes(entryDN, attrsToModify, DirContext.REMOVE_ATTRIBUTE, runAsContext);

        List<String> ldapGroups = getStringListValue(attrs, LdapConstants.LDAP_GROUPS_NAME);
        if (!isEmpty(ldapGroups)) {
            groupHelper.removeLdapGroupMemberships(entryDN, ldapGroups, runAsContext);
        }

        List<String> posixGroups = getStringListValue(attrs, LdapConstants.POSIX_GROUPS_NAME);
        if (!isEmpty(posixGroups)) {
            Set<GroupMembership> members = posixMember.getPosixGroupMembershipsByGroups(posixGroups);
            groupHelper.removePosixGroupMemberships(members, runAsContext);
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

    private Pair<Attributes, Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>> getAttributesToModify(Set<Attribute> attrs) {
        BasicAttributes ldapAttrs = new BasicAttributes();
        GuardedPasswordAttribute pwdAttr = null;
        GuardedPasswordAttribute curPwdAttr = null;
        final Set<Attribute> basicAttributes = AttributeUtil.getBasicAttributes(attrs);
        final Set<Attribute> specialAttributes = AttributeUtil.getSpecialAttributes(attrs);
        final Attribute pwd = AttributeUtil.find(OperationalAttributes.PASSWORD_NAME, specialAttributes);
        final Attribute curPwd = AttributeUtil.find(OperationalAttributes.CURRENT_PASSWORD_NAME, specialAttributes);

        if (pwd != null) {
            pwdAttr = conn.getSchemaMapping().encodePassword(oclass, pwd);
            specialAttributes.remove(pwd);
        }
        if (curPwd != null) {
            curPwdAttr = conn.getSchemaMapping().encodePassword(oclass, curPwd);
            specialAttributes.remove(curPwd);
        }

        for (Attribute attr : basicAttributes) {
            javax.naming.directory.Attribute ldapAttr = null;
            if (LdapConstants.isLdapGroups(attr.getName())) {
                // Handled elsewhere.
            } else if (LdapConstants.isPosixGroups(attr.getName())) {
                // Handled elsewhere.
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

        if (conn.getServerType().equals(LdapConstants.ServerType.MSAD) && !specialAttributes.isEmpty()) {
            if (ObjectClass.ACCOUNT.equals(oclass)) {
                try {
                    ADUserAccountControl aduac = ADUserAccountControl.createADUserAccountControl(conn, uid.getUidValue());
                    for (Attribute attr : specialAttributes) {
                        javax.naming.directory.Attribute ldapAttr = null;
                        try {
                            ldapAttr = aduac.addControl(attr);
                        } catch (ParseException ex) {
                            logger.warn("Error parsing AD control: {0}", ex.getMessage());
                        }
                        if (ldapAttr != null && ldapAttr.size() > 0) {
                            ldapAttrs.put(ldapAttr);
                        }
                    }
                } catch (NamingException e) {
                    logger.warn("Cannot find user {0}", uid.getUidValue());
                }
            } else if (ObjectClass.GROUP.equals(oclass)) {
                try {
                    ADGroupType adgt = ADGroupType.createADGroupType(conn, uid.getUidValue());
                    adgt.setScope(AttributeUtil.find(ADGroupType.GROUP_SCOPE_NAME, specialAttributes));
                    adgt.setType(AttributeUtil.find(ADGroupType.GROUP_TYPE_NAME, specialAttributes));
                    ldapAttrs.put(adgt.getLdapAttribute());
                } catch (NamingException e) {
                    logger.warn("Cannot find group {0}", uid.getUidValue());
                }
            }
        }

        return new Pair<Attributes, Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>>(ldapAttrs,
                new Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>(pwdAttr, curPwdAttr));
    }

    private void modifyAttributes(final String entryDN, Pair<Attributes, Pair<GuardedPasswordAttribute, GuardedPasswordAttribute>> attrs, final int ldapModifyOp, final LdapContext context) {
        final Pair<GuardedPasswordAttribute, GuardedPasswordAttribute> passwords = attrs.second;
        final List<ModificationItem> modItems = new ArrayList<ModificationItem>(attrs.first.size());
        NamingEnumeration<? extends javax.naming.directory.Attribute> attrEnum = attrs.first.getAll();

        while (attrEnum.hasMoreElements()) {
            modItems.add(new ModificationItem(ldapModifyOp, attrEnum.nextElement()));
        }

        if (passwords.first != null) {
            passwords.first.access(new Accessor() {
                public void access(javax.naming.directory.Attribute passwordAttr) {
                    hashPassword(passwordAttr, entryDN);
                    // No current password provided - we use 'replace'
                    if (passwords.second == null) {
                        modItems.add(new ModificationItem(ldapModifyOp, passwordAttr));
                        modifyAttributes(entryDN, modItems, context);
                    } else {
                        // We may have different implementation of Password Self service depending on the target directory
                        switch (conn.getServerType()) {
                            case MSAD_LDS:
                            case MSAD:
                                // Password change has to be done in 2 operations. Remove old, Add new
                                final javax.naming.directory.Attribute newPasswordAttr = passwordAttr;
                                passwords.second.access(new Accessor() {
                                    public void access(javax.naming.directory.Attribute oldPasswordAttr) {
                                        hashPassword(oldPasswordAttr, entryDN);
                                        modItems.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, oldPasswordAttr));
                                        modItems.add(new ModificationItem(DirContext.ADD_ATTRIBUTE, newPasswordAttr));
                                        modifyAttributes(entryDN, modItems, context);
                                    }
                                });
                                break;
                            default:
                                modItems.add(new ModificationItem(ldapModifyOp, passwordAttr));
                                modifyAttributes(entryDN, modItems, context);
                        }
                    }
                }
            });
        } else {
            modifyAttributes(entryDN, modItems, context);
        }
    }

    private void modifyAttributes(String entryDN, List<ModificationItem> modItems, LdapContext context) {
        try {
            if (context == null) {
                conn.getInitialContext().modifyAttributes(entryDN, modItems.toArray(new ModificationItem[modItems.size()]));
            } else {
                context.modifyAttributes(entryDN, modItems.toArray(new ModificationItem[modItems.size()]));
            }
        } catch (NameNotFoundException e) {
            throw (UnknownUidException) new UnknownUidException(uid, oclass).initCause(e);
        } catch (InvalidAttributeValueException e) {
            String message = e.getMessage().toLowerCase();
            switch (conn.getServerType()) {
                case MSAD:
                case MSAD_LDS:
                    if (message.contains("ldap: error code 19 ")) {
                        if (message.contains("(unicodepwd)")) {
                            if (message.contains("00000056:")) {
                                throw new org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException("Wrong password supplied");
                            } else if (message.contains("0000052d:")) {
                                throw new org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException("New password does not comply with password policy");
                            } else if (message.contains("00000775:")) {
                                throw new org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException("Account locked");
                            }
                        }
                    }
                    break;
                default:
                    throw new org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException(e.getCause());
            }
        } catch (OperationNotSupportedException e) {
            String message = e.getMessage().toLowerCase();
            switch (conn.getServerType()) {
                case MSAD:
                case MSAD_LDS:
                    if (message.contains("ldap: error code 53 ")) {
                        if (message.contains("will_not_perform")) {
                            throw new ConnectorException("Operation not supported");
                        }
                    }
                    break;
                default:
            }
        } catch (NoPermissionException e) {
            throw new ConnectorException("Insufficient Access Rights to perform");
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
