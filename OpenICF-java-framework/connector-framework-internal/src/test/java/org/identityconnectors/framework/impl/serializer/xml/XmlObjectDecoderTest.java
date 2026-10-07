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
package org.identityconnectors.framework.impl.serializer.xml;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.testng.annotations.Test;

/**
 * A peer sending a corrupted numeric value on the wire must fail with a
 * ConnectorException naming what failed to decode, not a bare
 * NumberFormatException with no indication of where in the stream it came
 * from.
 */
public class XmlObjectDecoderTest {

    @Test
    public void decodesAValidInt() {
        String xml = SerializerUtil.serializeXmlObject(Integer.valueOf(42), true);
        assertEquals(SerializerUtil.deserializeXmlObject(xml, true), Integer.valueOf(42));
    }

    @Test
    public void rejectsAMalformedInt() {
        assertRejected(corrupt(SerializerUtil.serializeXmlObject(Integer.valueOf(42), true), "42"), "int");
    }

    @Test
    public void rejectsAMalformedLong() {
        assertRejected(corrupt(SerializerUtil.serializeXmlObject(Long.valueOf(42L), true), "42"), "long");
    }

    @Test
    public void rejectsAMalformedDouble() {
        assertRejected(corrupt(SerializerUtil.serializeXmlObject(Double.valueOf(4.2), true), "4.2"), "double");
    }

    @Test
    public void rejectsAMalformedFloat() {
        assertRejected(corrupt(SerializerUtil.serializeXmlObject(Float.valueOf(4.5f), true), "4.5"), "float");
    }

    @Test
    public void rejectsAMalformedByte() {
        assertRejected(corrupt(SerializerUtil.serializeXmlObject(Byte.valueOf((byte) 42), true), "42"), "byte");
    }

    /**
     * An element with no text node decodes to null, which Double, Float and
     * Byte parsing reject with a NullPointerException rather than a
     * NumberFormatException.
     */
    @Test
    public void rejectsAnEmptyDouble() {
        assertRejectedEmpty(empty(SerializerUtil.serializeXmlObject(Double.valueOf(4.2), true), "4.2"), "double");
    }

    @Test
    public void rejectsAnEmptyFloat() {
        assertRejectedEmpty(empty(SerializerUtil.serializeXmlObject(Float.valueOf(4.5f), true), "4.5"), "float");
    }

    @Test
    public void rejectsAnEmptyByte() {
        assertRejectedEmpty(empty(SerializerUtil.serializeXmlObject(Byte.valueOf((byte) 42), true), "42"), "byte");
    }

    @Test
    public void rejectsAnEmptyInt() {
        assertRejectedEmpty(empty(SerializerUtil.serializeXmlObject(Integer.valueOf(42), true), "42"), "int");
    }

    @Test
    public void rejectsAnEmptyLong() {
        assertRejectedEmpty(empty(SerializerUtil.serializeXmlObject(Long.valueOf(42L), true), "42"), "long");
    }

    private static void assertRejected(String xml, String type) {
        try {
            SerializerUtil.deserializeXmlObject(xml, true);
            fail("Expected the malformed value to be rejected");
        } catch (ConnectorException e) {
            assertEquals(e.getMessage(), "Malformed " + type + " value on the wire: 'not-a-number'");
            assertTrue(e.getCause() instanceof NumberFormatException, String.valueOf(e.getCause()));
        }
    }

    private static void assertRejectedEmpty(String xml, String type) {
        try {
            SerializerUtil.deserializeXmlObject(xml, true);
            fail("Expected the empty value to be rejected");
        } catch (ConnectorException e) {
            assertEquals(e.getMessage(), "Malformed " + type + " value on the wire: empty element");
        }
    }

    /**
     * Replaces the encoded numeric payload of a valid wire message with a
     * non-numeric string, so the rest of the document stays schema-valid and
     * only the value under test is corrupted.
     */
    private static String corrupt(String validXml, String encodedValue) {
        return replaceValue(validXml, encodedValue, "not-a-number");
    }

    /**
     * Removes the encoded numeric payload of a valid wire message, leaving an
     * element with no text node, which the DTD's #PCDATA content allows.
     */
    private static String empty(String validXml, String encodedValue) {
        return replaceValue(validXml, encodedValue, "");
    }

    private static String replaceValue(String validXml, String encodedValue, String replacement) {
        String changed = validXml.replace(">" + encodedValue + "<", ">" + replacement + "<");
        if (changed.equals(validXml)) {
            throw new IllegalStateException("Could not locate '" + encodedValue
                    + "' in the serialized XML to replace it: " + validXml);
        }
        return changed;
    }
}
