/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.dbcommon;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.junit.*;

/**
 * Test for {@link PropertiesResolver}
 * @author kitko
 *
 */
public class PropertiesResolverTest {

	/**
	 * Test method for {@link org.identityconnectors.dbcommon.PropertiesResolver#resolveProperties(java.util.Properties, java.util.Properties)}.
	 */
	@Test
	public void testResolvePropertiesPropertiesProperties() {
		Properties p1 = new Properties();
		p1.setProperty("key1", "value1");
		p1.setProperty("key2", "value2");
		p1.setProperty("key7", "${key1}");
		Map<Object,Object> p1Copy = new HashMap<Object,Object>(p1); 
		Properties p2 = new Properties();
		p2.setProperty("key3", "${key1}");
		p2.setProperty("key4", "${key2}");
		Map<Object,Object> p2Copy = new HashMap<Object,Object>(p2);
		Properties p3 = PropertiesResolver.resolveProperties(p2,p1);
		assertEquals(p1Copy,p1);
		assertEquals(p2Copy,p2);
		assertEquals("value1",p3.getProperty("key3"));
		assertEquals("value2",p3.getProperty("key4"));
	}

	/**
	 * Simple test
	 * @throws InterruptedException 
	 */
	@Test
	public void testSimpleResolveProperties() throws InterruptedException {
		Properties properties = new Properties();
		properties.setProperty("key1","value1");
		properties.setProperty("key2", "Value of key1 is ${key1}");
		properties.setProperty("key3", "Reference ${key4}");
		properties = PropertiesResolver.resolveProperties(properties);
		Assert.assertEquals("Value of key1 is value1",properties.get("key2"));
		Assert.assertEquals("Reference ${key4}",properties.get("key3"));
	}
	
	/**
	 * Tesd adavanced
	 */
	@Test
	public void testAdvancedResolved(){
		Properties properties = new Properties();
		properties.setProperty("key1","value1");
		properties.setProperty("key2","${key1}");
		properties.setProperty("key3","value3");
		properties.setProperty("key4","${key2} ${key3}");
		properties = PropertiesResolver.resolveProperties(properties);
		Assert.assertEquals("value1 value3",properties.get("key4"));
	}
	
	/** Test that we will not fail on StackOverflowError */
	@Test
	public void testRecursion(){
		Properties properties = new Properties();
		properties.setProperty("key1","value1 ${key3}");
		properties.setProperty("key2","value2 ${key1}");
		properties.setProperty("key3","value3 ${key2}");
		properties = PropertiesResolver.resolveProperties(properties);
		System.out.println(properties.get("key3"));
		Assert.assertEquals("value3 value2 value1 RECURSION",properties.get("key3"));
	}
	
	

}
