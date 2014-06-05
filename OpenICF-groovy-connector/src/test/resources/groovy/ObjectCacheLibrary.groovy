/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

import org.identityconnectors.common.Pair
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.exceptions.PreconditionFailedException
import org.identityconnectors.framework.common.exceptions.PreconditionRequiredException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.SortKey
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.Filter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Singleton(strict = false)
public class ObjectCacheLibrary {

    private static final class ResourceComparator implements Comparator<ConnectorObject> {
        private final List<SortKey> sortKeys;

        private ResourceComparator(final List<SortKey> sortKeys) {
            this.sortKeys = sortKeys;
        }

        @Override
        public int compare(final ConnectorObject r1, final ConnectorObject r2) {
            for (final SortKey sortKey : sortKeys) {
                final int result = compare0(r1, r2, sortKey);
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }

        private int compare0(final ConnectorObject r1, final ConnectorObject r2,
                            final SortKey sortKey) {
            final List<Object> vs1 = getValuesSorted(r1, sortKey.getField());
            final List<Object> vs2 = getValuesSorted(r2, sortKey.getField());
            if (vs1.isEmpty() && vs2.isEmpty()) {
                return 0;
            } else if (vs1.isEmpty()) {
                // Sort resources with missing attributes last.
                return 1;
            } else if (vs2.isEmpty()) {
                // Sort resources with missing attributes last.
                return -1;
            } else {
                final Object v1 = vs1.get(0);
                final Object v2 = vs2.get(0);
                return sortKey.isAscendingOrder() ? compareValues(v1, v2) : -compareValues(v1, v2);
            }
        }

        private List<Object> getValuesSorted(final ConnectorObject resource, final String field) {
            final Attribute value = AttributeUtil.find(field, resource.getAttributes());
            if (value == null || value.getValue() == null || value.getValue().isEmpty()) {
                return Collections.emptyList();
            } else if (value.getValue().size() > 1) {
                List<Object> results = new ArrayList<Object>(value.getValue());
                Collections.sort(results, VALUE_COMPARATOR);
                return results;
            } else {
                return value.getValue();
            }
        }
    }

    private static final Comparator<Object> VALUE_COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(final Object o1, final Object o2) {
            return compareValues(o1, o2);
        }
    };

    private static int compareValues(final Object v1, final Object v2) {
        if (v1 instanceof String && v2 instanceof String) {
            final String s1 = (String) v1;
            final String s2 = (String) v2;
            return s1.compareToIgnoreCase(s2);
        } else if (v1 instanceof Number && v2 instanceof Number) {
            final Double n1 = ((Number) v1).doubleValue();
            final Double n2 = ((Number) v2).doubleValue();
            return n1.compareTo(n2);
        } else if (v1 instanceof Boolean && v2 instanceof Boolean) {
            final Boolean b1 = (Boolean) v1;
            final Boolean b2 = (Boolean) v2;
            return b1.compareTo(b2);
        } else {
            return v1.getClass().getName().compareTo(v2.getClass().getName());
        }
    }


    private ConcurrentMap<ObjectClass, ConcurrentMap<String, Pair<ConnectorObject, Date>>> store =
        new ConcurrentHashMap<ObjectClass, ConcurrentMap<String, Pair<ConnectorObject, Date>>>()

    ObjectCacheLibrary() {
        store.put(ObjectClass.ACCOUNT, new ConcurrentHashMap<String, Pair<ConnectorObject, Date>>())
        store.put(ObjectClass.GROUP, new ConcurrentHashMap<String, Pair<ConnectorObject, Date>>())
    }

    ConcurrentMap<String, Pair<ConnectorObject, Date>> getStore(ObjectClass type) {
        ConcurrentMap<String, Pair<ConnectorObject, Date>> result = store.get(type)
        if (null != result) {
            return result
        }
        throw new InvalidAttributeValueException("Unsupported ObjectClass" + type)
    }

    Uid create(ConnectorObject object) {
        ConcurrentMap<String, Pair<ConnectorObject, Date>> storage = getStore(object.getObjectClass());
        def item = Pair.of(object, new Date())
        if (storage.containsKey(object.getUid().getUidValue())) {
            throw new AlreadyExistsException().initUid(object.getUid())
        } else {
            storage.put(object.getUid().getUidValue(), item)
            return new Uid(object.getUid().getUidValue(), Long.toString(item.getValue().getTime()))
        }
    }

    TreeSet<ConnectorObject> search(ObjectClass objectClass, Filter query, SortKey[] sortKeys) {
        ConcurrentMap<String, Pair<ConnectorObject, Date>> storage = getStore(objectClass);

        def s = sortKeys
        if (null == sortKeys || sortKeys.size() == 0) {
            s = [new SortKey(Name.NAME, true)];
        } else {
            s = Arrays.asList(sortKeys)
        }

        // Rebuild the full result set.
        TreeSet<ConnectorObject> resultSet =
            new TreeSet<ConnectorObject>(new ObjectCacheLibrary.ResourceComparator(s));

        for (Pair<ConnectorObject, Date> co : storage.values()) {
            if (null == query || query.accept(co.getKey())) {
                resultSet.add(co.getKey());
            }
        }
        return resultSet;
    }

    Uid update(ConnectorObject object) {
        ConcurrentMap<String, Pair<ConnectorObject, Date>> storage = getStore(object.getObjectClass());

        Pair<ConnectorObject, Date> old = storage.get(object.getUid().getUidValue())
        if (null != old) {
            if (null != object.getUid().getRevision() &&
                    !object.getUid().getRevision().equals(Long.toString(old.getValue().getTime()))) {
                throw new PreconditionFailedException();
            } else if (null == object.getUid().getRevision()) {
                throw new PreconditionRequiredException()
            }
            def item = Pair.of(object, new Date())
            if (storage.replace(object.getUid().getUidValue(), old.key, item)) {
                throw new PreconditionFailedException();
            } else {
                return new Uid(object.getUid().getUidValue(), Long.toString(item.getValue().getTime()))
            }
        } else {
            throw new UnknownUidException(object.getUid(), object.getObjectClass())
        }
    }

    void delete(ObjectClass objectClass, Uid uid) {
        ConcurrentMap<String, Pair<ConnectorObject, Date>> storage = getStore(objectClass);
        if (null == storage.remove(uid.getUidValue())) {
            throw new UnknownUidException(uid, objectClass)
        }
    }
}


