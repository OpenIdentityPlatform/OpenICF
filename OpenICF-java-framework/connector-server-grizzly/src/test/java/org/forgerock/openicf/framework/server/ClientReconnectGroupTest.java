/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.framework.server;

import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.KEY_HASH;
import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.buildRemoteWSFrameworkConnectionInfo;
import static org.forgerock.openicf.framework.server.AsyncRemotePlainConnectorInfoManagerTest.findFreePort;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.openicf.common.rpc.RemoteConnectionGroup;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.ClientRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Pair;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.websockets.WebSocket;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * The client-side WebSocketConnectionGroup learns about a departed connection
 * only through the holder's close listeners
 * (OpenIdentityPlatform/OpenICF#118): before the fix ICFWebSocket.onClose in
 * ClientRemoteConnectorInfoManager never called adapter.close(), so every
 * reconnect of the same session left a stale duplicate holder in the group.
 */
public class ClientReconnectGroupTest {

    public final int PLAIN_PORT = findFreePort();

    public final RemoteWSFrameworkConnectionInfo CONNECTION_INFO =
            buildRemoteWSFrameworkConnectionInfo(false, PLAIN_PORT, "/reconnect");

    private ConnectorServer connectorServer = null;

    @BeforeTest
    public void startServer() throws Exception {
        connectorServer = new ConnectorServer() {
            protected String getContextPath() {
                return "/reconnect";
            }
        };
        connectorServer.setConnectorFrameworkFactory(new ConnectorFrameworkFactory());
        connectorServer.setConnectorBundleURLs(Arrays.asList(TstConnector.class
                .getProtectionDomain().getCodeSource().getLocation()));

        connectorServer.init();
        connectorServer.addListener("grizzly", NetworkListener.DEFAULT_NETWORK_HOST, PLAIN_PORT);
        connectorServer.setKeyHash(KEY_HASH);
        connectorServer.start();

        Reporter.log("Grizzly Server Started on port " + PLAIN_PORT, true);
    }

    @AfterTest
    public void stopServer() throws Exception {
        connectorServer.stop();
        connectorServer.destroy();
        Reporter.log("Grizzly Server Stopped", true);
    }

    @Test(timeOut = 180000)
    public void testGroupDropsHolderOnTransportClose() throws Exception {
        ReferenceCountedObject<ConnectorFramework>.Reference framework =
                new ConnectorFrameworkFactory().acquire();
        try {
            ClientRemoteConnectorInfoManager manager =
                    (ClientRemoteConnectorInfoManager) framework.get()
                            .getRemoteConnectionInfoManagerFactory().connect(CONNECTION_INFO);

            final ConcurrentMap<String, WebSocketConnectionGroup> groups =
                    connectionGroupsOf(manager);
            final int permitted = CONNECTION_INFO.getExpectedConnectionCount();

            for (int cycle = 1; cycle <= 2; cycle++) {
                await("all permitted connections must be up (cycle " + cycle + ")",
                        new Callable<Boolean>() {
                            public Boolean call() throws Exception {
                                return allHolders(groups).size() == permitted
                                        && operationalHolders(groups).size() == permitted;
                            }
                        });

                final WebSocketConnectionHolder victim = operationalHolders(groups).get(0);
                socketOf(victim).close();

                // The regression: without adapter.close() in
                // ICFWebSocket.onClose the victim stays in the group forever
                // and the holder count grows on every reconnect.
                await("the closed holder must leave the group after reconnect (cycle "
                        + cycle + ")",
                        new Callable<Boolean>() {
                            public Boolean call() throws Exception {
                                return !allHolders(groups).contains(victim)
                                        && allHolders(groups).size() == permitted
                                        && operationalHolders(groups).size() == permitted;
                            }
                        });
            }
        } finally {
            framework.release();
        }
    }

    private static void await(String message, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 60000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(100);
        }
        Assert.fail(message);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, WebSocketConnectionGroup> connectionGroupsOf(
            ClientRemoteConnectorInfoManager manager) throws Exception {
        Field field = ConnectionPrincipal.class.getDeclaredField("connectionGroups");
        field.setAccessible(true);
        return (ConcurrentMap<String, WebSocketConnectionGroup>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static List<WebSocketConnectionHolder> allHolders(
            ConcurrentMap<String, WebSocketConnectionGroup> groups) throws Exception {
        Field field = RemoteConnectionGroup.class.getDeclaredField("webSockets");
        field.setAccessible(true);
        List<WebSocketConnectionHolder> result = new ArrayList<WebSocketConnectionHolder>();
        for (WebSocketConnectionGroup group : groups.values()) {
            for (Pair<String, WebSocketConnectionHolder> pair :
                    (List<Pair<String, WebSocketConnectionHolder>>) field.get(group)) {
                result.add(pair.getSecond());
            }
        }
        return result;
    }

    private static List<WebSocketConnectionHolder> operationalHolders(
            ConcurrentMap<String, WebSocketConnectionGroup> groups) throws Exception {
        List<WebSocketConnectionHolder> result = new ArrayList<WebSocketConnectionHolder>();
        for (WebSocketConnectionHolder holder : allHolders(groups)) {
            if (holder.isOperational()) {
                result.add(holder);
            }
        }
        return result;
    }

    /**
     * The holder is an anonymous inner class of the private ICFWebSocket; its
     * synthetic outer-instance field is the only WebSocket-typed handle a
     * test can reach to close the connection at the transport level.
     */
    private static WebSocket socketOf(WebSocketConnectionHolder holder) throws Exception {
        for (Field field : holder.getClass().getDeclaredFields()) {
            if (WebSocket.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (WebSocket) field.get(holder);
            }
        }
        throw new AssertionError("No WebSocket handle on " + holder.getClass());
    }
}
