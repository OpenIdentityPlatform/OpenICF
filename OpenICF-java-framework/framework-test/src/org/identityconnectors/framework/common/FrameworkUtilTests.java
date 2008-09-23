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
package org.identityconnectors.framework.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.security.GuardedString;
import org.junit.Assert;
import org.junit.Test;

/**
 * Additional tests to cover the current functionality.
 */
public class FrameworkUtilTests {
    private static final Set<Class<?>> ATTR_SUPPORTED_TYPES;
    static {
        ATTR_SUPPORTED_TYPES = new HashSet<Class<?>>();
        ATTR_SUPPORTED_TYPES.add(String.class);
        ATTR_SUPPORTED_TYPES.add(long.class);
        ATTR_SUPPORTED_TYPES.add(Long.class);
        ATTR_SUPPORTED_TYPES.add(char.class);
        ATTR_SUPPORTED_TYPES.add(Character.class);
        ATTR_SUPPORTED_TYPES.add(double.class);
        ATTR_SUPPORTED_TYPES.add(Double.class);
        ATTR_SUPPORTED_TYPES.add(float.class);
        ATTR_SUPPORTED_TYPES.add(Float.class);
        ATTR_SUPPORTED_TYPES.add(int.class);
        ATTR_SUPPORTED_TYPES.add(Integer.class);
        ATTR_SUPPORTED_TYPES.add(boolean.class);
        ATTR_SUPPORTED_TYPES.add(Boolean.class);
        ATTR_SUPPORTED_TYPES.add(byte[].class);
        ATTR_SUPPORTED_TYPES.add(BigDecimal.class);
        ATTR_SUPPORTED_TYPES.add(BigInteger.class);
        ATTR_SUPPORTED_TYPES.add(GuardedString.class);
    }
    
	@Test
	public void testIsSupportedConfigurationType() {
		Assert.assertFalse(FrameworkUtil.isSupportedAttributeType(Timestamp.class));
		Assert.assertFalse(FrameworkUtil.isSupportedAttributeType(Date.class));
		// static boolean isSupportedConfigurationType(Class<?> clazz)
	}
	
	@Test
	public void testCheckAttributeType() {
		// static void checkAttributeType(final Class<?> clazz)
	}
	
	@Test
	public void testCheckAttributeValue() {
		// checkAttributeValue(Object value)
	}

	@Test
	public void testCheckOperationOptionType() {
		//static void checkOperationOptionType(final Class<?> clazz)
	}
	
	@Test
	public void testCheckOperationOptionValue() {
		// static void checkOperationOptionValue
	}
	
}
