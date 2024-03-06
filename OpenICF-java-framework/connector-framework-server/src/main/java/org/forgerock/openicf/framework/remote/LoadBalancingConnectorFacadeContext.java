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

import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.identityconnectors.framework.api.APIConfiguration;

/**
 * A LoadBalancingConnectorFacadeContext gives contextual information about the
 * current executing
 * {@link org.forgerock.openicf.framework.remote.ConnectionPrincipal}
 */
public interface LoadBalancingConnectorFacadeContext {
    
    /**
     * Loads the {@link org.identityconnectors.framework.spi.Connector} and
     * {@link org.identityconnectors.framework.spi.Configuration} class in order
     * to determine the proper default configuration parameters.
     * 
     * @return default APIConfiguration
     */
    APIConfiguration getAPIConfiguration();

    /**
     * Gets the principal name of executing
     * {@link org.forgerock.openicf.framework.remote.ConnectionPrincipal}.
     * 
     * @return name of
     *         {@link org.forgerock.openicf.framework.remote.ConnectionPrincipal}
     */
    String getPrincipalName();

    /**
     * Get the RemoteOperationContext of executing
     * {@link org.forgerock.openicf.framework.remote.ConnectionPrincipal}
     * 
     * @return context of
     *         {@link org.forgerock.openicf.framework.remote.ConnectionPrincipal}
     */
    RemoteOperationContext getRemoteOperationContext();
}
