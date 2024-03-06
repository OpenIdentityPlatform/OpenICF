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
 * Attempts to test the {@link SSHConnector} Linux samples with the framework.
 */

public class LinuxSamplesTests {

    /**
     * Setup logging for the {@link SSHConnectorTests}.
     */
    private static final Log logger = Log.getLog(LinuxSamplesTests.class);

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
            Set<Attribute> createAttributes = new HashSet<Attribute>();
            createAttributes.add(new Name("bulk" + i));
            createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
            createAttributes.add(AttributeBuilder.build("description", "This is bulk" + i));
            createAttributes.add(AttributeBuilder.build("home", "/home/bulk" + i));
            createAttributes.add(AttributeBuilder.build("group", "users"));
            createAttributes.add(AttributeBuilder.build("shell", "/bin/bash"));
            users[i][0] = "bulk" + i;
            users[i][1] = createAttributes;

        }
        return users;
    }


    /////////////////////  TEST

    @Test(enabled = true)
    public void testTest() {
        logger.info("Running Test test");
        final ConnectorFacade facade = getFacade("linux");
        facade.test();
    }

    @Test(enabled = true)
    public void validateTest() {
        logger.info("Running Validate test");
        final ConnectorFacade facade = getFacade("linux");
        facade.validate();
    }

    @Test(enabled = true, threadPoolSize = 10, invocationCount = 20, timeOut = 50000)
    public void testMultiTest() {
        logger.info("Running Multi Test test");
        final ConnectorFacade facade = getFacade("linux");
        facade.test();
    }

    //////////////  DELETE

    @Test(enabled = true, dependsOnGroups = {}, groups = {"delete"})
    public void deleteUserTest() {
        logger.info("Running Delete User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.ACCOUNT, new Uid("user1"), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, dependsOnGroups = {}, groups = {"delete"}, dataProvider = "bulkUsers")
    public void bulkDeleteUserTest(String name, Set<Attribute> createAttributes) {
        logger.info("Running Bulk Delete Users test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.ACCOUNT, new Uid(name), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, groups = {"delete"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void deleteUserUnknownUidTest() {
        logger.info("Running Delete User Unknown Uid test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        facade.delete(ObjectClass.ACCOUNT, new Uid("unknown"), builder.build());
    }

    @Test(enabled = true, groups = {"delete"}, dependsOnGroups = {})
    public void deleteGroupTest() {
        logger.info("Running Delete Group test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.GROUP, new Uid("group1"), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, groups = {"delete"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void deleteGroupUnknownUidTest() {
        logger.info("Running Delete Group Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        facade.delete(ObjectClass.GROUP, new Uid("unknown"), builder.build());
    }

    /////////////////  CREATE

    @Test(enabled = true, groups = {"create"}, dependsOnGroups = {})
    public void createUserTest() {
        logger.info("Running Create User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("user1"));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("description", "This is user1"));
        createAttributes.add(AttributeBuilder.build("home", "/home/user1"));
        createAttributes.add(AttributeBuilder.build("group", "users"));
        createAttributes.add(AttributeBuilder.build("shell", "/bin/bash"));
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1");
    }

    @Test(enabled = true, groups = {"create"}, dependsOnGroups = {}, dataProvider = "bulkUsers")
    public void createLinuxBulkUserTest(String name, Set<Attribute> createAttributes) {
        logger.info("Running Bulk Create Users test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), name);
    }

    @Test(enabled = true, groups = {"create"}, threadPoolSize = 5, invocationCount = 20, timeOut = 50000)
    public void createParallelUserTest() {
        logger.info("Running Parallel Create Users test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        int id = (int) Thread.currentThread().getId();
        int num = new Random().nextInt(100);
        String name = "user" + Integer.toString(id) + Integer.toString(num);
        createAttributes.add(new Name(name));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("description", "This is user " + name));
        createAttributes.add(AttributeBuilder.build("home", "/home/" + name));
        createAttributes.add(AttributeBuilder.build("group", "users"));
        createAttributes.add(AttributeBuilder.build("shell", "/bin/bash"));
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), name);
    }

    @Test(enabled = true, groups = {}, dependsOnGroups = {}, expectedExceptions = {AlreadyExistsException.class})
    public void createUserAlreadyExistsTest() {
        logger.info("Running Create User Already Exists test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("root"));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("description", "This is root"));
        createAttributes.add(AttributeBuilder.build("home", "/home/root"));
        facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"create"}, dependsOnGroups = {})
    public void createGroupTest() {
        logger.info("Running Create Group test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("group1"));
        Uid uid = facade.create(ObjectClass.GROUP, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "group1");
    }

    @Test(enabled = true, groups = {}, dependsOnGroups = {}, expectedExceptions = {AlreadyExistsException.class})
    public void createGroupAlreadyExistsTest() {
        logger.info("Running Create Group Already Exists test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("root"));
        facade.create(ObjectClass.GROUP, createAttributes, builder.build());
    }

    ////////////////////  UPDATE

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void updateUnknownUserTest() {
        logger.info("Running Update Unknown User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.build("description", "This is unknown"));
        facade.update(ObjectClass.ACCOUNT, new Uid("unknown"), updateAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {})
    public void updateUserTest() {
        logger.info("Running Update User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.build("description", "This is updated user1"));

        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1");
    }

    @Test(enabled = true, groups = {"update"}, dependsOnGroups = {})
    public void updatePasswordUserTest() {
        logger.info("Running Update Password User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildPassword("Password2".toCharArray()));

        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1");
    }

    ////////////////////  GET

    @Test(enabled = true, groups = {"get"}, dependsOnGroups = {})
    public void getUserTest() {
        logger.info("Running Get User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("shell");
        ConnectorObject co =
                facade.getObject(ObjectClass.ACCOUNT, new Uid("root"), builder.build());
        Assert.assertEquals(co.getName().getNameValue(), "root");
        Assert.assertEquals(co.getAttributeByName("shell").getValue().get(0), "/bin/bash");
    }

    @Test(enabled = true, groups = {"get"}, dependsOnGroups = {})
    public void getUserUnknownUidTest() {
        logger.info("Running Get User Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, new Uid("unknown"), builder.build()));
    }

    @Test(enabled = true, groups = {"get"}, dependsOnGroups = {})
    public void getGroupTest() {
        logger.info("Running Get Group Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        ConnectorObject co =
                facade.getObject(ObjectClass.GROUP, new Uid("root"), builder.build());
        Assert.assertEquals(co.getName().getNameValue(), "root");
    }

    @Test(enabled = true, groups = {"get"}, dependsOnGroups = {})
    public void getGroupUnknownUidTest() {
        logger.info("Running Get Group Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Assert.assertNull(facade.getObject(ObjectClass.GROUP, new Uid("unknown"), builder.build()));
    }

    ///////////////////  SEARCH

    @Test(enabled = true, groups = {"search"})
    public void searchTest() {
        logger.info("Running Search Linux User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setPageSize(10);
        final ResultsHandler handler = new ToListResultsHandler();

        SearchResult result =
                facade.search(ObjectClass.ACCOUNT, FilterBuilder.equalTo(new Uid("root")), handler,
                        builder.build());
        Assert.assertEquals(((ToListResultsHandler) handler).getObjects().size(), 1);
    }

    @Test(enabled = true, groups = {"search"})
    public void QueryAllUsersTest() {
        logger.info("Running Query All Users Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.ACCOUNT, null, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = true, groups = {"search"})
    public void QueryAllGroupsTest() {
        logger.info("Running Query All Groups Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.GROUP, null, handler, builder.build());
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
