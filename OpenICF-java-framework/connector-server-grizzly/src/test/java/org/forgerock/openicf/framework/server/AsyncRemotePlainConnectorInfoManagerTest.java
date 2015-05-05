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

import static org.forgerock.openicf.framework.server.OpenICFWebSocketTest.createSSLContext;

import java.util.Arrays;

import org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.Test;

@Test
public class AsyncRemotePlainConnectorInfoManagerTest extends
        AsyncConnectorInfoManagerTestBase<AsyncRemoteConnectorInfoManager> {

    public RemoteWSFrameworkConnectionInfo CONNECTION_INFO = null;
    public RemoteWSFrameworkConnectionInfo SECURE_CONNECTION_INFO = null;
    public static final RemoteWSFrameworkConnectionInfo SERVER_CONNECTION_INFO =
            buildRemoteWSFrameworkConnectionInfo(false, 8759, null);

    private final ConnectorFrameworkFactory localConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private final ConnectorFrameworkFactory serverConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private ConnectorServer connectorServer = null;

    protected ConnectorFrameworkFactory getConnectorFrameworkFactory() throws Exception {
        return localConnectorFrameworkFactory;
    }

    protected void setupTest(ITestContext context) throws Exception {
        connectorServer = new ConnectorServer();
        connectorServer.setConnectorFrameworkFactory(serverConnectorFrameworkFactory);
        connectorServer.setConnectorBundleURLs(Arrays.asList(TstConnector.class
                .getProtectionDomain().getCodeSource().getLocation()));

        int plainPort = findFreePort();
        int securePort = findFreePort();
        context.setAttribute("plainPort", plainPort);
        context.setAttribute("securePort", securePort);

        connectorServer
                .addListener("grizzly-test", NetworkListener.DEFAULT_NETWORK_HOST, plainPort);
        connectorServer.addListener("grizzly-secure-test", NetworkListener.DEFAULT_NETWORK_HOST,
                securePort, createSSLContext(false));

        connectorServer.setKeyHash(KEY_HASH);
        connectorServer.init();
        connectorServer.start();
        Reporter.log("Grizzly Server Started", true);
        Reporter.log(context.getName() + ": Ready to test", true);

    }

    protected void shutdownTest(ITestContext context) throws Exception {
        connectorServer.stop();
        connectorServer.destroy();
        Reporter.log("Grizzly Server Stopped", true);
    }

    protected void setupClass(ITestContext context) throws Exception {
        Integer plainPort = (Integer) context.getAttribute("plainPort");
        Integer securePort = (Integer) context.getAttribute("securePort");
        CONNECTION_INFO = buildRemoteWSFrameworkConnectionInfo(false, plainPort, null);
        SECURE_CONNECTION_INFO = buildRemoteWSFrameworkConnectionInfo(true, securePort, null);
    }

    protected AsyncRemoteConnectorInfoManager getConnectorInfoManager() throws Exception {
        return getConnectorFramework().getRemoteManager(CONNECTION_INFO);
    }
}
