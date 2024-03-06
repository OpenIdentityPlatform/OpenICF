/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
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

package org.identityconnectors.framework.impl.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;

public class ManagedConnectorFacadeFactoryImpl extends ConnectorFacadeFactoryImpl {

    private static final Log logger = Log.getLog(ManagedConnectorFacadeFactoryImpl.class);

    /**
     * Cache of the various ConnectorFacades.
     */
    private static final ConcurrentMap<String, ConnectorFacade> CACHE =
            new ConcurrentHashMap<String, ConnectorFacade>();

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectorFacade newInstance(final APIConfiguration config) {
        ConnectorFacade facade = super.newInstance(config);
        ConnectorFacade ret = CACHE.putIfAbsent(facade.getConnectorFacadeKey(), facade);
        if (null != ret) {
            logger.ok("ConnectorFacade found in cache");
            facade = ret;
        }

        return facade;
    }

    @Override
    public ConnectorFacade newInstance(final ConnectorInfo connectorInfo, String config) {
        ConnectorFacade facade = CACHE.get(config);
        if (null == facade) {
            // new ConnectorFacade creation must remain cheap operation
            facade = super.newInstance(
                            connectorInfo, config);
            ConnectorFacade ret =
                    CACHE.putIfAbsent(facade.getConnectorFacadeKey(), facade);
            if (null != ret) {
                logger.ok("ConnectorFacade found in cache");
                facade = ret;
            }
        } 
        return facade;
    }

    /**
     * Dispose of all object pools and other resources associated with this
     * class.
     */
    @Override
    public void dispose() {
        super.dispose();
        for (ConnectorFacade facade : CACHE.values()) {
            if (facade instanceof LocalConnectorFacadeImpl) {
                try {
                    ((LocalConnectorFacadeImpl) facade).dispose();
                } catch (Exception e) {
                    logger.warn(e, "Failed to dispose facade: {0}", facade);
                }
            }
        }
        CACHE.clear();
    }

    public void evictIdle(long time, TimeUnit unit) {
        if (unit == null)
            throw new NullPointerException();
        for (Map.Entry<String, ConnectorFacade> entry : CACHE.entrySet()) {
            if (CACHE.remove(entry.getKey(), entry.getValue())) {
                if ((entry.getValue() instanceof LocalConnectorFacadeImpl)
                        && ((LocalConnectorFacadeImpl) entry.getValue()).isUnusedFor(time, unit)) {
                    try {
                        ((LocalConnectorFacadeImpl) entry.getValue()).dispose();
                        logger.ok("Disposed managed facade: {0}", entry.getValue());
                    } catch (Exception e) {
                        logger.warn(e, "Failed to dispose facade: {0}", entry.getValue());
                    }
                }
            }
        }
    }

    /**
     * Finds the {@code ConnectorFacade} in the cache.
     *
     * This is used for testing only.
     *
     * @param facadeKey
     *            the key to find the {@code ConnectorFacade}.
     * @return The {@code ConnectorFacade} or {@code null} if not found.
     */
    public ConnectorFacade find(String facadeKey) {
        return CACHE.get(facadeKey);
    }
}
