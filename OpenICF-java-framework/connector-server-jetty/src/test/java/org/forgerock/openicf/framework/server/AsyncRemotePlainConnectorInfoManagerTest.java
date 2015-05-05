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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.servlet.BaseHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.AsyncRemoteConnectorInfoManager;
import org.forgerock.openicf.framework.server.jetty.OpenICFWebSocketServletBase;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.Test;

@Test
public class AsyncRemotePlainConnectorInfoManagerTest extends
        AsyncConnectorInfoManagerTestBase<AsyncRemoteConnectorInfoManager> {

    public final int PLAIN_PORT = findFreePort();
    public final int SECURE_PORT = findFreePort();
    public final int MUTUAL_SECURE_PORT = findFreePort();

    public final RemoteWSFrameworkConnectionInfo CONNECTION_INFO =
            buildRemoteWSFrameworkConnectionInfo(false, PLAIN_PORT);
    public final RemoteWSFrameworkConnectionInfo SECURE_CONNECTION_INFO =
            buildRemoteWSFrameworkConnectionInfo(true, SECURE_PORT);

    public static final RemoteWSFrameworkConnectionInfo SERVER_CONNECTION_INFO =
            RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                    URI.create("ws://127.0.0.1:8759/openicf")).setPrincipal("Something")
                    .setPassword(DEFAULT_GUARDED_PASSWORD).build();

    public static RemoteWSFrameworkConnectionInfo buildRemoteWSFrameworkConnectionInfo(
            boolean isSecure, int port) {
        return RemoteWSFrameworkConnectionInfo.newBuilder().setRemoteURI(
                URI.create((isSecure ? "wss" : "ws") + "://127.0.0.1:" + port + "/openicf"))
                .setPrincipal("anonymous").setPassword(DEFAULT_GUARDED_PASSWORD).build();
    }

    private final ConnectorFrameworkFactory localConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private final ConnectorFrameworkFactory serverConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private Server connectorServer = null;

    protected ConnectorFrameworkFactory getConnectorFrameworkFactory() throws Exception {
        return localConnectorFrameworkFactory;
    }

    private SecurityHandler getSecurityHandler() throws IOException {
        Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, "websocket");
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setPathSpec("/openicf/*");
        cm.setConstraint(constraint);

        ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
        sh.setAuthenticator(new BasicAuthenticator());
        sh.setConstraintMappings(Arrays.asList(new ConstraintMapping[] { cm }));

        MappedLoginService loginService = new MappedLoginService() {

            @Override
            protected UserIdentity loadUser(String username) {
                return null;
            }

            @Override
            protected void loadUsers() throws IOException {
                Credential credential = Credential.getCredential(DEFAULT_PASSWORD);
                String[] roles = new String[] { "websocket" };
                putUser("plain", credential, roles);
                putUser("secure", credential, roles);
                putUser("proxy", credential, roles);
                putUser("anonymous", credential, roles);
            }
        };
        loginService.setName("OpenICF-Service");
        sh.setLoginService(loginService);
        sh.setConstraintMappings(Arrays.asList(new ConstraintMapping[] { cm }));

        return sh;
    }

    protected void setupTest(ITestContext context) throws Exception {
        Reporter.log(String.format("HTTP:%d, HTTPS:%d , HTTPS(Mutual):%d", PLAIN_PORT, SECURE_PORT,
                MUTUAL_SECURE_PORT), true);

        connectorServer = new Server();

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(SECURE_PORT);
        httpConfig.setOutputBufferSize(32768);

        // HTTP
        ServerConnector http =
                new ServerConnector(connectorServer, new HttpConnectionFactory(httpConfig));
        http.setPort(PLAIN_PORT);
        http.setHost("127.0.0.1");
        http.setIdleTimeout(30000);

        // HTTPS
        SslContextFactory sslContextFactory = createSsllContextFactory(false);

        // HTTPS Configuration
        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        // HTTPS connector
        ServerConnector https =
                new ServerConnector(connectorServer, new SslConnectionFactory(sslContextFactory,
                        HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(httpsConfig));
        https.setPort(SECURE_PORT);
        http.setHost("127.0.0.1");
        https.setIdleTimeout(500000);

        // Mutual HTTPS connector
        sslContextFactory = createSsllContextFactory(false);
        sslContextFactory.setWantClientAuth(true);
        sslContextFactory.setNeedClientAuth(false);

        ServerConnector mutualHttps =
                new ServerConnector(connectorServer, new SslConnectionFactory(sslContextFactory,
                        HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(httpsConfig));
        mutualHttps.setPort(MUTUAL_SECURE_PORT);
        http.setHost("127.0.0.1");
        mutualHttps.setIdleTimeout(500000);

        connectorServer.setConnectors(new Connector[] { http, https, mutualHttps });

        // Initializing the security handler
        ServletContextHandler handler =
                new ServletContextHandler(connectorServer, "/", ServletContextHandler.SESSIONS
                        | ServletContextHandler.SECURITY);

        ServletHolder holder =
                handler.getServletHandler().newServletHolder(BaseHolder.Source.EMBEDDED);

        holder.setServlet(new OpenICFWebSocketServletBase(serverConnectorFrameworkFactory));
        holder.setInitParameter("maxIdleTime", "300000");
        holder.setInitParameter("maxAsyncWriteTimeout", "60000");

        holder.setInitParameter("maxBinaryMessageSize", "32768");
        holder.setInitParameter("inputBufferSize", "4096");

        handler.addServlet(holder, "/openicf/*");

        SecurityHandler sh = getSecurityHandler();
        sh.setHandler(handler);

        connectorServer.setHandler(sh);
        connectorServer.start();
        Reporter.log("Jetty Server Started", true);

        // Initialise the ConnectorFramework

        serverConnectorFrameworkFactory.acquire().get().getLocalManager().addConnectorBundle(
                TstConnector.class.getProtectionDomain().getCodeSource().getLocation());

        connectorServer.start();
    }

    protected void shutdownTest(ITestContext context) throws Exception {
        connectorServer.stop();
        connectorServer.destroy();
        Reporter.log("Jetty Server Stopped", true);
    }

    protected AsyncRemoteConnectorInfoManager getConnectorInfoManager() throws Exception {
        return getConnectorFramework().getRemoteManager(CONNECTION_INFO);
    }

    private SslContextFactory createSsllContextFactory(boolean clientContext) {
        final SslContextFactory sslContextFactory = new SslContextFactory(false);

        URL keystoreURL =
                AsyncRemotePlainConnectorInfoManagerTest.class.getClassLoader().getResource(
                        clientContext ? "clientKeystore.jks" : "serverKeystore.jks");
        Assert.assertNotNull(keystoreURL);
        String serverKeystoreFile = null;
        try {
            serverKeystoreFile = URLDecoder.decode(keystoreURL.getFile(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        URL truststoreURL =
                AsyncRemotePlainConnectorInfoManagerTest.class.getClassLoader().getResource(
                        "truststore.jks");
        Assert.assertNotNull(truststoreURL);
        String truststoreFile = null;
        try {
            truststoreFile = URLDecoder.decode(truststoreURL.getFile(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        sslContextFactory.setTrustStorePath(truststoreFile);
        sslContextFactory.setTrustStorePassword(JSK_PASSWORD);

        sslContextFactory.setKeyStorePath(serverKeystoreFile);
        sslContextFactory.setKeyStorePassword(JSK_PASSWORD);

        sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.1", "TLSv1");

        return sslContextFactory;
    }
}
