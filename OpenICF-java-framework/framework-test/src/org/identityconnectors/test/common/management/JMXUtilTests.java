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
package org.identityconnectors.test.common.management;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.identityconnectors.common.management.BaseDynamicMBean;
import org.identityconnectors.common.management.JMXUtil;


import static org.junit.Assert.*;

public class JMXUtilTests {

    // ========================================================================
    // Tests
    // ========================================================================
    @org.junit.Test
    public void testGetMBeanServer() throws Exception {
        // this should return the ProxyMBean server the has the default
        // domain of IDM for all its objects..
        assertNotNull(JMXUtil.getMBeanServer());
    }

    @org.junit.Test
    public void testRegisterObject() throws Exception {
        /** Test w/ MBeanObject Interface * */
        // test that its registered
        TestMBean mb = new TestMBean();
        JMXUtil.register(mb.getObjectName(), mb);
        assertTrue(JMXUtil.isRegistered(mb.getObjectName()));
        // test this its unregistered..
        JMXUtil.unregister(mb.getObjectName());
        assertFalse(JMXUtil.isRegistered(mb.getObjectName()));
        /** Test w/o MBeanObject Interface * */
        JMXUtil.register(mb.getObjectName(), mb);
        assertTrue(JMXUtil.isRegistered(mb.getObjectName()));
        // test this its unregistered..
        JMXUtil.unregister(mb.getObjectName());
        assertFalse(JMXUtil.isRegistered(mb.getObjectName()));
    }

    /**
     * Simple MBean for testing of the register methods..
     */
    static class TestMBean extends AbstractMBean {
        public static String OBJECT_NAME = "type=testMBean";

        public ObjectName getObjectName() throws MalformedObjectNameException {
            return JMXUtil.newObjectName(OBJECT_NAME);
        }
    }

    /**
     * Attempt to keep the retain some of the comminality of the objects.
     * Includes some refresh logic to make sure the object is not repeatedly
     * refreshed.
     */
    static abstract class AbstractMBean extends BaseDynamicMBean {
        // ===========================================================
        // Abstract Methods..
        // ===========================================================
        abstract public ObjectName getObjectName()
                throws MalformedObjectNameException;
    }
}
