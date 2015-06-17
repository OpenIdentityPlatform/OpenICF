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
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2010-2015 ForgeRock AS.
 */
package org.identityconnectors.framework.impl.api.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.identityconnectors.framework.api.ConfigurationPropertyChangeListener;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.ResolveUsernameApiOp;
import org.identityconnectors.framework.api.operations.SchemaApiOp;
import org.identityconnectors.framework.api.operations.ScriptOnConnectorApiOp;
import org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp;
import org.identityconnectors.framework.api.operations.TestApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.api.operations.ValidateApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.AbstractConnectorFacade;
import org.identityconnectors.framework.impl.api.LoggingProxy;
import org.identityconnectors.framework.impl.api.local.operations.APIOperationRunner;
import org.identityconnectors.framework.impl.api.local.operations.AuthenticationImpl;
import org.identityconnectors.framework.impl.api.local.operations.ConnectorAPIOperationRunner;
import org.identityconnectors.framework.impl.api.local.operations.ConnectorAPIOperationRunnerProxy;
import org.identityconnectors.framework.impl.api.local.operations.ConnectorOperationalContext;
import org.identityconnectors.framework.impl.api.local.operations.CreateImpl;
import org.identityconnectors.framework.impl.api.local.operations.DeleteImpl;
import org.identityconnectors.framework.impl.api.local.operations.GetImpl;
import org.identityconnectors.framework.impl.api.local.operations.OperationalContext;
import org.identityconnectors.framework.impl.api.local.operations.ResolveUsernameImpl;
import org.identityconnectors.framework.impl.api.local.operations.SchemaImpl;
import org.identityconnectors.framework.impl.api.local.operations.ScriptOnConnectorImpl;
import org.identityconnectors.framework.impl.api.local.operations.ScriptOnResourceImpl;
import org.identityconnectors.framework.impl.api.local.operations.SearchImpl;
import org.identityconnectors.framework.impl.api.local.operations.SubscriptionImpl;
import org.identityconnectors.framework.impl.api.local.operations.SyncImpl;
import org.identityconnectors.framework.impl.api.local.operations.TestImpl;
import org.identityconnectors.framework.impl.api.local.operations.ThreadClassLoaderManagerProxy;
import org.identityconnectors.framework.impl.api.local.operations.UpdateImpl;
import org.identityconnectors.framework.impl.api.local.operations.ValidateImpl;
import org.identityconnectors.framework.spi.Connector;

/**
 * Implements all the methods of the facade.
 * <p>
 */
public class LocalConnectorFacadeImpl extends AbstractConnectorFacade {

    // =======================================================================
    // Constants
    // =======================================================================
    /**
     * Map the API interfaces to their implementation counterparts.
     */
    private static final Map<Class<? extends APIOperation>, Constructor<? extends ConnectorAPIOperationRunner>> API_TO_IMPL =
            new HashMap<Class<? extends APIOperation>, Constructor<? extends ConnectorAPIOperationRunner>>();

    private static void addImplementation(final Class<? extends APIOperation> inter,
            final Class<? extends ConnectorAPIOperationRunner> impl) {
        Constructor<? extends ConnectorAPIOperationRunner> constructor;
        try {
            constructor = impl.getConstructor(ConnectorOperationalContext.class, Connector.class);
            API_TO_IMPL.put(inter, constructor);
        } catch (Exception e) {
            // this should never happen..
            throw ConnectorException.wrap(e);
        }
    }

    static {
        addImplementation(CreateApiOp.class, CreateImpl.class);
        addImplementation(DeleteApiOp.class, DeleteImpl.class);
        addImplementation(SchemaApiOp.class, SchemaImpl.class);
        addImplementation(SearchApiOp.class, SearchImpl.class);
        addImplementation(UpdateApiOp.class, UpdateImpl.class);
        addImplementation(AuthenticationApiOp.class, AuthenticationImpl.class);
        addImplementation(ResolveUsernameApiOp.class, ResolveUsernameImpl.class);
        addImplementation(TestApiOp.class, TestImpl.class);
        addImplementation(ScriptOnConnectorApiOp.class, ScriptOnConnectorImpl.class);
        addImplementation(ScriptOnResourceApiOp.class, ScriptOnResourceImpl.class);
        addImplementation(SyncApiOp.class, SyncImpl.class);
    }

    // =======================================================================
    // Fields
    // =======================================================================

    /**
     * The connector info
     */
    private final LocalConnectorInfoImpl connectorInfo;

    /**
     * Shared OperationalContext for stateful facades
     */
    private final ConnectorOperationalContext operationalContext;

    /**
     * Shared thread counter. 
     */
    private final ReferenceCounter referenceCounter = new ReferenceCounter();
    
    /**
     * Builds up the maps of supported operations and calls.
     */
    public LocalConnectorFacadeImpl(final LocalConnectorInfoImpl connectorInfo,
            final APIConfigurationImpl apiConfiguration) {
        super(apiConfiguration);
        this.connectorInfo = connectorInfo;
        if (connectorInfo.isConfigurationStateless()
                && !connectorInfo.isConnectorPoolingSupported()) {
            operationalContext = null;
        } else {
            operationalContext =
                    new ConnectorOperationalContext(connectorInfo, getAPIConfiguration());
        }
    }

