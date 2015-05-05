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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.util.promise.Function;

public abstract class TestRemoteRequest<H extends RemoteConnectionHolder<TestMessage, TestConnectionGroup<H>, H, TestConnectionContext<H>>>
        extends
        RemoteRequest<TestMessage, String, Exception, TestConnectionGroup<H>, H, TestConnectionContext<H>> {

    protected final List<String> results = new ArrayList<String>();

    public TestRemoteRequest(
            TestConnectionContext<H> context,
            long requestId,
            RemoteRequestFactory.CompletionCallback<TestMessage, String, Exception, TestConnectionGroup<H>, H, TestConnectionContext<H>> completionCallback) {
        super(context, requestId, completionCallback);
    }

    public void handleIncomingMessage(H sourceConnection, TestMessage message) {
        if (message.response != null) {
            getSuccessHandler().handleResult(message.response);
        } else if (message.exception != null) {
            getFailureHandler().handleError(new RuntimeException(message.exception));
        } else if (message.message != null) {
            results.add(message.message);
            handle(sourceConnection, this, message);
        }
    }

    public TestConnectionContext<H> getConnectionContext() {
        return super.getConnectionContext();
    }

    public void replyCallbackMessage(H sourceConnection, TestConnectionContext<H> remoteContext,
            long requestId) {
        TestMessage testMessage = new TestMessage();
        testMessage.message = "continue";
        sourceConnection.sendString(remoteContext.write(testMessage, requestId));
    }

    protected MessageElement createMessageElement(TestConnectionContext<H> remoteContext,
            long requestId) {
        TestMessage message = getTestMessage();
        return MessageElement.createStringMessage(remoteContext.write(message, requestId));
    }

    protected abstract TestMessage getTestMessage();

    protected abstract void handle(H sourceConnection, TestRemoteRequest<H> request,
            TestMessage message);

    protected void tryCancelRemote(TestConnectionContext<H> remoteContext, long requestId) {
        TestMessage message = new TestMessage();
        message.cancel = Boolean.TRUE;
        final String data = remoteContext.write(message, requestId);

        remoteContext.getRemoteConnectionGroup().trySendMessage(
                new Function<H, Boolean, Exception>() {
                    public Boolean apply(H value) throws Exception {
                        value.sendString(data).get();
                        return Boolean.TRUE;
                    }
                });
    }

    protected Exception createCancellationException(Throwable cancellationException) {
        if (cancellationException instanceof Exception)
            return (Exception) cancellationException;
        else if (null != cancellationException) {
            CancellationException exception =
                    new CancellationException(cancellationException.getMessage());
            exception.initCause(cancellationException);
            return exception;
        } else
            return new CancellationException("");
    }

    public List<String> getResults() {
        return results;
    }
}
