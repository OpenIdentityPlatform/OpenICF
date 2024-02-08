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

package org.forgerock.openicf.framework.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.forgerock.openicf.framework.remote.SecurityUtil;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.identityconnectors.common.logging.Log;

public class ConnectionManagerFactory {

    private static final Log logger = Log.getLog(ConnectionManagerFactory.class);

    protected Class<? extends RemoteConnectionInfoManagerFactory> implClass = null;

    private static final String IMPL_NAME =
            "org.forgerock.openicf.framework.client.ConnectionManager";

    protected String getClientConnectionManagerFactoryClass() {
        return IMPL_NAME;
    }

    public void setImplementationClass(Class<? extends RemoteConnectionInfoManagerFactory> implClass) {
        this.implClass = implClass;
    }

    /**
     * Returns the instance of this factory.
     *
     * @return The instance of this factory
     */
    @SuppressWarnings("unchecked")
    public synchronized RemoteConnectionInfoManagerFactory getNewInstance(
            final OperationMessageListener messageListener,
            final ConnectionManagerConfig managerConfig) throws Exception {
        try {
            Class<? extends RemoteConnectionInfoManagerFactory> impl = implClass;
            if (impl == null) {
                Class<?> clazz =
                        SecurityUtil
                                .loadClass(getClientConnectionManagerFactoryClass(), getClass());

                if (RemoteConnectionInfoManagerFactory.class.isAssignableFrom(clazz)) {
                    impl = (Class<? extends RemoteConnectionInfoManagerFactory>) clazz;
                } else {
                    logger.warn("The '{0}' class must extend RemoteConnectionInfoManagerFactory",
                            getClientConnectionManagerFactoryClass());
                    return null;
                }
                implClass = impl;
            }
            Constructor<? extends RemoteConnectionInfoManagerFactory> c =
                    impl.getConstructor(OperationMessageListener.class,
                            ConnectionManagerConfig.class);
            return c.newInstance(messageListener, managerConfig);
        } catch (ClassNotFoundException e) {
            logger.warn(e, "RemoteConnectionInfoManagerFactory is not initialised");
            throw e;
        } catch (InvocationTargetException e) {
            logger.ok(e, "TODO Investigate");
            throw e;
        } catch (NoSuchMethodException e) {
            logger.warn(
                    e,
                    "RemoteConnectionInfoManagerFactory must have constructor (OperationMessageListener, ConnectionManagerConfig)");
            throw e;
        } catch (InstantiationException e) {
            logger.ok(e, "TODO Investigate");
            throw e;
        } catch (IllegalAccessException e) {
            logger.ok(e, "TODO Investigate");
            throw e;
        }
    }

}
