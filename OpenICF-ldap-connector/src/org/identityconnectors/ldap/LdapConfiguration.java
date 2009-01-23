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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.EqualsHashCodeBuilder;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Encapsulates the LDAP connector's configuration.
 *
 * @author Andrei Badea
 */
public class LdapConfiguration extends AbstractConfiguration {

    // XXX should try to connect to the resource.

    private static final Log log = Log.getLog(LdapConfiguration.class);

    private static final int DEFAULT_PORT = 389;
    private static final int DEFAULT_SSL_PORT = 636;

    private static final int DEFAULT_PAGE_SIZE = 500;

    /**
     * The LDAP host server to connect to.
     */
    private String host = "localhost";

    /**
     * The port the server is listening on.
     */
    private int port = -1;

    /**
     * Whether the port is a secure SSL port.
     */
    private boolean useSsl;

    /**
     * Whether StartTLS should be used to negotiate a secure connection.
     */
    private boolean startTls;

    /**
     * The base DNs for operations on the server.
     */
    private String[] baseDNs = new String[0];

    /**
     * The bind DN for performing operations on the server.
     */
    private String bindDN;

    /**
     * The bind password associated with the bind DN.
     */
    private GuardedString bindPassword;

//    /**
//     * The name of an operational attribute that provides an immutable
//     * unique identifier of an LDAP entry. See RFC 4530.
//     */
//    private String uuidAttribute = null;
//
//    /**
//     * The name of the password attribute.
//     */
//    private String passwordAttribute = null;

    /**
     * The authentication mechanism to use against the LDAP server.
     */
    private String authentication = "none";

    /**
     * The pages size for paged and VLV index searches.
     */
    private int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * If true, simple paged search will be preferred over VLV index search
     * when both are available.
     */
    private boolean simplePagedSearchPreferred = false;

    /**
     * The LDAP attribute holding the member for non-POSIX static groups.
     */
    private String groupMemberAttribute = "uniqueMember";

    /**
     * The set of object classes to return in the schema
     * (apart from those returned by default).
     */
    private String[] extendedObjectClasses = new String[0];

    /**
     * The naming attributes for the extended object classes:
     * {@code extendedNamingAttributes[i]} is the naming attribute for
     * {@code extendedObjectClasses[i]}.
     */
    private String[] extendedNamingAttributes = new String[0];

    // Exposed configuration properties end here.

    private final ObjectClassMappingConfig accountConfig = new ObjectClassMappingConfig(ObjectClass.ACCOUNT, "inetOrgPerson");
    private final ObjectClassMappingConfig organizationConfig = new ObjectClassMappingConfig(LdapObjectClass.ORGANIZATION, "organization");
    private final ObjectClassMappingConfig groupConfig = new ObjectClassMappingConfig(LdapObjectClass.GROUP, "groupOfUniqueNames");

    private List<LdapName> baseDNsAsLdapNames;

