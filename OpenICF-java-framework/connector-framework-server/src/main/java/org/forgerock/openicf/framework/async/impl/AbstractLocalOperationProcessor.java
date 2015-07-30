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

package org.forgerock.openicf.framework.async.impl;

import org.forgerock.openicf.framework.remote.rpc.LocalOperationProcessor;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import com.google.protobuf.MessageLite;

public abstract class AbstractLocalOperationProcessor<V, M extends MessageLite> extends
        LocalOperationProcessor<V> {

    private final M requestMessage;

    protected AbstractLocalOperationProcessor(long requestId,
            final WebSocketConnectionHolder socket, final M message) {
        super(requestId, socket);
        requestMessage = message;
    }

    protected abstract V executeOperation(final ConnectorFacade connectorFacade,
            final M requestMessage);

    public void execute(final ConnectorFacade connectorFacade) {
        try {
            handleResult(executeOperation(connectorFacade, requestMessage));
        } catch (Error t) {
            handleException(ConnectorException.wrap(t));
        } catch (RuntimeException error) {
            handleException(error);
        }
    }
}
