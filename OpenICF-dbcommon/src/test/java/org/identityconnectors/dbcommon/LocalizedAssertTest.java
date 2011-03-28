package org.identityconnectors.dbcommon;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.Assert;
import org.testng.AssertJUnit;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Properties;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.junit.*;

/** Tests for LocalizedAssert */
public class LocalizedAssertTest {
    
    private static class TestConnectorMessages implements ConnectorMessages{
        private Properties properties;
        TestConnectorMessages(){
            try {
                properties = IOUtil.getResourceAsProperties(getClass()
                        .getClassLoader(), LocalizedAssertTest.class
                        .getPackage().getName().replace('.', '/')
                        + "/Messages.properties");
                AssertJUnit.assertNotNull("Cannot load Messages.properties", properties);
            } catch (IOException e) {
                Assert.fail("Cannot load Messages.properties" + e.getMessage());
            }
        }
        public String format(String key, String dflt, Object... args) {
            String value = properties.getProperty(key);
            if(value == null){
                return dflt != null ? dflt : key;
            }
            if(args == null){
                return value;
            }
            return MessageFormat.format(value, args);
        }
    }
    
    static LocalizedAssert testee; 
    
    /**
     * Setup testee
     */
    @BeforeClass
    public static void setup(){
        testee = new LocalizedAssert(new TestConnectorMessages());
    }
    
    /**
     * Test method for {@link org.identityconnectors.dbcommon.LocalizedAssert#assertNotNull(java.lang.Object, java.lang.String)}.
     */
    @Test
    public final void testAssertNotNull() {
       Integer i = AssertJUnit.assertNotNull(new Integer(1), "i");
       AssertJUnit.assertEquals(new Integer(1),i);
       try{
           i = AssertJUnit.assertNotNull(null, "i");
           Assert.fail("Must fail for null argument");
       }
       catch(RuntimeException e){
           AssertJUnit.assertEquals("Argument [i] cannot be null",e.getMessage());
       }
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.LocalizedAssert#assertNull(java.lang.Object, java.lang.String)}.
     */
    @Test
    public final void testAssertNull() {
        Integer i = AssertJUnit.assertNull(null, "i");
        assertNull(i);
        try{
            i = AssertJUnit.assertNull(new Integer(1),"i");
            Assert.fail("Must fail for not null argument");
        }
        catch(RuntimeException e){
            AssertJUnit.assertEquals("Argument [i] must be null",e.getMessage());
        }
        
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.LocalizedAssert#assertNotBlank(java.lang.String, java.lang.String)}.
     */
    @Test
    public final void testAssertNotBlank() {
        String os = testee.assertNotBlank("Linux", "os");
        AssertJUnit.assertEquals("Linux",os);
        try{
            os = testee.assertNotBlank(null,"os");
            Assert.fail("Must fail for null argument");
        }
        catch(RuntimeException e){
            AssertJUnit.assertEquals("Argument [os] cannot be blank",e.getMessage());
        }
        try{
            os = testee.assertNotBlank("","os");
            Assert.fail("Must fail for blank argument");
        }
        catch(RuntimeException e){
            AssertJUnit.assertEquals("Argument [os] cannot be blank",e.getMessage());
        }
        
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.LocalizedAssert#assertBlank(java.lang.String, java.lang.String)}.
     */
    @Test
    public final void testAsserBlank() {
        String os = testee.assertBlank(null, "os");
        assertNull(os);
        os = testee.assertBlank("", "os");
        AssertJUnit.assertEquals("",os);
        try{
            os = testee.assertBlank("Some os","os");
            Assert.fail("Must fail for non blank argument");
        }
        catch(RuntimeException e){
            AssertJUnit.assertEquals("Argument [os] must be blank",e.getMessage());
        }
    }
    
    /** Test of {@link LocalizedAssert#LocalizedAssert(ConnectorMessages)} */
    @Test
    public void testCreate(){
        new LocalizedAssert(new TestConnectorMessages());
        try{
            new LocalizedAssert(null);
            Assert.fail("Must fail for null ConnectorMessages");
        }
        catch(RuntimeException e){
            //emptyS
        }
    }
    
    /** 
     * test method
     */
    @Test
    public void testLocalizeArgumentNames(){
    	LocalizedAssert la = new LocalizedAssert(new TestConnectorMessages(),true);
    	try{
    		//Small hack, we do not create new TestMessages with dummy argument name, use assert.blank as argument name
    		la.assertNotBlank("", "assert.blank");
    		Assert.fail("Must fail for blank String");
    	}
    	catch(RuntimeException e){
    		AssertJUnit.assertEquals("Argument [Argument [{0}] must be blank] cannot be blank",e.getMessage());
    	}
    	
    }

}
