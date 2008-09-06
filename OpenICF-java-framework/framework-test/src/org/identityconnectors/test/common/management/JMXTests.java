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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.identityconnectors.common.management.JMXUtil;


import static org.junit.Assert.*;

/**
 * Base class for testing the MBeans in the system.
 */
public abstract class JMXTests
{
    // ========================================================================
    // Abstract Methods..
    // ========================================================================
    /**
     * Return the string object name of the MBean.
     */
    protected abstract String getObjectName();
    /**
     * Register the MBean for testing..
     */
    protected abstract void register()
        throws Exception;
    /**
     * Used for testing the existence of the attributes.
     */
    protected abstract String[] getAttributeNames();
    // ========================================================================
    // Test Methods..
    // ========================================================================
    /**
     * Tests if the register works for the MBean..
     */
    public void testRegistered()
        throws Exception
    {
        final String ERR = "The MBean " + getObjectName()
                + "should be registered";
        // register the MBean
        register();
        // Make sure its actually registered..
        assertTrue(ERR, isRegistered());
    }
    /**
     * Tests the each attribute we expect is there..
     */
    public void testAttributeExistance()
        throws Exception
    {
        // register the MBean..
        register();
        // check that we don't see extra attributes..
        MBeanInfo info = getMBeanInfo();
        MBeanAttributeInfo[] attrInfos = info.getAttributes();
        // create a name list..
        Map<String,Object> attrMap = new HashMap<String,Object>();
        for (int i = 0; i < attrInfos.length; i++) {
            attrMap.put(attrInfos[i].getName(), null);
        }
        // test that each attribute exists and is readable..
        String[] attrs = getAttributeNames();
        for (int i = 0; i < attrs.length; i++) {
            getAttribute(attrs[i]);
            attrMap.remove(attrs[i]);
        }
        // errupt at the fact there are attributes not being tested.
        if (attrMap.size() > 0) {
            StringBuffer buf = new StringBuffer();
            buf.append("The following attributes were not tested: ");
            boolean first = true;
            Iterator<String> iter = attrMap.keySet().iterator();
            while (iter.hasNext()) {
                if (!first) {
                    first = false;
                    buf.append(',');
                }
                buf.append(iter.next());
            }
            throw new Exception(buf.toString());
        }
    }

    // ========================================================================
    // Helper Methods..
    // ========================================================================
    /**
     * Determine if the MBean is registered.
     */
    protected boolean isRegistered()
    {
        return this.isRegistered(getObjectName());
    }
    /**
     * Determine if the MBean is registered based from its name.
     */
    protected boolean isRegistered(String objName)
    {
        return getMBeanServer().isRegistered(getObjectNameObj());
    }
    /**
     * Quick method for adding to extended classes.
     */
    protected MBeanServer getMBeanServer()
    {
        return JMXUtil.getMBeanServer();
    }
    /**
     * Quick conversion method to get the name of an object.
     */
    protected ObjectName getObjectNameObj()
    {
        ObjectName ret = null;
        try {
            String domain = JMXUtil.getMBeanServer().getDefaultDomain();
            ret = new ObjectName(domain + ':' + getObjectName());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ret;
    }
    protected Object getAttribute(String attrName)
        throws Exception
    {
        return getMBeanServer().getAttribute(getObjectNameObj(), attrName);
    }
    protected MBeanInfo getMBeanInfo()
        throws Exception
    {
        return JMXUtil.getMBeanServer().getMBeanInfo(getObjectNameObj());
    }
    protected static class SimpleNotificationListener implements NotificationListener
    {
        Notification _lastNotification;
        public SimpleNotificationListener()
        {
            _lastNotification= null;
        }

        public void handleNotification(Notification notification, Object handback)
        {
            _lastNotification= notification;
        }
        
        public Notification getLastNotification()
        {
            return _lastNotification;
        }
        
    }
    protected void addNotificationListener(NotificationListener listener)
        throws Exception
    {
       addNotificationListener(listener, null, null);
    }   
    protected void addNotificationListener(NotificationListener listener, NotificationFilter filter,
            Object handback)
        throws Exception
    {
        JMXUtil.getMBeanServer().addNotificationListener(getObjectNameObj(), listener, filter, handback);
    }
    protected void removeNotificationListener(NotificationListener listener)
        throws Exception
    {
        JMXUtil.getMBeanServer().removeNotificationListener(getObjectNameObj(), listener);
    }
    // ========================================================================
    // Debug Methods..
    // ========================================================================
    /**
     * Dump the info for the MBean we're trying test.
     */
    public void dump()
    {
        echo("\n>>> Getting the management information for the MBean");
        echo("    using the getMBeanInfo method of the MBeanServer");
        sleep(1000);
        MBeanInfo info = null;
        try {
            info = getMBeanInfo();
        }
        catch (Exception e) {
            echo("\t!!! Could not get MBeanInfo object!!!");
            e.printStackTrace();
            return;
        }
        echo("\nCLASSNAME: \t" + info.getClassName());
        echo("\nDESCRIPTION: \t" + info.getDescription());
        echo("\nATTRIBUTES");
        MBeanAttributeInfo[] attrInfo = info.getAttributes();
        if (attrInfo.length > 0) {
            for (int i = 0; i < attrInfo.length; i++) {
                echo(" ** NAME: \t" + attrInfo[i].getName());
                echo("    DESCR: \t" + attrInfo[i].getDescription());
                echo("    TYPE: \t" + attrInfo[i].getType() + "\tREAD: "
                        + attrInfo[i].isReadable() + "\tWRITE: "
                        + attrInfo[i].isWritable());
            }
        }
        else {
            echo(" ** No attributes **");
        }
        echo("\nCONSTRUCTORS");
        MBeanConstructorInfo[] constrInfo = info.getConstructors();
        for (int i = 0; i < constrInfo.length; i++) {
            echo(" ** NAME: \t" + constrInfo[i].getName());
            echo("    DESCR: \t" + constrInfo[i].getDescription());
            echo("    PARAM: \t" + constrInfo[i].getSignature().length
                    + " parameter(s)");
        }
        echo("\nOPERATIONS");
        MBeanOperationInfo[] opInfo = info.getOperations();
        if (opInfo.length > 0) {
            for (int i = 0; i < opInfo.length; i++) {
                echo(" ** NAME: \t" + opInfo[i].getName());
                echo("    DESCR: \t" + opInfo[i].getDescription());
                echo("    PARAM: \t" + opInfo[i].getSignature().length
                        + " parameter(s)");
            }
        }
        else {
            echo(" ** No operations ** ");
        }
        echo("\nNOTIFICATIONS");
        MBeanNotificationInfo[] notifInfo = info.getNotifications();
        if (notifInfo.length > 0) {
            for (int i = 0; i < notifInfo.length; i++) {
                echo(" ** NAME: \t" + notifInfo[i].getName());
                echo("    DESCR: \t" + notifInfo[i].getDescription());
            }
        }
        else {
            echo(" ** No notifications **");
        }
    }

    static void echo(String msg)
    {
        System.out.println(msg);
    }

    private static void sleep(int millis)
    {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
