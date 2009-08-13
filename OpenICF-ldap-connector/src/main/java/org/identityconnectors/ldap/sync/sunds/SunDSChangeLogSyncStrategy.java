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
package org.identityconnectors.ldap.sync.sunds;

import static java.util.Collections.singletonList;
import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveMap;
import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveSet;
import static org.identityconnectors.common.CollectionUtil.newSet;
import static org.identityconnectors.common.CollectionUtil.nullAsEmpty;
import static org.identityconnectors.common.StringUtil.isBlank;
import static org.identityconnectors.ldap.LdapUtil.checkedListByFilter;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import static org.identityconnectors.ldap.LdapUtil.isUnderContexts;
import static org.identityconnectors.ldap.LdapUtil.nullAsEmpty;
import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.Base64;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.GuardedByteArray.Accessor;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapEntry;
import org.identityconnectors.ldap.search.DefaultSearchStrategy;
import org.identityconnectors.ldap.search.LdapFilter;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.LdapSearch;
import org.identityconnectors.ldap.search.LdapSearches;
import org.identityconnectors.ldap.search.SearchResultsHandler;
import org.identityconnectors.ldap.sync.LdapSyncStrategy;
import org.identityconnectors.ldap.sync.sunds.LdifParser.ChangeSeparator;
import org.identityconnectors.ldap.sync.sunds.LdifParser.Line;
import org.identityconnectors.ldap.sync.sunds.LdifParser.NameValue;
import org.identityconnectors.ldap.sync.sunds.LdifParser.Separator;

/**
 * An implementation of the sync operation based on the retro change log
 * plugin of Sun Directory Server.
 */
public class SunDSChangeLogSyncStrategy implements LdapSyncStrategy {

    // TODO detect that the change log has been trimmed.

    private static final Log log = Log.getLog(SunDSChangeLogSyncStrategy.class);

    /**
     * The list of attribute operations supported by the "modify" LDIF change type.
     */
    private static final Set<String> LDIF_MODIFY_OPS;

    private final LdapConnection conn;
    private final ObjectClass oclass;

    private ChangeLogAttributes changeLogAttrs;
    private Set<String> oclassesToSync;
    private Set<String> attrsToSync;
    private PasswordDecryptor passwordDecryptor;

    static {
        LDIF_MODIFY_OPS = newCaseInsensitiveSet();
        LDIF_MODIFY_OPS.add("add");
        LDIF_MODIFY_OPS.add("delete");
        LDIF_MODIFY_OPS.add("replace");
    }

    public SunDSChangeLogSyncStrategy(LdapConnection conn, ObjectClass oclass) {
        this.conn = conn;
        this.oclass = oclass;
    }

    public SyncToken getLatestSyncToken() {
        return new SyncToken(getChangeLogAttributes().getLastChangeNumber());
    }

    public void sync(SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        String context = getChangeLogAttributes().getChangeLogContext();
        final String changeNumberAttr = getChangeNumberAttribute();
        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        controls.setReturningAttributes(new String[] { changeNumberAttr, "targetDN", "changeType", "changes", "newRdn", "deleteOldRdn", "newSuperior" });

        final int[] currentChangeNumber = { getStartChangeNumber(token) };

        final boolean[] results = new boolean[1];
        do {
            results[0] = false;

            String filter = getChangeLogSearchFilter(changeNumberAttr, currentChangeNumber[0]);
            LdapInternalSearch search = new LdapInternalSearch(conn, filter, singletonList(context), new DefaultSearchStrategy(false), controls);

            search.execute(new SearchResultsHandler() {
                public boolean handle(String baseDN, SearchResult result) throws NamingException {
                    results[0] = true;
                    LdapEntry entry = LdapEntry.create(baseDN, result);

                    int changeNumber = convertToInt(getStringAttrValue(entry.getAttributes(), changeNumberAttr), -1);
                    if (changeNumber > currentChangeNumber[0]) {
                        currentChangeNumber[0] = changeNumber;
                    }

                    SyncDelta delta = createSyncDelta(entry, changeNumber, options.getAttributesToGet());
                    if (delta != null) {
                        return handler.handle(delta);
                    }
                    return true;
                }
            });

            // We have already processed the current change.
            // In the next cycle we want to start with the next change.
            if (results[0]) {
                currentChangeNumber[0]++;
            }
        } while (results[0]);
    }

