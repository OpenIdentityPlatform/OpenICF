/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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
	@Test
	public void testArrayToHashtableFail() {
		try {
            String[] entries2 = { "a=A", "b=" };
            JNDIUtil.arrayToHashtable(entries2, null);
            fail();
        } catch (RuntimeException e) {
            //expected
        }
        try {
            String[] entries2 = { "a=A", "=" };
            JNDIUtil.arrayToHashtable(entries2, null);
            fail();
        } catch (RuntimeException e) {
            //expected
        }
        try {
            String[] entries2 = { "a=A", "=B" };
            JNDIUtil.arrayToHashtable(entries2, null);
            fail();
        } catch (RuntimeException e) {
            //expected
        }
		
	}
	
    /**
     * test for testArrayToHashtable fail
     */
    @Test
    public void testArrayToHashtableNull() {
        JNDIUtil.arrayToHashtable(null,null);
        String[] entries2 = {};
        JNDIUtil.arrayToHashtable(entries2,null);
        String[] entries3 = {null,null};
        JNDIUtil.arrayToHashtable(entries3,null);
        String[] entries4 = {""," "};
        JNDIUtil.arrayToHashtable(entries4,null);
        
    }
	

}
