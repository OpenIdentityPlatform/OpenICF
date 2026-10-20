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
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.testng.SkipException;
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

    /** A directory of its own in the test store directory, which is not under java.io.tmpdir. */
    private static Path newStoreDir() throws IOException {
        return Files.createDirectory(new File(XmlConnectorTestUtil.getRandomXMLFile().getPath() + ".d").toPath());
    }

    /** The copies of {@code store} in its directory: {@code <name>.<digits>.tmp}. */
    private static List<Path> copiesOf(Path store) throws IOException {
        List<Path> copies = new ArrayList<Path>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(store.getParent(), store.getFileName() + ".*.tmp")) {
            for (Path entry : entries) {
                copies.add(entry);
            }
        }
        return copies;
    }

    private static void requirePosix(Path path) throws IOException {
        if (!Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class)) {
            throw new SkipException("No POSIX permissions on " + path);
        }
    }

    private static String modeOf(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    @Test
    public void failedWriteLeavesTheFileAsItWas() throws Exception {
        Path store = newStoreDir().resolve("store.xml");
        Files.write(store, "<old/>\n".getBytes(StandardCharsets.UTF_8));
        Document document = parse("<r><a>x</a></r>");
        // An unpaired surrogate: no encoder can write it, so the write fails after it has started.
        document.getDocumentElement().appendChild(document.createTextNode("x\uD800y"));
        expectThrows(Exception.class, () -> XmlDocumentWriter.write(document, store.toFile()));
        assertEquals(new String(Files.readAllBytes(store), StandardCharsets.UTF_8), "<old/>\n");
        assertEquals(copiesOf(store), Collections.emptyList());
    }

    @Test
    public void copyIsPrivateAndNextToTheFileWhileItIsWritten() throws Exception {
        Path store = newStoreDir().resolve("store.xml");
        Files.write(store, "<old/>".getBytes(StandardCharsets.UTF_8));
        requirePosix(store);
        // Neither createTempFile's rw------- nor a umask-022 default: only a copied mode gives it.
        Files.setPosixFilePermissions(store, PosixFilePermissions.fromString("rw-r-----"));
        List<String> modesSeen = new ArrayList<String>();
        CountingDom dom = new CountingDom(() -> {
            try {
                if (modesSeen.isEmpty()) {
                    for (Path copy : copiesOf(store)) {
                        modesSeen.add(modeOf(copy));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        XmlDocumentWriter.write(dom.wrap(parse("<r><a>x</a></r>")), store.toFile());
        assertEquals(modesSeen, Collections.singletonList("rw-------"));
        assertEquals(modeOf(store), "rw-r-----");
        assertEquals(copiesOf(store), Collections.emptyList());
    }

    @Test
    public void newFileIsReadableByItsOwnerOnly() throws Exception {
        Path store = newStoreDir().resolve("store.xml");
        requirePosix(store.getParent());
        XmlDocumentWriter.write(parse("<r/>"), store.toFile());
        assertEquals(modeOf(store), "rw-------");
        assertEquals(copiesOf(store), Collections.emptyList());
    }

    @Test
    public void readOnlyFileIsRefusedAndLeftAlone() throws Exception {
        Path store = newStoreDir().resolve("store.xml");
        Files.write(store, "<old/>".getBytes(StandardCharsets.UTF_8));
        if (!store.toFile().setWritable(false, false) || Files.isWritable(store)) {
            store.toFile().setWritable(true, false);
            throw new SkipException("Cannot make " + store + " read-only here");
        }
        try {
            expectThrows(AccessDeniedException.class, () -> XmlDocumentWriter.write(parse("<r/>"), store.toFile()));
            assertEquals(new String(Files.readAllBytes(store), StandardCharsets.UTF_8), "<old/>");
            assertEquals(copiesOf(store), Collections.emptyList());
        } finally {
            store.toFile().setWritable(true, false);
        }
    }

    @Test
    public void storeInADirectoryThatCannotBeWrittenIsRefusedAndLeftAlone() throws Exception {
        Path dir = newStoreDir();
        Path store = dir.resolve("store.xml");
        Files.write(store, "<old/>".getBytes(StandardCharsets.UTF_8));
        if (!dir.toFile().setWritable(false, false) || Files.isWritable(dir)) {
            dir.toFile().setWritable(true, false);
            throw new SkipException("Cannot make " + dir + " read-only here");
        }
        try {
            expectThrows(AccessDeniedException.class, () -> XmlDocumentWriter.write(parse("<r/>"), store.toFile()));
            assertEquals(new String(Files.readAllBytes(store), StandardCharsets.UTF_8), "<old/>");
            assertEquals(copiesOf(store), Collections.emptyList());
        } finally {
            dir.toFile().setWritable(true, false);
        }
    }

    @Test
    public void fileReachedThroughALinkIsReplacedBehindTheLink() throws Exception {
        Path real = newStoreDir().resolve("store.xml");
        Files.write(real, "<old/>".getBytes(StandardCharsets.UTF_8));
        Path link = newStoreDir().resolve("link.xml");
        try {
            Files.createSymbolicLink(link, real);
        } catch (IOException | UnsupportedOperationException e) {
            throw new SkipException("Cannot create a symbolic link here: " + e);
        }
        XmlDocumentWriter.write(parse("<r><a>x</a></r>"), link.toFile());
        assertTrue(Files.isSymbolicLink(link));
        assertTrue(new String(Files.readAllBytes(real), StandardCharsets.UTF_8).contains("<a>x</a>"));
        assertEquals(copiesOf(real), Collections.emptyList());
        assertEquals(copiesOf(link), Collections.emptyList());
    }

    private static void createLink(Path link, Path to) {
        try {
            Files.createSymbolicLink(link, to);
        } catch (IOException | UnsupportedOperationException e) {
            throw new SkipException("Cannot create a symbolic link here: " + e);
        }
    }

    @Test
    public void danglingLinkKeepsTheLinkAndCreatesTheFileBehindIt() throws Exception {
        Path real = newStoreDir().resolve("store.xml");
        Path link = newStoreDir().resolve("link.xml");
        createLink(link, real);
        XmlDocumentWriter.write(parse("<r><a>x</a></r>"), link.toFile());
        assertTrue(Files.isSymbolicLink(link));
        assertTrue(Files.exists(real));
        assertTrue(new String(Files.readAllBytes(real), StandardCharsets.UTF_8).contains("<a>x</a>"));
        assertEquals(copiesOf(real), Collections.emptyList());
        assertEquals(copiesOf(link), Collections.emptyList());
    }

    @Test
    public void danglingLinkIntoAMissingDirectoryFailsAndKeepsTheLink() throws Exception {
        Path missing = newStoreDir().resolve("missing");
        Path real = missing.resolve("store.xml");
        Path link = newStoreDir().resolve("link.xml");
        createLink(link, real);
        expectThrows(IOException.class, () -> XmlDocumentWriter.write(parse("<r/>"), link.toFile()));
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(Files.readSymbolicLink(link), real);
        assertEquals(copiesOf(link), Collections.emptyList());
        assertFalse(Files.exists(missing));
    }

    @Test(timeOut = 10_000L)
    public void linkCycleIsRefused() throws Exception {
        Path dir = newStoreDir();
        Path a = dir.resolve("a.xml");
        Path b = dir.resolve("b.xml");
        createLink(a, b);
        createLink(b, a);
        expectThrows(FileSystemException.class, () -> XmlDocumentWriter.write(parse("<r/>"), a.toFile()));
        assertTrue(Files.isSymbolicLink(a));
        assertTrue(Files.isSymbolicLink(b));
        assertEquals(copiesOf(a), Collections.emptyList());
        assertEquals(copiesOf(b), Collections.emptyList());
    }

    @Test
    public void relativeDanglingLinksAreFollowedFromEachLinksDirectory() throws Exception {
        Path dir = newStoreDir();
        Path link = dir.resolve("link.xml");
        Path middle = Files.createDirectory(dir.resolve("sub")).resolve("middle.xml");
        Path real = middle.resolveSibling("store.xml");
        createLink(link, dir.relativize(middle)); // "sub/middle.xml", relative to dir
        createLink(middle, middle.getParent().relativize(real)); // "store.xml", relative to dir/sub
        XmlDocumentWriter.write(parse("<r><a>x</a></r>"), link.toFile());
        assertTrue(Files.isSymbolicLink(link));
        assertTrue(Files.isSymbolicLink(middle));
        assertTrue(new String(Files.readAllBytes(real), StandardCharsets.UTF_8).contains("<a>x</a>"));
        assertEquals(copiesOf(real), Collections.emptyList());
    }

    @Test
    public void failedRenameLeavesNoCopy() throws Exception {
        Path store = newStoreDir().resolve("store.xml");
        Files.createDirectory(store);
        expectThrows(IOException.class, () -> XmlDocumentWriter.write(parse("<r><a>x</a></r>"), store.toFile()));
        assertTrue(Files.isDirectory(store));
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(store)) {
            assertFalse(entries.iterator().hasNext());
        }
        assertEquals(copiesOf(store), Collections.emptyList());
    }

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
    public void followingWithARootOutsideTheAncestorsEndsAtTheTop() throws Exception {
        Document document = parse("<r><a><x/></a><b/></r>");
        assertNull(following(only(document, "b"), only(document, "a")));
    }

    @Test
    public void normalizedDocumentIsLeftAlone() throws Exception {
        Document document = parse("<r><a>x</a><b/></r>");
        Node text = only(document, "a").getFirstChild();
        assertFalse(XmlDocumentWriter.normalizeText(document));
        assertSingleText(only(document, "a"), "x");
        assertSame(only(document, "a").getFirstChild(), text);
    }

    @Test
    public void whitespaceOnlyTextIsRemoved() throws Exception {
        Document document = parse("<r>\n  <a> \t&#13;&#10;</a>\n  <c/>\n  <b>x</b>\n</r>");
        assertTrue(XmlDocumentWriter.normalizeText(document));
        assertNull(only(document, "a").getFirstChild());
        for (Node n = document.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
            assertEquals(n.getNodeType(), Node.ELEMENT_NODE);
        }
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
        assertTrue(XmlDocumentWriter.normalizeText(document));
        assertSingleText(only(document, "a"), " x<y ");
    }

    @Test
    public void singleCdataBecomesATextNode() throws Exception {
        Document document = parse("<r><b><![CDATA[z]]></b></r>");
        assertTrue(XmlDocumentWriter.normalizeText(document));
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
    public void textInsideAnEntityReferenceIsLeftAlone() throws Exception {
        // The children of an entity reference are read-only; Xerces keeps them when references are not expanded.
        DocumentBuilderFactory factory = newFactory();
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(
                "<!DOCTYPE r [<!ENTITY e '<a/> <b/>x<![CDATA[y]]>'>]><r>&e;</r>")));
        Node reference = document.getDocumentElement().getFirstChild();
        assertEquals(reference.getNodeType(), Node.ENTITY_REFERENCE_NODE);
        XmlDocumentWriter.normalizeText(document);
        assertEquals(reference.getFirstChild().getNextSibling().getNodeValue(), " ");
        assertEquals(reference.getLastChild().getNodeType(), Node.CDATA_SECTION_NODE);
    }

    @Test
    public void textAfterAnEntityReferenceIsNormalized() throws Exception {
        DocumentBuilderFactory factory = newFactory();
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(
                "<!DOCTYPE r [<!ENTITY e 'v'>]><r>&e; <c> </c></r>")));
        XmlDocumentWriter.normalizeText(document);
        Node reference = document.getDocumentElement().getFirstChild();
        assertEquals(reference.getNodeType(), Node.ENTITY_REFERENCE_NODE);
        assertSame(reference.getNextSibling(), only(document, "c"));
        assertNull(only(document, "c").getFirstChild());
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
    public void unprefixedDomLevel1ElementIsInNoNamespace() throws Exception {
        // As the old serializer wrote it, whether the default namespace in scope is declared by an attribute or not.
        for (boolean declared : new boolean[] {false, true}) {
            Document document = newFactory().newDocumentBuilder().newDocument();
            Element root = document.createElementNS("urn:x", "a");
            if (declared) {
                root.setAttributeNS(XMLNS, "xmlns", "urn:x");
            }
            document.appendChild(root);
            root.appendChild(document.createElement("b"));
            File file = XmlConnectorTestUtil.getRandomXMLFile();
            XmlDocumentWriter.write(document, file);
            assertEquals(new String(Files.readAllBytes(file.toPath()), "UTF-8"), before(document), "declared: " + declared);
        }
    }

    @Test
    public void aPrefixIsDeclaredOncePerElementAsBefore() throws Exception {
        // Each child binds one prefix to two namespaces: by an xmlns attribute, by its own name or by
        // a prefixed attribute. Declaring both made the file unreadable. The connector builds no such DOM.
        Document document = newFactory().newDocumentBuilder().newDocument();
        Element root = document.createElementNS("urn:x", "a");
        document.appendChild(root);
        Element bySetAttribute = document.createElement("b");
        bySetAttribute.setAttribute("xmlns", "urn:y");
        Element bySetAttributeNS = document.createElement("b");
        bySetAttributeNS.setAttributeNS(XMLNS, "xmlns", "urn:y");
        Element namespaced = document.createElementNS("urn:x", "b");
        namespaced.setAttributeNS(XMLNS, "xmlns", "urn:y");
        Element prefixed = document.createElementNS("urn:x", "p:b");
        prefixed.setAttributeNS("urn:y", "p:c", "v");
        for (Element child : new Element[] {bySetAttribute, bySetAttributeNS, namespaced, prefixed}) {
            root.appendChild(child);
        }
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);
        assertEquals(new String(Files.readAllBytes(file.toPath()), "UTF-8"), before(document));
    }

    @Test
    public void twoXmlnsAttributesDeclareTheDefaultNamespaceOnce() throws Exception {
        // setAttributeNS does not find the attribute set with setAttribute, so Xerces keeps both and
        // puts the new one first. The first declaration wins.
        Document document = parse("<r/>");
        Element root = document.getDocumentElement();
        root.setAttribute("xmlns", "urn:y");
        root.setAttributeNS(XMLNS, "xmlns", "urn:z");
        assertEquals(root.getAttributes().getLength(), 2);
        File file = XmlConnectorTestUtil.getRandomXMLFile();
        XmlDocumentWriter.write(document, file);
        assertEquals(parseFile(file).getDocumentElement().getNamespaceURI(), "urn:z");
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
