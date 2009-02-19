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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.ldap.AttributeMappingConfig;
import org.identityconnectors.ldap.LdapAttributeType;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConnector;
import org.identityconnectors.ldap.LdapNativeSchema;
import org.identityconnectors.ldap.ObjectClassMappingConfig;

class LdapSchemaBuilder {

    private static final Log log = Log.getLog(LdapSchemaBuilder.class);

    private final LdapConnection conn;
    private final LdapNativeSchema nativeSchema;
    private Schema schema;

    public LdapSchemaBuilder(LdapConnection conn) {
        this.conn = conn;
        this.nativeSchema = conn.createNativeSchema();
    }

    public Schema getSchema() {
        if (schema == null) {
            buildSchema();
        }
        return schema;
    }

    private void buildSchema() {
        SchemaBuilder schemaBld = new SchemaBuilder(LdapConnector.class);

        for (ObjectClassMappingConfig oclassConfig : conn.getConfiguration().getObjectClassMappingConfigs().values()) {
            ObjectClass oclass = oclassConfig.getObjectClass();

            ObjectClassInfoBuilder objClassBld = new ObjectClassInfoBuilder();
            objClassBld.setType(oclass.getObjectClassValue());
            objClassBld.setContainer(oclassConfig.isContainer());
            objClassBld.addAllAttributeInfo(createAttributeInfos(oclassConfig));
            objClassBld.addAllAttributeInfo(oclassConfig.getOperationalAttributes());

            schemaBld.defineObjectClass(objClassBld.build());
        }

        schema = schemaBld.build();
    }

    private Set<AttributeInfo> createAttributeInfos(ObjectClassMappingConfig oclassConfig) {
        Set<AttributeInfo> result = new HashSet<AttributeInfo>();

        String ldapClass = oclassConfig.getLdapClass();

        Set<String> requiredAttrs = nativeSchema.getRequiredAttributes(ldapClass);
        Set<String> optionalAttrs = nativeSchema.getOptionalAttributes(ldapClass);
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

    private void addAttributeInfos(String ldapClass, Set<String> attrs, Set<Flags> add, Set<Flags> remove, Set<AttributeInfo> toSet) {
        for (String attr : attrs) {
            addAttributeInfo(ldapClass, attr, attr, add, remove, toSet);
        }
    }

    private void addAttributeInfo(String ldapClass, String ldapAttrName, String realName, Set<Flags> add, Set<Flags> remove, Set<AttributeInfo> toSet) {
        LdapAttributeType attrDesc = nativeSchema.getAttributeDescription(ldapAttrName);
        if (attrDesc != null) {
            toSet.add(attrDesc.createAttributeInfo(realName, add, remove));
        } else {
            log.warn("Could not find attribute {0} of object class {1}", ldapAttrName, ldapClass);
        }
    }
}
