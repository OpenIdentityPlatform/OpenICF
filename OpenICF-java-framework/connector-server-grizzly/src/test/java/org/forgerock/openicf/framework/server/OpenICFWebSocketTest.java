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

package org.forgerock.openicf.framework.server;

import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.JSK_PASSWORD;
import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.KEY_HASH;
import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.TEST_CONNECTOR_KEY;
import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.buildRemoteWSFrameworkConnectionInfo;
import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.findFreePort;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.ClientRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteConnectionInfoManagerFactory;
import org.forgerock.openicf.framework.client.RemoteConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.local.AsyncLocalConnectorInfoManager;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.util.promise.Promise;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.identityconnectors.common.ConnectorKeyRange;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test
public class OpenICFWebSocketTest {

    public final int PLAIN_PORT = findFreePort();
    public final int SECURE_PORT = findFreePort();

    public final RemoteWSFrameworkConnectionInfo CONNECTION_INFO =
            buildRemoteWSFrameworkConnectionInfo(false, PLAIN_PORT, "/base");

    private ConnectorServer connectorServer = null;

    @BeforeTest
    public void startServer() throws Exception {

        connectorServer = new ConnectorServer() {
            protected String getContextPath() {
                return "/base";
            }
        };
        connectorServer.setConnectorFrameworkFactory(new ConnectorFrameworkFactory());
        connectorServer.setConnectorBundleURLs(Arrays.asList(TstConnector.class
                .getProtectionDomain().getCodeSource().getLocation()));

        connectorServer.init();
        connectorServer.addListener("grizzly", NetworkListener.DEFAULT_NETWORK_HOST, PLAIN_PORT);
        connectorServer.addListener("grizzly-secure", NetworkListener.DEFAULT_NETWORK_HOST,
                SECURE_PORT, createSSLContext(true));
        Reporter.log(String.format("Grizzly HTTP:%d, HTTPS:%d", PLAIN_PORT, SECURE_PORT), true);

        connectorServer.setKeyHash(KEY_HASH);
        connectorServer.start();

        Reporter.log("Grizzly Server Started", true);
    }

    @AfterTest
    public void stopServer() throws Exception {
        connectorServer.stop();
        connectorServer.destroy();
        Reporter.log("Grizzly Server Stopped", true);
    }

    static SSLContextConfigurator createSSLContext(boolean clientContext) {
        final SSLContextConfigurator sslContextConfigurator = new SSLContextConfigurator();

        URL keystoreURL =
                OpenICFWebSocketTest.class.getClassLoader().getResource(
                        clientContext ? "clientKeystore.jks" : "serverKeystore.jks");
        Assert.assertNotNull(keystoreURL);
        String serverKeystoreFile = null;
        try {
            serverKeystoreFile = URLDecoder.decode(keystoreURL.getFile(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        URL truststoreURL =
                OpenICFWebSocketTest.class.getClassLoader().getResource("truststore.jks");
        Assert.assertNotNull(truststoreURL);
        String truststoreFile = null;
        try {
            truststoreFile = URLDecoder.decode(truststoreURL.getFile(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        System.setProperty(SSLContextConfigurator.TRUST_STORE_FILE, truststoreFile);
        System.setProperty(SSLContextConfigurator.TRUST_STORE_PASSWORD, JSK_PASSWORD);

        sslContextConfigurator.setTrustStoreFile(truststoreFile);
        sslContextConfigurator.setTrustStorePass(JSK_PASSWORD);

        sslContextConfigurator.setKeyStoreFile(serverKeystoreFile);
        sslContextConfigurator.setKeyStorePass(JSK_PASSWORD);

        sslContextConfigurator.setSecurityProtocol("TLS");

        return sslContextConfigurator;
    }

    @Test
    public void testOnMessageReceived() throws Exception {

        ConnectorFrameworkFactory frameworkFactory = new ConnectorFrameworkFactory();
        ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework =
                frameworkFactory.acquire();
        try {
            AsyncLocalConnectorInfoManager manager = connectorFramework.get().getLocalManager();

            Promise<ConnectorInfo, RuntimeException> keyPromise =
                    manager.findConnectorInfoAsync(TEST_CONNECTOR_KEY);
            Promise<ConnectorInfo, RuntimeException> keyRangePromise =
                    manager.findConnectorInfoAsync(ConnectorKeyRange.newBuilder().setBundleName(
                            TEST_CONNECTOR_KEY.getBundleName()).setConnectorName(
                            TEST_CONNECTOR_KEY.getConnectorName()).setBundleVersion("[1.0,2.0)")
                            .build());

            manager.addConnectorBundle(TstConnector.class.getProtectionDomain().getCodeSource()
                    .getLocation());

            Assert.assertEquals(manager.getConnectorInfos().size(), 3);
            Assert.assertTrue(keyPromise.isDone());
            Assert.assertTrue(keyRangePromise.isDone());

        } finally {
            connectorFramework.release();
        }
    }

    @Test
    public void testHandshake() throws Exception {

        ReferenceCountedObject<ConnectorFramework>.Reference framework =
                new ConnectorFrameworkFactory().acquire();
        try {

            RemoteConnectionInfoManagerFactory connectionManager =
                    framework.get().getRemoteConnectionInfoManagerFactory();

            RemoteConnectorInfoManager manager = connectionManager.connect(CONNECTION_INFO);

            int i = 5;
            while (i > 0 && !manager.getRequestDistributor().isOperational()) {
                Thread.sleep(5000);
                i--;
            }

            Assert.assertTrue(manager.getRequestDistributor().isOperational());

        } finally {
            framework.release();
        }
    }

    @Test
    public void testRemoteConnectorInfoManager() throws Exception {
        ReferenceCountedObject<ConnectorFramework>.Reference framework =
                new ConnectorFrameworkFactory().acquire();
        try {

            AsyncRemoteConnectorInfoManager manager =
                    framework.get().getRemoteManager(CONNECTION_INFO);
            Assert.assertNotNull(manager);

            ConnectorInfo c = null;
            for (int i = 0; (c =
                    manager.findConnectorInfoAsync(TEST_CONNECTOR_KEY).getOrThrowUninterruptibly(5,
                            TimeUnit.MINUTES)) == null
                    && i < 5; i++) {
                // Wait until the connection is established and the connector
                // info are transferred.
                Thread.sleep(20000);
            }
            Assert.assertNotNull(c);
            for (ConnectorInfo ci : manager.getConnectorInfos()) {
                Reporter.log(String.valueOf(ci.getConnectorKey()), true);
            }
        } finally {
            framework.release();
        }
    }

    @Test(expectedExceptions = ExecutionException.class,
            expectedExceptionsMessageRegExp = ".*Response code was not 101: 403 Failed To connect")
    public void testConnectionException() throws Exception {
        ReferenceCountedObject<ConnectorFramework>.Reference framework =
                new ConnectorFrameworkFactory().acquire();
        try {
            RemoteConnectionInfoManagerFactory manager =
                    framework.get().getRemoteConnectionInfoManagerFactory();

            RemoteWSFrameworkConnectionInfo unauthenticated =
                    RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                            URI.create("ws://127.0.0.1:" + PLAIN_PORT + "/base/openicf"))
                            .setPrincipal("unknown").setPassword(
                                    new GuardedString("FAKE".toCharArray())).build();

            ClientRemoteConnectorInfoManager connection =
                    (ClientRemoteConnectorInfoManager) manager.connect(unauthenticated);

            Assert.assertFalse(connection.getRequestDistributor().isOperational());

            connection.connect().get(1, TimeUnit.MINUTES);

        } finally {
            framework.release();
        }
    }

}
