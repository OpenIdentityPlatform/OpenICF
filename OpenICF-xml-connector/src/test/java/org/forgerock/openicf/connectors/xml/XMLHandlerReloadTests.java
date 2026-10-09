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

import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.forgerock.openicf.connectors.xml.query.QueryBuilder;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.DOMException;

public class XMLHandlerReloadTests {

    private static final long HOUR = 3_600_000L;

    private File file;
    private ConcurrentXMLHandler handler;

    @BeforeMethod
    public void setUp() {
        file = getRandomXMLFile();
        handler = new ConcurrentXMLHandler(config(file), schemaParser().parseSchema(), schemaParser().getXsdSchema());
    }

    @Test
    public void readOnlyCallsDoNotWriteTheFile() throws Exception {
        writeAccounts(file, "alice");
        FileTime past = FileTime.fromMillis(now() - HOUR);
        Files.setLastModifiedTime(file.toPath(), past);
        byte[] before = Files.readAllBytes(file.toPath());

        names();
        call(h -> h.authenticate("alice", new GuardedString("secret-alice".toCharArray())));

        assertEquals(Files.getLastModifiedTime(file.toPath()), past);
        assertEquals(Files.readAllBytes(file.toPath()), before);
    }

    @Test
    public void newFileIsCreatedByTheFirstCall() {
        // Guards existing behaviour: with createFileIfNotExists even test() creates the file.
        assertFalse(file.exists());
        call(h -> null);
        assertTrue(file.exists());
    }

