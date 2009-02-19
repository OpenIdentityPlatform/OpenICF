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

import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

/**
 * A static pre-made definition of the native schema. This is needed
 * for backward compatibility with the LDAP resource adapter, which
 * does not read the schema either. See also {@link ServerNativeSchema}.
 */
public class StaticNativeSchema implements LdapNativeSchema {

    // XXX temporary implementation. The static schema should perhaps
    // rather be based on the object class mapping configuration in
    // LdapConfiguration.

    public Set<String> getRequiredAttributes(String ldapClass) {
        SortedSet<String> result = CollectionUtil.newCaseInsensitiveSet();
        if ("inetOrgPerson".equalsIgnoreCase(ldapClass)) {
            result.add("cn");
            result.add("sn");
        } else if ("groupOfUniqueNames".equalsIgnoreCase(ldapClass)) {
            result.add("cn");
        }
        return result;
    }

    public Set<String> getOptionalAttributes(String ldapClass) {
        SortedSet<String> result = CollectionUtil.newCaseInsensitiveSet();
        if ("inetOrgPerson".equalsIgnoreCase(ldapClass)) {
            result.add("uid");
            result.add("givenName");
            result.add("modifyTimeStamp");
            result.add("objectClass");
        } else if ("groupOfUniqueNames".equalsIgnoreCase(ldapClass)) {
            result.add("objectClass");
        }
        return result;
    }

    public Set<String> getSuperiorObjectClasses(String ldapClass) {
        SortedSet<String> result = CollectionUtil.newCaseInsensitiveSet();
        if ("inetOrgPerson".equalsIgnoreCase(ldapClass)) {
            result.add("organizationalPerson");
            result.add("person");
            result.add("top");
        } else if ("groupOfUniqueNames".equalsIgnoreCase(ldapClass)) {
            result.add("top");
        }
        return result;
    }

    public LdapAttributeType getAttributeDescription(String ldapAttrName) {
        if ("cn".equals(ldapAttrName) || "sn".equals(ldapAttrName)) {
            return new LdapAttributeType(String.class, EnumSet.of(Flags.REQUIRED, Flags.MULTIVALUED));
        } else if ("uid".equalsIgnoreCase(ldapAttrName) || "givenName".equalsIgnoreCase(ldapAttrName)) {
            return new LdapAttributeType(String.class, EnumSet.of(Flags.MULTIVALUED));
        } else if ("modifyTimeStamp".equalsIgnoreCase(ldapAttrName)) {
            return new LdapAttributeType(String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE));
        } else if ("objectClass".equalsIgnoreCase(ldapAttrName)) {
            return new LdapAttributeType(String.class, EnumSet.of(Flags.NOT_CREATABLE, Flags.NOT_UPDATEABLE, Flags.MULTIVALUED));
        }
        return null;
    }
}
