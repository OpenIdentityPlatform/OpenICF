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
package org.identityconnectors.ldap.schema;

import static org.identityconnectors.ldap.LdapUtil.attrNameEquals;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.ldap.AttributeMappingConfig;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConnector;
import org.identityconnectors.ldap.ObjectClassMappingConfig;

class LdapSchemaBuilder {

    private static final Log log = Log.getLog(LdapSchemaBuilder.class);

    private static final String CLASS_DEFINITION = "ClassDefinition";
    private static final String ATTRIBUTE_DEFINITION = "AttributeDefinition";

    private final LdapConnection conn;
    private final DirContext schemaCtx;

    private Map<String, Set<String>> ldapClass2MustAttrs;
    private Map<String, Set<String>> ldapClass2MayAttrs;
    private Map<String, Set<String>> ldapClass2Sup;

    private Map<String, LdapAttributeDescription> attr2Desc;

    private Schema schema;
    private Map<String, Set<String>> ldapClass2TransSup;

    public LdapSchemaBuilder(LdapConnection conn) {
        this.conn = conn;
        try {
            schemaCtx = conn.getInitialContext().getSchema("");
            try {
                initObjectClasses();
                initAttributeBuilders();
                buildSchema();
            } finally {
                schemaCtx.close();
            }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    public Schema getSchema() {
        return schema;
    }

    public Map<String, Set<String>> getLdapClassTransitiveSuperiors() {
        return ldapClass2TransSup;
    }

    private void buildSchema() {
        SchemaBuilder schemaBld = new SchemaBuilder(LdapConnector.class);
        Map<String, Set<String>> ldapClass2SupTransTemp = CollectionUtil.newCaseInsensitiveMap();

        for (ObjectClassMappingConfig oclassConfig : conn.getConfiguration().getObjectClassMappingConfigs().values()) {
            ObjectClass oclass = oclassConfig.getObjectClass();
            String ldapClass = oclassConfig.getLdapClass();

            ObjectClassInfoBuilder objClassBld = new ObjectClassInfoBuilder();
            objClassBld.setType(oclass.getObjectClassValue());
            objClassBld.setContainer(oclassConfig.isContainer());
            objClassBld.addAllAttributeInfo(createAttributeInfos(oclassConfig));
            objClassBld.addAllAttributeInfo(oclassConfig.getOperationalAttributes());

            schemaBld.defineObjectClass(objClassBld.build());
            ldapClass2SupTransTemp.put(ldapClass, Collections.unmodifiableSet(getLdapClassSuperiorsTransitively(ldapClass)));
        }

        schema = schemaBld.build();
        ldapClass2TransSup = Collections.unmodifiableMap(ldapClass2SupTransTemp);
    }

    private Set<AttributeInfo> createAttributeInfos(ObjectClassMappingConfig oclassConfig) {
        Set<AttributeInfo> result = new HashSet<AttributeInfo>();

        String ldapClass = oclassConfig.getLdapClass();

        Set<String> requiredAttrs = getMustAttributesTransitively(ldapClass);
        Set<String> optionalAttrs = getMayAttributesTransitively(ldapClass);
        // OpenLDAP's ipProtocol has MUST ( ... $ description ) MAY ( description )
        optionalAttrs.removeAll(requiredAttrs);

        Set<String> mappedLdapAttributes = CollectionUtil.newCaseInsensitiveSet();

        String ldapNameAttr = oclassConfig.getNameAttribute();
        if (ldapNameAttr != null) {
            // Name is required. So if Name is mapped to a required LDAP attribute,
            // make that attribute optional for further mapping.
            if (requiredAttrs.remove(ldapNameAttr)) {
                optionalAttrs.add(ldapNameAttr);
            }
            mappedLdapAttributes.add(ldapNameAttr);
            result.add(Name.INFO);
        }

        List<AttributeMappingConfig> attrMappings = oclassConfig.getAttributeMappings();
        for (AttributeMappingConfig attrMapping : attrMappings) {
            String attrName = attrMapping.getFromAttribute();
            String ldapAttrName = attrMapping.getToAttribute();
            boolean required;
            if (requiredAttrs.remove(ldapAttrName)) {
                required = true;
                optionalAttrs.add(ldapAttrName);
            } else {
                required = false;
            }
            mappedLdapAttributes.add(ldapAttrName);
            // Explicitly mapped attributes are always returned by default.
            addAttributeInfo(ldapClass, ldapAttrName, attrName, required ? EnumSet.of(Flags.REQUIRED) : null, EnumSet.of(Flags.NOT_RETURNED_BY_DEFAULT), result);
        }

        // Now add the attributes which were not mapped explicitly.

        requiredAttrs.removeAll(mappedLdapAttributes);
        optionalAttrs.removeAll(mappedLdapAttributes);

        if (attrMappings.isEmpty()) {
            // If no attributes were mapped explicitly, add all attributes.
            addAttributeInfos(ldapClass, requiredAttrs, EnumSet.of(Flags.REQUIRED), null, result);
            addAttributeInfos(ldapClass, optionalAttrs, null, null, result);
        } else {
            // Otherwise add the non-mapped attributes, but do not return them by default.
            addAttributeInfos(ldapClass, requiredAttrs, EnumSet.of(Flags.REQUIRED, Flags.NOT_RETURNED_BY_DEFAULT), null, result);
            addAttributeInfos(ldapClass, optionalAttrs, EnumSet.of(Flags.NOT_RETURNED_BY_DEFAULT), null, result);
        }

        return result;
    }

    private void addAttributeInfo(String ldapClass, String ldapAttrName, String realName, Set<Flags> add, Set<Flags> remove, Set<AttributeInfo> toSet) {
        LdapAttributeDescription attrDesc = getLdapAttributeDescription(ldapClass, ldapAttrName);
        if (attrDesc != null) {
            toSet.add(attrDesc.createAttributeInfo(realName, add, remove));
        }
    }

    private void addAttributeInfos(String ldapClass, Set<String> attrs, Set<Flags> add, Set<Flags> remove, Set<AttributeInfo> toSet) {
        for (String attr : attrs) {
            addAttributeInfo(ldapClass, attr, attr, add, remove, toSet);
        }
    }

    private Set<String> getMustAttributesTransitively(String ldapClass) {
        Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        addAttributesTransitively(ldapClass, ldapClass2MustAttrs, result);
        return result;
    }

    private Set<String> getMayAttributesTransitively(String ldapClass) {
        Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        addAttributesTransitively(ldapClass, ldapClass2MayAttrs, result);
        return result;
    }

    private void addAttributesTransitively(String ldapClass, Map<String, Set<String>> ldapClass2Attrs, Set<String> toSet) {
        Set<String> thisClassAttrs = ldapClass2Attrs.get(ldapClass);
        if (thisClassAttrs == null) {
            return;
        }
        toSet.addAll(thisClassAttrs);
        Set<String> supClasses = ldapClass2Sup.get(ldapClass);
        if (supClasses == null) {
            return;
        }
        for (String supClass : supClasses) {
            addAttributesTransitively(supClass, ldapClass2Attrs, toSet);
        }
    }

    private Set<String> getLdapClassSuperiorsTransitively(String ldapClass) {
        Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        addLdapClassSuperiorsTransitively(ldapClass, result);
        return result;
    }

    private void addLdapClassSuperiorsTransitively(String ldapClass, Set<String> toSet) {
        Set<String> supClasses = ldapClass2Sup.get(ldapClass);
        if (supClasses == null) {
            return;
        }
        toSet.addAll(supClasses);
        for (String supClass : supClasses) {
            addLdapClassSuperiorsTransitively(supClass, toSet);
        }
    }

    private LdapAttributeDescription getLdapAttributeDescription(String ldapClass, String ldapAttrName) {
        LdapAttributeDescription attrDesc = attr2Desc.get(ldapAttrName);
        if (attrDesc == null) {
            log.warn("Unknown attribute {0} of object class {1}\n", ldapAttrName, ldapClass);
        }
        return attrDesc;
    }

    private void initObjectClasses() throws NamingException {
        ldapClass2MustAttrs = CollectionUtil.newCaseInsensitiveMap();
        ldapClass2MayAttrs = CollectionUtil.newCaseInsensitiveMap();
        ldapClass2Sup = CollectionUtil.newCaseInsensitiveMap();

        DirContext objClassCtx = (DirContext) schemaCtx.lookup(CLASS_DEFINITION);
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

    private void initAttributeBuilders() throws NamingException {
        attr2Desc = CollectionUtil.newCaseInsensitiveMap();

        DirContext attrsCtx = (DirContext) schemaCtx.lookup(ATTRIBUTE_DEFINITION);
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
                boolean binary = LdapSchemaMapping.LDAP_BINARY_SYNTAX_ATTRS.contains(name);

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
                attr2Desc.put(name, new LdapAttributeDescription(type, flags));
            }
        }

        for (String dirAttrName : LdapSchemaMapping.LDAP_DIRECTORY_ATTRS) {
            attr2Desc.put(dirAttrName, new LdapAttributeDescription(String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE, Flags.NOT_RETURNED_BY_DEFAULT)));
        }
    }

    private static final class LdapAttributeDescription {

        private final Class<?> type;
        private final Set<Flags> flags;

        public LdapAttributeDescription(Class<?> type, Set<Flags> flags) {
            this.type = type;
            this.flags = Collections.unmodifiableSet(flags);
        }

        public AttributeInfo createAttributeInfo(String realName, Set<Flags> add, Set<Flags> remove) {
            EnumSet<Flags> realFlags = flags.isEmpty() ? EnumSet.noneOf(Flags.class) : EnumSet.copyOf(flags);
            if (add != null) {
                realFlags.addAll(add);
            }
            if (remove != null) {
                realFlags.removeAll(remove);
            }
            return AttributeInfoBuilder.build(realName, type, realFlags);
        }
    }
}
