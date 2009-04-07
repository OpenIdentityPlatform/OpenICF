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
package org.identityconnectors.ldap;

import static org.identityconnectors.ldap.LdapUtil.attrNameEquals;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValues;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

/**
 * Implements {@link LdapNativeSchema} by reading it from the server.
 */
public class ServerNativeSchema implements LdapNativeSchema {

    // The LDAP directory attributes to expose as framework attributes.
    private static final Set<String> LDAP_DIRECTORY_ATTRS;

    private final LdapConnection conn;
    private final DirContext schemaCtx;

    private final Map<String, Set<String>> ldapClass2MustAttrs = CollectionUtil.newCaseInsensitiveMap();
    private final Map<String, Set<String>> ldapClass2MayAttrs = CollectionUtil.newCaseInsensitiveMap();
    private final Map<String, Set<String>> ldapClass2Sup = CollectionUtil.newCaseInsensitiveMap();

    private final Map<String, LdapAttributeType> attrName2Type = CollectionUtil.newCaseInsensitiveMap();

    static {
        LDAP_DIRECTORY_ATTRS = CollectionUtil.newCaseInsensitiveSet();
        LDAP_DIRECTORY_ATTRS.add("createTimestamp");
        LDAP_DIRECTORY_ATTRS.add("modifyTimestamp");
        LDAP_DIRECTORY_ATTRS.add("creatorsName");
        LDAP_DIRECTORY_ATTRS.add("modifiersName");
        LDAP_DIRECTORY_ATTRS.add("entryDN");
    }

    public ServerNativeSchema(LdapConnection conn) throws NamingException {
        this.conn = conn;
        schemaCtx = conn.getInitialContext().getSchema("");
        try {
            initObjectClasses();
            initAttributeDescriptions();
        } finally {
            schemaCtx.close();
        }
    }

    public Set<String> getRequiredAttributes(String ldapClass) {
        return getAttributes(ldapClass, true);
    }

    public Set<String> getOptionalAttributes(String ldapClass) {
        return getAttributes(ldapClass, false);
    }

    private Set<String> getAttributes(String ldapClass, boolean required) {
        Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        Queue<String> queue = new LinkedList<String>();
        Set<String> visited = new HashSet<String>();
        queue.add(ldapClass);

        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.contains(current)) {
                visited.add(current);
                Set<String> attrs = required ? ldapClass2MustAttrs.get(current) : ldapClass2MayAttrs.get(current);
                if (attrs != null) {
                    result.addAll(attrs);
                }
                Set<String> supClasses = ldapClass2Sup.get(current);
                if (supClasses != null) {
                    queue.addAll(supClasses);
                }
            }
        }

        return result;
    }

    public Set<String> getSuperiorObjectClasses(String ldapClass) {
        Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        Queue<String> classQueue = new LinkedList<String>();
        Set<String> visitedClasses = new HashSet<String>();
        classQueue.add(ldapClass);

        while (!classQueue.isEmpty()) {
            String classToVisit = classQueue.remove();
            if (!visitedClasses.contains(classToVisit)) {
                visitedClasses.add(classToVisit);
                Set<String> supClasses = ldapClass2Sup.get(classToVisit);
                if (supClasses != null) {
                    classQueue.addAll(supClasses);
                }
            }
        }

        return result;
    }

    public LdapAttributeType getAttributeDescription(String ldapAttrName) {
        return attrName2Type.get(ldapAttrName);
    }

    private void initObjectClasses() throws NamingException {
        DirContext objClassCtx = (DirContext) schemaCtx.lookup("ClassDefinition");
        NamingEnumeration<NameClassPair> objClassEnum = objClassCtx.list("");
        while (objClassEnum.hasMore()) {
            String objClassName = objClassEnum.next().getName();
            Attributes attrs = objClassCtx.getAttributes(objClassName);

            Set<String> mustAttrs = getStringAttrValues(attrs, "MUST");
            Set<String> mayAttrs = getStringAttrValues(attrs, "MAY");
            // The objectClass attribute must not be required, since it is handled internally by the connector.
            if (mustAttrs.remove("objectClass")) {
                mayAttrs.add("objectClass");
            }
            Set<String> supClasses = getStringAttrValues(attrs, "SUP");

            for (String name : getStringAttrValues(attrs, "NAME")) {
                ldapClass2MustAttrs.put(name, mustAttrs);
                ldapClass2MayAttrs.put(name, mayAttrs);
                ldapClass2Sup.put(name, supClasses);
            }
        }
    }

    private void initAttributeDescriptions() throws NamingException {
        DirContext attrsCtx = (DirContext) schemaCtx.lookup("AttributeDefinition");
        NamingEnumeration<NameClassPair> attrsEnum = attrsCtx.list("");
        while (attrsEnum.hasMore()) {
            String attrName = attrsEnum.next().getName();
            Attributes attrs = attrsCtx.getAttributes(attrName);

            boolean singleValue = "true".equals(getStringAttrValue(attrs, "SINGLE-VALUE"));
            boolean noUserModification = "true".equals(getStringAttrValue(attrs, "NO-USER-MODIFICATION"));
            String usage = getStringAttrValue(attrs, "USAGE");
            boolean userApplications = "userApplications".equals(usage) || usage == null;

            for (String name : getStringAttrValues(attrs, "NAME")) {
                // The objectClass attribute must not be writable, since it is handled internally by the connector.
                boolean objectClass = attrNameEquals(name, "objectClass");
                boolean binary = conn.isBinarySyntax(attrName);

                boolean password = "userPassword".equals(name);

                Class<?> type;
                if (password) {
                    type = GuardedString.class;
                } else if (binary) {
                    type = byte[].class;
                } else {
                    type = String.class;
                }
                Set<Flags> flags = EnumSet.noneOf(Flags.class);
                if (!singleValue && !password) {
                    flags.add(Flags.MULTIVALUED);
                }
                if (noUserModification || objectClass) {
                    flags.add(Flags.NOT_CREATABLE);
                    flags.add(Flags.NOT_UPDATEABLE);
                }
                // XXX perhaps this should be true for binary attributes too.
                if (!userApplications) {
                    flags.add(Flags.NOT_RETURNED_BY_DEFAULT);
                }
                attrName2Type.put(name, new LdapAttributeType(type, flags));
            }
        }

        for (String dirAttrName : LDAP_DIRECTORY_ATTRS) {
            attrName2Type.put(dirAttrName, new LdapAttributeType(String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE, Flags.NOT_RETURNED_BY_DEFAULT)));
        }
    }
}
