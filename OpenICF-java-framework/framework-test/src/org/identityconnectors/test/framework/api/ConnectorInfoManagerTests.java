/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.test.framework.api;

import static org.identityconnectors.common.IOUtil.makeURL;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import junit.framework.Assert;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorInfoManagerFactory;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.ScriptContextBuilder;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.impl.api.ConfigurationPropertyImpl;
import org.identityconnectors.framework.impl.api.local.ConnectorPoolManager;
import org.junit.Test;


public class ConnectorInfoManagerTests {

    private static ConnectorInfo findConnectorInfo
        (ConnectorInfoManager manager,
         String version,
         String connectorName) {
        for (ConnectorInfo info : manager.getConnectorInfos()) {
            ConnectorKey key = info.getConnectorKey();
            if ( version.equals(key.getBundleVersion()) &&
                connectorName.equals(key.getConnectorName()) ) {
                //intentionally ineffecient to test
                //more code
                return manager.findConnectorInfo(key);
            }
        }
        return null;
    }

    @Test
    public void testClassLoading() throws Exception {
        final ClassLoader startLocal =
            Thread.currentThread().getContextClassLoader();
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info1 = 
            findConnectorInfo(manager,
                              "1.0.0.0",
                              "org.identityconnectors.testconnector.TstConnector");
        Assert.assertNotNull(info1);
        ConnectorInfo info2 = 
            findConnectorInfo(manager,
                             "2.0.0.0",
                             "org.identityconnectors.testconnector.TstConnector");
        Assert.assertNotNull(info2);
        
        APIConfiguration apiConfig1 = info1.createDefaultAPIConfiguration();
        ConfigurationProperties props = apiConfig1.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("numResults");
        property.setValue(1);
        
        ConnectorFacade facade1 =
            ConnectorFacadeFactory.getInstance().newInstance(apiConfig1);

        ConnectorFacade facade2 =
            ConnectorFacadeFactory.getInstance().newInstance(info2.createDefaultAPIConfiguration());
        
        Set<Attribute> attrs = CollectionUtil.<Attribute>newReadOnlySet();
        Assert.assertEquals("1.0", facade1.create(ObjectClass.ACCOUNT,attrs,null).getUidValue());
        Assert.assertEquals("2.0", facade2.create(ObjectClass.ACCOUNT,attrs,null).getUidValue());
        
        final int [] count = new int[1];
        facade1.search(ObjectClass.ACCOUNT,
                null, 
                new ResultsHandler() {
            public boolean handle(ConnectorObject obj) {
                count[0]++;
                //make sure thread local classloader is restored
                Assert.assertSame(startLocal, Thread.currentThread().getContextClassLoader());
                return true;
            }
        },null);
        Assert.assertEquals(1, count[0]);
        
        //make sure thread local classloader is restored
        Assert.assertSame(startLocal, Thread.currentThread().getContextClassLoader());
        shutdownConnnectorInfoManager();
    }
    
    /**
     * Attempt to test the information from the configuration.
     * 
     * @throws Exception iff there is an issue.
     */
    @Test
    public void testAPIConfiguration() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        ConfigurationProperties props = api.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("tstField");
        
        Assert.assertNotNull(property);
        
        Assert.assertEquals("Help for test field.",property.getHelpMessage(null));
        Assert.assertEquals("Display for test field.",property.getDisplayName(null));

        Locale xlocale = new Locale("es");
        CurrentLocale.set(xlocale);
        Assert.assertEquals("tstField.help_es",property.getHelpMessage(null));
        Assert.assertEquals("tstField.display_es",property.getDisplayName(null));
        
        Locale esESlocale = new Locale("es","ES");
        CurrentLocale.set(esESlocale);
        Assert.assertEquals("tstField.help_es-ES",property.getHelpMessage(null));
        Assert.assertEquals("tstField.display_es-ES",property.getDisplayName(null));
        
        Locale esARlocale = new Locale("es","AR");
        CurrentLocale.set(esARlocale);
        Assert.assertEquals("tstField.help_es",property.getHelpMessage(null));
        Assert.assertEquals("tstField.display_es",property.getDisplayName(null));
        
