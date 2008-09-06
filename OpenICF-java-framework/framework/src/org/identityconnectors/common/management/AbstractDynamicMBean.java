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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeMBeanException;

/**
 * Implements the DynamicMBean interface for easy of use in our system.
 */
public abstract class AbstractDynamicMBean extends
        NotificationBroadcasterSupport implements DynamicMBean {
    // =======================================================================
    // Constants..
    // =======================================================================
    private final static Object[] NO_ARGS = new Object[0];
    private final static Class<?>[] NO_PARAMS = new Class[0];
    private final static String[] NO_STRPARAMS = new String[0];

    // =======================================================================
    // Abstract methods..
    // =======================================================================
    /**
     * Return all the attributes in an array of MBeanAttributeInfo objects.
     */
    abstract protected MBeanAttributeInfo[] createMBeanAttributeInfo();

    /**
     * Return all the constructors in an array of MBeanContructorInfo objects.
     */
    abstract protected MBeanConstructorInfo[] createMBeanConstructorInfo();

    /**
     * Return all the operations in an array of MBeanOperationInfo objects.
     */
    abstract protected MBeanOperationInfo[] createMBeanOperationInfo();

    /**
     * Return all the notifications in an array of MBeanNotificationInfo
     * objects.
     */
    abstract protected MBeanNotificationInfo[] createMBeanNotificationInfo();

    /**
     * Return a description of the MBean.
     */
    abstract protected String getMBeanDescription();

    // =======================================================================
    // Fields
    // =======================================================================
    MBeanInfo _info;
    Map<MethodComparator, Method> _methodCache;
    Map<String, MBeanAttributeInfo> _attributeCache;

    /**
     * {@inheritDoc}
     */
    public MBeanInfo getMBeanInfo() {
        // initialize the internal MBeanInfo..
        if (_info == null) {
            _info = createMBeanInfo();
        }
        return _info;
    }

    /**
     * {@inheritDoc}
     */
    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException,
            ReflectionException {
        // no null here :)
        assert attribute != null;

        // get the attribute info..
        MBeanAttributeInfo attr = getAttributeInfo(attribute);

        // make sure we can read it..
        if (!attr.isReadable()) {
            throw new ReflectionException(new NoSuchMethodException(
                    "No getter defined for attribute: " + attribute));
        }

        // determine the bean getter
        String prefix = (attr.isIs()) ? "is" : "get";
        try {
            // invoke to get the result
            return invoke(prefix + attr.getName(), NO_PARAMS, NO_ARGS);
        } catch (InvalidAttributeValueException x) {
            throw new ReflectionException(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();

        if (attributes != null) {
            for (int i = 0; i < attributes.length; ++i) {
                String attribute = attributes[i];
                try {
                    Object result = getAttribute(attribute);
                    list.add(new Attribute(attribute, result));
                } catch (AttributeNotFoundException ignored) {
                } catch (MBeanException ignored) {
                } catch (ReflectionException ignored) {
                }
            }
        }

        return list;
    }

    /**
     * {@inheritDoc}
     */
    public Object invoke(String method, Object[] arguments, String[] params)
            throws MBeanException, ReflectionException {
        assert method != null;

        // add defaults
        if (arguments == null) {
            arguments = NO_ARGS;
        }
        if (params == null) {
            params = NO_STRPARAMS;
        }

        MBeanInfo info = getMBeanInfo();
        MBeanOperationInfo[] opers = info.getOperations();
        if (opers == null || opers.length == 0) {
            throw new ReflectionException(new NoSuchMethodException(
                    "No operations defined for this MBean"));
        }

        for (int i = 0; i < opers.length; ++i) {
            MBeanOperationInfo oper = opers[i];
            if (oper == null)
                continue;

            if (method.equals(oper.getName())) {
                MBeanParameterInfo[] parameters = oper.getSignature();
                if (params.length != parameters.length)
                    continue;

                String[] signature = new String[parameters.length];
                for (int j = 0; j < signature.length; ++j) {
                    MBeanParameterInfo param = parameters[j];
                    signature[j] = (param == null) ? null : param.getType();
                }

                if (Arrays.equals(params, signature)) {
                    // Found the right operation
                    try {
                        Class<?>[] classes = loadClasses(getClass()
                                .getClassLoader(), signature);
                        return invoke(method, classes, arguments);
                    } catch (ClassNotFoundException x) {
                        throw new ReflectionException(x);
                    } catch (InvalidAttributeValueException x) {
                        throw new ReflectionException(x);
                    }
                }
            }
        }

        throw new ReflectionException(new NoSuchMethodException("Operation "
                + method + " with signature " + Arrays.asList(params)
                + " is not defined for this MBean"));
    }

    /**
     * {@inheritDoc}
     */
    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException,
            MBeanException, ReflectionException {
        assert attribute != null;

        MBeanAttributeInfo attr = getAttributeInfo(attribute.getName());
        if (!attr.isWritable()) {
            throw new ReflectionException(new NoSuchMethodException(
                    "No setter defined for attribute: " + attribute));
        }

        try {
            String signature = attr.getType();
            Class<?> cls = loadClass(getClass().getClassLoader(), signature);
            invoke("set" + attr.getName(), new Class[] { cls },
                    new Object[] { attribute.getValue() });
            return;
        } catch (ClassNotFoundException x) {
            throw new ReflectionException(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList list = new AttributeList();

        if (attributes != null) {
            for (int i = 0; i < attributes.size(); ++i) {
                Attribute attribute = (Attribute) attributes.get(i);
                try {
                    setAttribute(attribute);
                    list.add(attribute);
                } catch (AttributeNotFoundException ignored) {
                } catch (InvalidAttributeValueException ignored) {
                } catch (MBeanException ignored) {
                } catch (ReflectionException ignored) {
                }
            }
        }

        return list;
    }

    // =======================================================================
    // Helper methods..
    // =======================================================================
    /**
     * Creates the MBeanInfo for this instance, calling in succession factory
     * methods that the user can override. Information to create MBeanInfo are
     * taken calling the following methods:
     * <ul>
     * <li><code>{@link #createMBeanAttributeInfo}</code></li>
     * <li><code>{@link #createMBeanConstructorInfo}</code></li>
     * <li><code>{@link #createMBeanOperationInfo}</code></li>
     * <li><code>{@link #createMBeanNotificationInfo}</code></li>
     * <li><code>{@link #getMBeanClassName}</code></li>
     * <li><code>{@link #getMBeanDescription}</code></li>
     * </ul>
     */
    protected MBeanInfo createMBeanInfo() {
        MBeanAttributeInfo[] attrs = createMBeanAttributeInfo();
        MBeanConstructorInfo[] ctors = createMBeanConstructorInfo();
        MBeanOperationInfo[] opers = createMBeanOperationInfo();
        MBeanNotificationInfo[] notifs = createMBeanNotificationInfo();
        String cName = getMBeanClassName();
        String desc = getMBeanDescription();
        return new MBeanInfo(cName, desc, attrs, ctors, opers, notifs);
    }

    Object invoke(String name, Class<?>[] params, Object[] args)
            throws InvalidAttributeValueException, MBeanException,
            ReflectionException {
        Object ret = null;
        try {
            Method method = findMethod(name, params);
            ret = invokeMethod(method, args);
        } catch (NoSuchMethodException x) {
            throw new ReflectionException(x);
        } catch (IllegalAccessException x) {
            throw new ReflectionException(x);
        } catch (IllegalArgumentException x) {
            throw new InvalidAttributeValueException(x.toString());
        } catch (InvocationTargetException x) {
            Throwable t = x.getTargetException();
            if (t instanceof RuntimeException)
                throw new RuntimeMBeanException((RuntimeException) t);
            else if (t instanceof Exception)
                throw new MBeanException((Exception) t);
            throw new RuntimeErrorException((Error) t);
        }
        return ret;
    }

    /**
     * Adding a cache here to speed up the lookup of methods.. Map<MethodComparator,
     * Method>
     */
    Method findMethod(String name, Class<?>[] params)
            throws NoSuchMethodException {
        // consider adding a cache here to speed things up..
        Method ret = null;
        Map<MethodComparator, Method> methodCache = getMethodCache();
        MethodComparator mc = new MethodComparator(name, params);
        ret = (Method) methodCache.get(mc);
        if (ret == null) {
            // didn't find the method so cache it..
            ret = getClass().getMethod(name, params);
            methodCache.put(mc, ret);
        }
        return ret;
    }

    private static class MethodComparator {
        final String _name;
        final Class<?>[] _params;

        public MethodComparator(String name, Class<?>[] params) {
            assert name != null && params != null;
            _name = name;
            _params = params;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof MethodComparator)) {
                return false;
            }
            MethodComparator mc = (MethodComparator) obj;
            // test that the names are equal..
            if (!mc._name.equals(_name)) {
                return false;
            }
            // test that the arrays are equal..
            return Arrays.equals(mc._params, _params);
        }

        public int hashCode() {
            return _name.hashCode() & _params.hashCode();
        }
    }

    Object invokeMethod(Method method, Object[] args)
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        return method.invoke(this, args);
    }

    /**
     * This methods load a class given the classloader and the name of the
     * class, and work for extended names of primitive types.
     * <p>
     * If you try to do ClassLoader.loadClass("boolean") it barfs it cannot find
     * the class, so this method cope with this problem.
     */
    Class<?> loadClass(ClassLoader loader, String name)
            throws ClassNotFoundException {
        if (name == null)
            throw new ClassNotFoundException("null");

        name = name.trim();
        if (name.equals("boolean"))
            return boolean.class;
        else if (name.equals("byte"))
            return byte.class;
        else if (name.equals("char"))
            return char.class;
        else if (name.equals("short"))
            return short.class;
        else if (name.equals("int"))
            return int.class;
        else if (name.equals("long"))
            return long.class;
        else if (name.equals("float"))
            return float.class;
        else if (name.equals("double"))
            return double.class;
        else if (name.equals("java.lang.String"))
            return String.class;
        else if (name.equals("java.lang.Object"))
            return Object.class;
        else if (name.startsWith("[")) {
            // It's an array, figure out how many dimensions
            int dimension = 0;
            while (name.charAt(dimension) == '[') {
                ++dimension;
            }
            char type = name.charAt(dimension);
            Class<?> cls = null;
            switch (type) {
            case 'Z':
                cls = boolean.class;
                break;
            case 'B':
                cls = byte.class;
                break;
            case 'C':
                cls = char.class;
                break;
            case 'S':
                cls = short.class;
                break;
            case 'I':
                cls = int.class;
                break;
            case 'J':
                cls = long.class;
                break;
            case 'F':
                cls = float.class;
                break;
            case 'D':
                cls = double.class;
                break;
            case 'L':
                // Strip the semicolon at the end
                String n = name.substring(dimension + 1, name.length() - 1);
                cls = loadClass(loader, n);
                break;
            }

            if (cls == null) {
                throw new ClassNotFoundException(name);
            } else {
                int[] dim = new int[dimension];
                return Array.newInstance(cls, dim).getClass();
            }
        } else {
            if (loader != null)
                return loader.loadClass(name);
            else
                return Class.forName(name, false, null);
        }
    }

    /**
     * Returns the classes whose names are specified by the <code>names</code>
     * argument, loaded with the specified classloader.
     */
    Class<?>[] loadClasses(ClassLoader loader, String[] names)
            throws ClassNotFoundException {
        int n = names.length;
        Class<?>[] cls = new Class[n];
        for (int i = 0; i < n; ++i) {
            String name = names[i];
            cls[i] = loadClass(loader, name);
        }
        return cls;
    }

    /**
     * Retrieves the attribute info from the cache..
     */
    MBeanAttributeInfo getAttributeInfo(String attribute)
            throws AttributeNotFoundException {
        Object attr = getAttributeInfoMap().get(attribute);
        if (attr == null) {
            StringBuffer buf = new StringBuffer(64);
            buf.append("Attribute ");
            buf.append(attribute);
            buf.append(" not found");
            throw new AttributeNotFoundException(buf.toString());
        }
        return (MBeanAttributeInfo) attr;
    }

    /**
     * Caches the attribute list into a map for easy access.
     */
    Map<String, MBeanAttributeInfo> getAttributeInfoMap()
            throws AttributeNotFoundException {
        // quick exit if we're already initialized.
        if (_attributeCache != null) {
            return _attributeCache;
        }

        // detect if there are attributes to process..
        MBeanInfo info = getMBeanInfo();
        MBeanAttributeInfo[] attrs = info.getAttributes();
        if (attrs == null || attrs.length == 0) {
            throw new AttributeNotFoundException(
                    "No attributes defined for this MBean");
        }

        // add the attributes to a map for easy access
        _attributeCache = new HashMap<String, MBeanAttributeInfo>();
        for (int i = 0; i < attrs.length; i++) {
            MBeanAttributeInfo attr = attrs[i];
            if (attr == null) {
                continue;
            }
            _attributeCache.put(attr.getName(), attr);
        }
        return _attributeCache;
    }

    /**
     * Cache the methods invoke since reflection search is slow. Use a
     * WeakHashMap to make sure we're not taking up memory such that it can't be
     * reclaimed.
     */
    Map<MethodComparator, Method> getMethodCache() {
        if (_methodCache == null) {
            _methodCache = new WeakHashMap<MethodComparator, Method>();
        }
        return _methodCache;
    }

    /**
     * Get the full-qualified name of this class, using dots as package separators.
     */
    protected String getMBeanClassName() {
        return getClass().getName();
    }
}
