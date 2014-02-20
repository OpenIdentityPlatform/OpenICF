/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock AS. All Rights Reserved
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.identityconnectors.common.Pair;
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
    private static final ConcurrentMap<String, Pair<AtomicLong, ConnectorFacade>> CACHE =
            new ConcurrentHashMap<String, Pair<AtomicLong, ConnectorFacade>>();

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectorFacade newInstance(final APIConfiguration config) {
        ConnectorFacade facade = super.newInstance(config);
        Pair<AtomicLong, ConnectorFacade> ret =
                CACHE.putIfAbsent(facade.getConnectorFacadeKey(), Pair.of(new AtomicLong(System
                        .currentTimeMillis()), facade));
        if (null != ret) {
            logger.ok("ConnectorFacade found in cache");
            ret.getKey().set(System.currentTimeMillis());
            facade = ret.getValue();
        }

        return facade;
    }

    @Override
    public ConnectorFacade newInstance(final ConnectorInfo connectorInfo, String config) {
        Pair<AtomicLong, ConnectorFacade> facade = CACHE.get(config);
        if (null == facade) {
            // new ConnectorFacade creation must remain cheap operation
            facade =
                    Pair.of(new AtomicLong(System.currentTimeMillis()), super.newInstance(
                            connectorInfo, config));
            Pair<AtomicLong, ConnectorFacade> ret =
                    CACHE.putIfAbsent(facade.getValue().getConnectorFacadeKey(), facade);
            if (null != ret) {
                logger.ok("ConnectorFacade found in cache");
                ret.getKey().set(System.currentTimeMillis());
                facade = ret;
            }
        } else {
            facade.getKey().set(System.currentTimeMillis());
        }
        return facade.getValue();
    }

    /**
     * Dispose of all object pools and other resources associated with this
     * class.
     */
    @Override
    public void dispose() {
        super.dispose();
        for (Pair<AtomicLong, ConnectorFacade> facade : CACHE.values()) {
            if (facade.getValue() instanceof LocalConnectorFacadeImpl) {
                try {
                    ((LocalConnectorFacadeImpl) facade.getValue()).dispose();
                } catch (Exception e) {
                    logger.warn(e, "Failed to dispose facade: {0}", facade.getValue());
                }
            }
        }
        CACHE.clear();
    }

    public void evictIdle(long time, TimeUnit unit) {
        if (unit == null)
            throw new NullPointerException();
        long lastTime = System.currentTimeMillis() - unit.toMillis(time);
        for (Map.Entry<String, Pair<AtomicLong, ConnectorFacade>> entry : CACHE.entrySet()) {
            if (entry.getValue().getKey().get() < lastTime) {
                if (CACHE.remove(entry.getKey(), entry.getValue())) {
                    if (entry.getValue().getValue() instanceof LocalConnectorFacadeImpl) {
                        try {
                            ((LocalConnectorFacadeImpl) entry.getValue().getValue()).dispose();
                            logger.ok("Disposed managed facade: {0}", entry.getValue());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to dispose facade: {0}", entry.getValue());
                        }
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
        Pair<AtomicLong, ConnectorFacade> pair = CACHE.get(facadeKey);
        if (pair != null) {
            return pair.getValue();
        }
        return null;
    }
}
