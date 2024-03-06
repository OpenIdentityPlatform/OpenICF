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

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.forgerock.openicf.common.rpc.LocalRequest;
import org.forgerock.openicf.common.rpc.RemoteConnectionGroup;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.forgerock.openicf.common.rpc.RemoteRequest;
import org.forgerock.util.Pair;

public class TestConnectionGroup<H extends RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>>>
        extends
        RemoteConnectionGroup<TestConnectionGroup<H>, H, TestConnectionContext<H>>
        implements Closeable {

    private final TestConnectionContext<H> connectionContext = new TestConnectionContext<H>(this);

    public TestConnectionGroup(String remoteSessionId) {
        super(remoteSessionId);
    }

    public TestConnectionContext<H> getRemoteConnectionContext() {
        return connectionContext;
    }

    public boolean isOperational() {
        return webSockets.size() > 0;
    }

    public void addConnection(H socket) {
        webSockets.add(Pair.of("name", socket));
    }

    public void removeConnection(H socket) {
        webSockets.remove(socket);
    }

    public Set<Long> getRemoteRequests() {
        return remoteRequests.keySet();
    }

    public Set<Long> getLocalRequests() {
        return localRequests.keySet();
    }

    public void close() throws IOException {
        for (Map.Entry<Long, RemoteRequest<?, ?, TestConnectionGroup<H>, H, TestConnectionContext<H>>> m : remoteRequests
                .entrySet()) {
            m.getValue().getPromise().cancel(true);
        }
        for (Map.Entry<Long, LocalRequest<?, ?, TestConnectionGroup<H>, H, TestConnectionContext<H>>> m : localRequests
                .entrySet()) {
            m.getValue().cancel();
        }
    }
}
