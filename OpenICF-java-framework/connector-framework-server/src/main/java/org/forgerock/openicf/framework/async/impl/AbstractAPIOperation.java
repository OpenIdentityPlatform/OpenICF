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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public abstract class AbstractAPIOperation {

    public static final ConnectionFailedException FAILED_EXCEPTION = new ConnectionFailedException(
            "No remote Connector Server is available at this moment");

    static {
        FAILED_EXCEPTION.setStackTrace(new StackTraceElement[0]);
    }

    private final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection;
    private final ConnectorKey connectorKey;
    private final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction;
    private long timeout;

    public AbstractAPIOperation(
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        this.remoteConnection = Assertions.nullChecked(remoteConnection, "remoteConnection");
        this.connectorKey = Assertions.nullChecked(connectorKey, "connectorKey");
        this.facadeKeyFunction = Assertions.nullChecked(facadeKeyFunction, "facadeKeyFunction");
        this.timeout = Math.max(timeout, APIOperation.NO_TIMEOUT);
    }

    public ConnectorKey getConnectorKey() {
        return connectorKey;
    }

    protected RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> getRemoteConnection() {
        return remoteConnection;
    }

    protected Function<RemoteOperationContext, ByteString, RuntimeException> getFacadeKeyFunction() {
        return facadeKeyFunction;
    }

    protected <V, M extends MessageLite, R extends AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<V, M>> Promise<V, RuntimeException> submitRequest(
            AbstractRemoteOperationRequestFactory<V, R> requestFactory) {
        R request = getRemoteConnection().trySubmitRequest(requestFactory);
        if (null != request) {
            return request.getPromise();
        }
        return Promises.<V, RuntimeException> newExceptionPromise(FAILED_EXCEPTION);
    }

    protected <T> T asyncTimeout(Promise<T, RuntimeException> promise) {
        if (APIOperation.NO_TIMEOUT == timeout) {
            return promise.getOrThrowUninterruptibly();
        } else {
            try {
                return promise.getOrThrowUninterruptibly(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                throw new OperationTimeoutException(ex);
            }
        }
    }
}
