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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.zip.CRC32;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class XmlDocumentWriterTests {

    private static final String ICF = "http://openidm.forgerock.com/xml/ns/public/resource/openicf/resource-schema-1.xsd";
    private static final String RI = "http://openidm.forgerock.com/xml/ns/public/resource/instances/ef2bc95b-76e0-48e2-86d6-4d4f44d4e4a4";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XMLNS = "http://www.w3.org/2000/xmlns/";
    private static final String TRICKY = "  a>b&c<d \"q\" 'a' é中 \r\n\ttab ]]> 😀  ";

    @Test
    public void followingIsTheNextSibling() throws Exception {
        Document document = parse("<r><a><x/></a><b/></r>");
        assertSame(following(only(document, "a"), document), only(document, "b"));
    }

    @Test
    public void followingClimbsToTheNextSiblingOfAnAncestor() throws Exception {
        Document document = parse("<r><a><x/></a><b/></r>");
        assertSame(following(only(document, "x"), document), only(document, "b"));
    }

    @Test
    public void followingStopsAtRoot() throws Exception {
        Document document = parse("<r><a><x/></a><b/></r>");
        assertNull(following(only(document, "x"), only(document, "a")));
    }

    @Test
    public void normalizedDocumentIsLeftAlone() throws Exception {
        Document document = parse("<r><a>x</a><b/></r>");
        assertFalse(XmlDocumentWriter.normalizeText(document));
    }

    @Test
    public void whitespaceOnlyTextIsRemoved() throws Exception {
        Document document = parse("<r>\n  <a> \t&#13;&#10;</a>\n  <c/>\n  <b>x</b>\n</r>");
        boolean changed = XmlDocumentWriter.normalizeText(document);
        assertNull(only(document, "a").getFirstChild());
        for (Node n = document.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
            assertEquals(n.getNodeType(), Node.ELEMENT_NODE);
        }
        assertTrue(changed);
    }

    @Test
    public void paddedAndNonXmlSpaceValuesAreKept() throws Exception {
        // U+3000 is whitespace to Character.isWhitespace and String.isBlank, but not to XML.
        Document document = parse("<r><a>  x  </a><b>\u3000</b></r>");
        XmlDocumentWriter.normalizeText(document);
        assertSingleText(only(document, "a"), "  x  ");
        assertSingleText(only(document, "b"), "\u3000");
    }

    @Test
    public void whitespaceOnlyCdataIsRemoved() throws Exception {
        Document document = parse("<r><a><![CDATA[ \n ]]></a></r>");
        XmlDocumentWriter.normalizeText(document);
        assertNull(only(document, "a").getFirstChild());
    }

    @Test
    public void whitespaceOnlyRunOfSeveralNodesIsRemovedWhole() throws Exception {
        // The XPath used before #157 removed only the first node of such a run.
        Document document = parse("<r><a> <![CDATA[ ]]> </a></r>");
        XmlDocumentWriter.normalizeText(document);
        assertNull(only(document, "a").getFirstChild());
    }

    @Test
    public void adjacentTextAndCdataBecomeOneTextNode() throws Exception {
        Document document = parse("<r><a> <![CDATA[x<y]]> </a></r>");
        XmlDocumentWriter.normalizeText(document);
        assertSingleText(only(document, "a"), " x<y ");
    }

    @Test
    public void singleCdataBecomesATextNode() throws Exception {
        Document document = parse("<r><b><![CDATA[z]]></b></r>");
        XmlDocumentWriter.normalizeText(document);
        assertSingleText(only(document, "b"), "z");
    }

    @Test
    public void mergedRunKeepsItsPlace() throws Exception {
        Document document = parse("<r><x>a<![CDATA[b]]><!--c--></x></r>");
        XmlDocumentWriter.normalizeText(document);
        Node text = only(document, "x").getFirstChild();
        assertEquals(text.getNodeType(), Node.TEXT_NODE);
        assertEquals(text.getNodeValue(), "ab");
        assertEquals(text.getNextSibling().getNodeType(), Node.COMMENT_NODE);
        assertNull(text.getNextSibling().getNextSibling());
    }

    @Test
    public void outputIsTheSameAsBefore() throws Exception {
        Document document = parse("<icf:OpenICFContainer xmlns:icf='" + ICF + "' xmlns:ri='" + RI + "'>"
                + "<ri:__ACCOUNT__><icf:__NAME__>a</icf:__NAME__><ri:email>b</ri:email><ri:email/></ri:__ACCOUNT__>"
                + "</icf:OpenICFContainer>");
        XmlDocumentWriter.normalizeText(document);
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);

        assertEquals(new String(Files.readAllBytes(file.toPath()), "UTF-8"), before(document));
    }

    @Test
    public void writeReturnsTheChecksumOfTheFile() throws Exception {
        Document document = parse("<r><a>x</a></r>");
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        long written = XmlDocumentWriter.write(document, file);
        CRC32 crc = new CRC32();
        crc.update(Files.readAllBytes(file.toPath()));
        assertEquals(written, crc.getValue());
    }

    @Test
    public void siblingsDeclareTheirOwnNamespaces() throws Exception {
        Document document = newFactory().newDocumentBuilder().newDocument();
        Element root = document.createElementNS(null, "r");
        document.appendChild(root);
        root.appendChild(document.createElementNS("urn:1", "p:a"));
        root.appendChild(document.createElementNS("urn:1", "p:b"));
        assertReadsBackTheSame(document);
    }

    @Test
    public void cdataIsWrittenAsText() throws Exception {
        Document document = parse("<r><![CDATA[x<y]]></r>");
        assertReadsBackTheSame(document); // not normalized: write() sends the CDATA section itself
    }

    @Test
    public void entityReferencesAndDoctypeAreWrittenAsBefore() throws Exception {
        DocumentBuilderFactory factory = newFactory();
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(
                "<!DOCTYPE r [<!ENTITY e 'v<x/>'>]><r>&e;</r>")));
        assertEquals(document.getDocumentElement().getFirstChild().getNodeType(), Node.ENTITY_REFERENCE_NODE);
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);
        assertEquals(new String(Files.readAllBytes(file.toPath()), "UTF-8"), before(document));
    }

    @Test
    public void prefixedAttributeWithoutANamespaceTakesTheOneInScope() throws Exception {
        // XMLHandlerImpl.createDocument() sets xsi:schemaLocation with setAttribute, a DOM level 1 call.
        Document document = parse("<r xmlns:xsi='" + XSI + "'/>");
        document.getDocumentElement().setAttribute("xsi:schemaLocation", "urn:a a.xsd");
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);
        assertEquals(parseFile(file).getDocumentElement().getAttributeNS(XSI, "schemaLocation"), "urn:a a.xsd");
    }

    @Test
    public void defaultNamespaceUndeclarationIsKept() throws Exception {
        assertReadsBackTheSame(parse("<r xmlns='urn:d'><a xmlns=''/></r>"));
    }

    @Test
    public void namespacedAttributeWithoutAPrefixDeclaresNoDefaultNamespace() throws Exception {
        Document document = parse("<r/>");
        document.getDocumentElement().setAttributeNS("urn:x", "a", "v");
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);
        assertNull(parseFile(file).getDocumentElement().getNamespaceURI());
    }

    @Test
    public void createdDocumentReadsBackTheSame() throws Exception {
        // No xmlns:icf attribute on the root and none for the entries: the writer has to declare them itself.
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .getDOMImplementation().createDocument(ICF, "OpenICFContainer", null);
        Element root = document.getDocumentElement();
        root.setAttributeNS(XMLNS, "xmlns:xsi", XSI);
        root.setAttributeNS(XMLNS, "xmlns:ri", RI);
        root.setPrefix("icf");
        root.setAttribute("xsi:schemaLocation", RI + " schema.xsd");
        Element entry = document.createElementNS(RI, "__ACCOUNT__");
        entry.setPrefix("ri");
        Element name = document.createElementNS(ICF, "__NAME__");
        name.setPrefix("icf");
        name.setTextContent(TRICKY);
        Element empty = document.createElementNS(RI, "firstname");
        empty.setPrefix("ri");
        empty.setTextContent("");
        entry.appendChild(name);
        entry.appendChild(empty);
        entry.appendChild(document.createComment(" c "));
        root.appendChild(entry);

        assertReadsBackTheSame(document);
    }

    @Test
    public void handEditedDocumentReadsBackTheSame() throws Exception {
        Document document = parse("<?xml version='1.0'?><!--top--><icf:OpenICFContainer xmlns:icf='" + ICF
                + "' xmlns:ri='" + RI + "'>\n  <ri:__ACCOUNT__>\n    <icf:__NAME__><!--x-->joe</icf:__NAME__>\n"
                + "    <ri:email>a<?pi data?>b</ri:email>\n    <ri:lastname xmlns:ri='urn:other'>&#13;\t&#x1F600;]]&gt;</ri:lastname>\n"
                + "    <x:extra xmlns:x='urn:x' x:attr='v'/>\n  </ri:__ACCOUNT__>\n</icf:OpenICFContainer>");
        XmlDocumentWriter.normalizeText(document);
        assertReadsBackTheSame(document);
    }

    @Test
    public void commentsSplitTextRuns() throws Exception {
        Document document = parse("<r><a> <!--c--> </a></r>");
        XmlDocumentWriter.normalizeText(document);
        Node child = only(document, "a").getFirstChild();
        assertEquals(child.getNodeType(), Node.COMMENT_NODE);
        assertNull(child.getNextSibling());
    }

    @Test
    public void normalizeAndWriteNeverUseNodeLists() throws Exception {
        String xml = "<icf:OpenICFContainer xmlns:icf='" + ICF + "' xmlns:ri='" + RI + "'>\n"
                + "  <ri:__ACCOUNT__>\n    <icf:__NAME__>a</icf:__NAME__>\n  </ri:__ACCOUNT__>\n"
                + "  <ri:__ACCOUNT__>\n    <icf:__NAME__>b</icf:__NAME__>\n  </ri:__ACCOUNT__>\n</icf:OpenICFContainer>";

        // The probe sees Saxon's DOMSender, which is what dispose() used before.
        Document control = parse(xml);
        assertFalse(XercesNodeLists.used(control));
        new net.sf.saxon.TransformerFactoryImpl().newTransformer()
                .transform(new DOMSource(control), new StreamResult(new ByteArrayOutputStream()));
        assertTrue(XercesNodeLists.used(control));

        Document document = parse(xml);
        XmlDocumentWriter.normalizeText(document);
        XmlDocumentWriter.write(document, XmlConnectorTestUtil.getRandomXMLFile());
        assertFalse(XercesNodeLists.used(document));
    }

    @Test
    public void normalizeAndWriteMakeALinearNumberOfDomCalls() throws Exception {
        // The probe above sees only NodeLists; a walk that, say, counts the earlier siblings of
        // every child is quadratic without one. Twice the entries take about twice the calls.
        long small = domCallsToSave(1000);
        long large = domCallsToSave(2000);
        assertTrue(large < 3 * small, "1,000 entries: " + small + " DOM calls, 2,000 entries: " + large);
    }

    /** The DOM calls that normalizeText and write make on an indented container of {@code entries} entries. */
    private static long domCallsToSave(int entries) throws Exception {
        StringBuilder xml = new StringBuilder("<icf:OpenICFContainer xmlns:icf='" + ICF + "' xmlns:ri='" + RI + "'>");
        for (int i = 0; i < entries; i++) {
            xml.append("\n  <ri:__ACCOUNT__>\n    <icf:__NAME__>a<![CDATA[").append(i)
                    .append("]]></icf:__NAME__>\n  </ri:__ACCOUNT__>");
        }
        xml.append("\n</icf:OpenICFContainer>");
        CountingDom dom = new CountingDom();
        Document document = dom.wrap(parse(xml.toString()));
        XmlDocumentWriter.normalizeText(document);
        XmlDocumentWriter.write(document, XmlConnectorTestUtil.getRandomXMLFile());
        return dom.calls();
    }

    /**
     * What XMLHandlerImpl.dispose() wrote before #157 for a document whose text is already
     * normalized: the serializer half only, without the XPath that removed whitespace-only text.
     */
    private static String before(Document document) throws Exception {
        Transformer saxon = new net.sf.saxon.TransformerFactoryImpl().newTransformer();
        saxon.setOutputProperty(OutputKeys.INDENT, "yes");
        saxon.setOutputProperty(OutputKeys.METHOD, "xml");
        saxon.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        saxon.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        saxon.transform(new DOMSource(document), new StreamResult(out));
        return out.toString("UTF-8");
    }

    private static void assertReadsBackTheSame(Document document) throws Exception {
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);
        assertTrue(Files.size(file.toPath()) > 0);
        assertEquals(describe(parseFile(file)), describe(document));
    }

    private static Document parse(String xml) throws Exception {
        return newFactory().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Document parseFile(File file) throws Exception {
        Document document = newFactory().newDocumentBuilder().parse(file);
        XmlDocumentWriter.normalizeText(document); // drops the indentation the serializer added
        return document;
    }

    private static DocumentBuilderFactory newFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    private static Element only(Document document, String localName) {
        NodeList list = document.getElementsByTagNameNS("*", localName);
        assertEquals(list.getLength(), 1, localName);
        return (Element) list.item(0);
    }

    private static void assertSingleText(Element element, String value) {
        Node child = element.getFirstChild();
        assertNotNull(child);
        assertEquals(child.getNodeType(), Node.TEXT_NODE);
        assertEquals(child.getNodeValue(), value);
        assertNull(child.getNextSibling());
    }

    /** Elements by namespace and local name, non-xmlns attributes by namespace, local name and value, text, comments, PIs. */
    private static String describe(Node node) {
        StringBuilder out = new StringBuilder();
        describe(node, out);
        return out.toString();
    }

    private static void describe(Node node, StringBuilder out) {
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            switch (n.getNodeType()) {
                case Node.ELEMENT_NODE:
                    out.append('<').append(n.getNamespaceURI()).append('|').append(n.getLocalName());
                    NamedNodeMap attributes = n.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        Node a = attributes.item(i);
                        String name = a.getNodeName();
                        if (!name.equals("xmlns") && !name.startsWith("xmlns:")) {
                            String uri = a.getNamespaceURI() != null ? a.getNamespaceURI()
                                    : name.startsWith("xsi:") ? XSI : "";
                            out.append(' ').append(uri).append('|').append(name.substring(name.indexOf(':') + 1))
                                    .append("=[").append(a.getNodeValue()).append(']');
                        }
                    }
                    out.append('>');
                    describe(n, out);
                    out.append("</>");
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    out.append("[text:").append(n.getNodeValue()).append(']');
                    break;
                case Node.COMMENT_NODE:
                    out.append("[comment:").append(n.getNodeValue()).append(']');
                    break;
                case Node.PROCESSING_INSTRUCTION_NODE:
                    out.append("[pi:").append(n.getNodeName()).append(' ').append(n.getNodeValue()).append(']');
                    break;
                default:
                    break;
            }
        }
    }
}
