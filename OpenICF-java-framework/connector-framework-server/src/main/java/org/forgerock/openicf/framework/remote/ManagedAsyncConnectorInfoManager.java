/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.remote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.CloseableAsyncConnectorInfoManager;
import org.forgerock.util.Iterables;
import org.forgerock.util.Predicate;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.common.ConnectorKeyRange;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;

/**
 * @since 1.5
 */
public class ManagedAsyncConnectorInfoManager<V extends ConnectorInfo, C extends ManagedAsyncConnectorInfoManager<V, C>>
        extends CloseableAsyncConnectorInfoManager<C> {

    private static Log logger = Log.getLog(ManagedAsyncConnectorInfoManager.class);

    private final ConcurrentMap<ConnectorKey, ConnectorEntry<V>> managedConnectorInfos =
            new ConcurrentSkipListMap<ConnectorKey, ConnectorEntry<V>>(
                    new Comparator<ConnectorKey>() {

                        /**
                         * Descending ConnectorKey Comparator.
                         * 
                         * @param left
                         *            the first object to be compared.
                         * @param right
                         *            the second object to be compared.
                         * @return a negative integer, zero, or a positive
                         *         integer as the first argument is less than,
                         *         equal to, or greater than the second.
                         */
                        public int compare(final ConnectorKey left, final ConnectorKey right) {
                            int result = left.getBundleName().compareTo(right.getBundleName());
                            if (result != 0) {
                                return result * -1;
                            }
                            result = left.getConnectorName().compareTo(right.getConnectorName());
                            if (result != 0) {
                                return result * -1;
                            }
                            return left.getBundleVersion().compareTo(right.getBundleVersion()) * -1;
                        }
                    });

    private final List<Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>>> rangePromiseCacheList =
            new CopyOnWriteArrayList<Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>>>();

    public static final IllegalStateException CLOSED_EXCEPTION = new IllegalStateException(
            "AsyncConnectorInfoManager is shut down!");

    static {
        CLOSED_EXCEPTION.setStackTrace(new StackTraceElement[] {});
    }

    protected void doClose() {
        for (ConnectorEntry<V> entry : managedConnectorInfos.values()) {
            entry.shutdown();
        }
        managedConnectorInfos.clear();
        for (Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>> entry : rangePromiseCacheList) {
            entry.getValue()
                    .handleError(
                            new IllegalStateException(
                                    "ManagedAsyncConnectorInfoManager is shutting down!"));
        }
        rangePromiseCacheList.clear();
    }

    private final AtomicInteger revision = new AtomicInteger(0);

    public boolean isChanged(int lastRevision) {
        return lastRevision != revision.get();
    }

    protected void addConnectorInfo(final V connectorInfo) {
        ConnectorEntry<V> entry = managedConnectorInfos.get(connectorInfo.getConnectorKey());
        if (null == entry) {
            entry = new ConnectorEntry<V>();
            ConnectorEntry<V> tmp =
                    managedConnectorInfos.putIfAbsent(connectorInfo.getConnectorKey(), entry);
            if (null != tmp) {
                entry = tmp;
            } else {
                logger.ok("Add new ConnectorInfo: {0}", connectorInfo.getConnectorKey());
            }
        } else if (null == entry.connectorInfo) {
            logger.ok("Add new ConnectorInfo: {0}", connectorInfo.getConnectorKey());
        }
        entry.setConnectorInfo(connectorInfo);
        revision.incrementAndGet();

        for (Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>> rangeEntry : Iterables
                .filter(rangePromiseCacheList,
                        new Predicate<Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>>>() {
                            public boolean apply(
                                    Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>> value) {
                                return value.getKey().isInRange(connectorInfo.getConnectorKey());
                            }
                        })) {
            rangeEntry.getValue().handleResult(connectorInfo);
        }

    }

    public List<ConnectorInfo> getConnectorInfos() {
        ArrayList<ConnectorInfo> resultList =
                new ArrayList<ConnectorInfo>(managedConnectorInfos.size());
        for (ConnectorEntry<V> entry : managedConnectorInfos.values()) {
            if (null != entry.connectorInfo) {
                resultList.add(entry.connectorInfo);
            }
        }
        return resultList;
    }

    public ConnectorInfo findConnectorInfo(ConnectorKey key) {
        ConnectorEntry<V> entry = managedConnectorInfos.get(key);
        if (null != entry) {
            return entry.connectorInfo;
        }
        return null;
    }

    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(
            final ConnectorKeyRange keyRange) {
        if (!isRunning.get()) {
            return Promises
                    .<ConnectorInfo, RuntimeException> newFailedPromise(new IllegalStateException(
                            "AsyncConnectorInfoManager is shut down!"));
        } else {
            if (keyRange.getBundleVersionRange().isEmpty()) {
                return Promises
                        .<ConnectorInfo, RuntimeException> newFailedPromise(new IllegalArgumentException(
                                "ConnectorBundle VersionRange is Empty"));
            } else if (keyRange.getBundleVersionRange().isExact()) {
                return findConnectorInfoAsync(keyRange.getExactConnectorKey());
            } else {

                final Pair<ConnectorKeyRange, PromiseImpl<ConnectorInfo, RuntimeException>> cacheEntry =
                        Pair.of(keyRange, PromiseImpl.<ConnectorInfo, RuntimeException> create());

                cacheEntry.getValue().onSuccessOrFailure(new Runnable() {
                    public void run() {
                        rangePromiseCacheList.remove(cacheEntry);
                    }
                });
                rangePromiseCacheList.add(cacheEntry);

                for (Map.Entry<ConnectorKey, ConnectorEntry<V>> entry : managedConnectorInfos
                        .entrySet()) {
                    final ConnectorInfo connectorInfo = entry.getValue().connectorInfo;
                    if (null != connectorInfo
                            && keyRange.isInRange(connectorInfo.getConnectorKey())) {
                        cacheEntry.getValue().handleResult(connectorInfo);
                        return cacheEntry.getValue();
                    }
                }

                if (!isRunning.get()) {
                    rangePromiseCacheList.remove(cacheEntry);

                    return Promises
                            .<ConnectorInfo, RuntimeException> newFailedPromise(CLOSED_EXCEPTION);
                }
                return cacheEntry.getValue();
            }
        }
    }

    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(final ConnectorKey key) {
        if (!isRunning.get()) {
            return Promises.<ConnectorInfo, RuntimeException> newFailedPromise(CLOSED_EXCEPTION);
        } else {
            final PromiseImpl<ConnectorInfo, RuntimeException> promise = PromiseImpl.create();
            ConnectorEntry<V> entry = managedConnectorInfos.get(key);
            if (null == entry) {
                entry = new ConnectorEntry<V>();
                ConnectorEntry<V> tmp = managedConnectorInfos.putIfAbsent(key, entry);
                if (null != tmp) {
                    entry = tmp;
                }
            }
            entry.addOrFirePromise(promise);
            if (!isRunning.get()) {
                promise.handleError(CLOSED_EXCEPTION);
            }
            return promise;
        }
    }

    public AsyncConnectorInfoManager wrap() {
        return new AsyncConnectorInfoManager() {

            public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(
                    final ConnectorKey key) {
                return ManagedAsyncConnectorInfoManager.this.findConnectorInfoAsync(key);
            }

            public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(
                    final ConnectorKeyRange keyRange) {
                return ManagedAsyncConnectorInfoManager.this.findConnectorInfoAsync(keyRange);
            }

            public List<ConnectorInfo> getConnectorInfos() {
                return ManagedAsyncConnectorInfoManager.this.getConnectorInfos();
            }

            public ConnectorInfo findConnectorInfo(final ConnectorKey key) {
                return ManagedAsyncConnectorInfoManager.this.findConnectorInfo(key);
            }

        };
    }

    private static class ConnectorEntry<V extends ConnectorInfo> {
        private V connectorInfo = null;
        private final Queue<PromiseImpl<ConnectorInfo, RuntimeException>> listeners =
                new ConcurrentLinkedQueue<PromiseImpl<ConnectorInfo, RuntimeException>>();

        void setConnectorInfo(final V info) {
            connectorInfo = info;
            PromiseImpl<ConnectorInfo, RuntimeException> listener;
            while ((listener = listeners.poll()) != null) {
                listener.handleResult(connectorInfo);
            }
        }

        void shutdown() {
            PromiseImpl<ConnectorInfo, RuntimeException> listener;
            while ((listener = listeners.poll()) != null) {
                listener.handleError(CLOSED_EXCEPTION);
            }
        }

        void addOrFirePromise(final PromiseImpl<ConnectorInfo, RuntimeException> listener) {
            final ConnectorInfo registered = this.connectorInfo;
            if (null != registered) {
                listener.handleResult(registered);
            } else {
                listeners.add(listener);
                final ConnectorInfo registeredAfter = this.connectorInfo;
                if (null != registeredAfter && listeners.remove(listener)) {
                    listener.handleResult(registeredAfter);
                }
            }
        }
    }

}
