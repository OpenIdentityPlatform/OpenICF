/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
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

package org.identityconnectors.testconnector;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.spi.StatefulConfiguration;

public class TstStatefulConnectorConfig extends TstConnectorConfig implements StatefulConfiguration {

    private boolean caseIgnore = false;

    public boolean isCaseIgnore() {
        return caseIgnore;
    }

    public void setCaseIgnore(boolean caseIgnore) {
        this.caseIgnore = caseIgnore;
    }

    private SortedSet<String> testObjectClasses =
            new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

    public String[] getTestObjectClass() {
        return testObjectClasses.toArray(new String[testObjectClasses.size()]);
    }

    public void setTestObjectClass(String[] testObjectClasses) {
        this.testObjectClasses.clear();
        if (null != testObjectClasses) {
            this.testObjectClasses.addAll(Arrays.asList(testObjectClasses));
        }
    }

    private boolean returnNullTest = false;

    public boolean isReturnNullTest() {
        return returnNullTest;
    }

    public void setReturnNullTest(boolean returnNullTest) {
        this.returnNullTest = returnNullTest;
    }

    private String randomString = null;

    public String getRandomString() {
        return randomString;
    }

    public void setRandomString(String randomString) {
        this.randomString = randomString;
    }

    private UUID guid;

    private ScheduledExecutorService executorService = null;

    public synchronized UUID getGuid() {
        if (null == guid) {
            guid = UUID.randomUUID();
        }
        return guid;
    }

    public void release() {
        guid = null;
        if (null != executorService) {
            executorService.shutdown();
        }
    }

    ScheduledExecutorService getExecutorService() {
        if (null == executorService) {
            synchronized (this) {
                if (null == executorService) {
                    executorService = Executors.newSingleThreadScheduledExecutor();
                }
            }
        }
        return executorService;
    }

    boolean isTestObjectClass(ObjectClass objectClass) {
        return null != objectClass && testObjectClasses.contains(objectClass.getObjectClassValue());
    }

