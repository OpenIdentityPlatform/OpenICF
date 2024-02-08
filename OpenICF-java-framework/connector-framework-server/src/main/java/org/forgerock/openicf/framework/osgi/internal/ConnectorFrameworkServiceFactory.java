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

package org.forgerock.openicf.framework.osgi.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.identityconnectors.common.logging.Log;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;

public class ConnectorFrameworkServiceFactory implements ServiceFactory<ConnectorFrameworkFactory> {

    private static Log logger = Log.getLog(ConnectorFrameworkServiceFactory.class);

    private final AsyncOsgiConnectorInfoManagerImpl connectorInfoManager;

    private final ConcurrentMap<ServiceRegistration<ConnectorFrameworkFactory>, ConnectorFrameworkFactory> registrations =
            new ConcurrentHashMap<ServiceRegistration<ConnectorFrameworkFactory>, ConnectorFrameworkFactory>();

    public ConnectorFrameworkServiceFactory(
            final AsyncOsgiConnectorInfoManagerImpl connectorInfoManager) {
        this.connectorInfoManager = connectorInfoManager;
    }

    public ConnectorFrameworkFactory getService(Bundle bundle,
            ServiceRegistration<ConnectorFrameworkFactory> registration) {

        ConnectorFrameworkFactory factory = registrations.get(registration);
        if (null == factory) {
            synchronized (registrations) {
                factory = registrations.get(registration);
                if (null == factory) {
                    final ClassLoader connectorBundleParentClassLoader =
                            bundle.adapt(BundleWiring.class).getClassLoader();

                    factory = new ConnectorFrameworkFactory() {
                        protected ConnectorFramework newInstance() {
                            return new OsgiConnectorFramework(
                                    getDefaultConnectorBundleParentClassLoader(),
                                    connectorInfoManager);
                        }
                    };

                    URL configURL = bundle.getEntry("openicf.properties");
                    if (null != configURL) {
                        try {
                            InputStream in = configURL.openStream();
                            Reader reader = new InputStreamReader(in, "UTF-8");

                            Properties prop = new Properties();
                            try {
                                prop.load(reader);
                            } finally {
                                reader.close();
                            }
                            factory.initialize(prop);
                        } catch (IOException e) {
                            logger.error(e, "Failed to initialise the configuration from {0}",
                                    configURL);
                        }
                    }

                    factory.setDefaultConnectorBundleParentClassLoader(connectorBundleParentClassLoader);
                    registrations.put(registration, factory);
                }
            }
        }
        return factory;
    }

    public void ungetService(Bundle bundle,
            ServiceRegistration<ConnectorFrameworkFactory> registration,
            ConnectorFrameworkFactory service) {
        registrations.remove(registration);

    }

    void shutdown() {
        for (ServiceRegistration<ConnectorFrameworkFactory> registration : registrations.keySet()) {
            registration.unregister();
        }
        registrations.clear();
        connectorInfoManager.close();
    }
}
