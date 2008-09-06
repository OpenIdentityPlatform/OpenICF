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

import java.util.ArrayList;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.identityconnectors.common.management.BaseDynamicMBean;
import org.junit.Test;


import static org.junit.Assert.*;

public class BaseDynamicMBeanTests
{
    // ========================================================================
    // Tests
    // ========================================================================
    @Test
    public void testRegistration()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);
    }

    @Test
    public void testGetAttibuteForIsAttribute()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);
        Object result = server.getAttribute(name, "Running");
        if (!((Boolean) result).booleanValue())
            fail("getAttribute does not work");
        
        // test caching..
        for (int i=0; i<20; i++) {
            result = server.getAttribute(name, "Running");
            if (!((Boolean) result).booleanValue())
                fail("getAttribute does not work");
        }
    }

    @Test
    public void testGetAttibuteForGetAttribute()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);
        Object result = server.getAttribute(name, "Name");
        assertEquals(result, mbean.getName());
    }

    @Test
    public void testGetAttibuteForPrimitiveType()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        DynamicDerived mbean = new DynamicDerived();
        server.registerMBean(mbean, name);
        Integer result = (Integer) server.getAttribute(name, "Status");
        assertEquals(result.intValue(), mbean.getStatus());
    }

    @Test
    public void testSetAttibute()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);

        String value = "simon";
        server.setAttribute(name, new Attribute("Name", value));

        assertEquals(value, mbean.getName());

        Object result = server.getAttribute(name, "Name");
        assertEquals(result, value);
    }

    @Test
    public void testSetAttributeWithPrimitiveType()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        DynamicDerived mbean = new DynamicDerived();
        server.registerMBean(mbean, name);

        Integer value = new Integer(13);
        server.setAttribute(name, new Attribute("Status", value));

        Integer result = (Integer) server.getAttribute(name, "Status");
        assertEquals(result.intValue(), value.intValue());
    }

    @Test
    public void testSetAttributeWithNullValue()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);

        String value = null;
        server.setAttribute(name, new Attribute("Name", value));

        assertEquals(value, mbean.getName());

        Object result = server.getAttribute(name, "Name");
        assertEquals(result, value);
    }

    @Test
    public void testOperation()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);

        String key = "key";
        Object value = new Object();
        List<?> list = (List<?>) server.invoke(name, "operation", new Object[] { key,
                value }, new String[] { String.class.getName(),
                Object.class.getName() });
        assertEquals(list.size(), 2);
        assertEquals(list.get(0), key);
        assertEquals(list.get(1), value);
    }

    @Test
    public void testInvocationOfMethodsNotPresentInMBeanInfo()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);

        try {
            server.getAttribute(name, "MBeanInfo");
            fail("getMBeanInfo should not be invocable");
        }
        catch (AttributeNotFoundException x) {
        }
    }

    @Test
    public void testInvocationOfNonExistingSetter()
        throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("domain", "mbean", "dynamic");
        Dynamic mbean = new Dynamic();
        server.registerMBean(mbean, name);

        try {
            server.setAttribute(name, new Attribute("Running", Boolean.FALSE));
            fail("getMBeanInfo should not be invocable");
        }
        catch (ReflectionException x) {
        }
    }

    public static class Dynamic
        extends BaseDynamicMBean
    {
        private String _name = "dummy";

        protected MBeanAttributeInfo[] createMBeanAttributeInfo()
        {
            return new MBeanAttributeInfo[] {
                    new MBeanAttributeInfo("Name", String.class.getName(),
                            "The name", true, true, false),
                    new MBeanAttributeInfo("Running", boolean.class.getName(),
                            "The running status", true, false, true) };
        }

        protected MBeanOperationInfo[] createMBeanOperationInfo()
        {
            return new MBeanOperationInfo[] { new MBeanOperationInfo(
                    "operation", "An operation", new MBeanParameterInfo[] {
                            new MBeanParameterInfo("key", String.class
                                    .getName(), "The key"),
                            new MBeanParameterInfo("value", Object.class
                                    .getName(), "The value") }, List.class
                            .getName(), MBeanOperationInfo.INFO) };
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public boolean isRunning()
        {
            return true;
        }

        public List<Object> operation(String key, Object value)
        {
            List<Object> list = new ArrayList<Object>();
            list.add(key);
            list.add(value);
            return list;
        }
    }

    public static class DynamicDerived
        extends Dynamic
    {
        private int _status;

        protected MBeanAttributeInfo[] createMBeanAttributeInfo()
        {
            MBeanAttributeInfo[] info = super.createMBeanAttributeInfo();
            MBeanAttributeInfo[] newInfo = new MBeanAttributeInfo[info.length + 1];
            System.arraycopy(info, 0, newInfo, 0, info.length);
            newInfo[info.length] = new MBeanAttributeInfo("Status", int.class
                    .getName(), "The status", true, true, false);
            return newInfo;
        }

        public MBeanInfo getMBeanInfo()
        {
            // Disable caching
            return createMBeanInfo();
        }

        public int getStatus()
        {
            return _status;
        }

        public void setStatus(int status)
        {
            _status = status;
        }
    }
}
