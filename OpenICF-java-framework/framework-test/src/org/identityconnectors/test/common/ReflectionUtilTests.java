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
package org.identityconnectors.test.common;

import static org.identityconnectors.common.ReflectionUtil.containsInterface;
import static org.identityconnectors.common.ReflectionUtil.getAllInterfaces;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class ReflectionUtilTests {

    @Test
    public void functional() {
        // getInterfaces
        Set<Class<?>> set = getAllInterfaces(C1.class);
        assertTrue(set.contains(I1.class));
        assertTrue(set.contains(I2.class));
        assertFalse(set.contains(I3.class));
        // containsInterface
        assertTrue(containsInterface(C1.class, I1.class));
        assertTrue(containsInterface(C1.class, I2.class));
        assertFalse(containsInterface(C1.class, I3.class));
    }

    //=======================================================================
    // Helper Classes/Interfaces
    //=======================================================================
    interface I1 {

    }

    interface I2 {

    }
    
    interface I3 {
        
    }

    class C1 implements I1, I2 {

    }
    
    interface I5 extends I1 {
        
    }
    
    class C2 implements I5 {
        
    }
}
