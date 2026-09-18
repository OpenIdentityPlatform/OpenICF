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

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

public class AttributeTypeUtilTest {

    @Test
    public void createInstantiatedObjectParsesEachNumericType() {
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3", XmlHandlerUtil.INT_PRIMITIVE), 3);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3", XmlHandlerUtil.INTEGER), 3);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3", XmlHandlerUtil.LONG_PRIMITIVE), 3L);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3", XmlHandlerUtil.LONG), 3L);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3.5", XmlHandlerUtil.DOUBLE_PRIMITIVE), 3.5);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3.5", XmlHandlerUtil.DOUBLE), 3.5);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3.5", XmlHandlerUtil.FLOAT_PRIMITIVE), 3.5f);
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3.5", XmlHandlerUtil.FLOAT), 3.5f);
    }

    /**
     * A malformed numeric value in the XML data file must fail with a
     * ConnectorException naming the value and the target type, not a bare
     * NumberFormatException with no indication of which attribute it came
     * from.
     */
    @Test(expectedExceptions = ConnectorException.class,
            dataProvider = "numericTypes")
    public void createInstantiatedObjectWrapsMalformedNumber(String type) {
        AttributeTypeUtil.createInstantiatedObject("not-a-number", type);
    }

    @Test(expectedExceptions = ConnectorException.class,
            dataProvider = "numericTypes")
    public void createInstantiatedObjectWrapsBlankNumber(String type) {
        AttributeTypeUtil.createInstantiatedObject("", type);
    }

    @org.testng.annotations.DataProvider
    public Object[][] numericTypes() {
        return new Object[][] {
            { XmlHandlerUtil.INT_PRIMITIVE },
            { XmlHandlerUtil.INTEGER },
            { XmlHandlerUtil.LONG_PRIMITIVE },
            { XmlHandlerUtil.LONG },
            { XmlHandlerUtil.DOUBLE_PRIMITIVE },
            { XmlHandlerUtil.DOUBLE },
            { XmlHandlerUtil.FLOAT_PRIMITIVE },
            { XmlHandlerUtil.FLOAT },
        };
    }
}
