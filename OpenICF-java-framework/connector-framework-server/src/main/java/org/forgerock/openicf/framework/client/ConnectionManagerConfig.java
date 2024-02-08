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

package org.forgerock.openicf.framework.client;

import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;

import org.identityconnectors.common.security.GuardedString;

public class ConnectionManagerConfig {

    // SSL Config

    private String trustStoreProvider;
    private String keyStoreProvider;

    private String trustStoreType;
    private String keyStoreType;

    private GuardedString trustStorePass;
    private GuardedString keyStorePass;
    private GuardedString keyPass;

    private String trustStoreFile;
    private String keyStoreFile;

    private byte[] trustStoreBytes;
    private byte[] keyStoreBytes;

    private String trustManagerFactoryAlgorithm;
    private String keyManagerFactoryAlgorithm;

    protected int maxConnectionPerHost;

    protected int connectionTimeOutInMs;
    protected int webSocketIdleTimeoutInMs = 15 * 60 * 1000;
    protected int idleConnectionTimeoutInMs = 15 * 60 * 1000;

    protected int idleConnectionInPoolTimeoutInMs;

    protected int requestTimeoutInMs = 60 * 1000;

    protected ExecutorService applicationThreadPool;
    protected SSLContext sslContext;

    protected int ioWorkerThreadMultiplier = 2;
    protected int ioSelectorThreadCount = 2;
    protected boolean useRelativeURIsWithConnectProxies;

    protected int maxConnectionLifeTimeInMs;

    public int getScheduledThreadPoolSize() {
        return 5;
    }

    private int heartbeatInterval = 60; // seconds

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Return the maximum time, in milliseconds, a
     * {@link org.glassfish.grizzly.websockets.WebSocket} may be idle before
     * being timed out.
     *
     * @return the maximum time, in milliseconds, a
     *         {@link org.glassfish.grizzly.websockets.WebSocket} may be idle
     *         before being timed out.
     */
    public int getWebSocketIdleTimeoutInMs() {
        return webSocketIdleTimeoutInMs;
    }

    /**
     * Return the maximum time in millisecond an
     * {@link ClientRemoteConnectorInfoManager} wait for a response
     *
     * @return the maximum time in millisecond an
     *         {@link ClientRemoteConnectorInfoManager} wait for a response
     */
    public int getRequestTimeoutInMs() {
        return requestTimeoutInMs;
    }

    /**
     * @return number to multiply by availableProcessors() that will determine #
     *         of NioWorkers to use
     */
    public int getIoWorkerThreadMultiplier() {
        return ioWorkerThreadMultiplier;
    }

    public int getIoSelectorThreadCount() {
        return ioSelectorThreadCount;
    }


    public String getTrustStoreProvider() {
        return trustStoreProvider;
    }

    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public GuardedString getTrustStorePass() {
        return trustStorePass;
    }

    public GuardedString getKeyStorePass() {
        return keyStorePass;
    }

    public GuardedString getKeyPass() {
        return keyPass;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public byte[] getTrustStoreBytes() {
        return trustStoreBytes;
    }

    public byte[] getKeyStoreBytes() {
        return keyStoreBytes;
    }

    public String getTrustManagerFactoryAlgorithm() {
        return trustManagerFactoryAlgorithm;
    }

    public String getKeyManagerFactoryAlgorithm() {
        return keyManagerFactoryAlgorithm;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

    }
}
