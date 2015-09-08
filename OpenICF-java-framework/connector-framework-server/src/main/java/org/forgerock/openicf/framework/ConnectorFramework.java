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

import java.io.Closeable;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.client.ConnectionManagerConfig;
import org.forgerock.openicf.framework.client.ConnectionManagerFactory;
import org.forgerock.openicf.framework.client.RemoteConnectionInfoManagerFactory;
import org.forgerock.openicf.framework.client.RemoteConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.local.AsyncLocalConnectorInfoManager;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.remote.AsyncRemoteLegacyConnectorInfoManager;
import org.forgerock.openicf.framework.remote.LoadBalancingAlgorithmFactory;
import org.forgerock.openicf.framework.remote.LoadBalancingConnectorInfoManager;
import org.forgerock.openicf.framework.remote.OpenICFServerAdapter;
import org.forgerock.openicf.framework.remote.RemoteAsyncConnectorFacade;
import org.forgerock.openicf.framework.remote.RemoteConnectorInfoImpl;
import org.forgerock.util.Function;
import org.forgerock.util.Utils;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationPropertyChangeListener;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.AbstractConnectorInfo;
import org.identityconnectors.framework.impl.api.local.ConnectorPoolManager;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.impl.api.remote.RemoteConnectorFacadeImpl;

/**
 * @since 1.5
 */
public class ConnectorFramework implements Closeable {

    private static final Log logger = Log.getLog(ConnectorFramework.class);

    public static final String REMOTE_LIBRARY_MISSING_EXCEPTION =
            "Remote Connection Library is not initialised";

    private final ClassLoader defaultConnectorBundleParentClassLoader;

    private RemoteConnectionInfoManagerFactory remoteConnectionInfoManagerFactory = null;

    public ConnectorFramework(final ClassLoader defaultConnectorBundleParentClassLoader) {
        this.defaultConnectorBundleParentClassLoader = defaultConnectorBundleParentClassLoader;
    }

    public ConnectionManagerConfig getConnectionManagerConfig() {
        return connectionManagerConfig;
    }

    private final AtomicBoolean isRunning = new AtomicBoolean(Boolean.TRUE);

    public boolean isRunning() {
        return isRunning.get();
    }

    public void close() {
        if (isRunning.compareAndSet(Boolean.TRUE, Boolean.FALSE)) {
            // Notify CloseListeners
            RemoteConnectionInfoManagerFactory factory = getRemoteConnectionInfoManagerFactory();
            if (null != factory) {
                logger.ok("Closing RemoteConnectionInfoManagerFactory");
                factory.close();
            }

            // We need to complete all pending Promises
            while (!remoteManagerCache.isEmpty()) {
                for (AsyncRemoteLegacyConnectorInfoManager manager : remoteManagerCache.values()) {
                    manager.close();
                }
            }

            // We need to complete all pending Promises
            for (AsyncLocalConnectorInfoManager manager : localConnectorInfoManagerCache.values()) {
                manager.close();
            }
            localConnectorInfoManagerCache.clear();

            if (null != scheduledManagedFacadeCacheFuture) {
                scheduledManagedFacadeCacheFuture.cancel(true);
                scheduledManagedFacadeCacheFuture = null;
            }
            scheduler.shutdown();

            for (ConnectorFacade facade : MANAGED_FACADE_CACHE.values()) {
                if (facade instanceof LocalConnectorFacadeImpl) {
                    ((LocalConnectorFacadeImpl) facade).dispose();
                }
            }
            MANAGED_FACADE_CACHE.clear();

            ConnectorPoolManager.dispose();

            org.forgerock.openicf.framework.CloseListener<ConnectorFramework> closeListener;
            while ((closeListener = closeListeners.poll()) != null) {
                invokeCloseListener(closeListener);
            }
        }
    }

    private final Queue<CloseListener<ConnectorFramework>> closeListeners =
            new ConcurrentLinkedQueue<CloseListener<ConnectorFramework>>();

    public void addCloseListener(CloseListener<ConnectorFramework> closeListener) {
        // check if ConnectorFramework is still running
        if (isRunning.get()) {
            // add close listener
            closeListeners.add(closeListener);
            // check the ConnectorFramework state again

            if (!isRunning.get() && closeListeners.remove(closeListener)) {
                // if ConnectorFramework was closed during the method call -
                // notify the
                // listener
                invokeCloseListener(closeListener);
            }
        } else { // if ConnectorFramework is closed - notify the listener
            invokeCloseListener(closeListener);
        }
    }

