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
package org.identityconnectors.contract.data;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * unit test for GroovyDataProvider
 * 
 * @author David Adam
 * 
 * Note: if getting MissingMethodException, update imports in configfile.groovy
 * 
 */
public class GroovyDataProviderTest {
	private static final String NON_EXISTING_PROPERTY = "abcdefghi123asiosfjds";
	private static GroovyDataProvider gdp;

	@BeforeClass
	public static void setUp() {
		gdp = new GroovyDataProvider(null, null, null);
	}

	@AfterClass
	public static void tearDown() {
		gdp = null;
	}

	@Test
	public void testSimpleStr() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Assert
				.assertEquals(
						"If you think you can do a thing or think you can't do a thing, you're right",
						gdp.get("aSimpleString", "string", true));
	}

	@Test(expected = ObjectNotFoundException.class)
	public void testNonExistingProperty() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Object o = getProperty(gdp, NON_EXISTING_PROPERTY);// gdp.get(NON_EXISTING_PROPERTY);
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof ConfigObject);
		if (o instanceof ConfigObject) {
			ConfigObject co = (ConfigObject) o;
			Assert.assertEquals(0, co.size());
		}
	}

	@Test
	public void testSimpleMapAcquire() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Object o = getProperty(gdp, "sampleMap");// gdp.get("sampleMap");
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof Map);
		printMap(o);
	}

	@Test
	public void testDotInNameMapAcquire() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Object o = getProperty(gdp, "sampleMap.foo.bar");// .get("sampleMap.foo.bar");
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof Map);
		printMap(o);
	}

	@Test
	public void testRecursiveAcquire() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		// query for a property with non-existing prefix foo
		// the DataProvider should try to evaluate substrings of the property
		// name (divided by .)
		// and find "abc"
		Object o = getProperty(gdp, "foo.abc");// .get("foo.abc");
		Assert.assertNotNull(o);
		Assert.assertEquals("abc", o.toString());
		printMap(o);
	}

	@Test
	public void testDotNameString() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Object o = getProperty(gdp, "eggs.spam.sausage"); // .get("eggs.spam.sausage");
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof String);
		Assert.assertEquals("the spanish inquisition", o.toString());
	}

	@Test
	@Ignore
	/** this test is no longer used */
	public void testRandomMacroValue() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());

		Object response = getProperty(gdp, "randomValue"); // .get("randomValue");
		Object responseTwo = getProperty(gdp, "randomValue"); // .get("randomValue");

		Assert.assertNotNull(response);
		Assert.assertNotNull(responseTwo);

		Assert.assertTrue(response instanceof String
				&& responseTwo instanceof String);

		// two responses should return the same result, as random macro is
		// evaluated once, and than cached.
		Assert.assertEquals(response, responseTwo);
		System.out.println("response #1: " + response.toString()
				+ "\n response #2: " + responseTwo.toString());
	}

	@Test
	public void testRandom() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Object o = getProperty(gdp, "random"); // .get("eggs.spam.sausage");
		Object o2 = getProperty(gdp, "random"); // .get("eggs.spam.sausage");
		Assert.assertNotNull(o);
		Assert.assertEquals(o, o2);
	}

	@Test
	public void testRandomHierarchicalName() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());
		Object o = getProperty(gdp, "foo.bla.horror.random"); // .get("eggs.spam.sausage");
		Object o2 = getProperty(gdp, "foo.bla.horror.random"); // .get("eggs.spam.sausage");
		Assert.assertNotNull(o);
		Assert.assertEquals(o, o2);
	}

	@Test
	public void configFileMerger() {
		ConfigSlurper cs = new ConfigSlurper();

		ConfigObject co1 = cs.parse("a = '1'\n b = '2'");
		assert "1" == co1.getProperty("a");
		ConfigObject co2 = cs.parse("c='3'\n d='4'");
		assert "3" == co2.getProperty("c");

		ConfigObject f = GroovyDataProvider.mergeConfigObjects(co1, co2);
		assert "1" == f.getProperty("a");
		assert "3" == f.getProperty("c");
	}

	@Test
	public void configFileMergerAdvanced() {
		ConfigSlurper cs = new ConfigSlurper();

		ConfigObject lowPriorityConfig = cs.parse("a = '1'\n c = '2'");
		assert "1" == lowPriorityConfig.getProperty("a");
		ConfigObject highPriorityConfig = cs.parse("c='3'\n d='4'");
		assert "3" == highPriorityConfig.getProperty("c");

		ConfigObject f = GroovyDataProvider.mergeConfigObjects(
				lowPriorityConfig, highPriorityConfig);
		assert "1" == f.getProperty("a");
		assert "3" == f.getProperty("c");
	}

	@Test
	public void testNewRandomGenerator() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());

		Object o = getProperty(gdp, "randomNewAge");
		Object o2 = getProperty(gdp, "remus");

		Assert.assertNotNull(o);
		Assert.assertNotNull(o2);

		Assert.assertTrue(o instanceof Long);
		Assert.assertTrue(o2 instanceof Integer);

		System.out.println("long value: " + o.toString() + "   int value: "
				+ o2.toString());
	}

	@Test
	public void testMapAttributesNew() throws Exception {
		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());

		{
			Object o = getProperty(gdp, "attributeMap.string");
			Assert.assertNotNull(o);
			Assert.assertTrue(o instanceof String);
			Assert.assertTrue(o.toString() == "Good morning!");
		}

		// attributeMapSecond['stringSec'] = 'Good morning!'
		{
			Object o = getProperty(gdp, "attributeMapSecond.stringSec");
			Assert.assertNotNull(o);
			Assert.assertTrue(o instanceof String);
			Assert.assertTrue(o.toString() == "Good morning Mrs. Smith!");
		}

		Assert.assertTrue(new File(GroovyDataProvider.CONFIG_FILE_PATH)
				.exists());

		{
			Object o = getProperty(gdp, "Delete.account.@@NAME@@.string");
			Assert.assertNotNull(o);
			Assert.assertTrue(o instanceof String);
			Assert.assertTrue(o.toString() == "blaf");
		}

		{
			Object o = getProperty(gdp, "account.@@NAME@@.string");
			Assert.assertNotNull(o);
			Assert.assertTrue(o instanceof String);
			Assert.assertTrue(o.toString() == "blaf blaf");
		}

	}

	@Test
	public void literalsMacroReplacementTest() throws Exception {
		{
			Object o = getProperty(gdp, "Tfloat");
			Assert.assertNotNull(o);
			System.out.println(o.getClass().getName() + " " + o.toString());
			Assert.assertTrue(o instanceof Float);
		}
	}

	@Test
	public void multiStringListTest() throws Exception {

		// multi.Tstring=[Lazy.random("AAAAA##") , Lazy.random("AAAAA##")]
		Object o = getProperty(gdp, "multi.Tstring");
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof List);
		if (o instanceof List) {
			List l = (List) o;
			printList(l);
			System.out.println();
		}
	}

	@Test
	public void multiStringRecursiveTest() throws Exception {
		// multi.recursive.Tstring=[Lazy.random("AAAAA##") ,
		// [Lazy.random("AAAAA##") , Lazy.random("AAAAA##")]]

		Object o = getProperty(gdp, "multi.recursive.Tstring", false);
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof List);
		if (o instanceof List) {
			List l = (List) o;

			boolean recursiveListPresent = false;
			boolean recursiveListPresent2 = false;
			for (Object object : l) {
				if (object instanceof List) {
					recursiveListPresent = true;
					
					List lRec = (List) object;
					for (Object object2 : lRec) {
						if (object2 instanceof List) {
							recursiveListPresent2 = true;
						}
					}
				}
			}
			Assert.assertTrue(recursiveListPresent);
			Assert.assertTrue(recursiveListPresent2);

			printList(l);
			System.out.println("\n");
		}
	}

	@Test
	public void testByteArray() throws Exception {
		Object o = getProperty(gdp, "byteArray.test");
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof byte[]);
		if (o instanceof byte[]) {
			byte[] barr = (byte[]) o;
			System.out.println("Byte Array values:");
			for (byte b : barr) {
				System.out.print(Byte.toString(b) + ", ");
			}
			System.out.println();
		}
	}

	@Test
	public void characterTest() throws Exception {
		Object o = getProperty(gdp, "charTest");
		Assert.assertNotNull(o);
		Assert.assertTrue(o instanceof Character);
	}
	
	@Test (expected=ObjectNotFoundException.class)
	public void testNonExistingDefault() throws Exception {
		//should not return default vale
		Object o = getProperty(gdp, "connector.login");
	}

	/* ************* UTILITY METHODS ***************** */
	/**
	 * recursively print a list
	 */
	private void printList(List l) {
		System.out.print(" [ ");
		for (Object object : l) {
			if (object instanceof List) {
				List newL = (List) object;
				printList(newL);
			} else {
				System.out.print(object.toString() + ", ");
			}
		}
		System.out.print(" ] ");
	}

	private void printMap(Object o) {
		if (o instanceof Map) {
			Map m = (Map) o;
			Set tmpSet = m.entrySet();
			for (Object object : tmpSet) {
				System.out.print(object + " ");
			}
			System.out.println("\n");
		}
	}

	/**
	 * just call the get, and do some println for neat output. Maybe will erase
	 * this TODO
	 * 
	 * @param gdp2
	 * @param propertyName
	 * @return
	 * @throws Exception
	 */
	private Object getProperty(GroovyDataProvider gdp2, String propertyName)
			throws Exception {

		return getProperty(gdp2, propertyName, true);
	}

	private Object getProperty(GroovyDataProvider gdp2, String propertyName,
			boolean printOut) throws Exception {
		// doing the acquire of property!
		Object o = gdp2.get(propertyName, "string", true);

		// just informational output
		if (o instanceof Map) {
			Map m = (Map) o;
			String s = (m.size() > 0) ? "is present" : "is missing";
			if (printOut) {
				System.out.println("property " + propertyName + " " + s);
			}
		} else {
			String s = o.toString();
			if (printOut) {
				System.out.println("property " + propertyName + " : "
						+ ((s.length() > 0) ? s : "EMPTY STRING"));
			}
		}

		return o;
	}
}