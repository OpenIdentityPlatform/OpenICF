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
package org.identityconnectors.common.management;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.List;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.identityconnectors.common.logging.Log;


public class JMXUtil {
    /**
     * Logging for the class.
     */
    private static final Log log = Log.getLog(JMXUtil.class);
    /**
     * Default domain for use w/ the MBeanServer factory.
     */
    public static final String DEFAULT_DOMAIN = "IdentityConnectors";

    // ===============================================================
    // Retrieve the MBean Server
    // ===============================================================
    private static MBeanServer _mbs = null;

    /**
     * Provide a one-stop get your MBeanServer implementation.
     */
    public static synchronized MBeanServer getMBeanServer() {
        // cache the server..
        if (_mbs == null) {
            log.info("Getting first instance of the MBeanServer.");
            MBeanServer mbs = null;
            try {
                List<?> ls = MBeanServerFactory.findMBeanServer(null);
                // use the first one found..
                if (ls.size() > 0) {
                    mbs = (MBeanServer) ls.get(0);
                }
                // create on if one is not found..
                if (mbs == null) {
                    mbs = MBeanServerFactory.createMBeanServer(DEFAULT_DOMAIN);
                }
            } catch (Exception e) {
                // this is properly related to permissions don't fail just pass
                // 'null' to the proxy and it will return 'null' etc for all the
                // method execution so that it doesn't fail outright..
                log.warn(e, "Unable to get the MBeanServer using a proxy.");
            }
            // create the new proxy object..
            InvocationHandler h = new DomainProxy(mbs);
            ClassLoader cl = JMXUtil.class.getClassLoader();
            Class<?> intef[] = new Class<?>[] { MBeanServer.class };
            _mbs = (MBeanServer) Proxy.newProxyInstance(cl, intef, h);
        }
        return _mbs;
    }

    /**
     * Register a MBean instance w/ the MBeanServer.
     * 
     * @param objectName
     *            name to register the object as..
     * @param obj
     *            instance to register inside the server.
     * @return the combination of the objectName and obj instance.
     */
    public static ObjectInstance register(ObjectName objectName, Object obj) {
        // register the server w/ the mbean server
        try {
            MBeanServer mbs = JMXUtil.getMBeanServer();
            return mbs.registerMBean(obj, objectName);
        } catch (InstanceAlreadyExistsException e) {
            // replace the object..
            unregister(objectName);
            return register(objectName, obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unregister from the MBeanServer and return the instance if it exists.
     * 
     * @param objectName
     *            name of the instance.
     * @return iff the object instance return it after it is unregister else
     *         null.
     */
    public static ObjectInstance unregister(ObjectName objectName) {
        ObjectInstance ret = null;
        try {
            // get an instance of the MBeanServer
            MBeanServer mbs = JMXUtil.getMBeanServer();
            ret = mbs.getObjectInstance(objectName);
            mbs.unregisterMBean(objectName);
        } catch (InstanceNotFoundException e) {
            // do nothing but log it for study
            log.warn(e, "Instance not found.");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ret;
    }

    /**
     * Determine if the object is registered in the domain.
     */
    public static boolean isRegistered(ObjectName objectName) {
        try {
            // get an instance of the MBeanServer
            MBeanServer mbs = JMXUtil.getMBeanServer();
            return mbs.isRegistered(objectName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an ObjectName from the string provided.
     */
    public static ObjectName newObjectName(String name)
            throws MalformedObjectNameException {
        MBeanServer mbs = JMXUtil.getMBeanServer();
        String domain = mbs.getDefaultDomain();
        return ObjectName.getInstance(domain + ':' + name);
    }

    public static ObjectName newObjectName(Hashtable<String, String> props)
            throws MalformedObjectNameException {
        MBeanServer mbs = JMXUtil.getMBeanServer();
        String domain = mbs.getDefaultDomain();
        return ObjectName.getInstance(domain, props);
    }

    /**
     * Get the instance of the object added to the server.
     */
    public static ObjectInstance getObjectInstance(ObjectName objectName)
            throws MalformedObjectNameException, InstanceNotFoundException {
        MBeanServer mbs = getMBeanServer();
        return mbs.getObjectInstance(objectName);
    }

    /**
     * Insures that the default domain is what we want.
     */
    static class DomainProxy implements InvocationHandler {
        /**
         * Delegate server object.
         */
        private final MBeanServer _server;

        /**
         * Pass in the server instance to call methods on..
         */
        public DomainProxy(MBeanServer server) {
            _server = server;
        }

        /**
         * Make sure any call to {@link MBeanServer#getDefaultDomain()} returns
         * our default domain.
         * 
         * @see InvocationHandler#invoke(Object, Method, Object[])
         */
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            // quick exit for the method we'd like to override..
            if ("getDefaultDomain".equals(method.getName())) {
                return DEFAULT_DOMAIN;
            }
            // check to see if we have an instance of the server..
            Object ret = null;
            if (_server != null) {
                ret = method.invoke(_server, args);
            } else {
                // create a empty proxy to the MBeanServer if one is not
                // available and attempt to return the proper type..
                Class<?> type = method.getReturnType();
                if (Boolean.class.equals(type)) {
                    ret = Boolean.FALSE;
                } else if (Integer.class.equals(type)) {
                    ret = Integer.valueOf(0);
                }
                // everything else returns Object so just return null..
            }
            return ret;
        }
    }
}
