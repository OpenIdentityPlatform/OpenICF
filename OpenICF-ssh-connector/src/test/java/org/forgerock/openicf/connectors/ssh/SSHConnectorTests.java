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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openicf.connectors.ssh;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.test.common.PropertyBag;
import org.identityconnectors.test.common.TestHelpers;
import org.identityconnectors.test.common.ToListResultsHandler;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Attempts to test the {@link SSHConnector} with the framework.
 */
public class SSHConnectorTests {

    /**
     * Setup logging for the {@link SSHConnectorTests}.
     */
    private static final Log logger = Log.getLog(SSHConnectorTests.class);

    private ConnectorFacade connectorFacade = null;

    @BeforeClass
    public void setUp() {
        //
        // other setup work to do before running tests
        //

        // Configuration config = new SSHConfiguration();
        // Map<String, ? extends Object> configData = (Map<String, ? extends
        // Object>) PROPERTIES.getProperty("configuration",Map.class)
        // TestHelpers.fillConfiguration(
    }

    @AfterClass
    public void tearDown() {
        //
        // clean up resources
        //
        if (connectorFacade instanceof LocalConnectorFacadeImpl) {
            ((LocalConnectorFacadeImpl) connectorFacade).dispose();
        }
    }

    /////////////////////  TEST

    @Test(enabled = false)
    public void testTest() {
        logger.info("Running academic Test");
        final ConnectorFacade facade = getFacade("test");
        facade.test();
    }

    @Test(enabled = false)
    public void testTestMac() {
        logger.info("Running Test Test Mac");
        final ConnectorFacade facade = getFacade("mac");
        facade.test();
    }


    ////////////////////  Test Utils

    public ConnectorFacade getFacade(String environment) {
        if (null == connectorFacade) {
            synchronized (this) {
                if (null == connectorFacade) {
                    connectorFacade = createConnectorFacade(environment);
                }
            }
        }
        return connectorFacade;
    }

    public static ConnectorFacade createConnectorFacade(String environment) {
        PropertyBag propertyBag =
                TestHelpers.getProperties(SSHConnector.class, environment);
        APIConfiguration impl =
                TestHelpers.createTestConfiguration(SSHConnector.class, propertyBag,
                        "configuration");

        if (propertyBag.getStringProperty("configuration.user").equalsIgnoreCase("__configureme__")) {
            throw new SkipException("SSH Sample tests are skipped. Create private configuration!");
        }
        impl.setProducerBufferSize(0);
        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableCaseInsensitiveFilter(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);

        return ConnectorFacadeFactory.getInstance().newInstance(impl);
    }
}
