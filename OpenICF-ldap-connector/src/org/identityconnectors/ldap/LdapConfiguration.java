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

import static java.util.Collections.singletonList;
import static org.identityconnectors.common.CollectionUtil.newList;
import static org.identityconnectors.common.StringUtil.isBlank;
import static org.identityconnectors.ldap.LdapUtil.nullAsEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.EqualsHashCodeBuilder;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.operations.SyncOp;

/**
 * Encapsulates the LDAP connector's configuration.
 *
 * @author Andrei Badea
 */
public class LdapConfiguration extends AbstractConfiguration {

    // XXX should try to connect to the resource.
    // XXX add @ConfigurationProperty.

    private static final Log log = Log.getLog(LdapConfiguration.class);

    static final int DEFAULT_PORT = 389;

    // Exposed configuration properties.

    /**
     * The LDAP host server to connect to.
     */
    private String host;

    /**
     * The port the server is listening on.
     */
    private int port = DEFAULT_PORT;

    /**
     * Whether the port is a secure SSL port.
     */
    private boolean ssl;

    /**
     * LDAP URL's to connect to if the main server specified through the host and port
     * properties is not available.
     */
    private String[] failover = { };

    /**
     * The bind DN for performing operations on the server.
     */
    private String principal;

    /**
     * The bind password associated with the bind DN.
     */
    private GuardedString credentials;

    /**
     * The name of the password attribute.
     */
    private String passwordAttribute = "userPassword";

    /**
     * The authentication mechanism to use against the LDAP server.
     */
    private String authentication = null;

    /**
     * The base DNs for operations on the server.
     */
    private String[] baseContexts = { };

    /**
     * A search filter that any account needs to match in order to be returned.
     */
    private String accountSearchFilter = null;

    /**
     * The LDAP attribute holding the member for non-POSIX static groups.
     */
    private String groupMemberAttribute = "uniqueMember";

    /**
     * If true, will modify group membership of renamed/deleted entries.
     */
    private boolean maintainLdapGroupMembership = false;

    /**
     * If true, will modify POSIX group membership of renamed/deleted entries.
     */
    private boolean maintainPosixGroupMembership = false;

    /**
     * If true, when binding check for the Password Expired control (and also Password Policy control)
     * and throw exceptions (PasswordExpiredException, etc.) appropriately.
     */
    private boolean respectResourcePasswordPolicyChangeAfterReset;

    /**
     * Whether to use block-based LDAP controls like simple paged results or VLV control.
     */
    private boolean useBlocks = true;

    /**
     * The block size (not count, but that's what IDM calls it) for paged and VLV index searches.
     */
    private int blockCount = 100;

    /**
     * If true, simple paged search will be preferred over VLV index search
     * when both are available.
     */
    private boolean usePagedResultControl = false;

    /**
     * The attribute used as the sort key for the VLV index.
     */
    private String vlvSortAttribute = "uid";

    /**
     * The LDAP attribute to map Uid to.
     */
    private String uidAttribute = "entryUUID";

    /**
     * Whether to read the schema from the server.
     */
    private boolean readSchema = true;

    /**
     * The set of object classes to return in the schema
     * (apart from those returned by default).
     */
    private String[] extendedObjectClasses = { };

    /**
     * The naming attributes for the extended object classes:
     * {@code extendedNamingAttributes[i]} is the naming attribute for
     * {@code extendedObjectClasses[i]}.
     */
    private String[] extendedNamingAttributes = { };

    // Sync configuration properties.

    private String[] baseContextsToSynchronize = { };

    private String[] objectClassesToSynchronize = { "inetOrgPerson" };

    private String[] attributesToSynchronize = { };

    private String[] modifiersNamesToFilterOut = { };

    private String accountSynchronizationFilter;

    private int changeLogBlockSize = 100;

    private String changeNumberAttribute = "changeNumber";

    private boolean filterWithOrInsteadOfAnd;

