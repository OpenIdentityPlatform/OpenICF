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
        String xml = corrupt(SerializerUtil.serializeXmlObject(Integer.valueOf(42), true), "42");
        try {
            SerializerUtil.deserializeXmlObject(xml, true);
            fail("Expected the malformed value to be rejected");
        } catch (ConnectorException expected) {
            // expected: not a bare NumberFormatException
        }
    }

    @Test
    public void rejectsAMalformedLong() {
        String xml = corrupt(SerializerUtil.serializeXmlObject(Long.valueOf(42L), true), "42");
        try {
            SerializerUtil.deserializeXmlObject(xml, true);
            fail("Expected the malformed value to be rejected");
        } catch (ConnectorException expected) {
            // expected
        }
    }

    @Test
    public void rejectsAMalformedDouble() {
        String xml = corrupt(SerializerUtil.serializeXmlObject(Double.valueOf(4.2), true), "4.2");
        try {
            SerializerUtil.deserializeXmlObject(xml, true);
            fail("Expected the malformed value to be rejected");
        } catch (ConnectorException expected) {
            // expected
        }
    }

    /**
     * Replaces the encoded numeric payload of a valid wire message with a
     * non-numeric string, so the rest of the document stays schema-valid and
     * only the value under test is corrupted.
     */
    private static String corrupt(String validXml, String encodedValue) {
        String corrupted = validXml.replace(">" + encodedValue + "<", ">not-a-number<");
        if (corrupted.equals(validXml)) {
            throw new IllegalStateException("Could not locate '" + encodedValue
                    + "' in the serialized XML to corrupt it: " + validXml);
        }
        return corrupted;
    }
}