        CurrentLocale.clear();
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);
        // call the various create/update/delete commands..
        facade.schema();
        shutdownConnnectorInfoManager();
    }
    
    /**
     * Attempt to test the information from the configuration.
     * 
     * @throws Exception iff there is an issue.
     */
    @Test
    public void testValidate() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        ConfigurationProperties props = api.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("failValidation");
        property.setValue(false);
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);
        facade.validate();
        property.setValue(true);
        facade = facf.newInstance(api);
        //validate and also test that locale is propogated
        //properly
        try {
            CurrentLocale.set(new Locale("en"));
            facade.validate();
            
            Assert.fail("exception expected");
        }
        catch (ConnectorException e) {
            Assert.assertEquals("validation failed en", e.getMessage());
        }
        finally {
            CurrentLocale.clear();
        }
        //validate and also test that locale is propogated
        //properly
        try {
            CurrentLocale.set(new Locale("es"));
            facade.validate();
            
            Assert.fail("exception expected");
        }
        catch (ConnectorException e) {
            Assert.assertEquals("validation failed es", e.getMessage());
        }
        finally {
            CurrentLocale.clear();
        }
        shutdownConnnectorInfoManager();
    }
    
    /**
     * Main purpose of this is to test searching with
     * many results and that we can properly handle
     * stopping in the middle of this. There's a bunch of
     * code in the remote stuff that is there to handle this
     * in particular that we want to excercise.
     */
    @Test 
    public void testSearchWithManyResults() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        ConfigurationProperties props = api.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("numResults");
        
        //1000 is several times the remote size between pauses
        property.setValue(1000);
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        final List<ConnectorObject> results = new ArrayList<ConnectorObject>();
        
        facade.search(ObjectClass.ACCOUNT, null, new ResultsHandler() {
            public boolean handle(ConnectorObject obj) {
                results.add(obj);
                return true;
            }
        },null);
        
        Assert.assertEquals(1000,results.size());
        for ( int i = 0; i < results.size(); i++ ) {
            ConnectorObject obj = results.get(i);
            Assert.assertEquals(String.valueOf(i),
                    obj.getUid().getUidValue());
        }
        
        results.clear();
        
        facade.search(ObjectClass.ACCOUNT,
                null, 
                new ResultsHandler() {
            public boolean handle(ConnectorObject obj) {
                if ( results.size() < 500 ) {
                    results.add(obj);
                    return true;
                }
                else {
                    return false;
                }
            }
        },null);
        
        Assert.assertEquals(500,results.size());
        for ( int i = 0; i < results.size(); i++ ) {
            ConnectorObject obj = results.get(i);
            Assert.assertEquals(String.valueOf(i),
                    obj.getUid().getUidValue());
        }
        
        shutdownConnnectorInfoManager();
    }
    
    //@Test 
    public void testSearchStress() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        ConfigurationProperties props = api.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("numResults");
        
        property.setValue(10000);
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);
        long start = System.currentTimeMillis();
        facade.search(ObjectClass.ACCOUNT, null, new ResultsHandler() {
            public boolean handle(ConnectorObject obj) {
                return true;
            }
        },null);
        long end = System.currentTimeMillis();
        System.out.println("Test took: "+(end-start)/1000);
        
        shutdownConnnectorInfoManager();
    }
    //@Test 
    public void testSchemaStress() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);
        
        for (int i = 0; i < 1000; i++) {
            facade.schema();
        }
        
        shutdownConnnectorInfoManager();
    }
    
    //@Test 
    public void testCreateStress() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);
        
        for (int i = 0; i < 1000; i++) {
            Set<Attribute> attrs = new HashSet<Attribute>();
            for ( int j = 0; j < 50; j++) {
                attrs.add(AttributeBuilder.build("myattributename"+j, "myattributevalue"+j));
            }
            facade.create(ObjectClass.ACCOUNT,attrs,null);
        }
        
        shutdownConnnectorInfoManager();
    }
    
    
    /**
     * Main purpose of this is to test sync with
     * many results and that we can properly handle
     * stopping in the middle of this. There's a bunch of
     * code in the remote stuff that is there to handle this
     * in particular that we want to excercise.
     */
    @Test 
    public void testSyncWithManyResults() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        ConfigurationProperties props = api.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("numResults");
        
        //1000 is several times the remote size between pauses
        property.setValue(1000);
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);

        SyncToken latest = facade.getLatestSyncToken();
        Assert.assertEquals("mylatest", latest.getValue());
        
        final List<SyncDelta> results = new ArrayList<SyncDelta>();
        
        facade.sync(ObjectClass.ACCOUNT, null, new SyncResultsHandler() {
            public boolean handle(SyncDelta obj) {
                results.add(obj);
                return true;
            }
        },null);
        
        Assert.assertEquals(1000,results.size());
        for ( int i = 0; i < results.size(); i++ ) {
            SyncDelta obj = results.get(i);
            Assert.assertEquals(String.valueOf(i),
                    obj.getObject().getUid().getUidValue());
        }
        
        results.clear();
        
        facade.sync(ObjectClass.ACCOUNT,
                null, 
                new SyncResultsHandler() {
            public boolean handle(SyncDelta obj) {
                if ( results.size() < 500 ) {
                    results.add(obj);
                    return true;
                }
                else {
                    return false;
                }
            }
        },null);
        
        Assert.assertEquals(500,results.size());
        for ( int i = 0; i < results.size(); i++ ) {
            SyncDelta obj = results.get(i);
            Assert.assertEquals(String.valueOf(i),
                    obj.getObject().getUid().getUidValue());
        }
        
        shutdownConnnectorInfoManager();
    }
    
    //TODO: this needs to overridden for C# testing
    @Test
    public void testScripting() throws Exception
    {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info = 
            findConnectorInfo(manager,
                    "1.0.0.0",
                    "org.identityconnectors.testconnector.TstConnector");
        APIConfiguration api = info.createDefaultAPIConfiguration();
        
        
        ConnectorFacadeFactory facf = ConnectorFacadeFactory.getInstance();
        ConnectorFacade facade = facf.newInstance(api);
        
        ScriptContextBuilder builder = new ScriptContextBuilder();
        builder.addScriptArgument("arg1", "value1");
        builder.addScriptArgument("arg2", "value2");
        builder.setScriptLanguage("GROOVY");

        //test that they can run the script and access the
        //connector object
        {
            String SCRIPT =
                "return connector.concat(arg1,arg2)";
            builder.setScriptText(SCRIPT);
            String result = (String)facade.runScriptOnConnector(builder.build(), 
                    null);
            
            Assert.assertEquals("value1value2", result);
        }
        
        //test that they can access a class in the class loader
        {
            String SCRIPT =
                "return org.identityconnectors.testcommon.TstCommon.getVersion()";
            builder.setScriptText(SCRIPT);
            String result = (String)facade.runScriptOnConnector(builder.build(), 
                    null);
            Assert.assertEquals("1.0", result);
        }
        
        //test that they cannot access a class in internal
        {
            String clazz = ConfigurationPropertyImpl.class.getName();
            
            String SCRIPT =
                "return new "+clazz+"()";
            builder.setScriptText(SCRIPT);
            try {
                facade.runScriptOnConnector(builder.build(), 
                        null);
                Assert.fail("exception expected");
            }
            catch (Exception e) {
                String msg = e.getMessage();
                String expectedMessage = 
                    "unable to resolve class org.identityconnectors.framework.impl.api.ConfigurationPropertyImpl";
                Assert.assertTrue("Unexpected message: "+msg, 
                        msg.contains(expectedMessage));
            }
        }
        
        // test that they can access a class in common
        {
            String clazz = AttributeBuilder.class.getName();
            String SCRIPT = "return " + clazz + ".build(\"myattr\")";
            builder.setScriptText(SCRIPT);
            Attribute attr = (Attribute) facade.runScriptOnConnector(builder.build(), null);
            Assert.assertEquals("myattr", attr.getName());
        }
        shutdownConnnectorInfoManager();
    }
    
    @Test
    public void testConnectionPooling() throws Exception {
        ConnectorPoolManager.dispose();
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info1 = 
            findConnectorInfo(manager,
                              "1.0.0.0",
                              "org.identityconnectors.testconnector.TstConnector");    
        Assert.assertNotNull(info1);
        
        //reset connection count
        {
            //trigger TstConnection.init to be called
            APIConfiguration config =
                info1.createDefaultAPIConfiguration();
            config.getConfigurationProperties().getProperty("resetConnectionCount").setValue(true);            
            ConnectorFacade facade1 =
                ConnectorFacadeFactory.getInstance().newInstance(config);
            facade1.schema(); //force instantiation            
        }
        
        APIConfiguration config =
            info1.createDefaultAPIConfiguration();
        
        config.getConnectorPoolConfiguration().setMinIdle(0);
        config.getConnectorPoolConfiguration().setMaxIdle(0);
        
        ConnectorFacade facade1 =
            ConnectorFacadeFactory.getInstance().newInstance(config);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption("testPooling", "true");
        OperationOptions options = builder.build();
        Set<Attribute> attrs = CollectionUtil.<Attribute>newReadOnlySet();
        Assert.assertEquals("1", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        Assert.assertEquals("2", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        Assert.assertEquals("3", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        Assert.assertEquals("4", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        config =
            info1.createDefaultAPIConfiguration();
        config.getConnectorPoolConfiguration().setMinIdle(1);
        config.getConnectorPoolConfiguration().setMaxIdle(2);
        facade1 =
            ConnectorFacadeFactory.getInstance().newInstance(config);
        Assert.assertEquals("5", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        Assert.assertEquals("5", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        Assert.assertEquals("5", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        Assert.assertEquals("5", facade1.create(ObjectClass.ACCOUNT,attrs,options).getUidValue());
        shutdownConnnectorInfoManager();
    }
    
    @Test
    public void testTimeout() throws Exception {
        ConnectorInfoManager manager = 
            getConnectorInfoManager();
        ConnectorInfo info1 = 
            findConnectorInfo(manager,
                              "1.0.0.0",
                              "org.identityconnectors.testconnector.TstConnector");
        
        APIConfiguration config = info1.createDefaultAPIConfiguration();
        config.setTimeout(CreateApiOp.class, 5000);
        config.setTimeout(SearchApiOp.class, 5000);
        ConfigurationProperties props = config.getConfigurationProperties();
        ConfigurationProperty property  = props.getProperty("numResults"); 
        //1000 is several times the remote size between pauses
        property.setValue(2);
        OperationOptionsBuilder opBuilder = new OperationOptionsBuilder();
        opBuilder.setOption("delay", 10000);
        
        ConnectorFacade facade1 =
            ConnectorFacadeFactory.getInstance().newInstance(config);

        
        Set<Attribute> attrs = CollectionUtil.<Attribute>newReadOnlySet();
        try {
            facade1.create(ObjectClass.ACCOUNT,attrs,opBuilder.build()).getUidValue();
            Assert.fail("expected timeout");
        }
        catch (OperationTimeoutException e) {
            //expected
        }
        
        try {
            facade1.search(ObjectClass.ACCOUNT, null, new ResultsHandler() {
                public boolean handle(ConnectorObject obj) {
                    return true;
                }
            },opBuilder.build());
            Assert.fail("expected timeout");
        }
        catch (OperationTimeoutException e) {
            //expected
        }
        
        shutdownConnnectorInfoManager();
    }
    
    private final static String TEST_BUNDLES_DIR_PROPERTY = "testbundles.dir";

    protected final File getTestBundlesDir() {
        File testBundlesDir = new File(System.getProperty(TEST_BUNDLES_DIR_PROPERTY, "../testbundles"));
        if (!testBundlesDir.isDirectory()) {
            throw new ConnectorException(testBundlesDir.getPath() + " does not exist");
        }
        return testBundlesDir;
    }
    
    protected final List<URL> getTestBundles() throws Exception {
        File testBundlesDir = getTestBundlesDir();
        List<URL> rv = new ArrayList<URL>();
        rv.add(makeURL(testBundlesDir,"testbundlev1/dist/org.identityconnectors.testbundle-1.0.0.0.jar"));
        rv.add(makeURL(testBundlesDir,"testbundlev2/dist/org.identityconnectors.testbundle-2.0.0.0.jar"));
        rv.add(makeURL(testBundlesDir,"testcommonv1/dist/org.identityconnectors.testcommon-1.0.0.0.jar"));
        rv.add(makeURL(testBundlesDir,"testcommonv2/dist/org.identityconnectors.testcommon-2.0.0.0.jar"));
        return rv;
    }

    /**
     * To be overridden by subclasses to get different ConnectorInfoManagers
     * @return
     * @throws Exception
     */
    protected ConnectorInfoManager getConnectorInfoManager() throws Exception {
        List<URL> urls = getTestBundles();
        ConnectorInfoManagerFactory fact = ConnectorInfoManagerFactory.getInstance();
        ConnectorInfoManager manager = fact.getLocalManager(urls.toArray(new URL[0]));
        return manager;
    }
    
    protected void shutdownConnnectorInfoManager() {
        
    }
    
    
}