    private SyncDelta createSyncDelta(LdapEntry changeLogEntry, int changeNumber, String[] attrsToGetOption) {
        log.ok("Attempting to create sync delta for log entry {0}", changeNumber);

        String targetDN = getStringAttrValue(changeLogEntry.getAttributes(), "targetDN");
        if (targetDN == null) {
            log.error("Skipping log entry because it does not have a targetDN attribute");
            return null;
        }

        LdapName targetName = quietCreateLdapName(targetDN);
        if (filterOutByBaseContexts(targetName)) {
            log.ok("Skipping log entry because it does not match any of the base contexts to synchronize");
            return null;
        }

        String changeType = getStringAttrValue(changeLogEntry.getAttributes(), "changeType");
        SyncDeltaType deltaType = getSyncDeltaType(changeType);

        SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
        syncDeltaBuilder.setToken(new SyncToken(changeNumber));
        syncDeltaBuilder.setDeltaType(deltaType);

        if (deltaType.equals(SyncDeltaType.DELETE)) {
            log.ok("Creating sync delta for deleted entry");
            // XXX fix this!
            String uidAttr = conn.getSchemaMapping().getLdapUidAttribute(oclass);
            if (!LdapEntry.isDNAttribute(uidAttr)) {
                throw new ConnectorException("Unsupported Uid attribute " + uidAttr);
            }
            syncDeltaBuilder.setUid(conn.getSchemaMapping().createUid(oclass, targetDN));
            return syncDeltaBuilder.build();
        }

        String changes = getStringAttrValue(changeLogEntry.getAttributes(), "changes");
        Map<String, List<Object>> attrChanges = getAttributeChanges(changeType, changes);

        if (filterOutByModifiersNames(attrChanges)) {
            log.ok("Skipping entry because modifiersName is in the list of modifiersName's to filter out");
            return null;
        }

        if (filterOutByAttributes(attrChanges)) {
            log.ok("Skipping entry because no changed attributes in the list of attributes to synchronize");
            return null;
        }
        
        // If the change type was modrdn, we need to compute the DN that the entry
        // was modified to.
        String newTargetDN = targetDN;
        if ("modrdn".equalsIgnoreCase(changeType)) {
            String newRdn = getStringAttrValue(changeLogEntry.getAttributes(), "newRdn");
            if (isBlank(newRdn)) {
                log.error("Skipping log entry because it does not have a newRdn attribute");
                return null;
            }
            String newSuperior = getStringAttrValue(changeLogEntry.getAttributes(), "newSuperior");
            newTargetDN = getNewTargetDN(targetName, newSuperior, newRdn);
        }

        // Always specify the attributes to get. This will return attributes with
        // empty values when the attribute is not present, allowing the client to
        // detect that the attribute has been removed.
        Set<String> attrsToGet;
        if (attrsToGetOption != null) {
            attrsToGet = newSet(attrsToGetOption);
            // Do not retrieve the password attribute from the entry (usually it is an unusable
            // hashed value anyway). We will use the one from the change log below.
            attrsToGet.remove(OperationalAttributes.PASSWORD_NAME);
        } else {
            attrsToGet = newSet(LdapSearch.getAttributesReturnedByDefault(conn, oclass));
        }
        // If objectClass is not in the list of attributes to get, prepare to remove it later.
        boolean removeObjectClass = attrsToGet.add("objectClass");
        LdapFilter filter = LdapFilter.forEntryDN(newTargetDN).withNativeFilter(getModifiedEntrySearchFilter());

        ConnectorObject object = LdapSearches.findObject(conn, oclass, filter, attrsToGet.toArray(new String[attrsToGet.size()]));
        if (object == null) {
            log.ok("Skipping entry because the modified entry is missing, not of the right object class, or not matching the search filter");
            return null;
        }

        Attribute oclassAttr = object.getAttributeByName("objectClass");
        List<String> objectClasses = checkedListByFilter(nullAsEmpty(oclassAttr.getValue()), String.class);
        if (filterOutByObjectClasses(objectClasses)) {
            log.ok("Skipping entry because no object class in the list of object classes to synchronize");
            return null;
        }

        Attribute passwordAttr = null;
        if (conn.getConfiguration().isSynchronizePasswords()) {
            List<Object> passwordValues = attrChanges.get(conn.getConfiguration().getPasswordAttributeToSynchronize());
            if (!passwordValues.isEmpty()) {
                byte[] encryptedPwd = (byte[]) passwordValues.get(0); 
                String decryptedPwd = getPasswordDecryptor().decryptPassword(encryptedPwd);
                passwordAttr = AttributeBuilder.buildPassword(new GuardedString(decryptedPwd.toCharArray()));
            }
        }

        if (removeObjectClass || passwordAttr != null) {
            ConnectorObjectBuilder objectBuilder = new ConnectorObjectBuilder();
            objectBuilder.setObjectClass(object.getObjectClass());
            objectBuilder.setUid(object.getUid());
            objectBuilder.setName(object.getName());
            if (removeObjectClass) {
                for (Attribute attr : object.getAttributes()) {
                    if (attr != oclassAttr) {
                        objectBuilder.addAttribute(attr);
                    }
                }
            } else {
                objectBuilder.addAttributes(object.getAttributes());
            }
            if (passwordAttr != null) {
                objectBuilder.addAttribute(passwordAttr);
            }
            object = objectBuilder.build();
        }

        log.ok("Creating sync delta for created or updated entry");
        if ("modrdn".equalsIgnoreCase(changeType)) {
            String uidAttr = conn.getSchemaMapping().getLdapUidAttribute(oclass);
            // We can only set the previous Uid if it is the entry DN, which is readily available.
            if (LdapEntry.isDNAttribute(uidAttr)) {
                syncDeltaBuilder.setPreviousUid(conn.getSchemaMapping().createUid(oclass, targetDN));
            }
        }
        syncDeltaBuilder.setUid(object.getUid());
        syncDeltaBuilder.setObject(object);
        return syncDeltaBuilder.build();
    }