    public LdapConfiguration() {
        // Note: order is important!

        accountConfig.setContainer(false);
        accountConfig.setUidAttribute("entryUUID");
        accountConfig.setNameAttribute("entryDN");
        // XXX perhaps SHORT_NAME needs to be configured too?
        accountConfig.addAttributeMapping(LdapPredefinedAttributes.PASSWORD_NAME, "userPassword");
        accountConfig.addAttributeMapping(LdapPredefinedAttributes.FIRSTNAME_NAME, "givenName");
        accountConfig.addAttributeMapping(LdapPredefinedAttributes.LASTNAME_NAME, "sn");
        accountConfig.addAttributeMapping("modifyTimeStamp", "modifyTimeStamp");

        groupConfig.setContainer(false);
        groupConfig.setUidAttribute("entryUUID");
        groupConfig.setNameAttribute("entryDN");
        groupConfig.addAttributeMapping("dn", "entryDN");
        groupConfig.addAttributeMapping("objectClass", "objectClass");
        groupConfig.addAttributeMapping("cn", "cn");
        groupConfig.addAttributeMapping("description", "description");
        groupConfig.addAttributeMapping("owner", "owner");
        groupConfig.addAttributeMapping("uniqueMember", "uniqueMember");
        groupConfig.addAttributeMapping(PredefinedAttributes.SHORT_NAME, "cn");
        groupConfig.addAttributeMapping(PredefinedAttributes.DESCRIPTION, "description");
        groupConfig.addDNMapping("owner", "cn");
        groupConfig.addDNMapping("uniqueMember", "cn");

        organizationConfig.setContainer(true);
        organizationConfig.setUidAttribute("entryUUID");
        organizationConfig.setNameAttribute("entryDN");
        organizationConfig.addAttributeMapping("o", "o");
        organizationConfig.addAttributeMapping("dn", "entryDN");
        organizationConfig.addAttributeMapping("objectClass", "objectClass");
        organizationConfig.addAttributeMapping(PredefinedAttributes.SHORT_NAME, "o");
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {
        if (useSsl && startTls) {
            String msg = "Cannot use StartTLS on a secure port";
            throw new ConfigurationException(msg);
        }

        if (baseDNs == null || baseDNs.length < 1) {
            throw new ConfigurationException("No base DN was provided in the LDAP configuration");
        }
        Set<String> baseDNSet = CollectionUtil.newCaseInsensitiveSet();
        baseDNSet.addAll(Arrays.asList(baseDNs));
        if (baseDNSet.size() != baseDNs.length) {
            throw new ConfigurationException("The list of base DNs in the LDAP configuration contains duplicates");
        }
        for (String baseDN : baseDNs) {
            try {
                if (baseDN == null) {
                    throw new ConfigurationException("The list of base DNs cannot contain null values");
                }
                new LdapName(baseDN);
            } catch (InvalidNameException e) {
                throw new ConfigurationException("The base DN " + baseDN + " in the LDAP configuration cannot be parsed");
            }
        }

        if (!isAuthenticationNone() && StringUtil.isBlank(bindDN)) {
            throw new ConfigurationException("No bind DN was provided in the LDAP configuration");
        }
        if (StringUtil.isNotBlank(bindDN) && bindPassword == null) {
            throw new ConfigurationException("No bind password was provided in the LDAP configuration");
        }
        if (bindDN != null) {
            try {
                new LdapName(bindDN);
            } catch (InvalidNameException e) {
                throw new ConfigurationException("The bind DN in the LDAP configuration cannot be parsed");
            }
        }

        if (extendedObjectClasses != null) {
            Set<String> extendedObjectClassSet = CollectionUtil.newCaseInsensitiveSet();
            extendedObjectClassSet.addAll(Arrays.asList(extendedObjectClasses));
            if (extendedObjectClassSet.size() != extendedObjectClasses.length) {
                throw new ConfigurationException("The list of extended object classes in the LDAP configuration contains duplicates");
            }
            for (String extendedObjectClass : extendedObjectClasses) {
                if (extendedObjectClass == null) {
                    throw new ConfigurationException("The list of extended object classes cannot contain null values");
                }
            }
        }
        if (extendedObjectClasses != null && extendedObjectClasses.length > 0) {
            if (extendedNamingAttributes == null || extendedNamingAttributes.length < extendedObjectClasses.length) {
                throw new ConfigurationException("No naming attributes were provided for all extended object classes in the LDAP configuration");
            }
            for (String extendedNamingAttribute : extendedNamingAttributes) {
                if (extendedNamingAttribute == null) {
                    throw new ConfigurationException("The list of extended naming attributes cannot contain null values");
                }
            }
        }

//        if (uuidAttribute == null) {
//            String msg = "The name of a UUID attribute was not provided in the LDAP configuration";
//            throw new ConfigurationException(msg);
//        }
//        if (passwordAttribute == null) {
//            String msg = "The name of a password attribute was not provided in the LDAP configuration";
//            throw new ConfigurationException(msg);
//        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        if (StringUtil.isBlank(host)) {
            throw new ConfigurationException("The host name should not be null or whitespace");
        }
        this.host = host;
    }

    public int getPort() {
        return port != -1 ? port : (useSsl ? DEFAULT_SSL_PORT : DEFAULT_PORT);
    }

