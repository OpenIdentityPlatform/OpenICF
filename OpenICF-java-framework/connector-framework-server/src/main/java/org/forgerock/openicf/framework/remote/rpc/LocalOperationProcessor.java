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

import java.util.concurrent.ExecutionException;

import org.forgerock.openicf.common.protobuf.RPCMessages.RPCResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages.RemoteMessage;
import org.forgerock.openicf.common.rpc.LocalRequest;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.util.Function;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

public abstract class LocalOperationProcessor<V>
        extends
        LocalRequest<V, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

    private static final Log logger = Log.getLog(LocalOperationProcessor.class);
    private int inconsistencyCounter = 0;

    protected boolean stickToConnection = false;
    protected WebSocketConnectionHolder reverseConnection = null;

    protected LocalOperationProcessor(long requestId, final WebSocketConnectionHolder socket) {
        super(requestId, socket);
    }

    protected abstract RPCResponse.Builder createOperationResponse(
            RemoteOperationContext remoteContext, V result);

    public boolean check() {
        boolean valid = inconsistencyCounter < 3;
        if (!valid) {
            logger.ok(
                    "LocalRequest:{0} -> inconsistent with remote server, trying to cancel local process.",
                    getRequestId());
            cancel();
        }
        return valid;
    }

    public void inconsistent() {
        inconsistencyCounter++;
    }
    
    protected boolean tryHandleResult(V result) {
        try {
            final byte[] responseMessage =
                    RemoteMessage.newBuilder().setMessageId(getRequestId()).setResponse(
                            createOperationResponse(getRemoteConnectionContext(), result)).build()
                            .toByteArray();

            return null != trySendBytes(responseMessage);
        } catch (ConnectorIOException e) {
            // May not be complete / failed to send response
            logger.ok(e, "Operation complete successfully but failed to send result");
        } catch (Throwable t) {
            logger.ok(t, "Operation complete successfully but failed to build result message");
        }
        return false;
    }

    protected boolean tryHandleError(RuntimeException error) {
        final byte[] responseMessage =
                MessagesUtil.createErrorResponse(getRequestId(), error).build().toByteArray();
        try {
            return null != trySendBytes(responseMessage, true);
        } catch (ConnectorIOException e) {
            logger.ok(e, "Operation complete unsuccessfully and failed to send error");
        } catch (Throwable t) {
            logger.ok(t, "Operation complete unsuccessfully and failed to build result message");
        }
        return false;
    }

    protected WebSocketConnectionHolder trySendBytes(final byte[] responseMessage) {
        return trySendBytes(responseMessage, false);  
    };
    
    protected WebSocketConnectionHolder trySendBytes(final byte[] responseMessage, boolean useAnyConnection) {
        if (stickToConnection && !useAnyConnection) {
            if (null != reverseConnection) {
                try {
                    reverseConnection.sendBytes(responseMessage).get();
                } catch (ExecutionException e) {
                    throw new ConnectorIOException(e.getMessage(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                synchronized (this) {
                    if (null == reverseConnection) {
                        reverseConnection = trySendMessageNow(responseMessage);
                        if (null == reverseConnection) {
                            throw new ConnectorIOException("Transport layer is not operational");
                        }
                    } else {
                        trySendBytes(responseMessage);
                    }
                }
            }
        } else {
            return trySendMessageNow(responseMessage);
        }
        return reverseConnection;
    }

    private WebSocketConnectionHolder trySendMessageNow(final byte[] responseMessage) {
        return getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                new Function<WebSocketConnectionHolder, WebSocketConnectionHolder, Exception>() {
                    public WebSocketConnectionHolder apply(WebSocketConnectionHolder value)
                            throws Exception {
                        value.sendBytes(responseMessage).get();
                        return value;
                    }
                });
    }

    protected boolean tryCancel() {
        return false;
    }

}
