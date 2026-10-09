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

import static org.forgerock.openicf.connectors.xml.util.XmlHandlerUtil.following;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * Writes the store document to its file in time linear in the size of the document.
 * <p>
 * Saxon's DOM wrappers walk a DOM through {@code NodeList.item(i)}, which turns quadratic on a
 * Xerces DOM once the document's node list cache gets into a bad state. Here both steps follow
 * sibling pointers, and Saxon's serializer only receives SAX events. A store the connector created
 * is written byte for byte as before. Namespace declarations written by hand come out in the order
 * of the element's attribute map (by name in Xerces), and a redeclaration of a binding already in
 * scope is kept; the old serializer put the element's own namespace first and dropped those.
 */
final class XmlDocumentWriter {

    private static final String XMLNS = "xmlns";

    private XmlDocumentWriter() {
    }

    /**
     * Removes whitespace-only text and turns every other run of text into a single Text node.
     * <p>
     * A run is a maximal sequence of adjacent Text and CDATA section siblings, which XPath sees as
     * one text node. A run that holds only XML whitespace (space, tab, CR, LF: what
     * {@code normalize-space} strips), CDATA included, is removed whole; the XPath used before,
     * {@code //text()[normalize-space(.) = '']}, removed only its first node. Any other run
     * becomes one Text node with the same value, which is what a reload of the written file gives.
     * Text inside an entity reference is left alone: the DOM makes it read-only.
     */
    static void normalizeText(Node root) {
        Node node = root.getFirstChild();
        while (node != null) {
            if (!isText(node)) {
                Node child = node.getNodeType() == Node.ENTITY_REFERENCE_NODE ? null : node.getFirstChild();
                node = child != null ? child : following(node, root);
                continue;
            }
            Node parent = node.getParentNode();
            Node after = node.getNextSibling();
            if (node.getNodeType() == Node.TEXT_NODE && (after == null || !isText(after))) {
                // A lone Text node, the usual case: nothing to merge.
                if (isXmlWhitespace(node.getNodeValue())) {
                    parent.removeChild(node);
                }
            } else {
                List<Node> run = new ArrayList<Node>();
                StringBuilder value = new StringBuilder();
                for (after = node; after != null && isText(after); after = after.getNextSibling()) {
                    run.add(after);
                    value.append(after.getNodeValue());
                }
                if (!isXmlWhitespace(value)) {
                    parent.insertBefore(parent.getOwnerDocument().createTextNode(value.toString()), node);
                }
                for (Node n : run) {
                    parent.removeChild(n);
                }
            }
            node = after != null ? after : following(parent, root);
        }
    }

    /**
     * Writes {@code document} to {@code file} through Saxon's serializer with the output settings
     * used before, and returns the CRC-32 of the bytes written.
     * <p>
     * Saxon's factory is created directly: the connector bundle embeds an old xml-apis, which lacks
     * the factory methods of newer JDKs and whose DOM classes the JDK's own transformer cannot take.
     */
    static long write(Document document, File file) throws IOException, TransformerException, SAXException {
        SAXTransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();
        TransformerHandler handler = factory.newTransformerHandler();
        Transformer serializer = handler.getTransformer();
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty(OutputKeys.METHOD, "xml");
        serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        CRC32 crc = new CRC32();
        try (OutputStream out = new CheckedOutputStream(new FileOutputStream(file), crc)) {
            handler.setResult(new StreamResult(out));
            NamespaceSupport namespaces = new NamespaceSupport();
            handler.startDocument();
            for (Node child = document.getFirstChild(); child != null; child = child.getNextSibling()) {
                send(child, handler, namespaces);
            }
            handler.endDocument();
        }
        return crc.getValue();
    }

    private static void send(Node node, TransformerHandler out, NamespaceSupport namespaces) throws SAXException {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                sendElement(node, out, namespaces);
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                char[] text = node.getNodeValue().toCharArray();
                out.characters(text, 0, text.length);
                break;
            case Node.COMMENT_NODE:
                char[] comment = node.getNodeValue().toCharArray();
                out.comment(comment, 0, comment.length);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                out.processingInstruction(node.getNodeName(), node.getNodeValue());
                break;
            case Node.ENTITY_REFERENCE_NODE:
                for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                    send(child, out, namespaces);
                }
                break;
            default:
                // Document types and the like were not written before either.
                break;
        }
    }

    private static void sendElement(Node element, TransformerHandler out, NamespaceSupport namespaces)
            throws SAXException {
        namespaces.pushContext();
        List<String> declared = new ArrayList<String>();
        NamedNodeMap map = element.getAttributes(); // an attribute map, not a child list
        for (int i = 0; i < map.getLength(); i++) {
            Node attribute = map.item(i);
            String name = attribute.getNodeName();
            if (XMLNS.equals(name)) {
                declare("", attribute.getNodeValue(), out, namespaces, declared);
            } else if (name.startsWith(XMLNS + ":")) {
                declare(name.substring(XMLNS.length() + 1), attribute.getNodeValue(), out, namespaces, declared);
            }
        }
        String qName = element.getNodeName();
        String uri = bind(prefixOf(qName), element.getNamespaceURI(), out, namespaces, declared);
        AttributesImpl attributes = new AttributesImpl();
        for (int i = 0; i < map.getLength(); i++) {
            Node attribute = map.item(i);
            String name = attribute.getNodeName();
            if (!XMLNS.equals(name) && !name.startsWith(XMLNS + ":")) {
                String prefix = prefixOf(name);
                String attributeUri = prefix.isEmpty() ? ""
                        : bind(prefix, attribute.getNamespaceURI(), out, namespaces, declared);
                attributes.addAttribute(attributeUri, localNameOf(name), name, "CDATA", attribute.getNodeValue());
            }
        }
        out.startElement(uri, localNameOf(qName), qName, attributes);
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            send(child, out, namespaces);
        }
        out.endElement(uri, localNameOf(qName), qName);
        for (String prefix : declared) {
            out.endPrefixMapping(prefix);
        }
        namespaces.popContext();
    }

    /**
     * Returns the namespace to use for {@code prefix}: the node's own, declared here if it is not
     * in scope yet. A DOM level 1 node has none: with a prefix it takes the namespace in scope, and
     * an element without one is in no namespace, as the old serializer wrote it.
     */
    private static String bind(String prefix, String uri, TransformerHandler out, NamespaceSupport namespaces,
            List<String> declared) throws SAXException {
        String inScope = namespaces.getURI(prefix);
        if (uri == null && !prefix.isEmpty()) {
            return inScope == null ? "" : inScope;
        }
        String own = uri == null ? "" : uri;
        if (!own.equals(inScope == null ? "" : inScope)) {
            declare(prefix, own, out, namespaces, declared);
        }
        return own;
    }

    private static void declare(String prefix, String uri, TransformerHandler out, NamespaceSupport namespaces,
            List<String> declared) throws SAXException {
        namespaces.declarePrefix(prefix, uri);
        out.startPrefixMapping(prefix, uri);
        declared.add(prefix);
    }

    private static String prefixOf(String qName) {
        int colon = qName.indexOf(':');
        return colon < 0 ? "" : qName.substring(0, colon);
    }

    private static String localNameOf(String qName) {
        return qName.substring(qName.indexOf(':') + 1);
    }

    private static boolean isText(Node node) {
        short type = node.getNodeType();
        return type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE;
    }

    private static boolean isXmlWhitespace(CharSequence value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return false;
            }
        }
        return true;
    }
}
