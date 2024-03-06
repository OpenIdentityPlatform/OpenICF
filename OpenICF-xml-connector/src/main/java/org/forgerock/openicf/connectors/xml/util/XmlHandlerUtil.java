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

package org.forgerock.openicf.connectors.xml.util;

import com.sun.xml.xsom.XSSchema;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;

public class XmlHandlerUtil {

    // List of all supported classes in the framework
    public static final String STRING = "String";
    public static final String INT_PRIMITIVE = "int";
    public static final String INTEGER = "Integer";
    public static final String LONG_PRIMITIVE = "long";
    public static final String LONG = "Long";
    public static final String BOOLEAN_PRIMITIVE = "boolean";
    public static final String BOOLEAN = "Boolean";
    public static final String DOUBLE_PRIMITIVE = "double";
    public static final String DOUBLE = "Double";
    public static final String FLOAT_PRIMITIVE = "float";
    public static final String FLOAT = "Float";
    public static final String CHAR_PRIMITIVE = "char";
    public static final String CHARACTER = "Character";
    public static final String BIG_INTEGER = "BigInteger";
    public static final String BIG_DECIMAL = "BigDecimal";
    public static final String GUARDED_STRING = "GuardedString";
    public static final String GUARDED_BYTE_ARRAY = "GuardedByteArray";
    public static final String BYTE_ARRAY = "byte[]";
    public static final String BASE_64_BINARY = "base64Binary";

    // List of all supported operations
    public static final String CREATE = "CREATE";
    public static final String AUTHENTICATE = "AUTHENTICATE";
    public static final String DELETE = "DELETE";
    public static final String RESOLVEUSERNAME = "RESOLVEUSERNAME";
    public static final String SCHEMA = "SCHEMA";
    public static final String SCRIPTONCONNECTOR = "SCRIPTONCONNECTOR";
    public static final String SCRIPTONRESOURCE = "SCRIPTONRESOURCE";
    public static final String SEARCH = "SEARCH";
    public static final String SYNC = "SYNC";
    public static final String TEST = "TEST";
    public static final String UPDATEATTRIBUTEVALUES = "UPDATEATTRIBUTEVALUES";
    public static final String UPDATE = "UPDATE";

    // List of all flags
    public static final String NOT_CREATABLE = AttributeInfo.Flags.NOT_CREATABLE.name();
    public static final String NOT_UPDATEABLE = AttributeInfo.Flags.NOT_UPDATEABLE.name();
    public static final String NOT_READABLE = AttributeInfo.Flags.NOT_READABLE.name();
    public static final String NOT_RETURNED_BY_DEFAULT = AttributeInfo.Flags.NOT_RETURNED_BY_DEFAULT.name();

    public static void checkObjectType(ObjectClass objClass, XSSchema schema) {
        if (!schema.getElementDecls().containsKey(objClass.getObjectClassValue())) {
            throw new IllegalArgumentException("Object type: " + objClass.getObjectClassValue() + " is not supported.");
        }
    }
}