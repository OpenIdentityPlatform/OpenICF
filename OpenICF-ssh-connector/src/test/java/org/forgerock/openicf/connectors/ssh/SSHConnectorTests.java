/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/CDDL-1.0
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://opensource.org/licenses/CDDL-1.0
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
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

    ///////////////////// DATA PROVIDERS
    @DataProvider(name = "bulkUsers")
    public Object[][] bulkUsers() {
        Object[][] users = new Object[10][2];
        for(int i=0;i<10;i++) {
            Set<Attribute> createAttributes = new HashSet<Attribute>();
            createAttributes.add(new Name("bulk"+i));
            createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
            createAttributes.add(AttributeBuilder.build("description", "This is bulk" + i));
            createAttributes.add(AttributeBuilder.build("home", "/home/bulk"+i));
            createAttributes.add(AttributeBuilder.build("group", "users"));
            createAttributes.add(AttributeBuilder.build("shell", "/bin/bash"));
            users[i][0] = "bulk"+i;
            users[i][1] = createAttributes;

        }
        return  users;
    }


    /////////////////////  TEST

    @Test(enabled = false)
    public void testTest() {
        logger.info("Running academic Test");
        final ConnectorFacade facade = getFacade("test");
        facade.test();
    }

    @Test(enabled = true, threadPoolSize = 10, invocationCount = 20,  timeOut = 50000)
    public void testTestLinux() {
        logger.info("Running Test Test Linux");
        int id = (int) Thread.currentThread().getId();
        Random r = new Random();
        int num = r.nextInt(100);
        logger.info("Random is: {0}-{1}",id,num);
        final ConnectorFacade facade = getFacade("linux");
        facade.test();
    }

    @Test(enabled = false)
    public void testTestKerberos() {
        logger.info("Running Test Test Kerberos");
        final ConnectorFacade facade = getFacade("kerberos");
        facade.test();
    }

    @Test(enabled = false)
    public void testTestMac() {
        logger.info("Running Test Test Mac");
        final ConnectorFacade facade = getFacade("mac");
        facade.test();
    }

    @Test(enabled = true)
    public void validateTest() {
        logger.info("Running Validate Test");
        final ConnectorFacade facade = getFacade("linux");
        facade.validate();
    }

    //////////////  DELETE

    @Test(enabled = true, dependsOnGroups = {}, groups = {"deletelinux"})
    public void deleteLinuxUserTest() {
        logger.info("Running Delete Linux User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.ACCOUNT, new Uid("user1"), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, dependsOnGroups = {}, groups = {"deletelinux"}, dataProvider = "bulkUsers")
    public void deleteLinuxBulkUserTest(String name, Set<Attribute> createAttributes) {
        logger.info("Running Delete Linux Bulk User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.ACCOUNT, new Uid(name), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, groups = {"deletelinux"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void deleteLinuxUserUnknownUidTest() {
        logger.info("Running Delete Linux User Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        facade.delete(ObjectClass.ACCOUNT, new Uid("unknown"), builder.build());
    }

    @Test(enabled = true, groups = {"deletelinux"}, dependsOnGroups = {"createlinux"})
    public void deleteLinuxGroupTest() {
        logger.info("Running Delete Linux Group Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        try {
            facade.delete(ObjectClass.GROUP, new Uid("group1"), builder.build());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Test(enabled = true, groups = {"deletelinux"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void deleteLinuxGroupUnknownUidTest() {
        logger.info("Running Delete Linux Group Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        facade.delete(ObjectClass.GROUP, new Uid("unknown"), builder.build());
    }

    /////////////////  CREATE

    @Test(enabled = true, groups = {"createlinux"}, dependsOnGroups = {}, dataProvider = "bulkUsers")
    public void createLinuxBulkUserTest(String name, Set<Attribute> createAttributes) {
        logger.info("Running Create Linux Bulk User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), name);
    }

    @Test(enabled = true, groups = {"createlinux"}, threadPoolSize = 5, invocationCount = 20,  timeOut = 50000)
    public void createLinuxParallelUserTest() {
        logger.info("Running Create Linux User Parallel Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        int id = (int) Thread.currentThread().getId();
        int num = new Random().nextInt(100);
        String name = "user"+Integer.toString(id)+Integer.toString(num);
        createAttributes.add(new Name(name));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("description", "This is user "+name));
        createAttributes.add(AttributeBuilder.build("home", "/home/"+name));
        createAttributes.add(AttributeBuilder.build("group", "users"));
        createAttributes.add(AttributeBuilder.build("shell", "/bin/bash"));
        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), name);
    }

    @Test(enabled = true, groups = {"createlinux"}, dependsOnGroups = {})
    public void createLinuxUserTest() {
        logger.info("Running Create Linux User Test");
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

    @Test(enabled = true, groups = {}, dependsOnGroups = {}, expectedExceptions = {AlreadyExistsException.class})
    public void createLinuxUserAlreadyExistsTest() {
        logger.info("Running Create Linux User Already Exists Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("root"));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("description", "This is root"));
        createAttributes.add(AttributeBuilder.build("home", "/home/root"));
        facade.create(ObjectClass.ACCOUNT, createAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"createlinux"}, dependsOnGroups = {})
    public void createLinuxGroupTest() {
        logger.info("Running Create Linux Group Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("group1"));
        Uid uid = facade.create(ObjectClass.GROUP, createAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "group1");
    }

    @Test(enabled = true, groups = {}, dependsOnGroups = {}, expectedExceptions = {AlreadyExistsException.class})
    public void createLinuxGroupAlreadyExistsTest() {
        logger.info("Running Create Linux Group Already Exists Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("root"));
        facade.create(ObjectClass.GROUP, createAttributes, builder.build());
    }

    ////////////////////  GET

    @Test(enabled = true, groups = {"getlinux"}, dependsOnGroups = {})
    public void getLinuxUserTest() {
        logger.info("Running Get Linux User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("shell");
        ConnectorObject co =
                facade.getObject(ObjectClass.ACCOUNT, new Uid("root"), builder.build());
        Assert.assertEquals(co.getName().getNameValue(), "root");
        Assert.assertEquals(co.getAttributeByName("shell").getValue().get(0), "/bin/bash");
    }

    @Test(enabled = true, groups = {"getlinux"}, dependsOnGroups = {})
    public void getLinuxUserUnknownUidTest() {
        logger.info("Running Get Linux User Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, new Uid("unknown"), builder.build()));
    }

    @Test(enabled = true, groups = {"getlinux"}, dependsOnGroups = {})
    public void getLinuxGroupTest() {
        logger.info("Running Get Linux Group Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        ConnectorObject co =
                facade.getObject(ObjectClass.GROUP, new Uid("root"), builder.build());
        Assert.assertEquals(co.getName().getNameValue(), "root");
    }

    @Test(enabled = true, groups = {"getlinux"}, dependsOnGroups = {})
    public void getLinuxGroupUnknownUidTest() {
        logger.info("Running Get Linux Group Unknown Uid Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Assert.assertNull(facade.getObject(ObjectClass.GROUP, new Uid("unknown"), builder.build()));
    }

    @Test(enabled = false, groups = {"getkerberos"}, dependsOnGroups = {})
    public void getKerberosPrincipalTest() {
        logger.info("Running Get Kerberos Principal Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, new Uid("kadmin/admin@COOPSRC"), builder.build());
        Assert.assertEquals(co.getName().getNameValue(), "kadmin/admin@COOPSRC");
    }
    ///////////////////  SEARCH

    @Test(enabled = true)
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

    @Test(enabled = true)
    public void QueryAllLinuxUsersTest() {
        logger.info("Running Query All Linux Users Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.ACCOUNT, null, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = true)
    public void QueryAllLinuxGroupsTest() {
        logger.info("Running Query All Linux Groups Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.GROUP, null, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"searchKerberos"})
    public void QueryAllKerberosPrincipalsTest() {
        logger.info("Running Query All Kerberos Principals Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();
        facade.search(ObjectClass.ACCOUNT, null, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"searchKerberos"})
    public void QueryStartsWithKerberosPrincipalsTest() {
        logger.info("Running Query StartsWith Kerberos Principals Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final Filter filter = FilterBuilder.startsWith(AttributeBuilder.build("Principal","test"));
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        facade.search(ObjectClass.ACCOUNT, filter, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"searchKerberos"})
    public void QueryEndsWithKerberosPrincipalsTest() {
        logger.info("Running Query EndsWith Kerberos Principals Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final Filter filter = FilterBuilder.endsWith(AttributeBuilder.build("Principal","st1"));
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        facade.search(ObjectClass.ACCOUNT, filter, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    @Test(enabled = false, groups = {"searchKerberos"})
    public void QueryContainsKerberosPrincipalsTest() {
        logger.info("Running Query Contains Kerberos Principals Test");
        final ConnectorFacade facade = getFacade("kerberos");
        final Filter filter = FilterBuilder.contains(AttributeBuilder.build("Principal","est"));
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        final ResultsHandler handler = new ToListResultsHandler();

        facade.search(ObjectClass.ACCOUNT, filter, handler, builder.build());
        int size = ((ToListResultsHandler) handler).getObjects().size();
        Assert.assertTrue(size > 0);
    }

    ////////////////////  UPDATE

    @Test(enabled = true, groups = {"updatelinux"}, dependsOnGroups = {}, expectedExceptions = {UnknownUidException.class})
    public void updateLinuxUnknownUserTest() {
        logger.info("Running Update Linux Unknown User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.build("description", "This is unknown"));
        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("unknown"), updateAttributes, builder.build());
    }

    @Test(enabled = true, groups = {"updatelinux"}, dependsOnGroups = {})
    public void updateLinuxUserTest() {
        logger.info("Running Update Linux User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.build("description", "This is updated user1"));

        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1");
    }

    @Test(enabled = true, groups = {"updatelinux"}, dependsOnGroups = {})
    public void updateLinuxPasswordUserTest() {
        logger.info("Running Update Linux Password User Test");
        final ConnectorFacade facade = getFacade("linux");
        final OperationOptionsBuilder builder = new OperationOptionsBuilder();
        Set<Attribute> updateAttributes = new HashSet<Attribute>();
        updateAttributes.add(AttributeBuilder.buildPassword("Password2".toCharArray()));

        Uid uid = facade.update(ObjectClass.ACCOUNT, new Uid("user1"), updateAttributes, builder.build());
        Assert.assertEquals(uid.getUidValue(), "user1");
    }


    ////////////////////  Test Utils

    protected ConnectorFacade getFacade(String environment) {
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
