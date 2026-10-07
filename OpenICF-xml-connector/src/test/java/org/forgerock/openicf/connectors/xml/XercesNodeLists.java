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

import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;

import org.apache.xerces.dom.CoreDocumentImpl;
import org.apache.xerces.dom.ParentNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Tells whether anything walked a Xerces DOM through {@code NodeList.item(i)} or
 * {@code getLength()}: those calls allocate the node list caches whose free list makes such walks
 * quadratic (#157). Sibling-pointer walks allocate none.
 */
final class XercesNodeLists {

    private XercesNodeLists() {
    }

    static boolean used(Document document) throws Exception {
        assertTrue(document instanceof CoreDocumentImpl, document.getClass().getName());
        Field free = CoreDocumentImpl.class.getDeclaredField("fFreeNLCache");
        free.setAccessible(true);
        if (free.get(document) != null) {
            return true;
        }
        Field cache = ParentNode.class.getDeclaredField("fNodeListCache");
        cache.setAccessible(true);
        Node node = document;
        while (node != null) {
            if (node instanceof ParentNode && cache.get(node) != null) {
                return true;
            }
            Node next = node.getFirstChild();
            while (next == null && node != null) {
                next = node.getNextSibling();
                node = node.getParentNode();
            }
            node = next;
        }
        return false;
    }
}
