/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.framework.remote.rpc;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.common.protobuf.RPCMessages.RemoteMessage;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Tests that {@link WebSocketConnectionGroup#shutdown()} does not resend the
 * initial 'CONNECTOR_INFO' request it has just cancelled: the retry attached
 * in {@link WebSocketConnectionGroup#handshakeComplete()} is meant for a
 * failure of the first request, not for the group's own shutdown.
 */
public class WebSocketConnectionGroupShutdownTest {

    private static final String SESSION_ID = "session";

    /** Exposes the request registry the shutdown loop iterates over. */
    private static final class TestGroup extends WebSocketConnectionGroup {

        TestGroup() {
            super(SESSION_ID);
        }

        int pendingRemoteRequests() {
            return remoteRequests.size();
        }
    }

    /** Records every frame the group writes to the socket. */
    private static final class RecordingHolder extends WebSocketConnectionHolder {

        final List<RemoteMessage> sent = new CopyOnWriteArrayList<RemoteMessage>();
        volatile RemoteOperationContext context;

        protected void handshake(HandshakeMessage message) {
        }

        protected void tryClose() {
        }

        public boolean isOperational() {
            return true;
        }

        public RemoteOperationContext getRemoteConnectionContext() {
            return context;
        }

        public Future<?> sendBytes(byte[] data) {
            try {
                sent.add(RemoteMessage.parseFrom(data));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException(e);
            }
            return CompletableFuture.completedFuture(null);
        }

        public Future<?> sendString(String data) {
            return CompletableFuture.completedFuture(null);
        }

        public void sendPing(byte[] applicationData) throws Exception {
        }

        public void sendPong(byte[] applicationData) throws Exception {
        }

        int controlRequests() {
            int count = 0;
            for (RemoteMessage message : sent) {
                if (message.hasRequest() && message.getRequest().hasControlRequest()) {
                    count++;
                }
            }
            return count;
        }

        int cancelRequests() {
            int count = 0;
            for (RemoteMessage message : sent) {
                if (message.hasRequest() && message.getRequest().hasCancelOpRequest()) {
                    count++;
                }
            }
            return count;
        }
    }

    @Test(timeOut = 30000)
    public void testShutdownDoesNotResendCancelledInitialConnectorInfoRequest() {
        final Principal principal = new Principal() {
            public String getName() {
                return "client";
            }
        };
        final TestGroup group = new TestGroup();
        final RecordingHolder holder = new RecordingHolder();

        holder.context = group.handshake(principal, holder,
                HandshakeMessage.newBuilder().setSessionId(SESSION_ID).build());
        group.handshakeComplete();

        Assert.assertEquals(holder.controlRequests(), 1, "initial 'CONNECTOR_INFO' request");
        Assert.assertEquals(group.pendingRemoteRequests(), 1, "initial request is pending");

        // The last principal leaves: shutdown() cancels the pending initial
        // request, which must not schedule its retry.
        group.principalIsShuttingDown(principal);

        Assert.assertEquals(holder.cancelRequests(), 1,
                "the pending initial request is cancelled on the remote side");
        Assert.assertEquals(holder.controlRequests(), 1,
                "initial 'CONNECTOR_INFO' request was resent while shutting down");
        Assert.assertEquals(group.pendingRemoteRequests(), 0,
                "a request stayed registered in the shut-down group");
    }
}
