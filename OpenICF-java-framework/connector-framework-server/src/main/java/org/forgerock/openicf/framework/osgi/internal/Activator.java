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

import java.util.Hashtable;

import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.osgi.ConnectorManifestScanner;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.ops4j.pax.swissbox.extender.BundleWatcher;
import org.ops4j.pax.swissbox.extender.ManifestEntry;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Sample Class Doc
 *
 * @author Laszlo Hordos
 * @since 1.1
 */
public class Activator implements BundleActivator {

    /**
     * Logger.
     */
    private static final Log logger = Log.getLog(Activator.class);

    /**
     * Bundle watcher of ConnectorBundle-.
     */
    private BundleWatcher<ManifestEntry> connectorWatcher = null;

    /**
     *
     */
    private ServiceRegistration<?> connectorFrameworkFactory = null;

    /**
     * 
     */
    private ConnectorFrameworkServiceFactory framework = null;

    @SuppressWarnings("unchecked")
    public void start(BundleContext context) throws Exception {
        logger.ok("OpenICF OSGi Extender - Starting");

        final AsyncOsgiConnectorInfoManagerImpl connectorInfoManager =
                new AsyncOsgiConnectorInfoManagerImpl();

        framework = new ConnectorFrameworkServiceFactory(connectorInfoManager);

        connectorWatcher =
                new BundleWatcher<ManifestEntry>(context, new ConnectorManifestScanner(
                        FrameworkUtil.getFrameworkVersion()), connectorInfoManager);
        connectorWatcher.start();

        Hashtable<String, String> prop = new Hashtable<String, String>();
        prop.put("ConnectorBundle-FrameworkVersion", FrameworkUtil.getFrameworkVersion()
                .getVersion());

        connectorFrameworkFactory =
                context.registerService(ConnectorFrameworkFactory.class.getName(), framework, prop);

        logger.ok("OpenICF OSGi Extender - Started");
    }

    public void stop(BundleContext context) throws Exception {
        logger.ok("OpenICF OSGi Extender - Stopping");

        connectorFrameworkFactory.unregister();
        // Stop the bundle watcher.
        // This will result in un-publish of each ConnectorBundle that was
        // registered during the lifetime of
        // bundle watcher.
        if (connectorWatcher != null) {
            connectorWatcher.stop();
            connectorWatcher = null;
        }

        if (framework != null) {
            framework.shutdown();
            framework = null;
        }
        logger.ok("OpenICF OSGi Extender - Stopped");
    }
}