    public Uid resolveByUsername(ObjectClass objectClass, String username) {
        ObjectClassCacheEntry cache = objectCache.get(objectClass);
        if (null != cache) {
            ConnectorObjectCacheEntry entry = cache.getByName(username);
            if (null != entry) {
                return entry.object.getUid();
            }
        }
        return null;
    }

    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password) {
        ObjectClassCacheEntry cache = objectCache.get(objectClass);
        if (null != cache) {
            ConnectorObjectCacheEntry entry = cache.getByName(username);
            if (null != entry) {
                if (entry.authenticate(password)) {
                    return entry.object.getUid();
                } else {
                    throw new InvalidPasswordException("Invalid Password");
                }
            } else {
                throw new InvalidCredentialException("Unknown username: " + username);
            }
        } else {
            throw new InvalidCredentialException("Empty ObjectClassCache: "
                    + objectClass.getObjectClassValue());
        }
    }

    SortedMap<Integer, SyncDelta> sync(ObjectClass objectClass, int token) {
        SortedMap<Integer, SyncDelta> result = new TreeMap<Integer, SyncDelta>();
        ObjectClassCacheEntry cache = objectCache.get(objectClass);
        if (null != cache) {
            for (ConnectorObjectCacheEntry entry : cache.objectCache.values()) {
                int rev = Integer.parseInt(entry.object.getUid().getRevision());
                if (rev > token) {
                    result.put(rev, new SyncDeltaBuilder().setDeltaType(entry.deltaType).setToken(
                            new SyncToken(rev)).setObject(entry.object).build());
                }
            }
        }
        return result;
    }

    private final AtomicInteger revision = new AtomicInteger(0);

    int getLatestSyncToken() {
        return revision.get();
    }

    Uid getNextUid(String uid) {
        return new Uid(uid, String.valueOf(revision.getAndIncrement()));
    }

    private final AtomicInteger id = new AtomicInteger(0);

    Uid getNewUid() {
        return getNextUid(String.valueOf(id.getAndIncrement()));
    }

    private final ConcurrentMap<ObjectClass, ObjectClassCacheEntry> objectCache =
            new ConcurrentHashMap<ObjectClass, ObjectClassCacheEntry>();

    ObjectClassCacheEntry geObjectCache(ObjectClass objectClass) {
        ObjectClassCacheEntry cache = objectCache.get(objectClass);
        if (null == cache) {
            cache = new ObjectClassCacheEntry(objectClass);
            ObjectClassCacheEntry rv = objectCache.putIfAbsent(objectClass, cache);
            if (null != rv) {
                cache = rv;
            }
        }
        return cache;
    }

    class ObjectClassCacheEntry {

        private final ObjectClass objectClass;

        private final ReadWriteLock lock = new ReentrantReadWriteLock(false);

        private ConcurrentMap<String, String> uniqueNameIndex =
                new ConcurrentSkipListMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        private ConcurrentMap<String, ConnectorObjectCacheEntry> objectCache =
                new ConcurrentHashMap<String, ConnectorObjectCacheEntry>();

        public ObjectClassCacheEntry(final ObjectClass objectClass) {
            this.objectClass = objectClass;
        }

        public ConnectorObjectCacheEntry getByName(String username) {
            String uid = uniqueNameIndex.get(username);
            if (null != uid) {
                return objectCache.get(uid);
            }
            return null;
        }

        public Uid create(Set<Attribute> createAttributes) {
            Name name = AttributeUtil.getNameFromAttributes(createAttributes);
            if (name == null) {
                throw new InvalidAttributeValueException("__NAME__ Required");
            }
            if (StringUtil.isBlank(name.getNameValue())) {
                throw new InvalidAttributeValueException("__NAME__ can not be blank");
            }
            Uid uid = getNewUid();
            if (uniqueNameIndex.putIfAbsent(name.getNameValue(), uid.getUidValue()) == null) {
                objectCache.putIfAbsent(uid.getUidValue(), new ConnectorObjectCacheEntry(
                        new ConnectorObjectBuilder().setObjectClass(objectClass).addAttributes(
                                createAttributes).setUid(uid).build()));
                return uid;
            } else {
                throw new AlreadyExistsException().initUid(new Uid(name.getNameValue()));
            }
        }

        public Uid update(Uid uid, Set<Attribute> updateAttributes) {
            ConnectorObjectCacheEntry entry = objectCache.get(uid.getUidValue());
            if (null == entry) {
                throw new UnknownUidException(uid, objectClass);
            }

            try {
                if (lock.writeLock().tryLock(1, TimeUnit.MINUTES)) {
                    try {
                        Map<String, Attribute> attributeMap =
                                CollectionUtil.<Attribute> newCaseInsensitiveMap();
                        for (Attribute attr : entry.object.getAttributes()) {
                            attributeMap.put(attr.getName(), attr);
                        }
                        for (Attribute attribute : updateAttributes) {
                            if (attribute.getValue() == null) {
                                attributeMap.remove(attribute.getName());
                            } else {
                                attributeMap.put(attribute.getName(), attribute);
                            }
                        }
                        return entry.update(attributeMap.values());

                    } finally {
                        lock.writeLock().unlock();
                    }
                } else {
                    throw new ConnectorException("Failed to acquire lock", new TimeoutException(
                            "Failed to acquire lock"));
                }
            } catch (InterruptedException e) {
                throw ConnectorException.wrap(e);
            }
        }

        public void delete(Uid uid) {
            ConnectorObjectCacheEntry entry = objectCache.get(uid.getUidValue());
            if (null == entry) {
                throw new UnknownUidException(uid, objectClass);
            }

            try {
                if (lock.writeLock().tryLock(1, TimeUnit.MINUTES)) {
                    try {
                        entry.update(entry.object.getAttributes());
                        entry.deltaType = SyncDeltaType.DELETE;
                    } finally {
                        lock.writeLock().unlock();
                    }
                } else {
                    throw new ConnectorException("Failed to acquire lock", new TimeoutException(
                            "Failed to acquire lock"));
                }
            } catch (InterruptedException e) {
                throw ConnectorException.wrap(e);
            }
        }

        public Iterable<ConnectorObject> getIterable(final Filter filter) {
            final Iterable<ConnectorObjectCacheEntry> inner = objectCache.values();
            return new Iterable<ConnectorObject>() {
                public Iterator<ConnectorObject> iterator() {
                    final Iterator<ConnectorObjectCacheEntry> innerIterator = inner.iterator();
                    return new Iterator<ConnectorObject>() {
                        ConnectorObject nextElement = null;

                        public boolean hasNext() {
                            if (nextElement != null) {
                                // fail-fast for next() or repeated hasNext()
                                // calls
                                return true;
                            }

                            while (innerIterator.hasNext()) {
                                ConnectorObjectCacheEntry e = innerIterator.next();
                                if (!SyncDeltaType.DELETE.equals(e.deltaType)
                                        && (null == filter || filter.accept(e.object))) {
                                    nextElement = e.object;
                                    return true;
                                }
                                nextElement = null;
                            }
                            return false;
                        }

                        public ConnectorObject next() {
                            if (!hasNext()) { // ensure hasNext() has "advanced"
                                              // the next "filtered-in" element
                                throw new NoSuchElementException();
                            }

                            // return next element and reset for next iteration
                            ConnectorObject retValue = nextElement;
                            nextElement = null;
                            return retValue;
                        }

                        public void remove() {
                            innerIterator.remove();
                        }
                    };
                }
            };
        }

    }

    private class ConnectorObjectCacheEntry {

        private SyncDeltaType deltaType = SyncDeltaType.CREATE;

        private ConnectorObject object;

        public ConnectorObjectCacheEntry(ConnectorObject object) {
            this.object = object;
        }

        public boolean authenticate(GuardedString password) {
            Attribute pw = object.getAttributeByName(OperationalAttributes.PASSWORD_NAME);
            return null != pw && null != password
                    && AttributeUtil.getSingleValue(pw).equals(password);
        }

        public Uid update(Collection<Attribute> updateAttributes) {
            object =
                    new ConnectorObjectBuilder().setObjectClass(object.getObjectClass())
                            .addAttributes(updateAttributes).setUid(
                                    getNextUid(object.getUid().getUidValue())).build();
            deltaType = SyncDeltaType.UPDATE;
            return object.getUid();
        }

    }

}
