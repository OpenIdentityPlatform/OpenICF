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

package org.forgerock.openicf.framework.server.jetty;

import java.lang.reflect.Method;

import javax.servlet.ServletConfig;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;

public class OpenICFWebSocketServletBase extends WebSocketServlet {

    public static final String INIT_PARAM_CONNECTOR_FRAMEWORK_FACTORY_CLASS =
            "connector-framework-factory-class";
    public static final String INIT_PARAM_CONNECTION_FACTORY_METHOD = "connection-factory-method";
    public static final String INIT_PARAM_CONNECTION_METHOD_DEFAULT = "acquire";

    private static final long serialVersionUID = 6089858120348026823L;

    private static final Logger logger = Log.getLogger(OpenICFWebSocketServletBase.class);

    private ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework;

    public OpenICFWebSocketServletBase() {
        connectorFramework = null;
    }

    public OpenICFWebSocketServletBase(final ConnectorFrameworkFactory connectorFramework) {
        this.connectorFramework = connectorFramework.acquire();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (connectorFramework != null) {
            try {
                connectorFramework.release();
            } catch (Exception e) {
                logger.warn(e);
            }
        }
    }

    @Override
    public void configure(WebSocketServletFactory factory) {

        if (null == connectorFramework) {
            connectorFramework = getConnectionFactory().acquire();
            configure(connectorFramework.get());
        }

        factory.setCreator(getWebsocketCreator());
        // To support onPing/onPong we need custom EventDriverFactory
        WebSocketServerFactory serverFactory = ((WebSocketServerFactory) factory);
        serverFactory.getEventDriverFactory().clearImplementations();
        serverFactory.getEventDriverFactory().addImplementation(new OpenICFListenerImpl());
        serverFactory.addSessionFactory(new OpenICFWebSocketSessionFactory(serverFactory));

        String max = getInitParameter("maxAsyncWriteTimeout");
        if (max != null) {
            factory.getPolicy().setAsyncWriteTimeout(Long.parseLong(max));
        }
    }

    /**
     * Overwrite with custom implementation.
     * 
     * @return
     */
    protected OpenICFWebSocketCreator getWebsocketCreator() {
        return new OpenICFWebSocketCreator(connectorFramework.get());
    }

    /**
     * Overwrite with custom implementation.
     * 
     * @return
     */
    protected void configure(ConnectorFramework connectorFramework) {
        logger.info("Implementation should overwrite this method");
    }

    /**
     * Overwrite with custom implementation.
     * 
     * @return
     */
    protected ConnectorFrameworkFactory getConnectionFactory() {
        final ServletConfig config = getServletConfig();
        if (config != null) {
            // Check for configured connection factory class first.
            final String className =
                    config.getInitParameter(INIT_PARAM_CONNECTOR_FRAMEWORK_FACTORY_CLASS);
            if (className != null) {
                try {
                    final Class<?> cls = Class.forName(className);
                    final String tmp =
                            config.getInitParameter(INIT_PARAM_CONNECTION_FACTORY_METHOD);
                    final String methodName =
                            tmp != null ? tmp : INIT_PARAM_CONNECTION_METHOD_DEFAULT;
                    try {
                        // Try method which accepts ServletConfig.
                        final Method factoryMethod =
                                cls.getDeclaredMethod(methodName, ServletConfig.class);
                        return (ConnectorFrameworkFactory) factoryMethod.invoke(null, config);
                    } catch (final NoSuchMethodException e) {
                        // Try no-arg method.
                        final Method factoryMethod = cls.getDeclaredMethod(methodName);
                        return (ConnectorFrameworkFactory) factoryMethod.invoke(null);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("Unable to initialize ConnectionFactory");
    }
}
