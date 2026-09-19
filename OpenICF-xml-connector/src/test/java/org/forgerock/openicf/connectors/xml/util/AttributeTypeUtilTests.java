/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.connectors.xml.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

public class AttributeTypeUtilTests {

    @Test
    public void testValidNumericValuesAreParsed() {
        assertEquals(AttributeTypeUtil.createInstantiatedObject("42", XmlHandlerUtil.INT_PRIMITIVE), 42);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("42", XmlHandlerUtil.INTEGER), Integer.valueOf(42));
        assertEquals(AttributeTypeUtil.createInstantiatedObject("42", XmlHandlerUtil.LONG), Long.valueOf(42L));
        assertEquals(AttributeTypeUtil.createInstantiatedObject("42", XmlHandlerUtil.LONG_PRIMITIVE), 42L);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("4.2", XmlHandlerUtil.DOUBLE), Double.valueOf(4.2));
        assertEquals(AttributeTypeUtil.createInstantiatedObject("4.2", XmlHandlerUtil.DOUBLE_PRIMITIVE), 4.2);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("4.2", XmlHandlerUtil.FLOAT), Float.valueOf(4.2f));
        assertEquals(AttributeTypeUtil.createInstantiatedObject("4.2", XmlHandlerUtil.FLOAT_PRIMITIVE), 4.2f);
    }

    @Test
    public void testMalformedNumericValueThrowsConnectorExceptionNotNumberFormatException() {
        String[] javaClasses = { XmlHandlerUtil.INT_PRIMITIVE, XmlHandlerUtil.INTEGER, XmlHandlerUtil.LONG,
            XmlHandlerUtil.LONG_PRIMITIVE, XmlHandlerUtil.DOUBLE, XmlHandlerUtil.DOUBLE_PRIMITIVE,
            XmlHandlerUtil.FLOAT, XmlHandlerUtil.FLOAT_PRIMITIVE };
        for (String javaClass : javaClasses) {
            try {
                AttributeTypeUtil.createInstantiatedObject("not-a-number", javaClass);
                fail("expected ConnectorException for javaClass " + javaClass);
            } catch (ConnectorException e) {
                // expected: the raw NumberFormatException must be wrapped, not propagated
            }
        }
    }
}
