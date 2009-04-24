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

import static org.identityconnectors.common.CollectionUtil.newReadOnlyList;
import static org.identityconnectors.common.CollectionUtil.newReadOnlySet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;

/**
 * Describes how to map a framework object class to an LDAP object class.
 */
public class ObjectClassMappingConfig {

    private final ObjectClass objectClass;
    private List<String> ldapClasses;

    private boolean container;

    private final Set<AttributeInfo> operationalAttributes = new HashSet<AttributeInfo>();

    public ObjectClassMappingConfig(ObjectClass objectClass, List<String> ldapClasses, boolean container, AttributeInfo... operationalAttributes) {
        assert objectClass != null;
        assert ldapClasses != null;
        this.objectClass = objectClass;
        setLdapClasses(ldapClasses);
        setContainer(container);
        addOperationalAttributes(operationalAttributes);
    }

    public ObjectClass getObjectClass() {
        return objectClass;
    }

    public List<String> getLdapClasses() {
        return ldapClasses;
    }

    public void setLdapClasses(List<String> ldapClasses) {
        this.ldapClasses = newReadOnlyList(ldapClasses);
    }

    public boolean isContainer() {
        return container;
    }

    public void setContainer(boolean container) {
        this.container = container;
    }

    public void addOperationalAttributes(AttributeInfo... attributeInfos) {
        operationalAttributes.addAll(Arrays.asList(attributeInfos));
    }

    public Set<AttributeInfo> getOperationalAttributes() {
        return newReadOnlySet(operationalAttributes);
    }

    public int hashCode() {
        return objectClass.hashCode();
    }

    public boolean equals(Object o) {
        if (o instanceof ObjectClassMappingConfig) {
            ObjectClassMappingConfig that = (ObjectClassMappingConfig)o;
            if (!objectClass.equals(that.objectClass)) {
                return false;
            }
            if ((ldapClasses == null) ? (that.ldapClasses != null) : !ldapClasses.equals(that.ldapClasses)) {
                return false;
            }
            if (!operationalAttributes.equals(that.operationalAttributes)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