    private String getNewTargetDN(LdapName targetName, String newSuperior, String newRdn) {
        try {
            LdapName newTargetName;
            if (newSuperior == null) {
                newTargetName = new LdapName(targetName.getRdns());
                if (newTargetName.size() > 0) {
                    newTargetName.remove(targetName.size() - 1);
                }
            } else {
                newTargetName = quietCreateLdapName(newSuperior);
            }
            newTargetName.add(newRdn);
            return newTargetName.toString();
        } catch (InvalidNameException e) {
            throw new ConnectorException(e);
        }
    }

    private boolean filterOutByBaseContexts(LdapName targetName) {
        List<LdapName> baseContexts = conn.getConfiguration().getBaseContextsToSynchronizeAsLdapNames();
        if (baseContexts.isEmpty()) {
            baseContexts = conn.getConfiguration().getBaseContextsAsLdapNames();
        }
        if (!isUnderContexts(targetName, baseContexts)) {
            return true;
        }
        return false;
    }

    private boolean filterOutByModifiersNames(Map<String, List<Object>> changes) {
        Set<LdapName> filter = conn.getConfiguration().getModifiersNamesToFilterOutAsLdapNames();
        if (filter.isEmpty()) {
            log.ok("Filtering by modifiersName disabled");
            return false;
        }
        List<?> modifiersNameValues = changes.get("modifiersName");
        if (isEmpty(modifiersNameValues)) {
            log.ok("Not filtering by modifiersName because not set for this entry");
            return false;
        }
        LdapName modifiersName = quietCreateLdapName(modifiersNameValues.get(0).toString());
        return filter.contains(modifiersName);
    }

    private boolean filterOutByAttributes(Map<String, List<Object>> attrChanges) {
        Set<String> filter = getAttributesToSynchronize();
        if (filter.isEmpty()) {
            log.ok("Filtering by attributes disabled");
            return false;
        }
        Set<String> changedAttrs = attrChanges.keySet();
        return !containsAny(filter, changedAttrs);
    }

    private boolean filterOutByObjectClasses(List<String> objectClasses) {
        Set<String> filter = getObjectClassesToSynchronize();
        if (filter.isEmpty()) {
            log.ok("Filtering by object class disabled");
            return false;
        }
        return !containsAny(filter, objectClasses);
    }