    public void setPort(int port) {
        if (port < 0 || port > 0xffff) {
            throw new ConfigurationException("The port number should be 0 through 65535");
        }
        this.port = port;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public boolean isStartTls() {
        return startTls;
    }

    public void setStartTls(boolean startTls) {
        this.startTls = startTls;
    }

    public String[] getBaseDNs() {
        return baseDNs;
    }

    public void setBaseDNs(String... baseDNs) {
        if (baseDNs == null) {
            throw new ConfigurationException("The base DNs parameter cannot be null");
        }
        this.baseDNs = baseDNs;
    }

    public String getBindDN() {
        return bindDN;
    }

    public void setBindDN(String bindDN) {
        this.bindDN = bindDN;
    }

    @ConfigurationProperty(confidential = true)
    public GuardedString getBindPassword() {
        return bindPassword;
    }

    public void setBindPassword(GuardedString bindPassword) {
        this.bindPassword = bindPassword;
    }

//    public String getUuidAttribute() {
//        return uuidAttribute;
//    }
//
//    public void setUuidAttribute(String uuidAttribute) {
//        this.uuidAttribute = uuidAttribute;
//    }
//
//    public String getPasswordAttribute() {
//        return passwordAttribute;
//    }
//
//    public void setPasswordAttribute(String passwordAttribute) {
//        this.passwordAttribute = passwordAttribute;
//    }

    public String getAuthentication() {
        return authentication;
    }

    public boolean isAuthenticationNone() {
        return "none".equals(authentication);
    }

    public void setAuthentication(String authentication) {
        if (StringUtil.isBlank(authentication)) {
            throw new ConfigurationException("The authentication type should not be null or whitespace");
        }
        this.authentication = authentication;
    }

    public String[] getExtendedObjectClasses() {
        return extendedObjectClasses;
    }

    public void setExtendedObjectClasses(String... extendedObjectClasses) {
        if (extendedObjectClasses == null) {
            throw new ConfigurationException("The extended object classes parameter cannot be null");
        }
        this.extendedObjectClasses = (String[]) extendedObjectClasses.clone();
    }

    public String[] getExtendedNamingAttributes() {
        return extendedNamingAttributes;
    }

    public void setExtendedNamingAttributes(String... extendedNamingAttributes) {
        if (extendedNamingAttributes == null) {
            throw new ConfigurationException("The extended naming attributes parameter cannot be null");
        }
        this.extendedNamingAttributes = (String[]) extendedNamingAttributes.clone();
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isSimplePagedSearchPreferred() {
        return simplePagedSearchPreferred;
    }

    public void setSimplePagedSearchPreferred(boolean simplePagedSearchPreferred) {
        this.simplePagedSearchPreferred = simplePagedSearchPreferred;
    }

    public String getGroupMemberAttribute() {
        return groupMemberAttribute;
    }

    public void setGroupMemberAttribute(String groupMemberAttribute) {
        this.groupMemberAttribute = groupMemberAttribute;
    }

    // Getters and setters for configuration properties end here.

    public String getLdapUrl() {
        try {
            return new URI("ldap", null, getHost(), getPort(),  null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new ConnectorException(e);
        }
    }

    public boolean isContainedUnderBaseDNs(LdapName entry) {
        for (LdapName container : getBaseDNsAsLdapNames()) {
            if (entry.startsWith(container)) {
                return true;
            }
        }
        return false;
    }

    private List<LdapName> getBaseDNsAsLdapNames() {
        if (baseDNsAsLdapNames == null) {
            List<LdapName> result = new ArrayList<LdapName>(baseDNs.length);
            try {
                for (String baseDN : baseDNs) {
                    result.add(new LdapName(baseDN));
                }
            } catch (InvalidNameException e) {
                throw new ConfigurationException(e);
            }
            baseDNsAsLdapNames = result;
        }
        return baseDNsAsLdapNames;
    }

    public Map<ObjectClass, ObjectClassMappingConfig> getObjectClassMappingConfigs() {
        HashMap<ObjectClass, ObjectClassMappingConfig> result = new HashMap<ObjectClass, ObjectClassMappingConfig>();
        result.put(accountConfig.getObjectClass(), accountConfig);
        result.put(organizationConfig.getObjectClass(), organizationConfig);
        result.put(groupConfig.getObjectClass(), groupConfig);

        for (int i = 0; i < extendedObjectClasses.length; i++) {
            String extendedObjectClass = extendedObjectClasses[i];
            ObjectClassMappingConfig config = new ObjectClassMappingConfig(new ObjectClass(extendedObjectClass), extendedObjectClass);
            if (i < extendedNamingAttributes.length) {
                config.setNameAttribute(extendedNamingAttributes[i]);
            } else {
                // Just in the case extendedNamingAttributes is not in sync with
                // extendedObjectClasses, we need to set a default naming attribute -- one
                // that always exists.
                // XXX or perhaps just throw an exception.
                config.setNameAttribute("entryUUID");
            }
            if (!result.containsKey(config.getObjectClass())) {
                result.put(config.getObjectClass(), config);
            } else {
                log.warn("Ignoring mapping configuration for object class {0} because it is already mapped", config.getObjectClass().getObjectClassValue());
            }
        }
        return result;
    }

    public boolean isPagedSearchEnabled() {
        return pageSize > 0;
    }

    private EqualsHashCodeBuilder createHashCodeBuilder() {
        EqualsHashCodeBuilder builder = new EqualsHashCodeBuilder();
        builder.append(host);
        builder.append(port);
        builder.append(useSsl);
        builder.append(startTls);
        for (String baseDN : baseDNs) {
            builder.append(baseDN);
        }
        builder.append(bindDN);
        builder.append(bindPassword);
//        builder.append(uuidAttribute);
//        builder.append(passwordAttribute);
        builder.append(authentication);
        builder.append(accountConfig);
        builder.append(groupConfig);
        builder.append(organizationConfig);
        for (String extendedObjectClass : extendedObjectClasses) {
            builder.append(extendedObjectClass);
        }
        for (String extendedNamingAttribute : extendedNamingAttributes) {
            builder.append(extendedNamingAttribute);
        }
        builder.append(pageSize);
        builder.append(simplePagedSearchPreferred);
        return builder;
    }

    @Override
    public int hashCode() {
        return createHashCodeBuilder().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LdapConfiguration) {
            LdapConfiguration that = (LdapConfiguration) obj;
            if (this == that) {
                return true;
            }
            return this.createHashCodeBuilder().equals(that.createHashCodeBuilder());
        }
        return false;
    }
}
