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

package org.forgerock.openicf.framework.local;

import static org.identityconnectors.framework.impl.api.local.LocalConnectorInfoManagerImpl.createConnectorInfo;
import static org.identityconnectors.framework.impl.api.local.LocalConnectorInfoManagerImpl.processDirectory;
import static org.identityconnectors.framework.impl.api.local.LocalConnectorInfoManagerImpl.processURL;

import java.io.File;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.forgerock.openicf.framework.remote.ManagedAsyncConnectorInfoManager;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.impl.api.local.WorkingBundleInfo;

/**
 * AsyncLocalConnectorInfoManager.
 *
 * @since 1.5
 */
public class AsyncLocalConnectorInfoManager extends
        ManagedAsyncConnectorInfoManager<LocalConnectorInfoImpl, AsyncLocalConnectorInfoManager> {

    private static final Log logger = Log.getLog(AsyncLocalConnectorInfoManager.class);
    
    private final ClassLoader connectorBundleParentClassLoader;

    public AsyncLocalConnectorInfoManager(final ClassLoader connectorBundleParentClassLoader) {
        this.connectorBundleParentClassLoader = connectorBundleParentClassLoader;
    }

    public void addConnectorInfo(LocalConnectorInfoImpl connectorInfo) {
        super.addConnectorInfo(connectorInfo);
    }

    public void addConnectorBundle(Collection<URL> connectorBundleURLs) {
        final List<WorkingBundleInfo> workingInfo = new ArrayList<WorkingBundleInfo>();
        for (URL url : connectorBundleURLs) {
            WorkingBundleInfo info = null;
            try {
                if ("file".equals(url.getProtocol())) {
                    final File file = new File(url.toURI());
                    if (file.isDirectory()) {
                        info = processDirectory(file);
                    }
                }
                if (info == null) {
                    info = processURL(url, true);
                }
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Invalid bundleURL: " + url.toExternalForm(), e);
            }
            workingInfo.add(info);
        }
        WorkingBundleInfo.resolve(workingInfo);
        for (ConnectorInfo connectorInfo : createConnectorInfo(workingInfo,
                connectorBundleParentClassLoader)) {
            addConnectorInfo((LocalConnectorInfoImpl) connectorInfo);
        }  
    }
    
    public void addConnectorBundle(URL... connectorBundleURLs) {
        addConnectorBundle(Arrays.asList(connectorBundleURLs));
    }

    protected boolean canCloseNow() {
        doClose();
        return Boolean.FALSE;
    }
}