    private boolean removeLogEntryObjectClassFromFilter = true;

    // Other state.

    private final ObjectClassMappingConfig accountConfig = new ObjectClassMappingConfig(ObjectClass.ACCOUNT,
            newList("top", "person", "organizationalPerson", "inetOrgPerson"));

    private final ObjectClassMappingConfig groupConfig = new ObjectClassMappingConfig(ObjectClass.GROUP,
            newList("top", "groupOfUniqueNames"));

    // Other state not to be included in hashCode/equals.

    private List<LdapName> baseContextsAsLdapNames;

    private List<LdapName> baseContextsToSynchronizeAsLdapNames;

    private Set<LdapName> modifiersNamesToFilterOutAsLdapNames;

    public LdapConfiguration() {
        // Note: order is important!

        accountConfig.setNameAttribute("entryDN");
        accountConfig.addAttributeMapping("uid", "uid");
        accountConfig.addAttributeMapping("cn", "cn");
        accountConfig.addAttributeMapping("givenName", "givenName");
        accountConfig.addAttributeMapping("sn", "sn");
        accountConfig.addAttributeMapping("modifyTimeStamp", "modifyTimeStamp");

        groupConfig.setNameAttribute("entryDN");
        groupConfig.addAttributeMapping("cn", "cn");
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {
        if (isBlank(host)) {
            throw new ConfigurationException("The host cannot be blank");
        }

        if (port < 0 || port > 0xffff) {
            throw new ConfigurationException("The port number should be 0 through 65535");
        }

        if (failover == null) {
            throw new ConfigurationException("The failover property cannot not be null");
        }

        if (isBlank(passwordAttribute)) {
            throw new ConfigurationException("The password attribute cannot be blank");
        }

        if (baseContexts == null || baseContexts.length < 1) {
            throw new ConfigurationException("The list of base contexts cannot be empty");
        }
        for (String baseContext : baseContexts) {
            try {
                if (isBlank(baseContext)) {
                    throw new ConfigurationException("The list of base contexts cannot contain blank values");
                }
                new LdapName(baseContext);
            } catch (InvalidNameException e) {
                throw new ConfigurationException("The base context " + baseContext + " cannot be parsed");
            }
        }

        if (accountConfig.getLdapClasses().size() < 1) {
            throw new ConfigurationException("The list of account object classes cannot be empty");
        }
        for (String accountObjectClass : accountConfig.getLdapClasses()) {
            if (isBlank(accountObjectClass)) {
                throw new ConfigurationException("The list of account object classes cannot contain blank values");
            }
        }

        if (blockCount < 0) {
            throw new ConfigurationException("The block size should be greather than 0");
        }

        if (isBlank(uidAttribute)) {
            throw new ConfigurationException("The attribute to map to Uid cannot be empty");
        }

        if (extendedObjectClasses == null) {
            throw new ConfigurationException("The list of extended object classes cannot be null");
        }
        if (extendedNamingAttributes == null) {
            throw new ConfigurationException("The list of extended naming attributes cannot be null");
        }
        for (String extendedObjectClass : extendedObjectClasses) {
            if (isBlank(extendedObjectClass)) {
                throw new ConfigurationException("The list of extended object classes cannot contain blank values");
            }
        }
        if (extendedObjectClasses.length > 0) {
            if (!readSchema) {
                throw new ConfigurationException("The readSchema property must be true when using extended object classes");
            }
            if (extendedNamingAttributes.length < extendedObjectClasses.length) {
                throw new ConfigurationException("No naming attributes were provided for all extended object classes");
            }
            for (String extendedNamingAttribute : extendedNamingAttributes) {
                if (isBlank(extendedNamingAttribute)) {
                    throw new ConfigurationException("The list of extended naming attributes cannot contain blank values");
                }
            }
        }

        if (changeLogBlockSize < 0) {
            throw new ConfigurationException("The synchronization block size should be greather than 0");
        }

        if (isBlank(changeNumberAttribute)) {
            throw new ConfigurationException("The change number attribute cannot be blank");
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String[] getFailover() {
        return failover.clone();
    }

    public void setFailover(String... failover) {
        this.failover = failover;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    @ConfigurationProperty(confidential = true)
    public GuardedString getCredentials() {
        return credentials;
    }

    public void setCredentials(GuardedString credentials) {
        this.credentials = credentials;
    }

    public String getPasswordAttribute() {
        return passwordAttribute;
    }

    public void setPasswordAttribute(String passwordAttribute) {
        this.passwordAttribute = passwordAttribute;
    }

    public String getAuthentication() {
        return authentication;
    }

    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String[] getBaseContexts() {
        return baseContexts.clone();
    }

    public void setBaseContexts(String... baseContexts) {
        this.baseContexts = baseContexts.clone();
    }

    public String[] getAccountObjectClasses() {
        List<String> ldapClasses = accountConfig.getLdapClasses();
        return ldapClasses.toArray(new String[ldapClasses.size()]);
    }

    public void setAccountObjectClasses(String... accountObjectClasses) {
        accountConfig.setLdapClasses(Arrays.asList(accountObjectClasses));
    }

    public String getAccountSearchFilter() {
        return accountSearchFilter;
    }

    public void setAccountSearchFilter(String accountSearchFilter) {
        this.accountSearchFilter = accountSearchFilter;
    }

    public String getGroupMemberAttribute() {
        return groupMemberAttribute;
    }

    public void setGroupMemberAttribute(String groupMemberAttribute) {
        this.groupMemberAttribute = groupMemberAttribute;
    }

    public boolean isMaintainLdapGroupMembership() {
        return maintainLdapGroupMembership;
    }

    public void setMaintainLdapGroupMembership(boolean maintainLdapGroupMembership) {
        this.maintainLdapGroupMembership = maintainLdapGroupMembership;
    }

    public boolean isMaintainPosixGroupMembership() {
        return maintainPosixGroupMembership;
    }

    public void setMaintainPosixGroupMembership(boolean maintainPosixGroupMembership) {
        this.maintainPosixGroupMembership = maintainPosixGroupMembership;
    }

    public boolean isRespectResourcePasswordPolicyChangeAfterReset() {
        return respectResourcePasswordPolicyChangeAfterReset;
    }

    public void setRespectResourcePasswordPolicyChangeAfterReset(boolean respectResourcePasswordPolicyChangeAfterReset) {
        this.respectResourcePasswordPolicyChangeAfterReset = respectResourcePasswordPolicyChangeAfterReset;
    }

    public boolean isUseBlocks() {
        return useBlocks;
    }

    public void setUseBlocks(boolean useBlocks) {
        this.useBlocks = useBlocks;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    public boolean isUsePagedResultControl() {
        return usePagedResultControl;
    }

    public String getVlvSortAttribute() {
        return vlvSortAttribute;
    }

    public void setVlvSortAttribute(String vlvSortAttribute) {
        this.vlvSortAttribute = vlvSortAttribute;
    }

    public void setUsePagedResultControl(boolean usePagedResultControl) {
        this.usePagedResultControl = usePagedResultControl;
    }

    public String getUidAttribute() {
        return uidAttribute;
    }

    public void setUidAttribute(String uidAttribute) {
        this.uidAttribute = uidAttribute;
    }

    public boolean isReadSchema() {
        return readSchema;
    }

    public void setReadSchema(boolean readSchema) {
        this.readSchema = readSchema;
    }

    public String[] getExtendedObjectClasses() {
        return extendedObjectClasses.clone();
    }

    public void setExtendedObjectClasses(String... extendedObjectClasses) {
        this.extendedObjectClasses = (String[]) extendedObjectClasses.clone();
    }

    public String[] getExtendedNamingAttributes() {
        return extendedNamingAttributes.clone();
    }

    public void setExtendedNamingAttributes(String... extendedNamingAttributes) {
        this.extendedNamingAttributes = (String[]) extendedNamingAttributes.clone();
    }

    // Sync properties getters and setters.

    @ConfigurationProperty(operations = { SyncOp.class })
    public String[] getBaseContextsToSynchronize() {
        return baseContextsToSynchronize.clone();
    }

    public void setBaseContextsToSynchronize(String... baseContextsToSynchronize) {
        this.baseContextsToSynchronize = (String[]) baseContextsToSynchronize.clone();
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public String[] getObjectClassesToSynchronize() {
        return objectClassesToSynchronize.clone();
    }

    public void setObjectClassesToSynchronize(String... objectClassesToSynchronize) {
        this.objectClassesToSynchronize = (String[]) objectClassesToSynchronize.clone();
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public String[] getAttributesToSynchronize() {
        return attributesToSynchronize.clone();
    }

    public void setAttributesToSynchronize(String... attributesToSynchronize) {
        this.attributesToSynchronize = (String[]) attributesToSynchronize.clone();
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public String[] getModifiersNamesToFilterOut() {
        return modifiersNamesToFilterOut.clone();
    }

    public void setModifiersNamesToFilterOut(String... modifiersNamesToFilterOut) {
        this.modifiersNamesToFilterOut = (String[]) modifiersNamesToFilterOut.clone();
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public String getAccountSynchronizationFilter() {
        return accountSynchronizationFilter;
    }

    public void setAccountSynchronizationFilter(String accountSynchronizationFilter) {
        this.accountSynchronizationFilter = accountSynchronizationFilter;
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public int getChangeLogBlockSize() {
        return changeLogBlockSize;
    }

    public void setChangeLogBlockSize(int changeLogBlockSize) {
        this.changeLogBlockSize = changeLogBlockSize;
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public String getChangeNumberAttribute() {
        return changeNumberAttribute;
    }

    public void setChangeNumberAttribute(String changeNumberAttribute) {
        this.changeNumberAttribute = changeNumberAttribute;
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public boolean isFilterWithOrInsteadOfAnd() {
        return filterWithOrInsteadOfAnd;
    }

    public void setFilterWithOrInsteadOfAnd(boolean filterWithOrInsteadOfAnd) {
        this.filterWithOrInsteadOfAnd = filterWithOrInsteadOfAnd;
    }

    @ConfigurationProperty(operations = { SyncOp.class })
    public boolean isRemoveLogEntryObjectClassFromFilter() {
        return removeLogEntryObjectClassFromFilter;
    }

    public void setRemoveLogEntryObjectClassFromFilter(boolean removeLogEntryObjectClassFromFilter) {
        this.removeLogEntryObjectClassFromFilter = removeLogEntryObjectClassFromFilter;
    }

    // Getters and setters for configuration properties end here.

    public List<LdapName> getBaseContextsAsLdapNames() {
        if (baseContextsAsLdapNames == null) {
            List<LdapName> result = new ArrayList<LdapName>(baseContexts.length);
            try {
                for (String baseContext : baseContexts) {
                    result.add(new LdapName(baseContext));
                }
            } catch (InvalidNameException e) {
                throw new ConfigurationException(e);
            }
            baseContextsAsLdapNames = result;
        }
        return baseContextsAsLdapNames;
    }

    public List<LdapName> getBaseContextsToSynchronizeAsLdapNames() {
        if (baseContextsToSynchronizeAsLdapNames == null) {
            String[] source = nullAsEmpty(baseContextsToSynchronize);
            List<LdapName> result = new ArrayList<LdapName>(source.length);
            try {
                for (String each : source) {
                    result.add(new LdapName(each));
                }
            } catch (InvalidNameException e) {
                throw new ConfigurationException(e);
            }
            baseContextsToSynchronizeAsLdapNames = result;
        }
        return baseContextsToSynchronizeAsLdapNames;
    }

    public Set<LdapName> getModifiersNamesToFilterOutAsLdapNames() {
        if (modifiersNamesToFilterOutAsLdapNames == null) {
            String[] source = nullAsEmpty(modifiersNamesToFilterOut);
            Set<LdapName> result = new HashSet<LdapName>(source.length);
            try {
                for (String each : source) {
                    result.add(new LdapName(each));
                }
            } catch (InvalidNameException e) {
                throw new ConfigurationException(e);
            }
            modifiersNamesToFilterOutAsLdapNames = result;
        }
        return modifiersNamesToFilterOutAsLdapNames;
    }

    public Map<ObjectClass, ObjectClassMappingConfig> getObjectClassMappingConfigs() {
        HashMap<ObjectClass, ObjectClassMappingConfig> result = new HashMap<ObjectClass, ObjectClassMappingConfig>();
        result.put(accountConfig.getObjectClass(), accountConfig);
        result.put(groupConfig.getObjectClass(), groupConfig);

        for (int i = 0; i < extendedObjectClasses.length; i++) {
            String extendedObjectClass = extendedObjectClasses[i];
            ObjectClassMappingConfig config = new ObjectClassMappingConfig(new ObjectClass(extendedObjectClass),
                    singletonList(extendedObjectClass));
            if (i < extendedNamingAttributes.length) {
                config.setNameAttribute(extendedNamingAttributes[i]);
            } else {
                // Just in the case extendedNamingAttributes is not in sync with
                // extendedObjectClasses, we need to set a default naming attribute -- one
                // that always exists.
                // XXX or perhaps just throw an exception.
                config.setNameAttribute("entryDN");
            }
            if (!result.containsKey(config.getObjectClass())) {
                result.put(config.getObjectClass(), config);
            } else {
                log.warn("Ignoring mapping configuration for object class {0} because it is already mapped", config.getObjectClass().getObjectClassValue());
            }
        }
        return result;
    }

    private EqualsHashCodeBuilder createHashCodeBuilder() {
        EqualsHashCodeBuilder builder = new EqualsHashCodeBuilder();
        // Exposed configuration properties.
        builder.append(host);
        builder.append(port);
        builder.append(ssl);
        builder.append(failover);
        builder.append(principal);
        builder.append(credentials);
        builder.append(passwordAttribute);
        builder.append(authentication);
        for (String baseContext : baseContexts) {
            builder.append(baseContext);
        }
        builder.append(accountSearchFilter);
        builder.append(groupMemberAttribute);
        builder.append(maintainLdapGroupMembership);
        builder.append(maintainPosixGroupMembership);
        builder.append(respectResourcePasswordPolicyChangeAfterReset);
        builder.append(useBlocks);
        builder.append(blockCount);
        builder.append(usePagedResultControl);
        builder.append(vlvSortAttribute);
        builder.append(uidAttribute);
        builder.append(readSchema);
        for (String extendedObjectClass : extendedObjectClasses) {
            builder.append(extendedObjectClass);
        }
        for (String extendedNamingAttribute : extendedNamingAttributes) {
            builder.append(extendedNamingAttribute);
        }
        // Sync configuration properties.
        for (String baseContextToSynchronize : baseContextsToSynchronize) {
            builder.append(baseContextToSynchronize);
        }
        for (String objectClassToSynchronize : objectClassesToSynchronize) {
            builder.append(objectClassToSynchronize);
        }
        for (String attributeToSynchronize : attributesToSynchronize) {
            builder.append(attributeToSynchronize);
        }
        for (String modifiersNameToFilterOut : modifiersNamesToFilterOut) {
            builder.append(modifiersNameToFilterOut);
        }
        builder.append(accountSynchronizationFilter);
        builder.append(changeLogBlockSize);
        builder.append(changeNumberAttribute);
        builder.append(filterWithOrInsteadOfAnd);
        builder.append(removeLogEntryObjectClassFromFilter);
        // Other state.
        builder.append(accountConfig);
        builder.append(groupConfig);
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
