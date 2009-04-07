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

import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttributes;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute.Accessor;

public class LdapCreate {

    // TODO old LDAP connector has a note about a RFC 4527 Post-Read control.

    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final Set<Attribute> attrs;

    public LdapCreate(LdapConnection conn, ObjectClass oclass, Set<Attribute> attrs, OperationOptions options) {
        this.conn = conn;
        this.oclass = oclass;
        this.attrs = CollectionUtil.newSet(attrs);
    }

    public Uid execute() {
        try {
            return executeImpl();
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    private Uid executeImpl() throws NamingException {
        final Name nameAttr = AttributeUtil.getNameFromAttributes(attrs);
        if (nameAttr == null) {
            throw new IllegalArgumentException("No Name attribute provided in the attributes");
        }
        attrs.remove(nameAttr);

        GuardedPasswordAttribute pwdAttr = null;
        final BasicAttributes ldapAttrs = new BasicAttributes(true);

        for (Attribute attr : attrs) {
            javax.naming.directory.Attribute ldapAttr = null;
            if (attr.is(OperationalAttributes.PASSWORD_NAME)) {
                pwdAttr = conn.getSchemaMapping().encodePassword(oclass, attr);
            } else {
                ldapAttr = conn.getSchemaMapping().encodeAttribute(oclass, attr);
                if (ldapAttr != null) {
                    ldapAttrs.put(ldapAttr);
                }
            }
        }

        final Uid[] uid = { null };
        if (pwdAttr != null) {
            pwdAttr.access(new Accessor() {
                public void access(javax.naming.directory.Attribute passwordAttr) {
                    ldapAttrs.put(passwordAttr);
                    uid[0] = conn.getSchemaMapping().create(oclass, nameAttr, ldapAttrs);
                }
            });
        } else {
            uid[0] = conn.getSchemaMapping().create(oclass, nameAttr, ldapAttrs);
        }

        return uid[0];
    }
}
