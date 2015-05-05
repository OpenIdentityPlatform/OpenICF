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

import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.testng.ITestContext;
import org.testng.annotations.Test;

@Test
public class AsyncRemoteProxyConnectorInfoManagerTest extends
        AsyncRemotePlainConnectorInfoManagerTest {

    private int PROXY_PORT = findFreePort();

    private RemoteWSFrameworkConnectionInfo PROXY_CONNECTION_INFO = null;

    HttpProxyServer proxyServer;

    protected void setupClass(final ITestContext context) throws Exception {
        super.setupClass(context);
        proxyServer =
                DefaultHttpProxyServer.bootstrap().withPort(PROXY_PORT).withAllowLocalOnly(true)
                        .start();
        Integer plainPort = (Integer) context.getAttribute("plainPort");
        PROXY_CONNECTION_INFO =
                buildRemoteProxyFrameworkConnectionInfo(false, plainPort, PROXY_PORT, null);
    }

    protected void shutdownClass(ITestContext context) throws Exception {
        super.shutdownClass(context);
        proxyServer.stop();
    }

    protected AsyncRemoteConnectorInfoManager getConnectorInfoManager() throws Exception {
        return getConnectorFramework().getRemoteManager(PROXY_CONNECTION_INFO);
    }
}
