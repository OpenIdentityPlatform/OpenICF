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

package org.forgerock.openicf.framework.remote.rpc;

import java.util.concurrent.CancellationException;

import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages.CancelOpRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages.ExceptionMessage;
import org.forgerock.openicf.common.protobuf.RPCMessages.RPCRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages.RemoteMessage;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.util.Function;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import com.google.protobuf.MessageLite;

public abstract class RemoteOperationRequest<V>
        extends
        RemoteRequest<V, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

    private final static Log logger = Log.getLog(RemoteOperationRequest.class);
    protected int inconsistencyCounter = 0;

    public RemoteOperationRequest(
            RemoteOperationContext context,
            long requestId,
            RemoteRequestFactory.CompletionCallback<V, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {
        super(context, requestId, completionCallback);
    }

    protected abstract boolean handleResponseMessage(WebSocketConnectionHolder sourceConnection,
            MessageLite message);

    protected abstract RPCRequest.Builder createOperationRequest(
            RemoteOperationContext remoteContext);

    public boolean check() {
        boolean valid = inconsistencyCounter < 3;
        if (!valid) {
            logger.ok(
                    "RemoteRequest:{0} -> inconsistent with remote server, set failed local process.",
                    getRequestId());
            getExceptionHandler().handleException(
                    new ConnectorException(
                            "Operation finished on remote server with unknown result"));
        }
        return valid;
    }

    public void inconsistent() {
        inconsistencyCounter++;
    }

    public void handleIncomingMessage(WebSocketConnectionHolder sourceConnection, Object message) {
        if (message instanceof RPCMessages.ExceptionMessage) {
            handleExceptionMessage((RPCMessages.ExceptionMessage) message);
        } else if (message instanceof MessageLite) {
            if (!handleResponseMessage(sourceConnection, (MessageLite) message)) {
                logger.ok("Request {0} has unknown response message type:{1}", getRequestId(),
                        getClass().getSimpleName());
                getExceptionHandler().handleException(
                        new ConnectorException("Unknown response message type:"
                                + message.getClass()));
            }
        }
    }

    protected MessageElement createMessageElement(RemoteOperationContext remoteContext,
            long requestId) {
        return MessageElement.createByteMessage(RemoteMessage.newBuilder().setMessageId(requestId)
                .setRequest(createOperationRequest(remoteContext)).build().toByteArray());
    }

    protected void tryCancelRemote(RemoteOperationContext remoteContext, long requestId) {
        final byte[] cancelMessage =
                RemoteMessage.newBuilder().setMessageId(requestId).setRequest(
                        RPCRequest.newBuilder().setCancelOpRequest(
                                CancelOpRequest.getDefaultInstance())).build().toByteArray();

        trySendBytes(cancelMessage);
    }

    protected RuntimeException createCancellationException(Throwable cancellationException) {
        if (cancellationException instanceof Exception)
            return (RuntimeException) cancellationException;
        else if (null != cancellationException) {
            CancellationException exception =
                    new CancellationException(cancellationException.getMessage());
            exception.initCause(cancellationException);
            return exception;
        } else
            return new CancellationException("Operation is cancelled #" + getRequestId());
    }

    protected void trySendBytes(final byte[] cancelMessage) {
        if (null == getConnectionContext().getRemoteConnectionGroup().trySendMessage(
                new Function<WebSocketConnectionHolder, Boolean, Exception>() {
                    public Boolean apply(WebSocketConnectionHolder value) throws Exception {
                        value.sendBytes(cancelMessage).get();
                        return Boolean.TRUE;
                    }
                })) {
            // Failed to send remote message
            throw new ConnectorIOException("Transport layer is not operational");
        }
    }

    protected void handleExceptionMessage(ExceptionMessage exceptionMessage) {
        try {
            getExceptionHandler().handleException(
                    MessagesUtil.fromExceptionMessage(exceptionMessage));
        } catch (Exception e) {
            logger.info(e, "Exception received but failed to handle it: {0}", getRequestId());
        }
    }
}
