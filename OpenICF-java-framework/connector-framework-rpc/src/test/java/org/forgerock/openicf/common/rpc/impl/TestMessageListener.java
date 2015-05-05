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

import org.forgerock.openicf.common.rpc.MessageListener;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;

public abstract class TestMessageListener<H extends RemoteConnectionHolder<TestMessage, TestConnectionGroup<H>, H, TestConnectionContext<H>>>
        implements
        MessageListener<TestMessage, TestConnectionGroup<H>, H, TestConnectionContext<H>> {

    private final TestConnectionGroup<H> connectionGroup;

    public TestMessageListener(TestConnectionGroup<H> connectionGroup) {
        this.connectionGroup = connectionGroup;
    }

    public void onConnect(H socket) {
        connectionGroup.addConnection(socket);
    }

    public void onClose(H socket, int code, String reason) {
        connectionGroup.removeConnection(socket);
    }

    protected TestConnectionGroup<H> getConnectionGroup() {
        return connectionGroup;
    }
}
