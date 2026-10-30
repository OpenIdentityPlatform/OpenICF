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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
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
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3", XmlHandlerUtil.BIG_INTEGER), new BigInteger("3"));
        assertEquals(AttributeTypeUtil.createInstantiatedObject("3.5", XmlHandlerUtil.BIG_DECIMAL), new BigDecimal("3.5"));
    }

    /**
     * A malformed numeric value in the XML data file must fail with a
     * ConnectorException naming the value and the target type, not a bare
     * NumberFormatException with no indication of which attribute it came
     * from.
     */
    @Test(dataProvider = "numericTypes")
    public void createInstantiatedObjectWrapsMalformedNumber(String type) {
        assertWrapsNumberFormatException("not-a-number", type);
    }

    @Test(dataProvider = "numericTypes")
    public void createInstantiatedObjectWrapsBlankNumber(String type) {
        assertWrapsNumberFormatException("", type);
    }

    private static void assertWrapsNumberFormatException(String value, String type) {
        try {
            AttributeTypeUtil.createInstantiatedObject(value, type);
            fail("ConnectorException expected for " + type);
        } catch (ConnectorException e) {
            assertEquals(e.getMessage(), "Value '" + value + "' is not a valid " + type);
            assertTrue(e.getCause() instanceof NumberFormatException);
        }
    }

    /** Each byte[] value is encoded once, with no null after it for the caller to write as an empty element. */
    @Test
    public void findAttributeValueEncodesEachByteArrayOnce() {
        AttributeInfo info = new AttributeInfoBuilder("jpegPhoto", byte[].class).setMultiValued(true).build();
        Attribute photo = AttributeBuilder.build("jpegPhoto", new byte[] { 1, 2, 3 }, new byte[] { 4, 5 });

        assertEquals(AttributeTypeUtil.findAttributeValue(photo, info), Arrays.asList("AQID", "BAU="));
    }

    /** Each value of a multi-valued guarded attribute is read from that value, not from the attribute's first one. */
    @Test(dataProvider = "guardedValues")
    public void findAttributeValueReadsEachGuardedValue(Class<?> type, Object first, Object second, List<String> expected) {
        AttributeInfo info = new AttributeInfoBuilder("secret", type).setMultiValued(true).build();
        Attribute secret = AttributeBuilder.build("secret", first, second);

        assertEquals(AttributeTypeUtil.findAttributeValue(secret, info), expected);
    }

    /**
     * A null value, first or not, is refused with a message that names the attribute as the schema spells it, not a
     * type (#190).
     */
    @Test(dataProvider = "nullValues")
    public void findAttributeValueRefusesANullValue(Attribute firstName) {
        AttributeInfo info = new AttributeInfoBuilder("firstname", String.class).setMultiValued(true).build();

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> AttributeTypeUtil.findAttributeValue(firstName, info));
        assertEquals(e.getMessage(), "firstname contains a null value");
    }

    /** A Map value is refused: createInstantiatedObject has no Map branch, so its text would read back as null. */
    @Test(dataProvider = "mapTypes")
    public void findAttributeValueRefusesAMapValue(Class<?> type) {
        AttributeInfo info = new AttributeInfoBuilder("settings", type).build();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("k", "v");
        Attribute settings = AttributeBuilder.build("settings", map);

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> AttributeTypeUtil.findAttributeValue(settings, info));
        assertEquals(e.getMessage(), "settings contains values of illegal type");
    }

    @org.testng.annotations.DataProvider
    public Object[][] nullValues() {
        return new Object[][] {
            { AttributeBuilder.build("firstname", (Object) null) },
            { AttributeBuilder.build("firstname", "a", null) },
            { AttributeBuilder.build("FirstName", (Object) null) },
        };
    }

    @org.testng.annotations.DataProvider
    public Object[][] mapTypes() {
        return new Object[][] { { Map.class }, { HashMap.class } };
    }

    @org.testng.annotations.DataProvider
    public Object[][] guardedValues() {
        return new Object[][] {
            { GuardedString.class, new GuardedString("a".toCharArray()), new GuardedString("b".toCharArray()),
                    Arrays.asList("a", "b") },
            { GuardedByteArray.class, new GuardedByteArray(new byte[] { 1, 2, 3 }),
                    new GuardedByteArray(new byte[] { 4, 5 }), Arrays.asList("AQID", "BAU=") },
        };
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
            { XmlHandlerUtil.BIG_INTEGER },
            { XmlHandlerUtil.BIG_DECIMAL },
        };
    }
}
