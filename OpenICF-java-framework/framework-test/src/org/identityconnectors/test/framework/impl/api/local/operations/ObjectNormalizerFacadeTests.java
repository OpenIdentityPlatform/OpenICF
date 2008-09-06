package org.identityconnectors.test.framework.impl.api.local.operations;

import junit.framework.Assert;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.identityconnectors.framework.impl.api.local.operations.ObjectNormalizerFacade;
import org.identityconnectors.framework.spi.AttributeNormalizer;
import org.junit.Test;


public class ObjectNormalizerFacadeTests {
    public static class MyAttributeNormalizer implements AttributeNormalizer {
        public Attribute normalizeAttribute(ObjectClass oclass, Attribute attribute) {
            if ( attribute.is("foo")) {
                String val = AttributeUtil.getStringValue(attribute);
                return AttributeBuilder.build("foo",val.trim());
            }
            else {
                return attribute;
            }
        }
    }
    
    private Attribute createTestAttribute()
    {
        return AttributeBuilder.build("foo"," bar ");
    }
    
    private Attribute createNormalizedTestAttribute()
    {
        return AttributeBuilder.build("foo","bar");        
    }
    
    private ObjectNormalizerFacade createTestNormalizer() 
    {
        ObjectNormalizerFacade facade = new
        ObjectNormalizerFacade(ObjectClass.ACCOUNT,
                new MyAttributeNormalizer());
        return facade;
    }
    
    private void assertNormalizedFilter(Filter expectedNormalizedFilter,
            Filter filter) {
        ObjectNormalizerFacade facade = 
            createTestNormalizer();
        filter = facade.normalizeFilter(filter);
        String expectedXml = SerializerUtil.serializeXmlObject(expectedNormalizedFilter, false);
        String actualXml = SerializerUtil.serializeXmlObject(filter, false);
        Assert.assertEquals(expectedXml, actualXml);
    }
    
    
    
    @Test
    public void testEndsWith()
    {
        Filter expected =
            FilterBuilder.endsWith(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.endsWith(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }    
    @Test
    public void testStartsWith()
    {
        Filter expected =
            FilterBuilder.startsWith(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.startsWith(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testContains()
    {
        Filter expected =
            FilterBuilder.contains(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.contains(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testEqualTo()
    {
        Filter expected =
            FilterBuilder.equalTo(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.equalTo(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testGreaterThanOrEqualTo()
    {
        Filter expected =
            FilterBuilder.greaterThanOrEqualTo(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.greaterThanOrEqualTo(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testLessThanOrEqualTo()
    {
        Filter expected =
            FilterBuilder.lessThanOrEqualTo(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.lessThanOrEqualTo(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testLessThan()
    {
        Filter expected =
            FilterBuilder.lessThan(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.lessThan(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testGreaterThan()
    {
        Filter expected =
            FilterBuilder.greaterThan(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.greaterThan(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testAnd()
    {
        Filter expected =
            FilterBuilder.and(FilterBuilder.contains(createNormalizedTestAttribute()),
                    FilterBuilder.contains(createNormalizedTestAttribute()));
        Filter filter = 
            FilterBuilder.and(FilterBuilder.contains(createTestAttribute()),
                    FilterBuilder.contains(createTestAttribute()));
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testOr()
    {
        Filter expected =
            FilterBuilder.or(FilterBuilder.contains(createNormalizedTestAttribute()),
                    FilterBuilder.contains(createNormalizedTestAttribute()));
        Filter filter = 
            FilterBuilder.or(FilterBuilder.contains(createTestAttribute()),
                    FilterBuilder.contains(createTestAttribute()));
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testNot()
    {
        Filter expected =
            FilterBuilder.not(FilterBuilder.contains(createNormalizedTestAttribute()));
        Filter filter = 
            FilterBuilder.not(FilterBuilder.contains(createTestAttribute()));
        assertNormalizedFilter(expected,filter);
    }
    @Test
    public void testContainsAllValues()
    {
        Filter expected =
            FilterBuilder.containsAllValues(createNormalizedTestAttribute());
        Filter filter = 
            FilterBuilder.containsAllValues(createTestAttribute());  
        assertNormalizedFilter(expected,filter);
    }
    @Test 
    public void testConnectorObject()
    {
        ConnectorObjectBuilder builder =
           new ConnectorObjectBuilder();
        builder.setName("myname");
        builder.setUid("myuid");
        builder.addAttribute(createTestAttribute());
        ConnectorObject v1 = builder.build();
        ConnectorObject v2 = createTestNormalizer().normalizeObject(v1);
        builder =
            new ConnectorObjectBuilder();
        builder.setName("myname");
        builder.setUid("myuid");
        builder.addAttribute(createNormalizedTestAttribute());
        ConnectorObject expected = builder.build();
        Assert.assertEquals(expected, v2);
        Assert.assertFalse(expected.equals(v1));
    }
    
    @Test
    public void testSyncDelta()
    {
        SyncDeltaBuilder builder =
            new SyncDeltaBuilder();
         builder.setDeltaType(SyncDeltaType.DELETE);
         builder.setToken(new SyncToken("mytoken"));
         builder.setUid(new Uid("myuid"));
         builder.addAttribute(createTestAttribute());
         SyncDelta v1 = builder.build();
         SyncDelta v2 = createTestNormalizer().normalizeSyncDelta(v1);
         builder =
             new SyncDeltaBuilder();
         builder.setDeltaType(SyncDeltaType.DELETE);
         builder.setToken(new SyncToken("mytoken"));
         builder.setUid(new Uid("myuid"));
         builder.addAttribute(createNormalizedTestAttribute());
         SyncDelta expected = builder.build();
         Assert.assertEquals(expected, v2);
         Assert.assertFalse(expected.equals(v1));
        
    }
    
}
