/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.ldap;

import static org.identityconnectors.framework.common.objects.AttributeUtil.createSpecialName;
import static org.identityconnectors.ldap.ADLdapUtil.guidStringtoByteString;
import static org.identityconnectors.ldap.LdapEntry.isDNAttribute;
import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.ldap.search.LdapInternalSearch;

/* 
 * This class provides static helper methods to handle 
 * the MS AD grouType attribute.
 */
public class ADGroupType {
    
    // A group defaults to Global security
    private int gt = SCOPE_GLOBAL | TYPE_SECURITY;
    
    /**
     * Specifies a group with global scope.
     */
    public static final int SCOPE_GLOBAL = 0x00000002;
    
    /**
     * Specifies a group with domain local scope.
     */
    public static final int SCOPE_DOMAIN_LOCAL = 0x00000004;
    
    /**
     * Specifies a group with universal scope.
     */
    public static final int SCOPE_UNIVERSAL = 0x00000008;
    
    /**
     * Specifies a security group. 
     * If this flag is not set, then the group is a distribution group.
     */
    public static final int TYPE_SECURITY = 0x80000000;
    
    /*
     * The Group Scope special attribute
     */
    public static final String GROUP_SCOPE_NAME = createSpecialName("GROUP_SCOPE");
    /*
     * The Group Type special attribute
     */
    public static final String GROUP_TYPE_NAME = createSpecialName("GROUP_TYPE");
    
    /**
     * The groupType attribute 
     */
    public static final String GROUPTYPE = "groupType";
    
    public static final String GLOBAL = "global";
    public static final String DOMAIN_LOCAL = "domain";
    public static final String UNIVERSAL = "universal";
    
    public static final String SECURITY = "security";
    public static final String DISTRIBUTION = "distribution";
    
    public ADGroupType() {}
    
    public ADGroupType(int gt) {
        this.gt = gt;
    }
    
    public void setScope(Attribute scope){
        if (scope != null) {
            if (!scope.getValue().isEmpty()) {
                setScope(scope.getValue().get(0).toString());
            }
        }
    }
    
    public void setScope(String scope){
        if (DOMAIN_LOCAL.equalsIgnoreCase(scope)){
            gt &= ~SCOPE_UNIVERSAL;
            gt &= ~SCOPE_GLOBAL;
            gt |= SCOPE_DOMAIN_LOCAL;
        } else if (UNIVERSAL.equalsIgnoreCase(scope)){
            gt &= ~SCOPE_DOMAIN_LOCAL;
            gt &= ~SCOPE_GLOBAL;
            gt |= SCOPE_UNIVERSAL;
        } else {
            // default to Global
            gt &= ~SCOPE_DOMAIN_LOCAL;
            gt &= ~SCOPE_UNIVERSAL;
            gt = gt | SCOPE_GLOBAL;
        }
    }
    
    public void setType(Attribute type){
        if (type != null) {
            if (!type.getValue().isEmpty()) {
                setType(type.getValue().get(0).toString());
            }
        }
    }
    
    public void setType(String type){
        if (DISTRIBUTION.equalsIgnoreCase(type)){
            gt = gt & ~TYPE_SECURITY;
        } else {
            // default to Security
            gt = gt | TYPE_SECURITY;
        }
    }
    
    public javax.naming.directory.Attribute getLdapAttribute() {
        return new BasicAttribute(GROUPTYPE,Integer.toString(gt));
    }
    
    // Static helpers
    
    public static boolean isScopeGlobal(String scope) {
        return ((Integer.parseInt(scope) & SCOPE_GLOBAL) == SCOPE_GLOBAL);
    }
    
    public static boolean isScopeDomainLocal(String scope) {
        return ((Integer.parseInt(scope) & SCOPE_DOMAIN_LOCAL) == SCOPE_DOMAIN_LOCAL);
    }
    
    public static boolean isScopeUniversal(String scope) {
        return ((Integer.parseInt(scope) & SCOPE_UNIVERSAL) == SCOPE_UNIVERSAL);
    }
    
    public static boolean isTypeSecurity(String type) {
        return ((Integer.parseInt(type) & TYPE_SECURITY) == TYPE_SECURITY);
    }
    
    public static String getType(String type){
        if (isTypeSecurity(type)){
            return SECURITY;
        } else {
            return DISTRIBUTION;
        }
    }
    
    public static String getScope(String scope){
        if (isScopeGlobal(scope)){
            return GLOBAL;
        } else if (isScopeDomainLocal(scope)){
            return DOMAIN_LOCAL;
        } else {
            return UNIVERSAL;
        }
    }
    
    public static ADGroupType createADGroupType(LdapConnection conn, String id) throws NamingException {
        if (LdapConstants.MS_GUID_ATTR.equalsIgnoreCase(conn.getConfiguration().getUidAttribute())) {
            SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{GROUPTYPE});

            for (String context : conn.getConfiguration().getBaseContexts()) {
                NamingEnumeration<SearchResult> entries = conn.getInitialContext().search(context, String.format("%s=%s", LdapConstants.MS_GUID_ATTR, guidStringtoByteString(id)), controls);
                if (entries.hasMore()) {
                    SearchResult res = entries.next();
                    int gt = Integer.parseInt(res.getAttributes().get(GROUPTYPE).get().toString());
                    return new ADGroupType(gt);
                }
            }
        } else if (isDNAttribute(conn.getConfiguration().getUidAttribute())) {
            Attributes attrs = conn.getInitialContext().getAttributes(escapeDNValueOfJNDIReservedChars(id), new String[]{GROUPTYPE});
            int gt = Integer.parseInt(attrs.get(GROUPTYPE).get().toString());
            return new ADGroupType(gt);
        }
        throw new NamingException("Entry not found");
    }
    
}