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

import java.util.concurrent.ExecutionException;

import org.forgerock.openicf.common.rpc.LocalRequest;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.forgerock.util.promise.Function;
import org.testng.Reporter;

public class TestLocalRequest<H extends RemoteConnectionHolder<TestMessage, TestConnectionGroup<H>, H, TestConnectionContext<H>>>
        extends
        LocalRequest<TestMessage, String, Exception, TestConnectionGroup<H>, H, TestConnectionContext<H>> {

    private H connection = null;
    private boolean cancelled = false;

    public TestLocalRequest(long requestId, H socket) {
        super(requestId, socket);
    }

    public boolean tryHandleResult(String result) {
        TestMessage remoteMessage = new TestMessage();
        remoteMessage.response = result;
        final String message = getRemoteConnectionContext().write(remoteMessage, getRequestId());

        return getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                new Function<H, Boolean, Exception>() {
                    public Boolean apply(H value) throws Exception {
                        value.sendString(message).get();
                        return Boolean.TRUE;
                    }
                });

    }

    public void callbackMessage(String result) {
        TestMessage remoteMessage = new TestMessage();
        remoteMessage.message = result;
        final String message = getRemoteConnectionContext().write(remoteMessage, getRequestId());

        if (null != connection) {
            try {
                connection.sendString(message).get();
            } catch (InterruptedException e) {
                handleError(e);
            } catch (ExecutionException e) {
                handleError(e);
            }
        } else {
            connection =
                    getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                            new Function<H, H, Exception>() {
                                public H apply(H value) throws Exception {
                                    value.sendString(message).get();
                                    return value;
                                }
                            });
            if (null == connection) {
                handleError(new RuntimeException("Failed to send response message"));
            }
        }
    }

    public boolean tryHandleError(Exception error) {
        TestMessage remoteMessage = new TestMessage();
        remoteMessage.exception = error.getMessage();
        final String message = getRemoteConnectionContext().write(remoteMessage, getRequestId());

        return Boolean.TRUE.equals(getRemoteConnectionContext().getRemoteConnectionGroup()
                .trySendMessage(new Function<H, Boolean, Exception>() {
                    public Boolean apply(H value) throws Exception {
                        value.sendString(message).get();
                        return Boolean.TRUE;
                    }
                }));
    }

    public boolean tryCancel() {
        cancelled = true;
        return true;
    }

    public void execute(int operation) throws InterruptedException {
        switch (operation) {
        case 0: {
            handleResult("OK");
            break;
        }
        case 1: {
            callbackMessage("Result0");
            callbackMessage("Result2");
            callbackMessage("Result3");
            handleResult("OK");
            break;
        }
        case 2: {
            synchronized (this) {
                callbackMessage("Result0");
                this.wait(30000, 0);
            }
            if (cancelled) {
                return;
            }
            synchronized (this) {
                callbackMessage("Result2");
                this.wait(30000, 0);
            }
            if (cancelled) {
                return;
            }
            synchronized (this) {
                callbackMessage("Result3");
                this.wait(30000, 0);
            }
            if (cancelled) {
                return;
            }
            handleResult("OK");
            break;
        }
        default:
            handleError(new RuntimeException("Unknown Test case number"));
        }
    }

    public void handleIncomingMessage(H sourceConnection, TestMessage message) {
        if (Boolean.TRUE.equals(message.cancel)) {
            cancel();
        }
        synchronized (this) {
            notifyAll();
        }
    }
}