    public LocalConnectorFacadeImpl(final LocalConnectorInfoImpl connectorInfo, String configuration) {
        super(configuration, connectorInfo);
        this.connectorInfo = connectorInfo;
        if (connectorInfo.isConfigurationStateless()
                && !connectorInfo.isConnectorPoolingSupported()) {
            operationalContext = null;
        } else {
            operationalContext =
                    new ConnectorOperationalContext(connectorInfo, getAPIConfiguration());
        }
    }


    public LocalConnectorFacadeImpl(
            LocalConnectorInfoImpl connectorInfo, String config, ConfigurationPropertyChangeListener changeListener) {
        this(connectorInfo, config);
        getAPIConfiguration().setChangeListener(changeListener);
    }

    public void dispose() {
        if (null != operationalContext) {
            operationalContext.dispose();
        }
    }

    protected ConnectorOperationalContext getOperationalContext() {
        if (null == operationalContext) {
            return new ConnectorOperationalContext(connectorInfo, getAPIConfiguration());
        }
        return operationalContext;
    }

    // =======================================================================
    // ConnectorFacade Interface
    // =======================================================================

    @Override
    protected APIOperation getOperationImplementation(final Class<? extends APIOperation> api) {

        APIOperation proxy;
        boolean enableTimeoutProxy = true;
        // first create the inner proxy - this is the proxy that obtaining
        // a connector from the pool, etc
        // NOTE: we want to skip this part of the proxy for
        // validate op, but we will want the timeout proxy
        if (api == ValidateApiOp.class) {
            final OperationalContext context =
                    new OperationalContext(connectorInfo, getAPIConfiguration());
            proxy = new ValidateImpl(context);
        } else if (api == GetApiOp.class) {
            final Constructor<? extends APIOperationRunner> constructor =
                    API_TO_IMPL.get(SearchApiOp.class);
            final ConnectorAPIOperationRunnerProxy handler =
                    new ConnectorAPIOperationRunnerProxy(getOperationalContext(), constructor);
            proxy = new GetImpl((SearchApiOp) newAPIOperationProxy(SearchApiOp.class, handler));
        } else if (api == ConnectorEventSubscriptionApiOp.class
                || api == SyncEventSubscriptionApiOp.class) {
            final ConnectorAPIOperationRunnerProxy handler =
                    new ConnectorAPIOperationRunnerProxy(getOperationalContext(), null) {
                        protected APIOperationRunner getApiOperationRunner(
                                final ConnectorOperationalContext operationalContext,
                                final Connector connector) throws Exception {
                            if (api == ConnectorEventSubscriptionApiOp.class) {
                                return new SubscriptionImpl.ConnectorEventSubscriptionApiOpImp(
                                        operationalContext, connector, referenceCounter);
                            } else {
                                return new SubscriptionImpl.SyncEventSubscriptionApiOpImpl(
                                        operationalContext, connector, referenceCounter);
                            }
                        }
                    };
            proxy = newAPIOperationProxy(api, handler);
            enableTimeoutProxy = false;
        } else {
            final Constructor<? extends APIOperationRunner> constructor = API_TO_IMPL.get(api);
            final ConnectorAPIOperationRunnerProxy handler =
                    new ConnectorAPIOperationRunnerProxy(getOperationalContext(), constructor);
            proxy = newAPIOperationProxy(api, handler);
        }

        // now proxy to setup the thread-local classloader
        proxy =
                newAPIOperationProxy(api, new ThreadClassLoaderManagerProxy(connectorInfo
                        .getConnectorClass().getClassLoader(), proxy));

        if (enableTimeoutProxy) {
            // now wrap the proxy in the appropriate timeout proxy
            proxy = createTimeoutProxy(api, proxy);
        }
        // wrap in a logging proxy..
        if (LoggingProxy.isLoggable()) {
            proxy = createLoggingProxy(api, proxy);
        }
        proxy = newAPIOperationProxy(api, new ReferenceCountingProxy(proxy));
        
        return proxy;
    }
    
    public boolean isUnusedFor(long duration, TimeUnit timeUnit){
        return referenceCounter.isUnusedFor(duration, timeUnit);
    }
    
    public static class ReferenceCounter {
        private final AtomicInteger threadCounts = new AtomicInteger(0);
        private final AtomicLong lastUsed = new AtomicLong(System.nanoTime());

        public boolean isUnusedFor(long duration, TimeUnit timeUnit){
            return threadCounts.get() == 0 && System.nanoTime() - lastUsed.get() > timeUnit.toNanos(duration);
        }
        
        
        public void acquire(){
            threadCounts.incrementAndGet();
        }
        
        public void release(){
            if (threadCounts.decrementAndGet() <= 0){
                lastUsed.set(System.nanoTime());
                
            }
        }
        
    }
    
    private class ReferenceCountingProxy implements InvocationHandler {

        private final Object target;

        public ReferenceCountingProxy(final Object target) {
            this.target = target;
        }

        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            // do not log equals, hashCode, toString
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(target, arguments);
            }
            try {
                referenceCounter.acquire();
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } finally {
                referenceCounter.release();
            }
        }
    }
}