    public void removeCloseListener(CloseListener<ConnectorFramework> closeListener) {
        closeListeners.remove(closeListener);
    }

    protected void invokeCloseListener(CloseListener<ConnectorFramework> closeListener) {
        try {
            closeListener.onClosed(this);
        } catch (Exception ignored) {
            logger.ok(ignored, "CloseListener failed");
        }
    }

    public ConnectorFacade newInstance(final APIConfiguration config) {
        ConnectorFacade ret = null;
        final APIConfigurationImpl impl = (APIConfigurationImpl) config;
        final AbstractConnectorInfo connectorInfo = impl.getConnectorInfo();
        if (connectorInfo instanceof LocalConnectorInfoImpl) {
            final LocalConnectorInfoImpl localInfo = (LocalConnectorInfoImpl) connectorInfo;
            try {
                // create a new Provisioner.
                ret = new LocalConnectorFacadeImpl(localInfo, impl);

            } catch (Exception ex) {
                String connector = impl.getConnectorInfo().getConnectorKey().toString();
                logger.error(ex, "Failed to create new connector facade: {0}, {1}", connector,
                        config);
                throw ConnectorException.wrap(ex);
            }
        } else if (connectorInfo instanceof org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl) {
            ret = new RemoteConnectorFacadeImpl(impl);
        } else if (connectorInfo instanceof RemoteConnectorInfoImpl) {
            ret = new RemoteAsyncConnectorFacade(impl);
        } else {
            throw new IllegalArgumentException("Unknown ConnectorInfo type");
        }
        return ret;
    }

    /**
     * Cache of the various ConnectorFacades.
     */
    private final ConcurrentMap<String, ConnectorFacade> MANAGED_FACADE_CACHE =
            new ConcurrentHashMap<String, ConnectorFacade>();

    private final Runnable MANAGED_FACADE_CACHE_RUNNABLE = new Runnable() {
        public void run() {
            for (Map.Entry<String, ConnectorFacade> entry : MANAGED_FACADE_CACHE.entrySet()) {
                if (entry.getValue() instanceof LocalConnectorFacadeImpl) {
                    LocalConnectorFacadeImpl value = (LocalConnectorFacadeImpl) entry.getValue();
                    if (value.isUnusedFor(120, TimeUnit.MINUTES)) {
                        if (MANAGED_FACADE_CACHE.remove(entry.getKey(), entry.getValue())) {
                            logger.ok("LocalConnectorFacade is disposed after 120min inactivity");
                        }
                        value.dispose();
                    }
                }
            }
        }
    };

    private ScheduledFuture<?> scheduledManagedFacadeCacheFuture = null;

    public ConnectorFacade newManagedInstance(ConnectorInfo connectorInfo, String config) {
        return newManagedInstance(connectorInfo, config, null);
    }

    public ConnectorFacade newManagedInstance(ConnectorInfo connectorInfo, String config,
            final ConfigurationPropertyChangeListener changeListener) {
        ConnectorFacade facade = MANAGED_FACADE_CACHE.get(config);
        if (null == facade) {
            // new ConnectorFacade creation must remain cheap operation
            facade = newInstance(connectorInfo, config, changeListener);
            if (facade instanceof LocalConnectorFacadeImpl) {
                ConnectorFacade ret =
                        MANAGED_FACADE_CACHE.putIfAbsent(facade.getConnectorFacadeKey(), facade);
                if (null != ret) {
                    logger.ok("ConnectorFacade found in cache");
                    facade = ret;
                } else {
                    synchronized (MANAGED_FACADE_CACHE) {
                        if (null == scheduledManagedFacadeCacheFuture) {
                            scheduledManagedFacadeCacheFuture =
                                    scheduler.scheduleAtFixedRate(MANAGED_FACADE_CACHE_RUNNABLE, 1,
                                            1, TimeUnit.MINUTES);
                        }
                    }
                }
            }
        }
        return facade;
    }

    public ConnectorFacade newInstance(final ConnectorInfo connectorInfo, String config) {
        return newInstance(connectorInfo, config, null);
    }

