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

package org.forgerock.openicf.common.rpc.impl;

import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;

public class TestRequestFactory<H extends RemoteConnectionHolder<TestMessage, TestConnectionGroup<H>, H, TestConnectionContext<H>>>
        implements
        RemoteRequestFactory<TestMessage, TestRemoteRequest<H>, String, Exception, TestConnectionGroup<H>, H, TestConnectionContext<H>> {

    public interface MessageListener<H extends RemoteConnectionHolder<TestMessage, TestConnectionGroup<H>, H, TestConnectionContext<H>>> {
        public void handle(H sourceConnection, TestRemoteRequest<H> request, TestMessage message);
    }

    private final int request;

    public TestRequestFactory(int testCase) {
        request = testCase;
    }

    public TestRemoteRequest<H> createRemoteRequest(
            TestConnectionContext<H> context,
            long requestId,
            CompletionCallback<TestMessage, String, Exception, TestConnectionGroup<H>, H, TestConnectionContext<H>> completionCallback) {
        return new TestRemoteRequest<H>(context, requestId, completionCallback) {
            protected TestMessage getTestMessage() {
                TestMessage message = new TestMessage();
                message.request = request;
                return message;
            }

            protected void handle(H sourceConnection, TestRemoteRequest<H> request,
                    TestMessage message) {
                handleCallback(sourceConnection, request, message);
            }
        };
    }

    public void handleCallback(H sourceConnection, TestRemoteRequest<H> request, TestMessage message) {
        request.replyCallbackMessage(sourceConnection, request.getConnectionContext(), request
                .getRequestId());
    };

}
