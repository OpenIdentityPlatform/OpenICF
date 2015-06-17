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

import java.io.Closeable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.forgerock.openicf.framework.CloseListener;

import com.google.protobuf.MessageLite;

public abstract class WebSocketConnectionHolder
        implements
        Closeable,
        RemoteConnectionHolder<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> {

    protected final Queue<CloseListener<WebSocketConnectionHolder>> listeners =
            new ConcurrentLinkedQueue<CloseListener<WebSocketConnectionHolder>>();

    public boolean receiveHandshake(HandshakeMessage message) {
        if (null == getRemoteConnectionContext()) {
            handshake(message);
        }
        return isHandHooked();
    }

    public boolean isHandHooked() {
        return null != getRemoteConnectionContext();
    }

    public final void close() {
        tryClose();
        CloseListener<WebSocketConnectionHolder> listener;
        while ((listener = listeners.poll()) != null) {
            try {
                listener.onClosed(this);
            } catch (Exception ignore) {
                // Ignore this and continue to next
            }
        }
    }

    protected abstract void handshake(HandshakeMessage message);

    protected abstract void tryClose();

    public abstract boolean isOperational();
}
