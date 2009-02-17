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
package org.identityconnectors.ldap.modify;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute.Accessor;
import org.identityconnectors.ldap.search.LdapSearches;

public class LdapUpdate {

    // XXX "return uid" in methods below is wrong, should re-read the attribute
    // (in case it isn't entryUUID).

    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final Uid uid;

    public LdapUpdate(LdapConnection conn, ObjectClass oclass, Uid uid) {
        this.conn = conn;
        this.oclass = oclass;
        this.uid = uid;
    }

    public Uid update(Set<Attribute> attrs) {
        Set<Attribute> modifyAttrs = attrs;

        Name newName = (Name) AttributeUtil.find(Name.NAME, attrs);
        if (newName != null) {
            String entryDN = LdapSearches.findDN(conn, oclass, uid);
            conn.getSchemaMapping().rename(oclass, entryDN, newName);
            modifyAttrs = CollectionUtil.newSet(attrs);
            modifyAttrs.remove(newName);
        }

        modifyAttributeValues(modifyAttrs, DirContext.REPLACE_ATTRIBUTE);
        return uid;
    }

    public Uid addAttributeValues(Set<Attribute> attrs) {
        modifyAttributeValues(attrs, DirContext.ADD_ATTRIBUTE);
        return uid;
    }

    public Uid removeAttributeValues(Set<Attribute> attrs) {
        modifyAttributeValues(attrs, DirContext.REMOVE_ATTRIBUTE);
        return uid;
    }

    private void modifyAttributeValues(Set<Attribute> attrs, final int ldapModifyOp) {
        final String entryDN = LdapSearches.findDN(conn, oclass, uid);

        GuardedPasswordAttribute pwdAttr = null;
        final List<ModificationItem> modItems = new ArrayList<ModificationItem>(attrs.size());
        for (Attribute attr : attrs) {
            javax.naming.directory.Attribute ldapAttr = null;
            if (attr.is(Uid.NAME)) {
                throw new IllegalArgumentException("Unable to modify an object's uid");
            } else if (attr.is(Name.NAME)) {
                // Such a change would have been handled in update() above.
                throw new IllegalArgumentException("Unable to modify an object's name");
            } else if (attr.is(OperationalAttributes.PASSWORD_NAME)) {
                pwdAttr = conn.getSchemaMapping().encodePassword(oclass, attr);
            } else {
                ldapAttr = conn.getSchemaMapping().encodeAttribute(oclass, attr);
            }
            if (ldapAttr != null) {
                modItems.add(new ModificationItem(ldapModifyOp, ldapAttr));
            }
        }

        if (pwdAttr != null) {
            pwdAttr.access(new Accessor() {
                public void access(javax.naming.directory.Attribute passwordAttr) {
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
}
