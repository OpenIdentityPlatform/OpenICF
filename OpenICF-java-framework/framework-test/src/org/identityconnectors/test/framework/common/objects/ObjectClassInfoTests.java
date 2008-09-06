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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.junit.Assert;
import org.junit.Test;


public class ObjectClassInfoTests {

    @Test(expected = IllegalArgumentException.class)
    public void testNoName() {
        new ObjectClassInfo(ObjectClass.ACCOUNT_NAME, new HashSet<AttributeInfo>());
    }
    
    @Test
    public void testBuilderAddsName() {
        ObjectClassInfo o = new ObjectClassInfoBuilder().build();
        Map<String, AttributeInfo> map = AttributeInfoUtil.toMap(o.getAttributeInfo());
        Assert.assertTrue(map.containsKey(Name.NAME));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testDuplicate() {
        ObjectClassInfoBuilder bld = new ObjectClassInfoBuilder();
        bld.addAttributeInfo(AttributeInfoBuilder.build("bob"));
        bld.addAttributeInfo(AttributeInfoBuilder.build("bob"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testAllDuplicate() {
        ObjectClassInfoBuilder bld = new ObjectClassInfoBuilder();
        Set<AttributeInfo> set = new HashSet<AttributeInfo>();
        set.add(AttributeInfoBuilder.build("bob"));
        set.add(AttributeInfoBuilder.build("bob", int.class));
        bld.addAllAttributeInfo(set);
    }
}