    private SyncDeltaType getSyncDeltaType(String changeType) {
        if ("delete".equalsIgnoreCase(changeType)) {
            return SyncDeltaType.DELETE;
        }
        return SyncDeltaType.CREATE_OR_UPDATE;
    }

    private String getModifiedEntrySearchFilter() {
        if (oclass.equals(ObjectClass.ACCOUNT)) {
            return conn.getConfiguration().getAccountSynchronizationFilter();
        }
        return null;
    }

    private int getStartChangeNumber(SyncToken lastToken) {
        Integer lastTokenValue = lastToken != null ? (Integer) lastToken.getValue() : null;
        if (lastTokenValue == null) {
            return getChangeLogAttributes().getFirstChangeNumber();
        }
        return lastTokenValue + 1; // Since the token value is the last value.
    }

    private Map<String, List<Object>> getAttributeChanges(String changeType, String ldif) {
        Map<String, List<Object>> result = newCaseInsensitiveMap();

        if ("modify".equalsIgnoreCase(changeType)) {
            LdifParser parser = new LdifParser(ldif);
            Iterator<Line> lines = parser.iterator();
            while (lines.hasNext()) {
                Line line = lines.next();
                // We only expect one change, so ignore any change separators.
                if (line instanceof Separator || line instanceof ChangeSeparator) {
                    continue;
                }
                NameValue nameValue = (NameValue) line;
                String operation = nameValue.getName();
                String attrName = nameValue.getValue();
                if (LDIF_MODIFY_OPS.contains(operation)) {
                    List<Object> values = new ArrayList<Object>();
                    while (lines.hasNext()) {
                        line = lines.next();
                        if (line instanceof Separator) {
                            break;
                        }
                        nameValue = (NameValue) line;
                        Object value = decodeAttributeValue(nameValue);
                        if (value != null) {
                            values.add(value);
                        }
                    }
                    if (!values.isEmpty()) {
                        result.put(attrName, values);
                    }
                }
            }
        } else if ("add".equalsIgnoreCase(changeType)) {
            LdifParser parser = new LdifParser(ldif);
            Iterator<Line> lines = parser.iterator();
            while (lines.hasNext()) {
                Line line = lines.next();
                // We only expect one change, so ignore any change separators.
                if (line instanceof Separator || line instanceof ChangeSeparator) {
                    continue;
                }
                NameValue nameValue = (NameValue) line;
                Object value = decodeAttributeValue(nameValue);
                if (value != null) {
                    List<Object> values = result.get(nameValue.getName());
                    if (values == null) {
                        values = new ArrayList<Object>();
                        result.put(nameValue.getName(), values);
                    }
                    values.add(value);
                }
            }
        }

        // Returning an empty map when changeType is "delete" or "modrdn".
        return result;
    }

    private Object decodeAttributeValue(NameValue nameValue) {
        String value = nameValue.getValue();
        if (value.startsWith(":")) {
            // This is a Base64 encoded value...
            String base64 = value.substring(1).trim();
            try {
                return Base64.decode(base64);
                // TODO the adapter had code here to convert the byte array
                // to a string if the attribute was of a string type. Since
                // that information is in the schema and we don't have access
                // to the resource schema, leaving that functionality out for now.
            } catch (Exception e) {
                log.error("Could not decode attribute {0} with Base64 value {1}", nameValue.getName(), nameValue.getValue());
                return null;
            }
        } else {
            return value;
        }
    }

