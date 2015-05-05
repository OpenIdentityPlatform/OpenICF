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

package org.forgerock.openicf.common.rpc;

import java.util.concurrent.Future;

/**
 * A RemoteConnectionHolder is a wrapper class for the underlying communication
 * chanel.
 * <p/>
 * The API is detached from the real underlying protocol and abstracted through
 * this interface. The message transmitted via this implementation should
 * trigger the appropriate method on
 * {@link org.forgerock.openicf.common.rpc.MessageListener}.
 *
 */
public interface RemoteConnectionHolder<M, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>> {

    P getRemoteConnectionContext();

    /**
     * Initiates the asynchronous transmission of a binary message. This method
     * returns before the message is transmitted. Developers may use the
     * returned Future object to track progress of the transmission.
     *
     * @param data
     *            the data being sent
     * @return the Future object representing the send operation.
     */
    Future<?> sendBytes(byte[] data);

    /**
     * Initiates the asynchronous transmission of a string message. This method
     * returns before the message is transmitted. Developers may use the
     * returned Future object to track progress of the transmission.
     *
     * @param data
     *            the data being sent
     * @return the Future object representing the send operation.
     */
    Future<?> sendString(String data);

    /**
     * Send a Ping message containing the given application data to the remote
     * endpoint. The corresponding Pong message may be picked up using the
     * MessageHandler.Pong handler.
     *
     * @param applicationData
     *            the data to be carried in the ping request
     */
    void sendPing(byte[] applicationData) throws Exception;

    /**
     * Allows the developer to send an unsolicited Pong message containing the
     * given application data in order to serve as a unidirectional heartbeat
     * for the session.
     *
     * @param applicationData
     *            the application data to be carried in the pong response.
     */
    void sendPong(byte[] applicationData) throws Exception;

    /**
     * Closes this stream and releases any system resources associated with it.
     * If the stream is already closed then invoking this method has no effect.
     *
     */
    void close();
}
