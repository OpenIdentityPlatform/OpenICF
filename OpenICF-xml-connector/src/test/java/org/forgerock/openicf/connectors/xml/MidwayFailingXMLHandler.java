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
package org.forgerock.openicf.connectors.xml;

import com.sun.xml.xsom.XSSchemaSet;

import org.identityconnectors.framework.common.objects.Schema;
import org.w3c.dom.Element;

/** Makes update fail midway: the armed attribute's old elements are removed, then its new element is not made. */
final class MidwayFailingXMLHandler extends XMLHandlerImpl {

    private String failOn;

    MidwayFailingXMLHandler(XMLConfiguration config, Schema connSchema, XSSchemaSet xsdSchemas) {
        super(config, connSchema, xsdSchemas);
    }

    /** The next element made for {@code attributeName} fails; once. */
    void failOnce(String attributeName) {
        failOn = attributeName;
    }

    @Override
    Element createDomElement(String elementName, String value) {
        if (elementName.equals(failOn)) {
            failOn = null;
            throw new IllegalStateException("update fails midway at " + elementName);
        }
        return super.createDomElement(elementName, value);
    }
}
