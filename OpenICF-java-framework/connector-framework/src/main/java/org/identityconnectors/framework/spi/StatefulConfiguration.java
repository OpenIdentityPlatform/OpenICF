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

package org.identityconnectors.framework.spi;

/**
 * A Stateful Configuration interface extends the default {@link Configuration}
 * and makes the framework keep the same instance.
 * <p/>
 * The default Configuration object instance is constructed every single time
 * before the {@link Connector#init(Configuration)} is called. If the
 * configuration class implements this interface then the Framework keeps one
 * instance of Configuration and the {@link Connector#init(Configuration)} is
 * called with the same instance. This requires extra caution because the
 * framework only guaranties to create one instance and set the properties
 * before it calls the {@link Connector#init(Configuration)} on different
 * connector instances in multiple different threads at the same time. The
 * Connector developer must quarantine that the necessary resource
 * initialisation are thread-safe.
 *
 * <p/>
 * If the connector implements the {@link PoolableConnector} then this
 * configuration is kept in the
 * {@link org.identityconnectors.framework.impl.api.local.ConnectorPoolManager}
 * and when the
 * {@link org.identityconnectors.framework.impl.api.local.ConnectorPoolManager#dispose()}
 * calls the {@link #release()} method. If the connector implements only the
 * {@link Connector} then this configuration is kept in the
 * {@link org.identityconnectors.framework.api.ConnectorFacade} and the
 * application must take care of releasing.
 * @since 1.4
 */
public interface StatefulConfiguration extends Configuration {

    /**
     * Release any allocated resources.
     */
    public void release();

}
