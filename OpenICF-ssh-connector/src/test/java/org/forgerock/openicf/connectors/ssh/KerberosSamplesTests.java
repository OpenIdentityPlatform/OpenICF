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
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
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
 * Attempts to test the {@link SSHConnector} MIT Kerberos samples with the framework.
 */

public class KerberosSamplesTests {
    /**
     * Setup logging for the {@link SSHConnectorTests}.
     */
    private static final Log logger = Log.getLog(KerberosSamplesTests.class);

    private static final String REALM = "@EXAMPLE.COM";

    private static final String DATE = "2020-01-01";

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


    ///////////////////// DATA PROVIDERS
    @DataProvider(name = "bulkUsers")
    public Object[][] bulkUsers() {
        Object[][] users = new Object[10][2];
        for (int i = 0; i < 10; i++) {
            Set<Attribute> attributes = new HashSet<Attribute>();
            attributes.add(new Name("bulk" + i));
            attributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
            attributes.add(AttributeBuilder.build("expirationDate", DATE));
            attributes.add(AttributeBuilder.build("maximumTicketLife", DATE));
            attributes.add(AttributeBuilder.build("maximumRenewableLife", DATE));
            attributes.add(AttributeBuilder.build("passwordExpiration", DATE));
            attributes.add(AttributeBuilder.build("policy", "user"));
            users[i][0] = "bulk" + i + REALM;
            users[i][1] = attributes;

        }
        return users;
    }

    /////////////////////  TEST

    @Test(enabled = true)
    public void testTest() {
        logger.info("Running Test test");
        final ConnectorFacade facade = getFacade("kerberos");
        facade.test();
    }

    @Test(enabled = true)
    public void validateTest() {
        logger.info("Running Validate Test");
        final ConnectorFacade facade = getFacade("linux");
        facade.validate();
    }

    @Test(enabled = true, threadPoolSize = 4, invocationCount = 20, timeOut = 50000)
    public void testTestMulti() {
        logger.info("Running Multiple Test test");
        int id = (int) Thread.currentThread().getId();
        Random r = new Random();
        int num = r.nextInt(100);
        logger.info("Random is: {0}-{1}", id, num);
        final ConnectorFacade facade = getFacade("kerberos");
        facade.test();
    }

    //////////////  DELETE

