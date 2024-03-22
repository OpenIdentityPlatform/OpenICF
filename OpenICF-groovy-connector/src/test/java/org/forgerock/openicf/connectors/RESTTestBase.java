/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openicf.connectors;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

import org.forgerock.http.HttpApplication;
import org.forgerock.http.servlet.HttpFrameworkServlet;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.test.common.PropertyBag;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

/**
 * @author Laszlo Hordos
 */
public abstract class RESTTestBase {

    /**
     * Setup logging for the {@link RESTTestBase}.
     */
    private static final Log logger = Log.getLog(RESTTestBase.class);

    private ConnectorFacade facade = null;
    private Server server = null;

    private SecurityHandler getSecurityHandler() throws IOException {
        Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, "user");
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setPathSpec("/*");
        cm.setConstraint(constraint);

        ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
        sh.setAuthenticator(new BasicAuthenticator());
        sh.setConstraintMappings(Arrays.asList(new ConstraintMapping[]{cm}));

        HashLoginService loginService = new HashLoginService();
        UserStore us=new UserStore();
        Credential credential = Credential.getCredential("Passw0rd");
        String[] roles = new String[]{"user"};
        us.addUser("admin", credential, roles);
        loginService.setUserStore(us);
        loginService.setName("user");
        sh.setLoginService(loginService);
        sh.setConstraintMappings(Arrays.asList(new ConstraintMapping[]{cm}));

        return sh;
    }

    @BeforeSuite
    public void startServer() throws Exception {
        String httpPort = System.getProperty("jetty.http.port", "28080");
        System.out.append("Test port: ").println(httpPort);

        server = new Server(Integer.parseInt(httpPort));
        for (org.eclipse.jetty.server.Connector c : server.getConnectors()) {
            //c.setHost("127.0.0.1");
        }

        // Initializing the security handler
        ServletContextHandler handler =
                new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS
                        | ServletContextHandler.SECURITY);

        // Attach the CREST router
        HttpApplication app = new TestHttpApplication();
        HttpFrameworkServlet servlet = new HttpFrameworkServlet(app);
        ServletHolder holder = new ServletHolder(servlet);
        handler.addServlet(holder, "/test/*");

        // SECURITY HANDLER
        SecurityHandler sh = getSecurityHandler();
        sh.setHandler(handler);

        server.setHandler(sh);
        server.start();

        logger.info("Jetty Server Started: " + server.getState() + ", " + server.isRunning());
    }

    @AfterSuite
    public void stopServer() throws Exception {
        server.stop();
        logger.info("Jetty Server Stopped");
    }


    protected abstract ConnectorFacade getFacade();

    protected ConnectorFacade getFacade(Class<? extends Connector> clazz, String environment) {
        if (null == facade) {
            facade = createConnectorFacade(clazz, environment);
        }
        return facade;
    }

    @AfterClass
    public synchronized void afterClass() {
        if (facade instanceof LocalConnectorFacadeImpl) {
            ((LocalConnectorFacadeImpl) facade).dispose();
        }
        facade = null;
    }

    public static ConnectorFacade createConnectorFacade(Class<? extends Connector> clazz,
                                                        String environment) {
        PropertyBag propertyBag =
                TestHelpers.getProperties(ScriptedConnectorBase.class, environment);

        APIConfiguration impl =
                TestHelpers.createTestConfiguration(clazz, propertyBag, "configuration");
        impl.setProducerBufferSize(0);
        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableCaseInsensitiveFilter(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);

        impl.setTimeout(CreateApiOp.class, 25000);
        impl.setTimeout(UpdateApiOp.class, 25000);
        impl.setTimeout(DeleteApiOp.class, 25000);

        return ConnectorFacadeFactory.getInstance().newInstance(impl);
    }
}
