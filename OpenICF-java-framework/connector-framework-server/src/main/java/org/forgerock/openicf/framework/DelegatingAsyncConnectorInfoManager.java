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

package org.forgerock.openicf.framework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.CloseableAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ManagedAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.remote.RemoteConnectorInfoImpl;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;
import org.identityconnectors.common.ConnectorKeyRange;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;

/**
 * @since 1.5
 */
public abstract class DelegatingAsyncConnectorInfoManager extends
        CloseableAsyncConnectorInfoManager<DelegatingAsyncConnectorInfoManager> {

    private static final Log logger = Log.getLog(DelegatingAsyncConnectorInfoManager.class);

    protected final List<AsyncConnectorInfoManager> delegates =
            new CopyOnWriteArrayList<AsyncConnectorInfoManager>();

    private final List<Pair<ConnectorKeyRange, DeferredPromise>> deferredRangePromiseCacheList;

    private final List<Pair<ConnectorKey, DeferredPromise>> deferredKeyPromiseCacheList;

    private final boolean allowDeferred;

    public DelegatingAsyncConnectorInfoManager(boolean allowDeferred) {
        this.allowDeferred = allowDeferred;
        if (allowDeferred) {
            deferredRangePromiseCacheList =
                    new CopyOnWriteArrayList<Pair<ConnectorKeyRange, DeferredPromise>>();

            deferredKeyPromiseCacheList =
                    new CopyOnWriteArrayList<Pair<ConnectorKey, DeferredPromise>>();
            CloseListener<DelegatingAsyncConnectorInfoManager> closeListener =
                    new CloseListener<DelegatingAsyncConnectorInfoManager>() {
                        public void onClosed(DelegatingAsyncConnectorInfoManager source) {
                            for (Pair<ConnectorKeyRange, DeferredPromise> promise : deferredRangePromiseCacheList) {
                                promise.getValue().shutdown();
                            }
                            deferredRangePromiseCacheList.clear();

                            for (Pair<ConnectorKey, DeferredPromise> promise : deferredKeyPromiseCacheList) {
                                promise.getValue().shutdown();
                            }
                            deferredKeyPromiseCacheList.clear();
                        }
                    };
            addCloseListener(closeListener);
        } else {
            deferredRangePromiseCacheList = null;
            deferredKeyPromiseCacheList = null;
        }
    }

    protected abstract RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getMessageDistributor();

    protected Collection<? extends AsyncConnectorInfoManager> getDelegates() {
        return delegates;
    }

    protected boolean addAsyncConnectorInfoManager(final AsyncConnectorInfoManager delegate) {
        if (null != delegate && delegates.add(delegate)) {
            logger.ok("Add AsyncConnectorInfoManager to delegates");
            onAddAsyncConnectorInfoManager(delegate);
            return true;
        } else {
            return false;
        }
    }

    protected boolean removeAsyncConnectorInfoManager(final AsyncConnectorInfoManager delegate) {
        return null != delegate && delegates.remove(delegate);
    }

    protected void onAddAsyncConnectorInfoManager(final AsyncConnectorInfoManager delegate) {
        if (allowDeferred) {
            for (Pair<ConnectorKeyRange, DeferredPromise> promise : deferredRangePromiseCacheList) {
                promise.getValue().add(delegate.findConnectorInfoAsync(promise.getKey()));
            }
            for (Pair<ConnectorKey, DeferredPromise> promise : deferredKeyPromiseCacheList) {
                promise.getValue().add(delegate.findConnectorInfoAsync(promise.getKey()));
            }
        }
    }

    private static class DeferredPromise extends PromiseImpl<ConnectorInfo, RuntimeException> {

        final AtomicInteger remaining;
        final AtomicBoolean pending = new AtomicBoolean(Boolean.TRUE);

        public DeferredPromise(boolean neverFail) {
            remaining = new AtomicInteger(neverFail ? 1 : 0);
        }

        public void shutdown() {
            handleException(ManagedAsyncConnectorInfoManager.CLOSED_EXCEPTION);
        }

        protected RuntimeException tryCancel(boolean mayInterruptIfRunning) {
            return super.tryCancel(mayInterruptIfRunning);
        }

        protected boolean add(Promise<ConnectorInfo, RuntimeException> promise) {
            remaining.incrementAndGet();
            promise.thenOnResult(new ResultHandler<ConnectorInfo>() {
                @Override
                public void handleResult(final ConnectorInfo value) {
                    pending.set(Boolean.FALSE);
                    DeferredPromise.this.handleResult(value);
                }
            }).thenOnException(new ExceptionHandler<RuntimeException>() {
                @Override
                public void handleException(final RuntimeException error) {
                    if (remaining.decrementAndGet() == 0) {
                        DeferredPromise.this.handleException(error);
                    }
                }
            });
            return pending.get();
        }
    }

    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(final ConnectorKey key) {
        if (!isRunning.get()) {
            return Promises
                    .<ConnectorInfo, RuntimeException> newExceptionPromise(ManagedAsyncConnectorInfoManager.CLOSED_EXCEPTION);
        } else {
            final Iterator<? extends AsyncConnectorInfoManager> safeDelegates =
                    getDelegates().iterator();
            final DeferredPromise promise = new DeferredPromise(allowDeferred);
            final Pair<ConnectorKey, DeferredPromise> entry = Pair.of(key, promise);

            if (allowDeferred) {
                deferredKeyPromiseCacheList.add(entry);
            }

            boolean pending = true;
            while (pending && safeDelegates.hasNext()) {
                pending = promise.add(safeDelegates.next().findConnectorInfoAsync(key));
            }

            if (allowDeferred && isRunning()) {
                if (pending) {
                    promise.thenOnResultOrException(new Runnable() {
                        public void run() {
                            deferredKeyPromiseCacheList.remove(entry);
                        }
                    });
                } else {
                    deferredKeyPromiseCacheList.remove(entry);
                }
            } else if (!isRunning()) {
                promise.shutdown();
            }

            return promise.then(new Function<ConnectorInfo, ConnectorInfo, RuntimeException>() {
                public ConnectorInfo apply(final ConnectorInfo value) throws RuntimeException {
                    // Replace the RemoteConnectorInfoManager with this!!
                    return new RemoteConnectorInfoImpl(getMessageDistributor(),
                            (RemoteConnectorInfoImpl) value);
                }
            });
        }
    }

    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(
            final ConnectorKeyRange keyRange) {
        if (!isRunning.get()) {
            return Promises
                    .<ConnectorInfo, RuntimeException> newExceptionPromise(ManagedAsyncConnectorInfoManager.CLOSED_EXCEPTION);
        } else {
            if (keyRange.getBundleVersionRange().isEmpty()) {
                return Promises
                        .<ConnectorInfo, RuntimeException> newExceptionPromise(new IllegalArgumentException(
                                "ConnectorBundle VersionRange is Empty"));
            } else if (keyRange.getBundleVersionRange().isExact()) {
                return findConnectorInfoAsync(keyRange.getExactConnectorKey());
            } else {
                final Iterator<? extends AsyncConnectorInfoManager> safeDelegates =
                        getDelegates().iterator();
                final DeferredPromise promise = new DeferredPromise(allowDeferred);
                final Pair<ConnectorKeyRange, DeferredPromise> entry = Pair.of(keyRange, promise);

                if (allowDeferred) {
                    deferredRangePromiseCacheList.add(entry);
                }

                boolean pending = true;
                while (pending && safeDelegates.hasNext()) {
                    pending = promise.add(safeDelegates.next().findConnectorInfoAsync(keyRange));
                }
                if (allowDeferred && isRunning()) {
                    if (pending) {
                        promise.thenOnResultOrException(new Runnable() {
                            public void run() {
                                deferredRangePromiseCacheList.remove(entry);
                            }
                        });
                    } else {
                        deferredRangePromiseCacheList.remove(entry);
                    }
                } else if (!isRunning()) {
                    promise.shutdown();
                }

                return promise.then(new Function<ConnectorInfo, ConnectorInfo, RuntimeException>() {
                    public ConnectorInfo apply(final ConnectorInfo value) throws RuntimeException {
                        // Replace the RemoteConnectorInfoManager with
                        // this!!
                        return new RemoteConnectorInfoImpl(getMessageDistributor(),
                                (RemoteConnectorInfoImpl) value);
                    }
                });
            }
        }
    }

    public List<ConnectorInfo> getConnectorInfos() {
        final Set<ConnectorKey> keys = new HashSet<ConnectorKey>();
        final List<ConnectorInfo> result = new ArrayList<ConnectorInfo>();
        for (AsyncConnectorInfoManager group : getDelegates()) {
            for (ConnectorInfo info : group.getConnectorInfos()) {
                if (!keys.contains(info.getConnectorKey())) {
                    keys.add(info.getConnectorKey());
                    result.add(info);
                }
            }
        }
        return result;
    }

    public ConnectorInfo findConnectorInfo(ConnectorKey key) {
        for (AsyncConnectorInfoManager group : getDelegates()) {
            ConnectorInfo result = group.findConnectorInfo(key);
            if (null != result) {
                return result;
            }
        }
        return null;
    }

    /**
     * One Success or All Fail
     *
     * @param promises
     * @param <V>
     * @param <E>
     * @return
     */
    public static <V, E extends Exception> Promise<V, E> when(final List<Promise<V, E>> promises) {
        final AtomicInteger remaining = new AtomicInteger(promises.size());
        final PromiseImpl<V, E> composite = PromiseImpl.create();
        for (final Promise<V, E> promise : promises) {
            promise.thenOnResult(new ResultHandler<V>() {
                @Override
                public void handleResult(final V value) {
                    composite.handleResult(value);
                }
            }).thenOnException(new ExceptionHandler<E>() {
                @Override
                public void handleException(final E error) {
                    if (remaining.decrementAndGet() == 0) {
                        composite.handleException(error);
                    }
                }
            });
        }
        return composite;
    }
}
