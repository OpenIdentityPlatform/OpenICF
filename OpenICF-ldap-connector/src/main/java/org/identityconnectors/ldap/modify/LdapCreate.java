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
 * "Portions Copyrighted 2014-2015 ForgeRock AS"
 */
package org.identityconnectors.ldap.modify;

import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.common.CollectionUtil.nullAsEmpty;
import static org.identityconnectors.ldap.LdapUtil.checkedListByFilter;
import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;
import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;

import java.util.List;
import java.util.Set;

import java.text.ParseException;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;


import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
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
import org.identityconnectors.ldap.LdapAuthenticate;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapModifyOperation;
import org.identityconnectors.ldap.LdapConstants;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute.Accessor;

public class LdapCreate extends LdapModifyOperation {

    // TODO old LDAP connector has a note about a RFC 4527 Post-Read control.
    private final ObjectClass oclass;
    private final Set<Attribute> attrs;
    private final OperationOptions options;

    private static final Log log = Log.getLog(LdapCreate.class);

    public LdapCreate(LdapConnection conn, ObjectClass oclass, Set<Attribute> attrs, OperationOptions options) {
        super(conn);
        this.oclass = oclass;
        this.options = options;
        this.attrs = attrs;
    }

    public Uid execute() {
        try {
            return executeImpl();
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    private Uid executeImpl() throws NamingException {
        final LdapContext runAsContext;
        List<String> ldapGroups = null;
        List<String> posixGroups = null;
        GuardedPasswordAttribute pwdAttr = null;
        final BasicAttributes ldapAttrs = new BasicAttributes(true);

        final Set<Attribute> basicAttributes = AttributeUtil.getBasicAttributes(attrs);
        final Set<Attribute> specialAttributes = AttributeUtil.getSpecialAttributes(attrs);
        final Name nameAttr = AttributeUtil.getNameFromAttributes(specialAttributes);
        final Attribute pwd = AttributeUtil.find(OperationalAttributes.PASSWORD_NAME, specialAttributes);

        if (nameAttr == null) {
            throw new IllegalArgumentException("No Name attribute provided in the attributes");
        }
        specialAttributes.remove(nameAttr);

        if (pwd != null) {
            pwdAttr = conn.getSchemaMapping().encodePassword(oclass, pwd);
            specialAttributes.remove(pwd);
        }

        for (Attribute attr : basicAttributes) {
            if (LdapConstants.isLdapGroups(attr.getName())) {
                ldapGroups = checkedListByFilter(nullAsEmpty(attr.getValue()), String.class);
            } else if (LdapConstants.isPosixGroups(attr.getName())) {
                posixGroups = checkedListByFilter(nullAsEmpty(attr.getValue()), String.class);
            } else {
                javax.naming.directory.Attribute ldapAttr = conn.getSchemaMapping().encodeAttribute(oclass, attr);
                // Do not send empty attributes. The server complains for "uniqueMember", for example.
                if (ldapAttr != null && ldapAttr.size() > 0) {
                    ldapAttrs.put(ldapAttr);
                }
            }
        }

        if (conn.getServerType().equals(LdapConstants.ServerType.MSAD)) {
            if (ObjectClass.ACCOUNT.equals(oclass)) {
                ADUserAccountControl aduac = new ADUserAccountControl();
                for (Attribute attr : specialAttributes) {
                    javax.naming.directory.Attribute ldapAttr = null;
                    try {
                        ldapAttr = aduac.addControl(attr);
                    } catch (ParseException ex) {
                        log.warn("Error parsing AD control: {0}", ex.getMessage());
                    }
                    if (ldapAttr != null && ldapAttr.size() > 0) {
                        ldapAttrs.put(ldapAttr);
                    }
                }
            } else if (ObjectClass.GROUP.equals(oclass)) {
                ADGroupType adgt = new ADGroupType();
                adgt.setScope(AttributeUtil.find(ADGroupType.GROUP_SCOPE_NAME, specialAttributes));
                adgt.setType(AttributeUtil.find(ADGroupType.GROUP_TYPE_NAME, specialAttributes));
                ldapAttrs.put(adgt.getLdapAttribute());
            }
        }

        if (StringUtil.isNotBlank(options.getRunAsUser())) {
            String dn = new LdapAuthenticate(conn, oclass, options.getRunAsUser(), options).getDn();
            runAsContext = conn.getRunAsContext(dn, options.getRunWithPassword());
        } else {
            runAsContext = null;
        }

        final String[] entryDN = {null};
        try {
            if (pwdAttr != null) {
                pwdAttr.access(new Accessor() {
                    public void access(javax.naming.directory.Attribute passwordAttr) {
                        hashPassword(passwordAttr, null);
                        ldapAttrs.put(passwordAttr);
                        entryDN[0] = doCreate(nameAttr, ldapAttrs, runAsContext);
                    }
                });
            } else {
                entryDN[0] = doCreate(nameAttr, ldapAttrs, runAsContext);
            }

            entryDN[0] = escapeDNValueOfJNDIReservedChars(entryDN[0]);

            if (!isEmpty(ldapGroups)) {
                groupHelper.addLdapGroupMemberships(entryDN[0], ldapGroups, runAsContext);
            }

            if (!isEmpty(posixGroups)) {
                Set<String> posixRefAttrs = getAttributeValues(GroupHelper.getPosixRefAttribute(), null, ldapAttrs);
                String posixRefAttr = getFirstPosixRefAttr(entryDN[0], posixRefAttrs);
                groupHelper.addPosixGroupMemberships(posixRefAttr, posixGroups, runAsContext);
            }
        } finally {
            if (runAsContext != null) {
                try {
                    runAsContext.close();
                } catch (NamingException e) {
                }
            }
        }

        return conn.getSchemaMapping().createUid(oclass, entryDN[0]);
    }

    public String doCreate(Name name, javax.naming.directory.Attributes initialAttrs, LdapContext runAsContext) {
        LdapName entryName = quietCreateLdapName(name.getNameValue());

        BasicAttributes ldapAttrs = new BasicAttributes();
        NamingEnumeration<? extends javax.naming.directory.Attribute> initialAttrEnum = initialAttrs.getAll();
        while (initialAttrEnum.hasMoreElements()) {
            ldapAttrs.put(initialAttrEnum.nextElement());
        }
        if (ldapAttrs.get("objectClass") == null) {
            BasicAttribute objectClass = new BasicAttribute("objectClass");
            for (String ldapClass : conn.getSchemaMapping().getEffectiveLdapClasses(oclass)) {
                objectClass.add(ldapClass);
            }
            ldapAttrs.put(objectClass);
        }

        log.ok("Creating LDAP entry {0} with attributes {1}", entryName, ldapAttrs);
        try {
            if (runAsContext == null) {
                conn.getInitialContext().createSubcontext(entryName, ldapAttrs).close();
            } else {
                runAsContext.createSubcontext(entryName, ldapAttrs).close();
            }
            return entryName.toString();
        } catch (NameAlreadyBoundException e) {
            throw new AlreadyExistsException(e);
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }
}