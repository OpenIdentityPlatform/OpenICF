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
 * Copyright 2015-2016 ForgeRock AS.
 * Portions copyright 2025 3A Systems LLC.
 */

package org.forgerock.openicf.framework.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.*;
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
import org.eclipse.jetty.servlet.Source;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.forgerock.openicf.framework.AsyncConnectorInfoManagerTestBase;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.DelegatingAsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.server.jetty.OpenICFWebSocketServletBase;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.testconnector.TstConnector;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.Test;

@Test
public class AsyncRemotePlainConnectorInfoManagerTest extends
        AsyncConnectorInfoManagerTestBase<DelegatingAsyncConnectorInfoManager> {

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

    protected static final ConnectorFrameworkFactory serverConnectorFrameworkFactory =
            new ConnectorFrameworkFactory();

    private Server connectorServer = null;
    protected static ReferenceCountedObject<ConnectorFramework>.Reference localConnectorFramework = null;
    private static ReferenceCountedObject<ConnectorFramework>.Reference serverConnectorFramework = null;

    protected ConnectorFrameworkFactory getConnectorFrameworkFactory() throws Exception {
        return localConnectorFrameworkFactory;
    }

    protected DelegatingAsyncConnectorInfoManager getConnectorInfoManager() throws Exception {
        return getConnectorFramework().getRemoteManager(CONNECTION_INFO);
    }

    private SecurityHandler getSecurityHandler() throws IOException {
        Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, "websocket");
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setPathSpec("/openicf/*");
        cm.setConstraint(constraint);

        ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
        sh.setAuthenticator(new BasicAuthenticator());
        sh.setConstraintMappings(List.of(cm));

        HashLoginService loginService = new HashLoginService();
        UserStore us=new UserStore();
        String[] roles = new String[] { "websocket" };
        Credential credential = Credential.getCredential(DEFAULT_PASSWORD);
        us.addUser("plain", credential, roles);
        us.addUser("secure", credential, roles);
        us.addUser("proxy", credential, roles);
        us.addUser("anonymous", credential, roles);
        loginService.setUserStore(us);
        loginService.setName("OpenICF-Service");
        sh.setLoginService(loginService);
        sh.setConstraintMappings(List.of(cm));

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
        SslContextFactory.Server sslContextFactory = createSsllContextFactory(false);

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
                handler.getServletHandler().newServletHolder(Source.EMBEDDED);

        serverConnectorFramework = serverConnectorFrameworkFactory.acquire();
        localConnectorFramework = localConnectorFrameworkFactory.acquire();
        holder.setServlet(new OpenICFWebSocketServletBase(serverConnectorFrameworkFactory));
        holder.setInitParameter("maxIdleTime", "300000");
        holder.setInitParameter("maxAsyncWriteTimeout", "60000");

        holder.setInitParameter("maxBinaryMessageSize", "32768");
        holder.setInitParameter("inputBufferSize", "4096");

        handler.addServlet(holder, "/openicf/*");

        JettyWebSocketServletContainerInitializer.configure(handler, null);

        SecurityHandler sh = getSecurityHandler();
        sh.setHandler(handler);

        connectorServer.setHandler(sh);
        connectorServer.start();
        Reporter.log("Jetty Server Started", true);

        // Initialise the ConnectorFramework

        serverConnectorFramework.get().getLocalManager().addConnectorBundle(
                TstConnector.class.getProtectionDomain().getCodeSource().getLocation());

        localConnectorFramework.get().getLocalManager().addConnectorBundle(
                TstConnector.class.getProtectionDomain().getCodeSource().getLocation());

        connectorServer.start();
    }

    protected void shutdownTest(ITestContext context) throws Exception {
        connectorServer.stop();
        connectorServer.destroy();

        serverConnectorFramework.release();
        localConnectorFramework.release();
        Reporter.log("Jetty Server Stopped", true);
    }

    private SslContextFactory.Server createSsllContextFactory(boolean clientContext) {
        final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

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
        truststoreFile = truststoreURL.getFile();//URLDecoder.decode(truststoreURL.getFile(), "UTF-8");

        sslContextFactory.setTrustStorePath("file://"+truststoreFile);
        sslContextFactory.setTrustStorePassword(JSK_PASSWORD);

        sslContextFactory.setKeyStorePath("file://"+serverKeystoreFile);
        sslContextFactory.setKeyStorePassword(JSK_PASSWORD);

        sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.1", "TLSv1");

        return sslContextFactory;
    }

    @Test
    public void testRequiredServerConnectorInfo() throws Exception {
        AsyncConnectorInfoManager manager = localConnectorFramework.get()
                .getRemoteManager(CONNECTION_INFO);
        Assert.assertNotNull(manager);

        manager = serverConnectorFramework.get().getServerManager("anonymous");
        Assert.assertNotNull(manager);

        ConnectorInfo c =
                manager.findConnectorInfoAsync(getTestConnectorKey()).getOrThrowUninterruptibly(5,
                        TimeUnit.MINUTES);
        Assert.assertNotNull(c);

        Assert.assertNotNull(manager.findConnectorInfoAsync(
                getTestStatefulConnectorKey()).getOrThrowUninterruptibly(30, TimeUnit.SECONDS));

        Assert.assertNotNull(manager.findConnectorInfoAsync(
                getTestPoolableStatefulConnectorKey()).getOrThrowUninterruptibly(30,
                TimeUnit.SECONDS));

        for (ConnectorInfo ci : manager.getConnectorInfos()) {
            Reporter.log(String.valueOf(ci.getConnectorKey()), true);
        }
    }
}
