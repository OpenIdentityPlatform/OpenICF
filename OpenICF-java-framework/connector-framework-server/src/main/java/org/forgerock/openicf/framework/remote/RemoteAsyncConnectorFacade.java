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

import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.AsyncConnectorFacade;
import org.forgerock.openicf.framework.async.AuthenticationAsyncApiOp;
import org.forgerock.openicf.framework.async.CreateAsyncApiOp;
import org.forgerock.openicf.framework.async.DeleteAsyncApiOp;
import org.forgerock.openicf.framework.async.GetAsyncApiOp;
import org.forgerock.openicf.framework.async.ResolveUsernameAsyncApiOp;
import org.forgerock.openicf.framework.async.SchemaAsyncApiOp;
import org.forgerock.openicf.framework.async.ScriptOnConnectorAsyncApiOp;
import org.forgerock.openicf.framework.async.ScriptOnResourceAsyncApiOp;
import org.forgerock.openicf.framework.async.TestAsyncApiOp;
import org.forgerock.openicf.framework.async.UpdateAsyncApiOp;
import org.forgerock.openicf.framework.async.ValidateAsyncApiOp;
import org.forgerock.openicf.framework.async.impl.AuthenticationAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.BatchApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ConnectorEventSubscriptionApiOpImpl;
import org.forgerock.openicf.framework.async.impl.CreateAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.DeleteAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.GetAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ResolveUsernameAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SchemaAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ScriptOnConnectorAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ScriptOnResourceAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SearchAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SyncAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SyncEventSubscriptionApiOpImpl;
import org.forgerock.openicf.framework.async.impl.TestAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.UpdateAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ValidateAsyncApiOpImpl;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.api.operations.BatchApiOp;
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
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.AbstractConnectorFacade;
import org.identityconnectors.framework.impl.api.LoggingProxy;

import com.google.protobuf.ByteString;