    @Test(enabled = true, dependsOnGroups = {}, groups = {"delete"})
    public void deletePrincipalTest() {
        logger.info("Running Delete Principal Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.ACCOUNT, new Uid("user1" + REALM), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, dependsOnGroups = {}, groups = {"delete"}, dataProvider = "bulkUsers")
    public void bulkDeletePrincipalTest(String name, Set<Attribute> createAttributes) {
        logger.info("Running Bulk Delete Principals test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.ACCOUNT, new Uid(name), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, groups = {"delete"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void deletePrincipalUnknownUidTest() {
        logger.info("Running Delete Principal Unknown Uid test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        facade.delete(ObjectClass.ACCOUNT, new Uid("unknown" + REALM), builder.build());
    }


    /////////////////  CREATE


    @Test(enabled = true, groups = {"create"}, dependsOnGroups = {}, dataProvider = "bulkUsers")
    public void bulkCreatePrincipalTest(String name, Set<Attribute> createAttributes) {
        logger.info("Running Bulk Create Principals Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), name);
    }

    @Test(enabled = true, groups = {"create"}, threadPoolSize = 10, invocationCount = 2000, timeOut = 1000000)
    public void multiCreatePrincipalTest() {
        logger.info("Running Parallel Create Principals test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        int id = (int) Thread.currentThread().getId();
        int num = new Random().nextInt(100000);
        String name = "j" + Integer.toString(id) + Integer.toString(num);
        createAttributes.add(new Name(name));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("expirationDate", DATE));
        createAttributes.add(AttributeBuilder.build("maximumTicketLife", DATE));
        createAttributes.add(AttributeBuilder.build("maximumRenewableLife", DATE));
        createAttributes.add(AttributeBuilder.build("passwordExpiration", DATE));
        createAttributes.add(AttributeBuilder.build("policy", "user"));
        try {
            Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
            Assert.assertEquals(uid.getUidValue(), name + REALM);
        } catch (AlreadyExistsException e) {
            // silently ignore this
        }
    }

    @Test(enabled = true, groups = {"create"}, dependsOnGroups = {})
    public void createPrincipalTest() {
        logger.info("Running Create Principal Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("user1"));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("expirationDate", DATE));
        createAttributes.add(AttributeBuilder.build("maximumTicketLife", DATE));
        createAttributes.add(AttributeBuilder.build("maximumRenewableLife", DATE));
        createAttributes.add(AttributeBuilder.build("passwordExpiration", DATE));
        createAttributes.add(AttributeBuilder.build("policy", "user"));
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1" + REALM);
    }

    @Test(enabled = true, groups = {"create"}, dependsOnGroups = {}, expectedExceptions = {AlreadyExistsException.class})
    public void createPrincipalAlreadyExistsTest() {
        logger.info("Running Create Principal Already Exists Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("kadmin/admin"));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
    }

    ////////////////////  UPDATE


    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {})
    public void updatePrincipalUnlock() {
        logger.info("Running Update Principal Unlock test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildLockOut(false));
        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1" + REALM);
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void updatePrincipalUnlockUnknownUid() {
        logger.info("Running Update Principal Unlock UnlockUnknownUid test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildLockOut(false));
        facade.update(ObjectClass.ACCOUNT, new Uid("unknown"), updateAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {})
    public void updatePrincipalPassword() {
        logger.info("Running Update Principal Password Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildPassword("Password1".toCharArray()));
        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1" + REALM);
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {}, expectedExceptions = {InvalidAttributeValueException.class})
    public void updatePrincipalSamePassword() {
        logger.info("Running Update Principal Same Password Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildPassword("Password1".toCharArray()));
        facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {}, expectedExceptions = {InvalidAttributeValueException.class})
    public void updatePrincipalPasswordTooShort() {
        logger.info("Running Update Principal Password Too Short Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildPassword("123".toCharArray()));
        facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void updatePrincipalPasswordUnknownUid() {
        logger.info("Running Update Principal Password Unknown Uid Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildPassword("Passw0rd1".toCharArray()));
        facade.update(ObjectClass.ACCOUNT, new Uid("unknown"), updateAttributes, builder.build());
    }

    ////////////////////  GET


    @Test(enabled = true, groups = {"get"}, dependsOnGroups = {})
    public void getKerberosPrincipalTest() {
        logger.info("Running Get Principal Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, new Uid("kadmin/admin" + REALM), builder.build());
        Assert.assertEquals(co.getName().getNameValue(), "kadmin/admin" + REALM);
    }


    ////////////////////  SEARCH

    @Test(enabled = true, groups = {"search"})
    public void searchTest() {
        logger.info("Running Exact Search Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        SearchResult result =
                facade.search(ObjectClass.ACCOUNT, FilterBuilder.equalTo(new Uid("user1")), handler,
                        builder.build());
        Assert.assertEquals(((ToListResultsHandler) handler).getObjects().size(), 1);
    }

    @Test(enabled = true, groups = {"search"})
    public void QueryAllPrincipalsTest() {
        logger.info("Running Query All Principals Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.ACCOUNT, null, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        logger.info("Size is: {0}", size);
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"search"})
    public void QueryPrincipalStartsWithTest() {
        logger.info("Running Query Principals StartsWith Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final Filter filter = FilterBuilder.startsWith(AttributeBuilder.build("Principal", "test"));
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        facade.search(ObjectClass.ACCOUNT, filter, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"search"})
    public void QueryPrincipalEndsWithTest() {
        logger.info("Running Query Principal EndsWith Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final Filter filter = FilterBuilder.endsWith(AttributeBuilder.build("Principal", "st1"));
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        facade.search(ObjectClass.ACCOUNT, filter, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"search"})
    public void QueryPrincipalContainsTest() {
        logger.info("Running Query Principal Contains Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final Filter filter = FilterBuilder.contains(AttributeBuilder.build("Principal", "est"));
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        facade.search(ObjectClass.ACCOUNT, filter, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
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
            throw new SkipException("Kerberos Sample tests are skipped. Create private configuration!");
        }
        impl.setProducerBufferSize(0);
        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableCaseInsensitiveFilter(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);

        return ConnectorFacadeFactory.getInstance().newInstance(impl);
    }


}
