/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */
package com.forgerock.openicf.xml.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;

public class AttributeTypeUtil {

    public static Object createInstantiatedObject(String attrValue, String javaclass) {
        if (javaclass.equals(XmlHandlerUtil.STRING)) {
            String s = new String(attrValue);
            return s;
        }
        else if (javaclass.equals(XmlHandlerUtil.INT_PRIMITIVE)) {
            int i = new Integer(attrValue);
            return i;
        }
        else if (javaclass.equals(XmlHandlerUtil.INTEGER)) {
            Integer i = new Integer(attrValue);
            return i;
        }
        else if (javaclass.equals(XmlHandlerUtil.LONG)) {
            Long l = new Long(attrValue);
            return l;
        }
        else if (javaclass.equals(XmlHandlerUtil.LONG_PRIMITIVE)) {
            long l = new Long(attrValue);
            return l;
        }
        else if (javaclass.equals(XmlHandlerUtil.BOOLEAN)) {
            Boolean b = new Boolean(attrValue);
            return b;
        }
        else if (javaclass.equals(XmlHandlerUtil.BOOLEAN_PRIMITIVE)) {
            boolean b = new Boolean(attrValue);
            return b;
        }
        else if (javaclass.equals(XmlHandlerUtil.DOUBLE)) {
            Double d = new Double(attrValue);
            return d;
        }
        else if (javaclass.equals(XmlHandlerUtil.DOUBLE_PRIMITIVE)) {
            double d = new Double(attrValue);
            return d;
        }
        else if (javaclass.equals(XmlHandlerUtil.FLOAT)) {
            Float f = new Float(attrValue);
            return f;
        }
        else if (javaclass.equals(XmlHandlerUtil.FLOAT_PRIMITIVE)) {
            float f = new Float(attrValue);
            return f;
        }
        else if (javaclass.equals(XmlHandlerUtil.CHARACTER)) {
            Character c = attrValue.charAt(0);
            return c;
        }
        else if (javaclass.equals(XmlHandlerUtil.CHAR_PRIMITIVE)) {
            char c = attrValue.charAt(0);
            return c;
        }
        else if (javaclass.equals(XmlHandlerUtil.BIG_INTEGER)) {
            BigInteger bi = new BigInteger(attrValue);
            return bi;
        }
        else if (javaclass.equals(XmlHandlerUtil.BIG_DECIMAL)) {
            BigDecimal bd = new BigDecimal(attrValue);
            return bd;
        }
        else if (javaclass.equals(XmlHandlerUtil.GUARDED_STRING)) {
            GuardedString gs = new GuardedString(attrValue.toCharArray());
            return gs;
        }
        else if (javaclass.equals(XmlHandlerUtil.GUARDED_BYTE_ARRAY)) {
            GuardedByteArray gb = new GuardedByteArray(attrValue.getBytes());
            return gb;
        }
        else if (javaclass.equals(XmlHandlerUtil.BYTE_ARRAY)) {
            byte[] b = attrValue.getBytes();
            return b;
        }
        else {
            return null;
        }
    }

    public static List<String> findAttributeValue(Attribute attr, AttributeInfo attrInfo) {

        String javaClass = attrInfo.getType().getName();
        List<String> results = new ArrayList<String>();
        String stringValue = null;

        if (attr != null && attr.getValue() != null && attrInfo != null) {
            for (Object value : attr.getValue()) {

                Class clazz;
                try {
                    if (attrInfo.getType().isPrimitive())
                        javaClass = convertPrimitiveToWrapper(attrInfo.getType().getName()).getName();
                        
                    clazz = Class.forName(javaClass);

                    if (!clazz.isInstance(value)) {
                        throw new IllegalArgumentException(attrInfo.getName() + " contains invalid type. Value(s) should be of type " + clazz.getName());
                    }
                }
                catch (ClassNotFoundException ex) {
                    throw ConnectorException.wrap(ex);
                }

                if (javaClass.equals("org.identityconnectors.common.security.GuardedString")) {
                    GuardedStringAccessor accessor = new GuardedStringAccessor();
                    GuardedString gs = AttributeUtil.getGuardedStringValue(attr);
                    gs.access(accessor);
                    stringValue = String.valueOf(accessor.getArray());
                } else if (javaClass.equals("org.identityconnectors.common.security.GuardedByteArray")) {
                    GuardedByteArrayAccessor accessor = new GuardedByteArrayAccessor();
                    GuardedByteArray gba = (GuardedByteArray) attr.getValue().get(0);
                    gba.access(accessor);
                    stringValue = new String(accessor.getArray());
                } else {
                    stringValue = value.toString();
                }
                
                results.add(stringValue);
            }
        }
        return results;
    }

    private static final Map<String, Class<?>> primitiveMap = new HashMap<String, Class<?>>();
    static {
        primitiveMap.put("boolean", Boolean.class);
        primitiveMap.put("byte", Byte.class);
        primitiveMap.put("short", Short.class);
        primitiveMap.put("char", Character.class);
        primitiveMap.put("int", Integer.class);
        primitiveMap.put("long", Long.class);
        primitiveMap.put("float", Float.class);
        primitiveMap.put("double", Double.class);
    }

    public static Class convertPrimitiveToWrapper(String name) {
        return primitiveMap.get(name);
    }
}