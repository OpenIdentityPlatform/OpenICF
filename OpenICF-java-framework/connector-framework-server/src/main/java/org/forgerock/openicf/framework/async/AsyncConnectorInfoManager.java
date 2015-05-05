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

package org.forgerock.openicf.framework.async;

import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.ConnectorKeyRange;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorKey;

/**
 * An AsyncConnectorInfoManager maintains a list of <code>ConnectorInfo</code>
 * instances, each of which describes a connector that is available.
 *
 * @since 1.5
 */
public interface AsyncConnectorInfoManager extends ConnectorInfoManager {

    /**
     * Add a promise which will be fulfilled with the
     * {@link org.identityconnectors.framework.api.ConnectorInfo} for the given
     * {@ConnectorKey}.
     * 
     * Add a Promise which will be fulfilled immediately if the
     * {@link org.identityconnectors.framework.api.ConnectorInfo} is maintained
     * currently by this instance or later when it became available.
     * 
     * @param key
     * @return new promise
     */
    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(ConnectorKey key);

    /**
     * Add a promise which will be fulfilled with the
     * {@link org.identityconnectors.framework.api.ConnectorInfo} for the given
     * {@ConnectorKeyRange}.
     *
     * Add a Promise which will be fulfilled immediately if the
     * {@link org.identityconnectors.framework.api.ConnectorInfo} is maintained
     * currently by this instance or later when it became available.
     * 
     * There may be multiple ConnectorInfo matching the range. The
     * implementation can only randomly fulfill the promise. It can not grantee
     * the highest version to return because it may became available after the
     * promised added and after a lower version of ConnectorInfo became
     * available in this manager.
     *
     * @param keyRange
     * @return new promise
     */
    public Promise<ConnectorInfo, RuntimeException> findConnectorInfoAsync(
            ConnectorKeyRange keyRange);
}