    public ConnectorFacade newInstance(final ConnectorInfo connectorInfo, String config,
            final ConfigurationPropertyChangeListener changeListener) {
        ConnectorFacade ret = null;
        if (connectorInfo instanceof LocalConnectorInfoImpl) {
            try {
                // create a new Provisioner.
                ret =
                        new LocalConnectorFacadeImpl((LocalConnectorInfoImpl) connectorInfo,
                                config, changeListener);

            } catch (Exception ex) {
                String connector = connectorInfo.getConnectorKey().toString();
                logger.error(ex, "Failed to create new connector facade: {0}, {1}", connector,
                        config);
                throw ConnectorException.wrap(ex);
            }
        } else if (connectorInfo instanceof org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl) {
            ret =
                    new RemoteConnectorFacadeImpl(
                            (org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl) connectorInfo,
                            config, changeListener);
        } else if (connectorInfo instanceof RemoteConnectorInfoImpl) {
            Assertions.nullCheck(connectorInfo, "connectorInfo");
            final APIConfigurationImpl configuration =
                    (APIConfigurationImpl) SerializerUtil.deserializeBase64Object(Assertions
                            .nullChecked(config, "configuration"));
            configuration.setConnectorInfo((RemoteConnectorInfoImpl) connectorInfo);

            configuration.setChangeListener(changeListener);
            ret = newInstance(configuration);
        } else {
            throw new IllegalArgumentException("Unknown ConnectorInfo type");
        }
        return ret;
    }

    // ------ LocalConnectorFramework Implementation Start ------

    private final ConcurrentMap<ClassLoader, AsyncLocalConnectorInfoManager> localConnectorInfoManagerCache =
            new ConcurrentHashMap<ClassLoader, AsyncLocalConnectorInfoManager>(2);

    public AsyncLocalConnectorInfoManager getLocalManager() {
        return getLocalConnectorInfoManager(defaultConnectorBundleParentClassLoader);
    }

    public AsyncLocalConnectorInfoManager getLocalConnectorInfoManager(
            final ClassLoader connectorBundleParentClassLoader) {
        ClassLoader key = connectorBundleParentClassLoader;
        if (null == key) {
            key = defaultConnectorBundleParentClassLoader;
        }
        AsyncLocalConnectorInfoManager manager = localConnectorInfoManagerCache.get(key);
        if (null == manager) {
            manager = new AsyncLocalConnectorInfoManager(key);
            AsyncLocalConnectorInfoManager tmp =
                    localConnectorInfoManagerCache.putIfAbsent(key, manager);
            if (null != tmp) {
                manager = tmp;
            }
        }
        return manager;
    }

    public AsyncConnectorInfoManager getOSGiConnectorInfoManager() {
        throw new IllegalStateException("Framework is not running in OSGi context");
    }

    public boolean isOSGiEnabled() {
        return false;
    }

    // ------ LocalConnectorFramework Implementation End ------

