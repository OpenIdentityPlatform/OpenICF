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

import org.forgerock.openicf.connectors.xml.xsdparser.XSDAnnotationFactory;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.parser.XSOMParser;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SPIOperation;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;
import org.xml.sax.SAXException;

public class SchemaParserUtil {

    /**
     * Setup logging for the {@link SchemaParserUtil}.
     */
    private static final Log log = Log.getLog(SchemaParserUtil.class);

    public static XSSchemaSet parseXSDSchema(File file) {
        XSOMParser parser = new XSOMParser();

        try {
            parser.setAnnotationParser(new XSDAnnotationFactory());
            parser.parse(file);

            return parser.getResult();

        } catch (SAXException e) {
            String eMessage = "Failed to parse XSD-schema from file: " + file.getAbsolutePath();

            log.error(e, eMessage);
            throw new ConnectorIOException(eMessage, e);

        } catch (IOException e) {
            String eMessage = "Failed to read from file: " + file.getAbsolutePath();

            log.error(e, eMessage);
            throw new ConnectorIOException(eMessage, e);
        }
    }

    public static List<Class<? extends SPIOperation>> getSupportedOpClasses(List<String> supportedOpList) {
        List<Class<? extends SPIOperation>> list = new LinkedList<Class<? extends SPIOperation>>();

        for (String s : supportedOpList) {
            if (s.equals(XmlHandlerUtil.CREATE)) {
                list.add(CreateOp.class);

            } else if (s.equals(XmlHandlerUtil.AUTHENTICATE)) {
                list.add(AuthenticateOp.class);

            } else if (s.equals(XmlHandlerUtil.DELETE)) {
                list.add(DeleteOp.class);

            } else if (s.equals(XmlHandlerUtil.RESOLVEUSERNAME)) {
                list.add(ResolveUsernameOp.class);

            } else if (s.equals(XmlHandlerUtil.SCHEMA)) {
                list.add(SchemaOp.class);

            } else if (s.equals(XmlHandlerUtil.SCRIPTONCONNECTOR)) {
                list.add(ScriptOnConnectorOp.class);

            } else if (s.equals(XmlHandlerUtil.SCRIPTONRESOURCE)) {
                list.add(ScriptOnResourceOp.class);

            } else if (s.equals(XmlHandlerUtil.SEARCH)) {
                list.add(SearchOp.class);

            } else if (s.equals(XmlHandlerUtil.SYNC)) {
                list.add(SyncOp.class);

            } else if (s.equals(XmlHandlerUtil.TEST)) {
                list.add(TestOp.class);

            } else if (s.equals(XmlHandlerUtil.UPDATEATTRIBUTEVALUES)) {
                list.add(UpdateAttributeValuesOp.class);

            } else if (s.equals(XmlHandlerUtil.UPDATE)) {
                list.add(UpdateOp.class);
            }
        }
        return list;
    }

    public static Class<?> getJavaClassType(List<String> list) {
        
        for (int i = 0; i < list.size(); i++) {
            String fileString = list.get(i);

            if (fileString.contains("javaclass")) {
                String clasString = list.get(i + 1);

                try {
                    return Class.forName(clasString);

                } catch (ClassNotFoundException e) {
                    log.error(e, "Class {0} not found.", clasString);
                }
            }
        }
        return null;
    }

    public static Class<?> findJavaClassType(String s) {
        
        if (s != null) {
            if (s.equalsIgnoreCase(XmlHandlerUtil.BOOLEAN_PRIMITIVE)) {
                return boolean.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.STRING)) {
                return String.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.CHAR_PRIMITIVE)) {
                return char.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.INT_PRIMITIVE)) {
                return int.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.LONG_PRIMITIVE)) {
                return long.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.FLOAT_PRIMITIVE)) {
                return float.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.DOUBLE_PRIMITIVE)) {
                return double.class;

            } else if (s.equalsIgnoreCase(XmlHandlerUtil.BASE_64_BINARY)) {
                return byte[].class;
            }
        }

        return null;
    }

    public static Set<Flags> getFlags(List<String> list) {
        Set<Flags> flags = EnumSet.noneOf(Flags.class);

        for (String s : list) {
            if (s.equals(XmlHandlerUtil.NOT_CREATABLE)) {
                flags.add(Flags.NOT_CREATABLE);

            } else if (s.equals(XmlHandlerUtil.NOT_UPDATEABLE)) {
                flags.add(Flags.NOT_UPDATEABLE);

            } else if (s.equals(XmlHandlerUtil.NOT_READABLE)) {
                flags.add(Flags.NOT_READABLE);
                flags.add(Flags.NOT_RETURNED_BY_DEFAULT);

            } else if (s.equals(XmlHandlerUtil.NOT_RETURNED_BY_DEFAULT)) {
                flags.add(Flags.NOT_RETURNED_BY_DEFAULT);
            }
        }
        return flags;
    }
}