public class RemoteAsyncConnectorFacade extends AbstractConnectorFacade implements
        AsyncConnectorFacade {

    private static final Log logger = Log.getLog(RemoteAsyncConnectorFacade.class);

    private final ConcurrentMap<String, ByteString> facadeKeys;

    private final AuthenticationAsyncApiOp authenticationApiOp;
    private final BatchApiOp batchApiOp;
    private final CreateAsyncApiOp createApiOp;
    private final ConnectorEventSubscriptionApiOp connectorEventSubscriptionApiOp;
    private final DeleteAsyncApiOp deleteApiOp;
    private final GetAsyncApiOp getApiOp;
    private final ResolveUsernameAsyncApiOp resolveUsernameApiOp;
    private final SchemaAsyncApiOp schemaApiOp;
    private final ScriptOnConnectorAsyncApiOp scriptOnConnectorApiOp;
    private final ScriptOnResourceAsyncApiOp scriptOnResourceApiOp;
    private final SearchApiOp searchApiOp;
    private final SyncApiOp syncApiOp;
    private final SyncEventSubscriptionApiOp syncEventSubscriptionApiOp;
    private final TestAsyncApiOp testApiOp;
    private final UpdateAsyncApiOp updateApiOp;
    private final ValidateAsyncApiOp validateApiOp;

    protected RemoteAsyncConnectorFacade(
            final APIConfigurationImpl configuration,
            final Function<LoadBalancingConnectorFacadeContext, APIConfiguration, RuntimeException> transformer) {
        super(configuration);
        if (configuration.getConnectorInfo() instanceof RemoteConnectorInfoImpl) {
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection =
                    Assertions.nullChecked(((RemoteConnectorInfoImpl) configuration
                            .getConnectorInfo()).messageDistributor, "messageDistributor");

            final ConnectorKey connectorKey =
                    getAPIConfiguration().getConnectorInfo().getConnectorKey();
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction;
            if (null != transformer) {
                facadeKeys = new ConcurrentHashMap<String, ByteString>();
                facadeKeyFunction =
                        new Function<RemoteOperationContext, ByteString, RuntimeException>() {
                            public ByteString apply(final RemoteOperationContext value)
                                    throws RuntimeException {
                                ByteString facadeKey =
                                        facadeKeys.get(value.getRemotePrincipal().getName());
                                if (null == facadeKey) {
                                    final ConnectorInfo connectorInfo =
                                            value.getRemoteConnectionGroup().findConnectorInfo(
                                                    connectorKey);
                                    if (null != connectorInfo) {
                                        // Remote server has the ConnectorInfo
                                        try {
                                            APIConfiguration fullConfiguration =
                                                    transformer
                                                            .apply(new LoadBalancingConnectorFacadeContext() {

                                                                public APIConfiguration getAPIConfiguration() {
                                                                    return connectorInfo
                                                                            .createDefaultAPIConfiguration();
                                                                }

                                                                public String getPrincipalName() {
                                                                    return value
                                                                            .getRemotePrincipal()
                                                                            .getName();
                                                                }

                                                                public RemoteOperationContext getRemoteOperationContext() {
                                                                    return value;
                                                                }
                                                            });
                                            if (null != fullConfiguration) {
                                                String connectorFacadeKey =
                                                        (SerializerUtil
                                                                .serializeBase64Object(fullConfiguration));
                                                facadeKey =
                                                        ByteString.copyFromUtf8(connectorFacadeKey);
                                                if (null != getAPIConfiguration()
                                                        .getChangeListener()) {
                                                    value.getRemoteConnectionGroup()
                                                            .addConfigurationChangeListener(
                                                                    connectorFacadeKey,
                                                                    getAPIConfiguration()
                                                                            .getChangeListener());
                                                }
                                                facadeKeys.putIfAbsent(value.getRemotePrincipal()
                                                        .getName(), facadeKey);
                                            }
                                        } catch (Throwable t) {
                                            logger.warn(t,
                                                    "Failed to build APIConfiguration for {0}",
                                                    value.getRemotePrincipal().getName());
                                        }
                                    } else {
                                        logger.ok(
                                                "Can not execute Operation on {0} because ConnectorInfo [{1}] is not installed",
                                                value.getRemotePrincipal().getName(), connectorKey);
                                    }
                                }
                                return facadeKey;
                            }
                        };
            } else {
                facadeKeys = null;
                final ByteString facadeKey = ByteString.copyFromUtf8(getConnectorFacadeKey());
                facadeKeyFunction =
                        new Function<RemoteOperationContext, ByteString, RuntimeException>() {
                            public ByteString apply(RemoteOperationContext context)
                                    throws RuntimeException {
                                context.getRemoteConnectionGroup().findConnectorInfo(
                                        getAPIConfiguration().getConnectorInfo().getConnectorKey());
                                if (null != getAPIConfiguration().getChangeListener()) {
                                    context.getRemoteConnectionGroup()
                                            .addConfigurationChangeListener(
                                                    getConnectorFacadeKey(),
                                                    getAPIConfiguration().getChangeListener());
                                }
                                return facadeKey;
                            }
                        };
            }

            // initialise operations
            if (configuration.isSupportedOperation(AuthenticationApiOp.class)) {
                authenticationApiOp =
                        createLogging(AuthenticationApiOp.class, new AuthenticationAsyncApiOpImpl(
                                remoteConnection, connectorKey, facadeKeyFunction,
                                getAPIConfiguration().getTimeout(AuthenticationApiOp.class)));
            } else {
                authenticationApiOp = null;
            }
            if (configuration.isSupportedOperation(BatchApiOp.class)) {
                batchApiOp =
                        createLogging(BatchApiOp.class, new BatchApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        BatchApiOp.class)));
            } else {
                batchApiOp = null;
            }
            if (configuration.isSupportedOperation(CreateApiOp.class)) {
                createApiOp =
                        createLogging(CreateApiOp.class, new CreateAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        CreateApiOp.class)));
            } else {
                createApiOp = null;
            }
            if (configuration.isSupportedOperation(ConnectorEventSubscriptionApiOp.class)) {
                connectorEventSubscriptionApiOp =
                        createLogging(ConnectorEventSubscriptionApiOp.class,
                                new ConnectorEventSubscriptionApiOpImpl(remoteConnection,
                                        connectorKey, facadeKeyFunction, getAPIConfiguration()
                                                .getTimeout(ConnectorEventSubscriptionApiOp.class)));
            } else {
                connectorEventSubscriptionApiOp = null;
            }
            if (configuration.isSupportedOperation(DeleteApiOp.class)) {
                deleteApiOp =
                        createLogging(DeleteApiOp.class, new DeleteAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        DeleteApiOp.class)));
            } else {
                deleteApiOp = null;
            }
            if (configuration.isSupportedOperation(ResolveUsernameApiOp.class)) {
                resolveUsernameApiOp =
                        createLogging(ResolveUsernameApiOp.class,
                                new ResolveUsernameAsyncApiOpImpl(remoteConnection, connectorKey,
                                        facadeKeyFunction, getAPIConfiguration().getTimeout(
                                                ResolveUsernameApiOp.class)));
            } else {
                resolveUsernameApiOp = null;
            }
            if (configuration.isSupportedOperation(SchemaApiOp.class)) {
                schemaApiOp =
                        createLogging(SchemaApiOp.class, new SchemaAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        SchemaApiOp.class)));
            } else {
                schemaApiOp = null;
            }
            if (configuration.isSupportedOperation(ScriptOnConnectorApiOp.class)) {
                scriptOnConnectorApiOp =
                        createLogging(ScriptOnConnectorApiOp.class,
                                new ScriptOnConnectorAsyncApiOpImpl(remoteConnection, connectorKey,
                                        facadeKeyFunction, getAPIConfiguration().getTimeout(
                                                ScriptOnConnectorApiOp.class)));
            } else {
                scriptOnConnectorApiOp = null;
            }
            if (configuration.isSupportedOperation(ScriptOnResourceApiOp.class)) {
                scriptOnResourceApiOp =
                        createLogging(ScriptOnResourceApiOp.class,
                                new ScriptOnResourceAsyncApiOpImpl(remoteConnection, connectorKey,
                                        facadeKeyFunction, getAPIConfiguration().getTimeout(
                                                ScriptOnResourceApiOp.class)));
            } else {
                scriptOnResourceApiOp = null;
            }
            if (configuration.isSupportedOperation(SearchApiOp.class)) {
                searchApiOp =
                        createLogging(SearchApiOp.class, new SearchAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        SearchApiOp.class)));
                getApiOp =
                        createLogging(GetApiOp.class, new GetAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        GetApiOp.class)));
            } else {
                searchApiOp = null;
                getApiOp = null;
            }
            if (configuration.isSupportedOperation(SyncApiOp.class)) {
                syncApiOp =
                        createLogging(SyncApiOp.class, new SyncAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        SyncApiOp.class)));
            } else {
                syncApiOp = null;
            }
            if (configuration.isSupportedOperation(SyncEventSubscriptionApiOp.class)) {
                syncEventSubscriptionApiOp =
                        createLogging(SyncEventSubscriptionApiOp.class,
                                new SyncEventSubscriptionApiOpImpl(remoteConnection, connectorKey,
                                        facadeKeyFunction, getAPIConfiguration().getTimeout(
                                                SyncEventSubscriptionApiOp.class)));
            } else {
                syncEventSubscriptionApiOp = null;
            }
            if (configuration.isSupportedOperation(TestApiOp.class)) {
                testApiOp =
                        createLogging(TestApiOp.class, new TestAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        TestAsyncApiOp.class)));
            } else {
                testApiOp = null;
            }
            if (configuration.isSupportedOperation(UpdateApiOp.class)) {
                updateApiOp =
                        createLogging(UpdateApiOp.class, new UpdateAsyncApiOpImpl(remoteConnection,
                                connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                        UpdateApiOp.class)));
            } else {
                updateApiOp = null;
            }
            validateApiOp =
                    createLogging(ValidateApiOp.class, new ValidateAsyncApiOpImpl(remoteConnection,
                            connectorKey, facadeKeyFunction, getAPIConfiguration().getTimeout(
                                    ValidateApiOp.class)));
        } else {
            throw new IllegalArgumentException("Unsupported ConnectorInfo type");
        }
    }

    public <T extends APIOperation> T createLogging(Class<? extends APIOperation> api, T target) {
        if (LoggingProxy.isLoggable()) {
            return (T) Proxy.newProxyInstance(api.getClassLoader(), target.getClass()
                    .getInterfaces(), new LoggingProxy(api, target));
        }
        return target;
    }

    public RemoteAsyncConnectorFacade(
            RemoteConnectorInfoImpl firstConnectorInfo,
            final Function<LoadBalancingConnectorFacadeContext, APIConfiguration, RuntimeException> transformer) {
        this((APIConfigurationImpl) firstConnectorInfo.createDefaultAPIConfiguration(), transformer);
    }

    public RemoteAsyncConnectorFacade(APIConfigurationImpl configuration) {
        this(configuration, null);
    }

    protected <T extends APIOperation> T getAsyncOperationCheckSupported(final Class<T> api) {
        T op = api.cast(getOperationImplementation(api));

        // check if this operation is supported.
        if (null == op) {
            throw new UnsupportedOperationException(MessageFormat.format(MSG, api));
        }
        return op;
    }

    protected APIOperation getOperationImplementation(Class<? extends APIOperation> api) {
        if (AuthenticationApiOp.class.isAssignableFrom(api)) {
            return authenticationApiOp;
        } else if (BatchApiOp.class.isAssignableFrom(api)) {
            return batchApiOp;
        } else if (CreateApiOp.class.isAssignableFrom(api)) {
            return createApiOp;
        } else if (ConnectorEventSubscriptionApiOp.class.isAssignableFrom(api)) {
            return connectorEventSubscriptionApiOp;
        } else if (DeleteApiOp.class.isAssignableFrom(api)) {
            return deleteApiOp;
        } else if (GetApiOp.class.isAssignableFrom(api)) {
            return getApiOp;
        } else if (ResolveUsernameApiOp.class.isAssignableFrom(api)) {
            return resolveUsernameApiOp;
        } else if (SchemaApiOp.class.isAssignableFrom(api)) {
            return schemaApiOp;
        } else if (ScriptOnConnectorApiOp.class.isAssignableFrom(api)) {
            return scriptOnConnectorApiOp;
        } else if (ScriptOnResourceApiOp.class.isAssignableFrom(api)) {
            return scriptOnResourceApiOp;
        } else if (SearchApiOp.class.isAssignableFrom(api)) {
            return searchApiOp;
        } else if (SyncApiOp.class.isAssignableFrom(api)) {
            return syncApiOp;
        } else if (SyncEventSubscriptionApiOp.class.isAssignableFrom(api)) {
            return syncEventSubscriptionApiOp;
        } else if (TestApiOp.class.isAssignableFrom(api)) {
            return testApiOp;
        } else if (UpdateApiOp.class.isAssignableFrom(api)) {
            return updateApiOp;
        } else if (ValidateApiOp.class.isAssignableFrom(api)) {
            return validateApiOp;
        } else {
            return null;
        }
    }

    public Promise<Uid, RuntimeException> authenticateAsync(ObjectClass objectClass,
            String username, GuardedString password, OperationOptions options) {
        return getAsyncOperationCheckSupported(AuthenticationAsyncApiOp.class).authenticateAsync(
                objectClass, username, password, options);
    }

    public Promise<Uid, RuntimeException> createAsync(ObjectClass objectClass,
            Set<Attribute> createAttributes, OperationOptions options) {
        return getAsyncOperationCheckSupported(CreateAsyncApiOp.class).createAsync(objectClass,
                createAttributes, options);
    }

    public Promise<Void, RuntimeException> deleteAsync(ObjectClass objectClass, Uid uid,
            OperationOptions options) {
        return getAsyncOperationCheckSupported(DeleteAsyncApiOp.class).deleteAsync(objectClass,
                uid, options);
    }

    public Promise<ConnectorObject, RuntimeException> getObjectAsync(ObjectClass objectClass,
            Uid uid, OperationOptions options) {
        return getAsyncOperationCheckSupported(GetAsyncApiOp.class).getObjectAsync(objectClass,
                uid, options);
    }

    public Promise<Uid, RuntimeException> resolveUsernameAsync(ObjectClass objectClass,
            String username, OperationOptions options) {
        return getAsyncOperationCheckSupported(ResolveUsernameAsyncApiOp.class)
                .resolveUsernameAsync(objectClass, username, options);
    }

    public Promise<Schema, RuntimeException> schemaAsync() {
        return getAsyncOperationCheckSupported(SchemaAsyncApiOp.class).schemaAsync();
    }

    public Promise<Object, RuntimeException> runScriptOnConnectorAsync(ScriptContext request,
            OperationOptions options) {
        return getAsyncOperationCheckSupported(ScriptOnConnectorAsyncApiOp.class)
                .runScriptOnConnectorAsync(request, options);
    }

    public Promise<Object, RuntimeException> runScriptOnResourceAsync(ScriptContext request,
            OperationOptions options) {
        return getAsyncOperationCheckSupported(ScriptOnResourceAsyncApiOp.class)
                .runScriptOnResourceAsync(request, options);
    }

    public Promise<Void, RuntimeException> testAsync() {
        return getAsyncOperationCheckSupported(TestAsyncApiOp.class).testAsync();
    }

    public Promise<Uid, RuntimeException> updateAsync(ObjectClass objectClass, Uid uid,
            Set<Attribute> replaceAttributes, OperationOptions options) {
        return getAsyncOperationCheckSupported(UpdateAsyncApiOp.class).updateAsync(objectClass,
                uid, replaceAttributes, options);
    }

    public Promise<Uid, RuntimeException> addAttributeValuesAsync(ObjectClass objectClass, Uid uid,
            Set<Attribute> valuesToAdd, OperationOptions options) {
        return getAsyncOperationCheckSupported(UpdateAsyncApiOp.class).addAttributeValuesAsync(
                objectClass, uid, valuesToAdd, options);
    }

    public Promise<Uid, RuntimeException> removeAttributeValuesAsync(ObjectClass objectClass,
            Uid uid, Set<Attribute> valuesToRemove, OperationOptions options) {
        return getAsyncOperationCheckSupported(UpdateAsyncApiOp.class).removeAttributeValuesAsync(
                objectClass, uid, valuesToRemove, options);
    }

    public Promise<Void, RuntimeException> validateAsync() {
        return getAsyncOperationCheckSupported(ValidateAsyncApiOp.class).validateAsync();
    }
}
