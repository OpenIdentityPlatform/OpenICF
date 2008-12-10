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
package org.identityconnectors.test.framework.common.objects;

import static org.identityconnectors.framework.common.objects.AttributeInfoBuilder.build;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.junit.Test;

public class AttributeInfoUtilTests {

    @Test
    public void testIsMethod() {
        assertTrue(build("fad").is("Fad"));
        assertFalse(build("fadsf").is("f"));
    }

    @Test
    public void testFindMethod() {
        AttributeInfo expected = build("FIND_ME");
        Set<AttributeInfo> attrs = new HashSet<AttributeInfo>();
        attrs.add(build("fadsf"));
        attrs.add(build("fadsfadsf"));
        attrs.add(expected);
        assertEquals(expected, AttributeInfoUtil.find("FIND_ME", attrs));
        assertTrue(AttributeInfoUtil.find("Daffff", attrs) == null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPasswordBuild() {
        AttributeInfoBuilder.build(OperationalAttributes.PASSWORD_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCurrentPasswordBuild() {
        AttributeInfoBuilder.build(OperationalAttributes.CURRENT_PASSWORD_NAME);
    }
    
    @Test
    public void testRegularPassword() {
        AttributeInfoBuilder.build(OperationalAttributes.PASSWORD_NAME, GuardedString.class);
    }
    
}
