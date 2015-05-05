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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Properties;

import org.forgerock.openicf.framework.client.ConnectionManagerConfig;
import org.forgerock.openicf.framework.client.RemoteConnectionInfoManagerFactory;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.remote.SecurityUtil;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;

/**
 * A ConnectorFrameworkFactory initialize a new ConnectorFramework and counts
 * the references to that instance.
 *
 */
public class ConnectorFrameworkFactory extends ReferenceCountedObject<ConnectorFramework> {

    public static final ConnectorFrameworkFactory DEFAULT_FACTORY = new ConnectorFrameworkFactory();

    private final static Log logger = Log.getLog(ConnectorFrameworkFactory.class);

    static {
        String properties = System.getProperty("openicf.connectorframeworkfactory.properties.file");
        if (StringUtil.isNotBlank(properties)) {
            try {
                DEFAULT_FACTORY.initialize(IOUtil.loadPropertiesFile(properties));
            } catch (IOException e) {
                logger.warn("Default Factory can not be configured");
            }
        }
    }

    private static final String ORG_FORGEROCK_OPENICF_FRAMEWORK_CLIENT_CONNECTION_MANAGER =
            "org.forgerock.openicf.framework.client.ConnectionManager";
    protected static final String ERROR_MESSAGE = "ConnectorFramework has been acquired";

    public void initialize(Properties properties) {
        if (isNull()) {
            String connectionManagerFactoryClass = properties.getProperty("ClientConnectionManagerFactoryClass");
            if (StringUtil.isBlank(connectionManagerFactoryClass)){
                setClientConnectionManagerFactoryClass(connectionManagerFactoryClass);
            }
        } else {
            throw new IllegalStateException(ERROR_MESSAGE);
        }
    }

    private ClassLoader defaultConnectorBundleParentClassLoader = null;

    public ConnectorFrameworkFactory setDefaultConnectorBundleParentClassLoader(
            final ClassLoader defaultConnectorBundleParentClassLoader) {
        if (isNull()) {
            this.defaultConnectorBundleParentClassLoader = defaultConnectorBundleParentClassLoader;
        } else {
            throw new IllegalStateException(ERROR_MESSAGE);
        }
        return this;
    }

    protected ClassLoader getDefaultConnectorBundleParentClassLoader() {
        return defaultConnectorBundleParentClassLoader != null ? defaultConnectorBundleParentClassLoader
                : ConnectorFrameworkFactory.class.getClassLoader();
    }

    private boolean requireClientConnectionManager = true;

    public ConnectorFrameworkFactory setRequireClientConnectionManager(
            boolean requireClientConnectionManager) {
        if (isNull()) {
            this.requireClientConnectionManager = requireClientConnectionManager;
        } else {
            throw new IllegalStateException(ERROR_MESSAGE);
        }

        return this;
    }

    protected boolean isRequireClientConnectionManager() {
        return requireClientConnectionManager;
    }

    private String clientConnectionManagerFactoryClass =
            ORG_FORGEROCK_OPENICF_FRAMEWORK_CLIENT_CONNECTION_MANAGER;

    public ConnectorFrameworkFactory setClientConnectionManagerFactoryClass(
            String clientConnectionManagerFactoryClass) {
        if (isNull()) {
            this.clientConnectionManagerFactoryClass = clientConnectionManagerFactoryClass;
        } else {
            throw new IllegalStateException(ERROR_MESSAGE);
        }
        return this;
    }

    protected String getClientConnectionManagerFactoryClass() {
        return StringUtil.isBlank(clientConnectionManagerFactoryClass) ? ORG_FORGEROCK_OPENICF_FRAMEWORK_CLIENT_CONNECTION_MANAGER
                : clientConnectionManagerFactoryClass;
    }

    protected void destroyInstance(ConnectorFramework instance) {
        try {
            instance.close();
        } catch (Exception e) {
            logger.warn(e, "Failed to close the ConnectorFramework after releasing last reference");
        }
    }

    protected ConnectorFramework newInstance() {
        return new ConnectorFramework(getDefaultConnectorBundleParentClassLoader());
    }

    protected Constructor<RemoteConnectionInfoManagerFactory> init() throws Exception {
        try {
            Class<?> clazz =
                    SecurityUtil.loadClass(getClientConnectionManagerFactoryClass(), getClass());
            if (RemoteConnectionInfoManagerFactory.class.isAssignableFrom(clazz)) {
                return (Constructor<RemoteConnectionInfoManagerFactory>) clazz.getConstructor(
                        OperationMessageListener.class, ConnectionManagerConfig.class);
            } else {
                logger.warn("The '{0}' class must extend RemoteConnectionInfoManagerFactory",
                        getClientConnectionManagerFactoryClass());
            }
        } catch (ClassNotFoundException e) {
            logger.warn(e, "RemoteConnectionInfoManagerFactory is not initialised");
        }
        return null;
    }
}
