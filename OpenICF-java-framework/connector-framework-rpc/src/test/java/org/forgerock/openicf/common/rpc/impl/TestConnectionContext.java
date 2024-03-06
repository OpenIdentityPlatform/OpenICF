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

import java.io.IOException;

import org.forgerock.openicf.common.rpc.RemoteConnectionContext;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestConnectionContext<H extends RemoteConnectionHolder<TestConnectionGroup<H>, H, TestConnectionContext<H>>>
        implements
        RemoteConnectionContext<TestConnectionGroup<H>, H, TestConnectionContext<H>> {

    private final TestConnectionGroup<H> connectionGroup;
    private final ObjectMapper m = new ObjectMapper();

    public TestConnectionContext(final TestConnectionGroup<H> connectionGroup) {
        this.connectionGroup = connectionGroup;
    }

    public TestConnectionGroup<H> getRemoteConnectionGroup() {
        return connectionGroup;
    }

    public String write(TestMessage message, long requestId) {
        message.messageId = requestId;
        try {
            return m.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public TestMessage read(String message) {
        try {
            return m.readValue(message, TestMessage.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