    private String getChangeLogSearchFilter(String changeNumberAttr, int startChangeNumber) {
        int blockSize = conn.getConfiguration().getChangeLogBlockSize();
        boolean filterWithOrInsteadOfAnd = conn.getConfiguration().isFilterWithOrInsteadOfAnd();
        boolean filterByLogEntryOClass = !conn.getConfiguration().isRemoveLogEntryObjectClassFromFilter();

        StringBuilder result = new StringBuilder();
        if (filterWithOrInsteadOfAnd) {
            if (filterByLogEntryOClass) {
                result.append("(&(objectClass=changeLogEntry)");
            }
            result.append("(|(");
            result.append(changeNumberAttr);
            result.append('=');
            result.append(startChangeNumber);
            result.append(')');

            int endChangeNumber = startChangeNumber + blockSize;
            for (int i = startChangeNumber + 1; i <= endChangeNumber; i++) {
                result.append("(");
                result.append(changeNumberAttr);
                result.append('=');
                result.append(i);
                result.append(')');
            }

            result.append(')');
            if (filterByLogEntryOClass) {
                result.append(')');
            }
        } else {
            result.append("(&");
            if (filterByLogEntryOClass) {
                result.append("(objectClass=changeLogEntry)");
            }
            result.append("(");
            result.append(changeNumberAttr);
            result.append(">=");
            result.append(startChangeNumber);
            result.append(')');

            int endChangeNumber = startChangeNumber + blockSize;
            result.append("(");
            result.append(changeNumberAttr);
            result.append("<=");
            result.append(endChangeNumber);
            result.append(')');

            result.append(')');
        }

        return result.toString();
    }

    ChangeLogAttributes getChangeLogAttributes() {
        if (changeLogAttrs == null) {
            try {
                Attributes attrs = conn.getInitialContext().getAttributes("", new String[] { "changeLog", "firstChangeNumber", "lastChangeNumber" });
                String changeLog = getStringAttrValue(attrs, "changeLog");
                String firstChangeNumber = getStringAttrValue(attrs, "firstChangeNumber");
                String lastChangeNumber = getStringAttrValue(attrs, "lastChangeNumber");
                if (changeLog == null || firstChangeNumber == null | lastChangeNumber == null) {
                    String error = "Unable to locate the replication change log.\n"+
                            "From the admin console please verify that the "+
                            "change log is enabled under Configuration: "+
                            "Replication: Supplier Settings and that the Retro "+
                            "Change Log Plugin is enabled under Configuration: "+
                            "Plug-ins: Retro Change Log Plugin";
                    throw new ConnectorException(error);
                }
                changeLogAttrs = new ChangeLogAttributes(changeLog, convertToInt(firstChangeNumber, 0), convertToInt(lastChangeNumber, 0));
            } catch (NamingException e) {
                throw new ConnectorException(e);
            }
        }
        return changeLogAttrs;
    }

    private String getChangeNumberAttribute() {
        String result = conn.getConfiguration().getChangeNumberAttribute();
        if (isBlank(result)) {
            result = "changeNumber";
        }
        return result;
    }

    private Set<String> getAttributesToSynchronize() {
        if (attrsToSync == null) {
            Set<String> result = newCaseInsensitiveSet();
            result.addAll(Arrays.asList(nullAsEmpty(conn.getConfiguration().getAttributesToSynchronize())));
            if (conn.getConfiguration().isSynchronizePasswords()) {
                result.add(conn.getConfiguration().getPasswordAttributeToSynchronize());
            }
            attrsToSync = result;
        }
        return attrsToSync;
    }

    private Set<String> getObjectClassesToSynchronize() {
        if (oclassesToSync == null) {
            Set<String> result = newCaseInsensitiveSet();
            result.addAll(Arrays.asList(nullAsEmpty(conn.getConfiguration().getObjectClassesToSynchronize())));
            oclassesToSync = result;
        }
        return oclassesToSync;
    }
    
    private PasswordDecryptor getPasswordDecryptor() {
        if (passwordDecryptor == null) {
            conn.getConfiguration().getPasswordDecryptionKey().access(new Accessor() {
                public void access(final byte[] decryptionKey) {
                    conn.getConfiguration().getPasswordDecryptionInitializationVector().access(new Accessor() {
                        public void access(byte[] decryptionIV) {
                            passwordDecryptor = new PasswordDecryptor(decryptionKey, decryptionIV);
                        }
                    });
                }
            });
        }
        assert passwordDecryptor != null;
        return passwordDecryptor;
    }

    private boolean containsAny(Set<String> haystack, Collection<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static int convertToInt(String number, int def) {
        int result = def;
        if (number != null && number.length() > 0) {
            int decimal = number.indexOf('.');
            if (decimal > 0) {
                number = number.substring(0, decimal);
            }
            try {
                result = Integer.parseInt(number);
            } catch (NumberFormatException e) {
                // Ignore.
            }
        }
        return result;
    }
}