    @Test
    public void changeIsInTheFileWhenTheLastUserLeaves() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        call(h -> h.create(ObjectClass.ACCOUNT, account("bob")));
        assertEquals(namesInFile(file), List.of("alice", "bob"));
    }

    @Test
    public void failedUpdateLeavesTheFileInAgreementWithMemory() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        Set<Attribute> twoLastNames = Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "A", "B"));
        List<String> inMemory;
        handler.init(); // holds the document: no reload and no save until the last dispose()
        try {
            expectThrows(IllegalArgumentException.class,
                    () -> call(h -> h.update(ObjectClass.ACCOUNT, new Uid("uid-alice"), twoLastNames)));
            inMemory = lastNames(handler.search(allAccounts(), ObjectClass.ACCOUNT));
        } finally {
            handler.dispose();
        }
        assertEquals(lastNamesIn(file), inMemory);
    }

    @Test
    public void deleteIsInTheFileWhenTheLastUserLeaves() throws Exception {
        writeAccounts(file, "alice", "bobby");
        setModified(file, now() - HOUR);
        call(h -> {
            h.delete(ObjectClass.ACCOUNT, new Uid("uid-bobby"));
            return null;
        });
        assertEquals(namesInFile(file), List.of("alice"));
    }

    @Test
    public void unchangedFileIsServedFromMemory() throws Exception {
        long past = now() - HOUR;
        writeAccounts(file, "alice");
        setModified(file, past);
        assertEquals(names(), List.of("alice"));

        // Same size, same mtime, same file, stamp not racy: indistinguishable from no change.
        writeAccounts(file, "bobby");
        setModified(file, past);
        assertEquals(names(), List.of("alice"));
    }

    @Test
    public void externalCopyWithAnOlderMtimeIsLoaded() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        assertEquals(names(), List.of("alice"));

        writeAccounts(file, "carol");
        setModified(file, now() - 2 * HOUR);
        assertEquals(names(), List.of("carol"));
    }

    @Test
    public void malformedEditFailsEveryCallUntilFixed() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - 2 * HOUR);
        assertEquals(names(), List.of("alice"));

        Files.write(file.toPath(), "<broken".getBytes(StandardCharsets.UTF_8));
        setModified(file, now() - HOUR);
        captureStdErr(() -> expectThrows(ConnectorException.class, () -> handler.init()));
        captureStdErr(() -> expectThrows(ConnectorException.class, () -> handler.init()));

        writeAccounts(file, "bob");
        setModified(file, now() - HOUR / 2);
        assertEquals(names(), List.of("bob"));
    }

    @Test
    public void sameSizeEditInsideTheRacyWindowIsLoaded() throws Exception {
        long future = now() + HOUR; // a stamp at or after the clock is racy
        writeAccounts(file, "alice");
        setModified(file, future);
        assertEquals(names(), List.of("alice"));

        writeAccounts(file, "bobby");
        setModified(file, future);
        assertEquals(names(), List.of("bobby"));
    }

    @Test
    public void racyLoadIsNotParsedAgain() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() + HOUR); // racy for as long as the test runs
        String first = captureStdOut(() -> names());
        assertTrue(first.contains("Loading XML document from: " + file.getPath()), first);
        String second = captureStdOut(() -> names());
        assertFalse(second.contains("Loading XML document from"), second);
    }

    @Test
    public void unreadableFileBehindARacyStampFails() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() + HOUR); // racy for as long as the test runs
        assertEquals(names(), List.of("alice"));
        makeUnreadable(file);
        try {
            expectThrows(ConnectorException.class, () -> handler.init());
        } finally {
            file.setReadable(true, false);
        }
    }

    @Test
    public void stampConfirmedByContentStopsBeingRacy() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - FileStamp.RACY_WINDOW_MILLIS + 1_000L); // racy for one more second
        assertEquals(names(), List.of("alice"));
        if (now() - Files.getLastModifiedTime(file.toPath()).toMillis() >= FileStamp.RACY_WINDOW_MILLIS) {
            // A slow load, or a file system that truncates mtimes to seconds: the stamp taken was not racy.
            throw new SkipException("The load of " + file + " fell outside the racy window");
        }
        Thread.sleep(1_500L); // the same mtime is now outside the window
        names(); // racy stamp, same content: the stamp is taken again
        makeUnreadable(file);
        try {
            assertEquals(names(), List.of("alice")); // nothing reads the file any more
        } finally {
            file.setReadable(true, false);
        }
    }

    @Test
    public void relativeDtdIsResolvedAgainstTheFile() throws Exception {
        File dtd = new File(file.getParentFile(), file.getName() + ".dtd");
        Files.write(dtd.toPath(), "<!ENTITY who 'alice'>".getBytes(StandardCharsets.UTF_8));
        writeAccounts(file, "alice");
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("<icf:OpenICFContainer", "<!DOCTYPE icf:OpenICFContainer SYSTEM '" + dtd.getName() + "'>\n<icf:OpenICFContainer")
                .replace("<icf:__NAME__>alice<", "<icf:__NAME__>&who;<");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(names(), List.of("alice"));
    }

    @Test
    public void cdataValueOfALoadedFileIsRead() throws Exception {
        writeAccounts(file, "alice");
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("<ri:lastname>Last-alice</ri:lastname>", "<ri:lastname><![CDATA[Last-alice]]></ri:lastname>");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        setModified(file, now() - HOUR);
        assertEquals(call(h -> lastNames(h.search(allAccounts(), ObjectClass.ACCOUNT))), List.of("Last-alice"));
        assertEquals(call(h -> lastNames(h.search(allAccounts(), ObjectClass.ACCOUNT))), List.of("Last-alice"));
    }

    @Test
    public void whitespaceOnlyValueOfALoadedFileIsAbsent() throws Exception {
        writeAccounts(file, "alice");
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("<ri:lastname>Last-alice</ri:lastname>", "<ri:lastname> </ri:lastname>");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        setModified(file, now() - HOUR);
        // As after a save and a reload: the blank text is gone, and so is the attribute.
        assertEquals(call(h -> lastNames(h.search(allAccounts(), ObjectClass.ACCOUNT))), Collections.singletonList(null));
    }

    @Test
    public void relativeDtdIsResolvedUnderANonAsciiDirectory() throws Exception {
        // Unique per run: a directory left by a killed run would otherwise turn this test into a skip.
        File dir = new File(file.getParentFile(), "\u0434\u0438\u0440-\u00e9-" + file.getName());
        try {
            // A path the platform encoding cannot hold (Linux under LANG=C) throws here, before anything is created.
            Files.createDirectory(dir.toPath());
        } catch (InvalidPathException | IOException e) {
            throw new SkipException("Cannot create a directory with a non-ASCII name: " + dir, e);
        }
        try {
            File store = new File(dir, "store.xml");
            File dtd = new File(dir, "store.dtd");
            Files.write(dtd.toPath(), "<!ENTITY who 'alice'>".getBytes(StandardCharsets.UTF_8));
            writeAccounts(store, "alice");
            String xml = new String(Files.readAllBytes(store.toPath()), StandardCharsets.UTF_8)
                    .replace("<icf:OpenICFContainer", "<!DOCTYPE icf:OpenICFContainer SYSTEM 'store.dtd'>\n<icf:OpenICFContainer")
                    .replace("<icf:__NAME__>alice<", "<icf:__NAME__>&who;<");
            Files.write(store.toPath(), xml.getBytes(StandardCharsets.UTF_8));
            XMLHandler other = new ConcurrentXMLHandler(config(store), schemaParser().parseSchema(), schemaParser().getXsdSchema());
            other.init();
            try {
                List<String> found = new ArrayList<String>();
                for (ConnectorObject object : other.search(allAccounts(), ObjectClass.ACCOUNT)) {
                    found.add(object.getName().getNameValue());
                }
                assertEquals(found, List.of("alice"));
            } finally {
                other.dispose();
            }
        } finally {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    child.delete();
                }
            }
            dir.delete();
        }
    }

    @Test
    public void outsideEditDuringAChangeIsReportedAndOverwritten() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - 2 * HOUR);
        handler.init();
        handler.create(ObjectClass.ACCOUNT, account("bob"));
        writeAccounts(file, "carol");
        setModified(file, now() - HOUR);
        String errors = captureStdErr(handler::dispose);
        assertTrue(errors.contains("UPDATE COLLISION: " + file + " has changed since it was loaded or saved"), errors);
        assertEquals(namesInFile(file), List.of("alice", "bob"));
    }

    @Test
    public void ownChangeIsSavedWithoutACollision() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        String errors = captureStdErr(() -> call(h -> h.create(ObjectClass.ACCOUNT, account("bob"))));
        assertFalse(errors.contains("UPDATE COLLISION"), errors);
    }

    @Test
    public void deletedFileIsRecreatedWithoutACollision() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        assertEquals(names(), List.of("alice"));
        Files.delete(file.toPath());
        String errors = captureStdErr(() -> assertEquals(names(), List.of()));
        assertFalse(errors.contains("UPDATE COLLISION"), errors);
    }

    @Test
    public void retryAfterAFailedSaveDoesNotReportItsOwnPartialWrite() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        byte[] before = Files.readAllBytes(file.toPath());
        XMLHandlerImpl impl = new XMLHandlerImpl(config(file), schemaParser().parseSchema(), schemaParser().getXsdSchema());
        impl.init();
        Set<Attribute> bob = account("bob");
        bob.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "x\uD800y")); // an unpaired surrogate: no encoder can write it
        impl.create(ObjectClass.ACCOUNT, bob);
        captureStdErr(() -> expectThrows(ConnectorException.class, impl::dispose));
        assertFalse(Arrays.equals(Files.readAllBytes(file.toPath()), before)); // the failed save truncated the file
        String errors = captureStdErr(() -> expectThrows(ConnectorException.class, impl::dispose));
        assertFalse(errors.contains("UPDATE COLLISION"), errors);
    }

    @Test
    public void ownSaveIsNotParsedAgain() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        call(h -> h.create(ObjectClass.ACCOUNT, account("bob"))); // the stamp after this save is racy
        String log = captureStdOut(() -> assertEquals(names(), List.of("alice", "bob")));
        assertFalse(log.contains("Loading XML document from"), log);
    }

    @Test
    public void failedSaveKeepsTheChangeInMemoryAndRetriesIt() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - 2 * HOUR);
        handler.init();
        handler.create(ObjectClass.ACCOUNT, account("bob"));
        Files.delete(file.toPath());
        assertTrue(file.mkdir()); // a directory cannot be written, whoever runs the test
        try {
            captureStdErr(() -> expectThrows(ConnectorException.class, handler::dispose));
        } finally {
            assertTrue(file.delete());
        }

        // An outside file appears: it must not replace the unsaved change, which overwrites it and says so.
        writeAccounts(file, "carol");
        setModified(file, now() - HOUR);
        String errors = captureStdErr(() -> assertEquals(names(), List.of("alice", "bob")));
        assertTrue(errors.contains("UPDATE COLLISION"), errors);
        assertEquals(namesInFile(file), List.of("alice", "bob"));
    }

    @Test
    public void unsavedNewDocumentGivesWayToAFileThatAppears() throws Exception {
        assertFalse(file.exists());
        handler.init(); // no file: an empty document in memory
        assertTrue(file.mkdir());
        try {
            captureStdErr(() -> expectThrows(ConnectorException.class, handler::dispose));
        } finally {
            assertTrue(file.delete());
        }

        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        byte[] appeared = Files.readAllBytes(file.toPath());
        assertEquals(names(), List.of("alice"));
        assertEquals(Files.readAllBytes(file.toPath()), appeared); // loaded, not written back
    }

    @Test
    public void changedNewDocumentGivesWayToAFileThatAppears() throws Exception {
        assertFalse(file.exists());
        handler.init(); // no file: a new document in memory
        handler.create(ObjectClass.ACCOUNT, account("bob"));
        assertTrue(file.mkdir());
        try {
            captureStdErr(() -> expectThrows(ConnectorException.class, handler::dispose));
        } finally {
            assertTrue(file.delete());
        }

        // A store appears (a restore, a late mount): the connector never loaded it, so a save would replace all of it.
        writeAccounts(file, "alice", "carol");
        setModified(file, now() - HOUR);
        byte[] appeared = Files.readAllBytes(file.toPath());
        String errors = captureStdErr(() -> assertEquals(names(), List.of("alice", "carol")));
        assertEquals(Files.readAllBytes(file.toPath()), appeared);
        assertTrue(errors.contains(file + " appeared before the new document was saved"), errors);
    }

    @Test
    public void fileThatAppearsDuringAChangeToANewDocumentIsKept() throws Exception {
        assertFalse(file.exists());
        handler.init(); // no file: a new document in memory
        handler.create(ObjectClass.ACCOUNT, account("bob"));
        writeAccounts(file, "alice", "carol");
        setModified(file, now() - HOUR);
        byte[] appeared = Files.readAllBytes(file.toPath());
        String errors = captureStdErr(handler::dispose);
        assertEquals(Files.readAllBytes(file.toPath()), appeared);
        assertTrue(errors.contains(file + " appeared before the new document was saved"), errors);
        assertEquals(names(), List.of("alice", "carol"));
    }

    @Test
    public void fileThatAppearsDuringTheFirstCallIsNotOverwritten() throws Exception {
        assertFalse(file.exists());
        handler.init(); // no file: an empty new document
        writeAccounts(file, "alice");
        setModified(file, now() - HOUR);
        byte[] appeared = Files.readAllBytes(file.toPath());
        captureStdErr(handler::dispose);
        assertEquals(Files.readAllBytes(file.toPath()), appeared);
        assertEquals(names(), List.of("alice"));
    }

    @Test
    public void changeToANewDocumentIsSavedOnceThePathIsFree() throws Exception {
        assertFalse(file.exists());
        handler.init(); // no file: a new document in memory
        handler.create(ObjectClass.ACCOUNT, account("bob"));
        assertTrue(file.mkdir());
        try {
            captureStdErr(() -> expectThrows(ConnectorException.class, handler::dispose));
        } finally {
            assertTrue(file.delete());
        }

        // The stamp changed, but no file appeared: the change is saved.
        captureStdErr(() -> assertEquals(names(), List.of("bob")));
        assertEquals(namesInFile(file), List.of("bob"));
    }

    @Test
    public void ownPartialWriteOfANewDocumentIsSavedAgain() throws Exception {
        XMLHandlerImpl impl = new XMLHandlerImpl(config(file), schemaParser().parseSchema(), schemaParser().getXsdSchema());
        impl.init(); // no file: a new document in memory
        Set<Attribute> bob = account("bob");
        bob.add(AttributeBuilder.build(ATTR_ACCOUNT_FIRST_NAME, "x\uD800y")); // an unpaired surrogate: no encoder can write it
        impl.create(ObjectClass.ACCOUNT, bob);
        captureStdErr(() -> expectThrows(ConnectorException.class, impl::dispose));
        assertTrue(file.exists()); // the failed save left a partial file
        captureStdErr(() -> expectThrows(ConnectorException.class, impl::dispose)); // not a file that appeared: saved again
    }

    @Test
    public void savedNewFileIsNotWrittenAgain() throws Exception {
        call(h -> null); // creates the file
        String log = captureStdOut(() -> call(h -> null));
        assertTrue(log.contains("Exit serialize: nothing to save"), log);
    }

    @Test
    public void failedDeleteOfANestedEntryLeavesTheFileAlone() throws Exception {
        writeAccounts(file, "alice");
        // An XSD-invalid hand edit: getEntry's descendant query finds the entry, removeChild on the container rejects it.
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("  <ri:__ACCOUNT__>\n", "  <ri:wrapper>\n  <ri:__ACCOUNT__>\n")
                .replace("  </ri:__ACCOUNT__>\n", "  </ri:__ACCOUNT__>\n  </ri:wrapper>\n");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        FileTime past = FileTime.fromMillis(now() - HOUR);
        Files.setLastModifiedTime(file.toPath(), past);
        byte[] before = Files.readAllBytes(file.toPath());

        expectThrows(DOMException.class, () -> call(h -> {
            h.delete(ObjectClass.ACCOUNT, new Uid("uid-alice"));
            return null;
        }));

        assertEquals(Files.getLastModifiedTime(file.toPath()), past);
        assertEquals(Files.readAllBytes(file.toPath()), before);
    }

    @Test
    public void externalEditWithANewerMtimeIsLoaded() throws Exception {
        writeAccounts(file, "alice");
        setModified(file, now() - 2 * HOUR);
        assertEquals(names(), List.of("alice"));

        writeAccounts(file, "bob");
        setModified(file, now() - HOUR);
        assertEquals(names(), List.of("bob"));
    }

    /** Makes {@code file} unreadable, or skips the test where its owner can read it anyway (root, Windows). */
    private static void makeUnreadable(File file) {
        if (!file.setReadable(false, false) || Files.isReadable(file.toPath())) {
            file.setReadable(true, false);
            throw new SkipException("Cannot make " + file + " unreadable here");
        }
    }

    @Test
    public void loadedDocumentIsFullyExpanded() throws Exception {
        writeAccounts(file, "alice");
        XMLHandlerImpl impl = new XMLHandlerImpl(config(file), schemaParser().parseSchema(), schemaParser().getXsdSchema());
        impl.init();
        assertFalse(impl.getDocument() instanceof org.apache.xerces.dom.DeferredDocumentImpl,
                impl.getDocument().getClass().getName());
    }

    @Test
    public void connectorObjectIsReadWithoutNodeLists() throws Exception {
        writeAccounts(file, "alice");
        XMLHandlerImpl impl = new XMLHandlerImpl(config(file), schemaParser().parseSchema(), schemaParser().getXsdSchema());
        impl.init();
        org.w3c.dom.Element entry = (org.w3c.dom.Element) impl.getDocument()
                .getElementsByTagNameNS(RI_NAMESPACE, ObjectClass.ACCOUNT_NAME).item(0);
        assertFalse(XercesNodeLists.used(impl.getDocument()));

        ConnectorObject object = impl.newConnectorObjectCreator(ObjectClass.ACCOUNT).createConnectorObject(entry);

        assertEquals(object.getUid().getUidValue(), "uid-alice");
        assertEquals(object.getName().getNameValue(), "alice");
        assertEquals(AttributeUtil.getStringValue(object.getAttributeByName(ATTR_ACCOUNT_LAST_NAME)), "Last-alice");
        assertFalse(XercesNodeLists.used(impl.getDocument()));
    }

    @Test
    public void parserWithoutTheDeferAttributeStillLoads() throws Exception {
        writeAccounts(file, "alice");
        withDocumentBuilderFactory(NoDeferAttributeFactory.class, () -> assertEquals(names(), List.of("alice")));
    }

    @Test
    public void parserWithoutTheDeferAttributeIsReported() throws Exception {
        writeAccounts(file, "alice");
        String log = captureStdOut(() -> withDocumentBuilderFactory(NoDeferAttributeFactory.class, () -> names()));
        assertTrue(log.contains("The XML parser " + NoDeferAttributeFactory.class.getName()
                + " does not support http://apache.org/xml/features/dom/defer-node-expansion"), log);
    }

    /** Runs {@code action} with {@code factory} as the JAXP DocumentBuilderFactory, the way a JVM-wide setting selects it. */
    private static void withDocumentBuilderFactory(Class<?> factory, Runnable action) {
        String property = "javax.xml.parsers.DocumentBuilderFactory";
        String previous = System.getProperty(property);
        System.setProperty(property, factory.getName());
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    /** A JAXP parser that supports none of the Xerces attributes: setAttribute throws, as the JAXP contract allows. */
    public static final class NoDeferAttributeFactory extends DocumentBuilderFactory {

        private final DocumentBuilderFactory xerces = new org.apache.xerces.jaxp.DocumentBuilderFactoryImpl();

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            xerces.setNamespaceAware(isNamespaceAware());
            return xerces.newDocumentBuilder();
        }

        @Override
        public void setAttribute(String name, Object value) {
            throw new IllegalArgumentException("Not supported: " + name);
        }

        @Override
        public Object getAttribute(String name) {
            throw new IllegalArgumentException("Not supported: " + name);
        }

        @Override
        public void setFeature(String name, boolean value) throws ParserConfigurationException {
            throw new ParserConfigurationException("Not supported: " + name);
        }

        @Override
        public boolean getFeature(String name) throws ParserConfigurationException {
            throw new ParserConfigurationException("Not supported: " + name);
        }
    }

    /** One framework call: init, the operation, dispose. */
    private <T> T call(Function<XMLHandler, T> operation) {
        handler.init();
        try {
            return operation.apply(handler);
        } finally {
            handler.dispose();
        }
    }

    private List<String> names() {
        return call(h -> {
            List<String> names = new ArrayList<String>();
            for (ConnectorObject object : h.search(allAccounts(), ObjectClass.ACCOUNT)) {
                names.add(object.getName().getNameValue());
            }
            return names;
        });
    }

    private static String allAccounts() {
        return new QueryBuilder(null, ObjectClass.ACCOUNT).toString();
    }

    private static List<String> lastNames(Collection<ConnectorObject> objects) {
        List<String> values = new ArrayList<String>();
        for (ConnectorObject object : objects) {
            Attribute lastName = object.getAttributeByName(ATTR_ACCOUNT_LAST_NAME);
            values.add(lastName == null ? null : AttributeUtil.getStringValue(lastName));
        }
        return values;
    }

    private static List<String> lastNamesIn(File file) {
        XMLHandler fresh = new XMLHandlerImpl(config(file), schemaParser().parseSchema(), schemaParser().getXsdSchema()).init();
        return lastNames(fresh.search(allAccounts(), ObjectClass.ACCOUNT));
    }

    private static XMLConfiguration config(File file) {
        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(file);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        config.setCreateFileIfNotExists(true);
        return config;
    }

    private static SchemaParser schemaParser() {
        return new SchemaParser(XMLConnector.class, XSD_SCHEMA_FILEPATH);
    }

    private static String captureStdOut(Runnable action) throws UnsupportedEncodingException {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8.name()));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static String captureStdErr(Runnable action) throws UnsupportedEncodingException {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8.name()));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
