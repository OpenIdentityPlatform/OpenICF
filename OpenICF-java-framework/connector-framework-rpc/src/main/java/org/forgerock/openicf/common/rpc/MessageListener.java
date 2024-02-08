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

public interface MessageListener<G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>> {

    /**
     * <p>
     * Invoked when the opening handshake has been completed for a specific
     * {@link RemoteConnectionHolder} instance.
     * </p>
     *
     * @param socket
     *            the newly connected {@link RemoteConnectionHolder}
     */
    void onConnect(H socket);

    /**
     * <p>
     * Invoked when {@link RemoteConnectionHolder#close()} has been called on a
     * particular {@link RemoteConnectionHolder} instance.
     * <p>
     *
     * @param socket
     *            the {@link RemoteConnectionHolder} being closed.
     * @param code
     *            the closing code sent by the remote end-point.
     * @param reason
     *            the closing reason sent by the remote end-point.
     */
    void onClose(H socket, int code, String reason);

    /**
     * <p>
     * Invoked when the {@link RemoteConnectionHolder} is open and an error
     * occurs processing the request.
     * </p>
     *
     * @param t
     */
    public void onError(Throwable t);

    /**
     * <p>
     * Invoked when {@link RemoteConnectionHolder#sendBytes(byte[])} has been
     * called on a particular {@link RemoteConnectionHolder} instance.
     * </p>
     *
     * @param socket
     *            the {@link RemoteConnectionHolder} that received a message.
     * @param data
     *            the message received.
     */
    void onMessage(H socket, byte[] data);

    /**
     * <p>
     * Invoked when {@link RemoteConnectionHolder#sendString(String)} has been
     * called on a particular {@link RemoteConnectionHolder} instance.
     * </p>
     *
     * @param socket
     *            the {@link RemoteConnectionHolder} that received a message.
     * @param data
     *            the message received.
     */
    void onMessage(H socket, String data);

    /**
     * <p>
     * Invoked when {@link RemoteConnectionHolder#sendPing(byte[])} has been
     * called on a particular {@link RemoteConnectionHolder} instance.
     * </p>
     *
     * @param socket
     *            the {@link RemoteConnectionHolder} that received the ping.
     * @param bytes
     *            the payload of the ping frame, if any.
     */
    void onPing(H socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link RemoteConnectionHolder#sendPong(byte[])} has been
     * called on a particular {@link RemoteConnectionHolder} instance.
     * </p>
     *
     * @param socket
     *            the {@link RemoteConnectionHolder} that received the pong.
     * @param bytes
     *            the payload of the pong frame, if any.
     */
    void onPong(H socket, byte[] bytes);
}
