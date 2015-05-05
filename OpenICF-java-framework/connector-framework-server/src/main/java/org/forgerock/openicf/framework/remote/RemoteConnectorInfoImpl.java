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

import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.impl.api.AbstractConnectorInfo;

import com.google.protobuf.MessageLite;

/**
 * @since 1.5
 */
public class RemoteConnectorInfoImpl extends AbstractConnectorInfo {

    final RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> messageDistributor;

    public RemoteConnectorInfoImpl(
            final RequestDistributor<MessageLite, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final AbstractConnectorInfo copyFrom) {
        messageDistributor = Assertions.nullChecked(remoteConnection, "remoteConnection");
        Assertions.nullCheck(copyFrom, "copyFrom");
        setConnectorDisplayNameKey(copyFrom.getConnectorDisplayNameKey());
        setConnectorKey(copyFrom.getConnectorKey());
        setMessages(copyFrom.getMessages());
        setConnectorCategoryKey(copyFrom.getConnectorCategoryKey());
        setDefaultAPIConfiguration(copyFrom.getDefaultAPIConfiguration());
    }
}
