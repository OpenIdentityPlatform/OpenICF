/**
 * 
 */
package org.identityconnectors.dbcommon;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

/**
 * @author kitko
 *
 */
public class JNDIUtilTest {

	/**
	 * Test method for {@link org.identityconnectors.dbcommon.JNDIUtil#arrayToHashtable(java.lang.String[], org.identityconnectors.framework.common.objects.ConnectorMessages)}.
	 */
	@Test
	public void testArrayToHashtableSuc() {
		String[] entries1 = {"a=A","b=B"};
		Map<String,String> res1 = new HashMap<String,String>();
		res1.put("a","A");res1.put("b","B");
		Assert.assertEquals(res1,JNDIUtil.arrayToHashtable(entries1,null));
	}
	
	/**
	 * test for testArrayToHashtable fail
	 */
	public void testArrayToHashtableFail() {
		try{
			String[] entries2 = {"a=A","b="};
			JNDIUtil.arrayToHashtable(entries2,null);
			fail();
		}catch(RuntimeException e){}
		try{
			String[] entries2 = {"a=A","="};
			JNDIUtil.arrayToHashtable(entries2,null);
			fail();
		}catch(RuntimeException e){}
		try{
			String[] entries2 = {"a=A","=B"};
			JNDIUtil.arrayToHashtable(entries2,null);
			fail();
		}catch(RuntimeException e){}
		
	}
	
	

}