    // ------ Legacy RemoteConnectorInfoManager Support ------
    private final Map<Pair<String, Integer>, AsyncRemoteLegacyConnectorInfoManager> remoteManagerCache =
            new ConcurrentHashMap<Pair<String, Integer>, AsyncRemoteLegacyConnectorInfoManager>(4,
                    0.75f, 16);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, Utils.newThreadFactory(null,
            "OpenICF ConnectorFramework Scheduler %d", false));

    public AsyncRemoteLegacyConnectorInfoManager getRemoteManager(
            final RemoteFrameworkConnectionInfo info) {
        if (null == info) {
            return null;
        }
        if (isRunning()) {
            final Pair<String, Integer> key =
                    Pair.of(info.getHost().toLowerCase(Locale.ENGLISH), info.getPort());
            AsyncRemoteLegacyConnectorInfoManager rv = remoteManagerCache.get(key);
            if (rv == null) {
                synchronized (remoteManagerCache) {
                    rv = remoteManagerCache.get(key);
                    if (rv == null) {
                        rv = new AsyncRemoteLegacyConnectorInfoManager(info, scheduler);
                        rv.addCloseListener(new CloseListener<AsyncRemoteLegacyConnectorInfoManager>() {
                            public void onClosed(AsyncRemoteLegacyConnectorInfoManager source) {
                                remoteManagerCache.remove(key);
                            }
                        });
                        if (!isRunning() && remoteManagerCache.remove(key) != null) {
                            rv.close();
                            throw new IllegalStateException("ConnectorFramework is shut down");
                        }
                    }
                    remoteManagerCache.put(key, rv);
                }
            }
            return rv;
        } else {
            throw new IllegalStateException("ConnectorFramework is shut down");
        }
    }

    // ------ RemoteConnectorFramework Implementation Start ------

    public AsyncRemoteConnectorInfoManager getRemoteManager(RemoteWSFrameworkConnectionInfo info) {
        if (null == info) {
            return null;
        }
        return new AsyncRemoteConnectorInfoManager(getRemoteConnectionInfoManagerFactory().connect(
                info));
    }

    public LoadBalancingConnectorInfoManager getRemoteManager(
            final LoadBalancingAlgorithmFactory loadBalancingAlgorithmFactory) {
        if (null != loadBalancingAlgorithmFactory
                && !loadBalancingAlgorithmFactory.getAsyncRemoteConnectorInfoManager().isEmpty()) {
            return new LoadBalancingConnectorInfoManager(loadBalancingAlgorithmFactory);
        } else {
            return null;
        }
    }

    public ConnectorFacade newInstance(ConnectorInfo connectorInfo,
            Function<RemoteConnectorInfoImpl, APIConfiguration, RuntimeException> transformer) {
        if (null != remoteConnectionInfoManagerFactory) {
            return null;
        }
        throw new UnsupportedOperationException(REMOTE_LIBRARY_MISSING_EXCEPTION);
    }

    public Promise<ConnectorFacade, RuntimeException> newInstance(ConnectorKey key,
            Function<RemoteConnectorInfoImpl, APIConfiguration, RuntimeException> transformer) {
        if (null != remoteConnectionInfoManagerFactory) {
            return null;
        }
        throw new UnsupportedOperationException(REMOTE_LIBRARY_MISSING_EXCEPTION);
    }

    // ------ RemoteConnectorFramework Implementation End ------

    public synchronized RemoteConnectionInfoManagerFactory getRemoteConnectionInfoManagerFactory() {
        if (null == remoteConnectionInfoManagerFactory && isRunning()) {
            final OpenICFServerAdapter listener =
                    new OpenICFServerAdapter(this, getConnectionInfoManager(), true);
            try {
                remoteConnectionInfoManagerFactory =
                        getConnectionManagerFactory().getNewInstance(listener,
                                getConnectionManagerConfig());
            } catch (final Exception e) {
                logger.warn(e, "RemoteConnectionInfoManagerFactory is not available");
                remoteConnectionInfoManagerFactory =
                        new RemoteConnectionInfoManagerFactory(listener,
                                getConnectionManagerConfig()) {
                            public RemoteConnectorInfoManager connect(
                                    RemoteWSFrameworkConnectionInfo info) {
                                throw new UnsupportedOperationException(
                                        REMOTE_LIBRARY_MISSING_EXCEPTION, e);
                            }

                            public void doClose() {

                            }
                        };
            }
            remoteConnectionInfoManagerFactory
                    .addCloseListener(new CloseListener<RemoteConnectionInfoManagerFactory>() {
                        public void onClosed(RemoteConnectionInfoManagerFactory source) {
                            remoteConnectionInfoManagerFactory = null;
                        }
                    });
        }
        return remoteConnectionInfoManagerFactory;
    }

    private ConnectionManagerFactory connectionManagerFactory = new ConnectionManagerFactory();
    private ConnectionManagerConfig connectionManagerConfig = new ConnectionManagerConfig();

    protected ConnectionManagerFactory getConnectionManagerFactory() {
        return connectionManagerFactory;
    }

    public void setConnectionManagerFactory(final ConnectionManagerFactory connectionManagerFactory) {
        this.connectionManagerFactory =
                null != connectionManagerFactory ? connectionManagerFactory
                        : new ConnectionManagerFactory();
    }

    protected AsyncConnectorInfoManager getConnectionInfoManager() {
        return getLocalManager();
    }

    public void setConnectionManagerConfig(final ConnectionManagerConfig connectionManagerConfig) {
        this.connectionManagerConfig =
                null != connectionManagerConfig ? connectionManagerConfig
                        : new ConnectionManagerConfig();
    }
}
