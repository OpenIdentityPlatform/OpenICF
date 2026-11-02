/*
 *
 * Copyright (c) 2010-2012 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 * Portions Copyrighted 2026 3A Systems, LLC
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml;

import org.forgerock.openicf.connectors.xml.util.AttributeTypeUtil;
import org.forgerock.openicf.connectors.xml.util.ElementIdentifierFieldType;
import org.forgerock.openicf.connectors.xml.util.GuardedStringAccessor;
import org.forgerock.openicf.connectors.xml.util.NamespaceLookupUtil;
import org.forgerock.openicf.connectors.xml.util.XmlHandlerUtil;
import org.forgerock.openicf.connectors.xml.query.ComparisonQuery;
import org.forgerock.openicf.connectors.xml.query.QueryBuilder;
import org.forgerock.openicf.connectors.xml.query.XQueryHandler;
import org.forgerock.openicf.connectors.xml.query.abstracts.Query;
import org.forgerock.openicf.connectors.xml.query.abstracts.QueryPart;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQResultSequence;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.Uid;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XMLHandlerImpl implements XMLHandler {

    /**
     * Setup logging for the {@link XMLHandlerImpl}.
     */
    private static final Log log = Log.getLog(XMLHandlerImpl.class);
    private static final String DEFER_NODE_EXPANSION = "http://apache.org/xml/features/dom/defer-node-expansion";
    private XMLConfiguration config;
    private volatile Document document;
    private Schema connSchema;
    private XSSchema icfSchema;
    private XSSchema riSchema;
    private final EntryIndex entryIndex;
    /** Whether the document holds user changes that the file does not have yet. */
    private boolean dirty;
    /** Whether the document was created in memory because the file did not exist, and is not saved yet. */
    private boolean unsavedNewFile;
    /** The file as last seen: at the last load, successful save, new document, or content-confirmed check. */
    private FileStamp stamp = FileStamp.MISSING;
    /** The CRC-32 of the bytes last parsed or written. */
    private long checksum;
    public static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String ICF_NAMESPACE_PREFIX = "icf";
    public static final String RI_NAMESPACE_PREFIX = "ri";
    public static final String XSI_NAMESPACE_PREFIX = "xsi";
    public static final String ICF_CONTAINER_TAG = "OpenICFContainer";

    public XMLHandlerImpl(XMLConfiguration config, Schema connSchema, XSSchemaSet xsdSchemas) {
        Assertions.nullCheck(config.getXmlFilePath(), "filePath");
        this.config = config;

        this.connSchema = connSchema;
        this.riSchema = xsdSchemas.getSchema(1);
        this.icfSchema = xsdSchemas.getSchema(2);

        Set<String> objectClasses = new HashSet<String>();
        for (ObjectClassInfo info : connSchema.getObjectClassInfo()) {
            objectClasses.add(info.getType());
        }
        entryIndex = new EntryIndex(icfSchema.getTargetNamespace(), riSchema.getTargetNamespace(), objectClasses);

        NamespaceLookupUtil.INSTANCE.initialize(icfSchema, riSchema);
    }

    @Override
    public XMLHandler init() {
        if (document != null && fileAppearedOverNewDocument()) {
            dropNewDocument();
        }
        if (document == null || (!dirty && !unsavedNewFile && fileHasChanged())) {
            buildDocument();
        }
        return this;
    }

    /**
     * Whether a file appeared where a new document is not saved yet. Such a file wins over the
     * document: the connector never loaded it, so a save would replace a whole store. A failed
     * save writes nothing at the path, so any file there is not the connector's.
     */
    private boolean fileAppearedOverNewDocument() {
        return unsavedNewFile && config.getXmlFilePath().isFile();
    }

    /** Drops the new document with its changes: the next init() loads the file, or starts a new document if the file is gone. */
    private void dropNewDocument() {
        if (dirty) {
            log.error("{0} appeared before the new document was saved; keeping the file and dropping the changes made to the new document", config.getXmlFilePath());
            dirty = false;
        }
        document = null;
    }

    /** A racy stamp cannot rule out a same-size write in the same tick, so the content decides. */
    private boolean fileHasChanged() {
        File xmlFile = config.getXmlFilePath();
        FileStamp current = FileStamp.read(xmlFile);
        if (!stamp.sameState(current)) {
            return true;
        }
        if (!stamp.isRacy()) {
            return false;
        }
        try {
            if (FileStamp.checksum(xmlFile) != checksum) {
                return true;
            }
        } catch (IOException e) {
            return true;
        }
        stamp = current; // the content is what we hold; the stamp stops being racy once it is old enough
        return false;
    }

    private void markDirty() {
        dirty = true;
    }

    @Override
    public Uid create(final ObjectClass objClass, final Set<Attribute> attributes) {
        final String method = "create";
        log.info("Entry {0}", method);

        // Validate object type
        XmlHandlerUtil.checkObjectType(objClass, riSchema);

        ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());
        Map<String, AttributeInfo> objAttributes = AttributeInfoUtil.toMap(objInfo.getAttributeInfo());
        String uidValue = null;

        // Check if __NAME__ is defined. The framework names attributes in any case, so __name__ defines it too (#212).
        // Only the first spelling found is looked at. So __NAME__ given along with a __name__ without a value is refused
        // for the missing value, here or in checkAttribute as the order of the set falls, not as a name given twice.
        Attribute givenName = AttributeUtil.find(Name.NAME, attributes);
        if (givenName == null || givenName.getValue() == null || givenName.getValue().isEmpty()) {
            throw new IllegalArgumentException(Name.NAME + " must be defined.");
        }

        // Every given attribute is checked as update checks it, so one the schema does not have is refused rather than
        // dropped (#192). It is kept under the schema's name, which the checks below and the file use, whatever case it
        // was given in (#212).
        Map<String, List<String>> checked = new HashMap<String, List<String>>();
        for (CheckedAttribute c : checkAttributes(attributes, objInfo, Change.CREATE)) {
            checked.put(c.name, c.values);
        }
        for (AttributeInfo attributeInfo : objInfo.getAttributeInfo()) {
            if (attributeInfo.isRequired() && !checked.containsKey(attributeInfo.getName())) {
                throw new IllegalArgumentException("No values provided for required attribute: " + attributeInfo.getName());
            }
        }

        // Not AttributeUtil.getNameFromAttributes: it casts the attribute to Name, which only an exact __NAME__ is.
        String name = checked.get(Name.NAME).get(0);

        // Check if entry already exists
        if (entryExists(objClass, new Uid(name), ElementIdentifierFieldType.BY_NAME)) {
            throw new AlreadyExistsException("Could not create entry. An entry with the " + Uid.NAME + " of "
                    + name + " already exists.");
        }

        // Create or get UID. The lookup is case-insensitive, but the loop below writes the uid only into __UID__.
        AttributeInfo uidInfo = objAttributes.get(Uid.NAME);
        if (uidInfo != null && uidInfo.getName().equals(Uid.NAME)) {
            uidValue = UUID.randomUUID().toString();
        } else {
            uidValue = name;
        }

        // Create object type element
        Element objElement = getDocument().createElementNS(riSchema.getTargetNamespace(), objClass.getObjectClassValue());
        objElement.setPrefix(RI_NAMESPACE_PREFIX);

        // Add child elements
        for (AttributeInfo attributeInfo : objInfo.getAttributeInfo()) {

            String attributeName = attributeInfo.getName();

            Element childElement = null;

            if (attributeName.equals(Uid.NAME)) {
                childElement = createDomElement(attributeName, uidValue);
                objElement.appendChild(childElement);
            } else if (checked.containsKey(attributeName)) {
                // Create elements
                for (String value : checked.get(attributeName)) {
                    childElement = createDomElement(attributeName, value);
                    objElement.appendChild(childElement);
                }
            } // Create empty element if not provided
            else {
                childElement = createDomElement(attributeName, "");
                objElement.appendChild(childElement);
            }

            log.info("Creating new entry: {0}", attributes.toString());
        }

        markDirty();
        getDocument().getDocumentElement().appendChild(objElement);
        entryIndex.added(objElement);
        saveChange();

        log.info("Exit {0}", method);

        return new Uid(uidValue);
    }

    @Override
    public Uid update(ObjectClass objClass, Uid uid, Set<Attribute> replaceAttributes) {
        final String method = "update";
        log.info("Entry {0}", method);

        XmlHandlerUtil.checkObjectType(objClass, riSchema);

        ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());

        Element entry = getEntry(objClass, uid, ElementIdentifierFieldType.AUTO);
        if (entry == null) {
            throw new UnknownUidException("Could not update entry. No entry of type " + objClass.getObjectClassValue() + " with the id " + uid.getUidValue() + " found.");
        }

        // Every attribute is checked before the entry changes, so a rejected update leaves the document as it was (#158).
        // The name is checked for a clash once every attribute has passed, as create() checks it.
        List<CheckedAttribute> checkedAttributes = checkAttributes(replaceAttributes, objInfo, Change.UPDATE);
        Uid newUid = null;
        for (CheckedAttribute checked : checkedAttributes) {
            if (Name.NAME.equals(checked.name)) {
                for (String value : checked.values) {
                    if (nameTakenByAnother(objClass, entry, value)) {
                        throw new AlreadyExistsException("Could not update entry. An entry with the " + Name.NAME + " of "
                                + value + " already exists.");
                    }
                    // Without a __UID__ element the uid is the name, as create() makes it.
                    if (!isSupportUid(objClass)) {
                        newUid = new Uid(value);
                    }
                }
            }
        }

        try {
            for (CheckedAttribute checkedAttribute : checkedAttributes) {
                String attributeName = checkedAttribute.name;
                List<String> values = checkedAttribute.values;

                // Remove existing nodes from entry
                markDirty();
                removeChildrenFromElement(entry, attributeName);

                // Append empty element if no values is provided
                if (values.isEmpty()) {
                    Element updatedElement = createDomElement(attributeName, "");
                    entry.appendChild(updatedElement);
                } else {
                    for (String value : values) {
                        Element updatedElement = createDomElement(attributeName, value);
                        entry.appendChild(updatedElement);
                    }
                }
            }
        } finally {
            // A failure midway may leave the entry already changed, so report the change either way.
            entryIndex.rekeyed(entry);
        }
        saveChange();

        log.info("Exit {0}", method);

        return newUid != null ? newUid : uid;
    }

    @Override
    public void delete(final ObjectClass objClass, final Uid uid) {
        final String method = "delete";
        log.info("Entry {0}", method);

        XmlHandlerUtil.checkObjectType(objClass, riSchema);

        Element elementToRemove = getEntry(objClass, uid, ElementIdentifierFieldType.AUTO);
        if (elementToRemove == null) {
            throw new UnknownUidException("Deleting entry failed. Could not find an entry of type " + objClass.getObjectClassValue() + " with the uid " + uid.getUidValue());
        }
        // Not always a child of the container: a hand-edited file may have it below another element.
        elementToRemove.getParentNode().removeChild(elementToRemove);
        markDirty();
        entryIndex.removed(elementToRemove);
        saveChange();
        log.info("Deleting entry: " + elementToRemove.toString());

        log.info("Exit {0}", method);
    }

    @Override
    public Collection<ConnectorObject> search(String query, ObjectClass objClass) {
        final String method = "search";
        log.info("Entry {0}", method);

        List<ConnectorObject> results = new ArrayList<ConnectorObject>();

        if (query != null && !query.isEmpty() && objClass != null) {

            ConnectorObjectCreator conObjCreator = newConnectorObjectCreator(objClass);

            XQueryHandler xqHandler = null;
            try {
                xqHandler = new XQueryHandler(query, getDocument());
                XQResultSequence queryResult = xqHandler.getResultSequence();


                while (queryResult.next()) {

                    results.add(conObjCreator.createConnectorObject(queryResult.getItem().getNode()));
                }
            } catch (XQException ex) {
                log.error("Error while searching: {0}", ex);
                throw new ConnectorException(ex);
            } finally {
                if (null != xqHandler) {
                    xqHandler.close();
                }
            }
        }
        log.info("Exit {0}", method);

        return results;
    }

    @Override
    public Collection<ConnectorObject> search(Query query, ObjectClass objClass) {
        // A uid lookup also finds an entry whose uid is its name, as getEntry does; the index and the XQuery do not.
        List<Element> entries = query != null && Uid.NAME.equals(identifierField(query))
                ? entriesByUid(objClass, ((ComparisonQuery) query.getParts().iterator().next()).getValue())
                : indexedEntries(query, objClass);
        if (entries == null) {
            return search(new QueryBuilder(query, objClass).toString(), objClass);
        }
        return toConnectorObjects(entries, objClass);
    }

    /** The entries a query selects if the index can tell, in document order; {@code null} otherwise. */
    List<Element> indexedEntries(Query query, ObjectClass objClass) {
        getDocument(); // throws if there is no document, as the XQuery search did
        String objectClass = objClass.getObjectClassValue();
        if (query == null || query.getParts().isEmpty()) {
            return entryIndex.all(objectClass);
        }
        String field = identifierField(query);
        if (field == null) {
            return null;
        }
        String value = ((ComparisonQuery) query.getParts().iterator().next()).getValue();
        return entryIndex.find(objectClass, field, value);
    }

    /**
     * The identifier a query compares, if the query is exactly {@code $x/icf:__UID__ = 'v'} or
     * {@code $x/icf:__NAME__ = 'v'} after XMLFilterTranslator's rewrites; otherwise {@code null}.
     */
    static String identifierField(Query query) {
        if (query.getParts().size() != 1) {
            return null;
        }
        QueryPart part = query.getParts().iterator().next();
        if (!(part instanceof ComparisonQuery)) {
            return null;
        }
        ComparisonQuery comparison = (ComparisonQuery) part;
        if (!"=".equals(comparison.getOperator()) || comparison.getValue() == null) {
            return null;
        }
        for (String field : new String[] {Uid.NAME, Name.NAME}) {
            if (("$x/" + ICF_NAMESPACE_PREFIX + ":" + field).equals(comparison.getName())) {
                return field;
            }
        }
        return null;
    }

    private List<ConnectorObject> toConnectorObjects(Collection<Element> entries, ObjectClass objClass) {
        ConnectorObjectCreator creator = newConnectorObjectCreator(objClass);
        List<ConnectorObject> results = new ArrayList<ConnectorObject>(entries.size());
        for (Element entry : entries) {
            results.add(creator.createConnectorObject(entry));
        }
        return results;
    }

    @Override
    public void dispose() {
        final String method = "serialize";
        log.info("Entry {0}", method);
        if (!dirty && !unsavedNewFile) {
            log.info("Exit {0}: nothing to save", method);
            return;
        }
        File xmlFile = config.getXmlFilePath();
        if (fileAppearedOverNewDocument()) {
            dropNewDocument();
            log.info("Exit {0}: {1} appeared before the new document was saved; not overwriting it", method, xmlFile);
            return;
        }
        save();

        log.info("Exit {0}", method);
    }

    /**
     * Writes the document to the file, whole or not at all. A path that holds no file has nothing to overwrite: the
     * save creates the file. Callers hold the handler's write lock.
     */
    private void save() {
        File xmlFile = config.getXmlFilePath();
        if (xmlFile.isFile() && !stamp.sameState(FileStamp.read(xmlFile))) {
            log.error("UPDATE COLLISION: {0} has changed since it was loaded or saved; overwriting it with the data in memory.", xmlFile);
        }
        Document saved = getDocument();
        try {
            if (XmlDocumentWriter.normalizeText(saved)) {
                entryIndex.rebuild(saved); // a whitespace-only identifier may have just become empty
            }
            checksum = XmlDocumentWriter.write(saved, xmlFile);
            stamp = FileStamp.read(xmlFile);
            dirty = false;
            unsavedNewFile = false;
            log.info("Saving changes to xml file");
        } catch (IOException ex) {
            log.error("Failed saving changes to xml file: {0}", ex);
            throw new ConnectorIOException(ex);
        } catch (TransformerException | SAXException ex) {
            log.error("Failed saving changes to xml file: {0}", ex);
            throw ConnectorException.wrap(ex);
        }
    }

    /**
     * Saves the change that create, update or delete has just made, before the call returns: the framework only logs
     * what dispose() throws. A change that is not saved is undone, so the document again agrees with the file.
     * Callers hold the handler's write lock.
     */
    private void saveChange() {
        if (!dirty) {
            return;
        }
        try {
            if (fileAppearedOverNewDocument()) {
                throw new ConnectorException(config.getXmlFilePath()
                        + " appeared before the new document was saved; the change is not saved, and the file is loaded instead");
            }
            save();
        } catch (RuntimeException | Error e) {
            try {
                revert();
            } catch (RuntimeException | Error reloadFailure) {
                if (reloadFailure != e) { // the JVM may throw one shared instance twice, e.g. an OutOfMemoryError
                    e.addSuppressed(reloadFailure);
                }
            }
            throw e;
        }
    }

    /** Drops what the file does not have: reads the file again, or starts a new document if there is none. */
    private void revert() {
        dirty = false;
        // If the reload fails there is no document: dispose() has nothing to save and the next init()
        // builds one. If the file is missing and createFileIfNotExists is set, buildDocument() starts
        // a new document (unsavedNewFile is true again) and the last dispose() saves it as an empty store.
        unsavedNewFile = false;
        document = null;
        buildDocument();
    }

    @Override
    public Uid authenticate(String username, GuardedString password) {
        final String method = "authenticate";
        log.info("Entry {0}", method);

        Uid uid = null;

        Element entry = getEntry(ObjectClass.ACCOUNT, new Uid(username), ElementIdentifierFieldType.BY_NAME);

        if (entry != null) {
            String xmlPassword = firstOwnText(entry, "__PASSWORD__");

            GuardedStringAccessor accessor = new GuardedStringAccessor();

            password.access(accessor);

            StringBuilder sb = new StringBuilder();
            sb.append(accessor.getArray());

            String userPassword = sb.toString();

            // An entry without a password of its own does not authenticate. An empty one counts as none, the way
            // ConnectorObjectCreator reads an empty element as no value.
            if (xmlPassword != null && !xmlPassword.isEmpty() && xmlPassword.equals(userPassword)) {
                // The uid the search reports for this entry. Found by name, so it has one.
                uid = new Uid(ConnectorObjectCreator.uidOf(entry));
            }
        }

        log.info("Exit {0}", method);

        return uid;
    }

    @Override
    public boolean isSupportUid(ObjectClass objectClass) {
        ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objectClass.getObjectClassValue());
        if (null != objInfo) {
            for (AttributeInfo info : objInfo.getAttributeInfo()) {
                if (info.is(Uid.NAME)) {
                    return true;
                }
            }
        }
        return false;
    }


    private void createDocument() {
        final String method = "createDocument";
        log.info("Entry {0}", method);

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder builder = null;

        try {
            builder = builderFactory.newDocumentBuilder();
            log.info("Creating new xml storage file: {0}", config.getXmlFilePath());
        } catch (ParserConfigurationException ex) {
            log.error("Filed creating XML document: {0}", ex);
            throw ConnectorException.wrap(ex);
        }

        DOMImplementation implementation = builder.getDOMImplementation();
        document = implementation.createDocument(icfSchema.getTargetNamespace(), ICF_CONTAINER_TAG, null);

        Element root = document.getDocumentElement();
        // Declared like the others so that the writer puts it first, where Saxon's DOM serializer put it.
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + ICF_NAMESPACE_PREFIX, icfSchema.getTargetNamespace());
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + XSI_NAMESPACE_PREFIX, XSI_NAMESPACE);
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + RI_NAMESPACE_PREFIX, riSchema.getTargetNamespace());
        root.setPrefix(ICF_NAMESPACE_PREFIX);

        if (config.getXsdIcfFilePath() == null) {
            root.setAttribute(XSI_NAMESPACE_PREFIX + ":schemaLocation",
                    riSchema.getTargetNamespace() + " " + config.getXsdFilePath());
        } else {
            root.setAttribute(XSI_NAMESPACE_PREFIX + ":schemaLocation",
                    riSchema.getTargetNamespace() + " " + config.getXsdFilePath() + " "
                            + icfSchema.getTargetNamespace() + " " + config.getXsdIcfFilePath());
        }

        // Not read again: a file that appeared since the check found none is not the connector's to overwrite.
        stamp = FileStamp.MISSING;
        // Not a user change: if the first save fails, a file that appears afterwards is loaded instead.
        unsavedNewFile = true;
        log.info("Exit {0}", method);
    }

    ConnectorObjectCreator newConnectorObjectCreator(ObjectClass objClass) {
        ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());
        HashMap<String, String> attributeClassMap = new HashMap<String, String>();
        for (AttributeInfo info : objInfo.getAttributeInfo()) {
            attributeClassMap.put(info.getName(), info.getType().getSimpleName());
        }
        HashMap<String, AttributeInfo> attributeInfoMap =
                new HashMap<String, AttributeInfo>(AttributeInfoUtil.toMap(objInfo.getAttributeInfo()));
        return new ConnectorObjectCreator(attributeClassMap, attributeInfoMap, objClass);
    }

    private void loadDocument(File xmlFile) {
        final String method = "loadDocument";
        log.info("Entry {0}", method);

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        try {
            // Build every node now: a deferred node is built on its first read, and readers run in parallel.
            docBuilderFactory.setAttribute(DEFER_NODE_EXPANSION, Boolean.FALSE);
        } catch (IllegalArgumentException ex) {
            log.warn("The XML parser {0} does not support {1}", docBuilderFactory.getClass().getName(), DEFER_NODE_EXPANSION);
        }

        try {
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            // Taken before reading: a write during the read shows as a change at the next init().
            FileStamp loadedStamp = FileStamp.read(xmlFile);
            CRC32 crc = new CRC32();
            Document loaded;
            // The checksum is of the bytes parsed: the parser reads on to the end, past what follows the root.
            try (InputStream in = new CheckedInputStream(Files.newInputStream(xmlFile.toPath()), crc)) {
                InputSource source = new InputSource(in);
                source.setSystemId(xmlFile.toURI().toASCIIString());
                loaded = docBuilder.parse(source);
            }
            // Same text shape as a saved document: CDATA and split runs become one text node, blank text goes.
            XmlDocumentWriter.normalizeText(loaded);
            // Nothing changes unless the parse succeeded: a malformed file keeps failing until it is fixed.
            document = loaded;
            stamp = loadedStamp;
            checksum = crc.getValue();
            unsavedNewFile = false;
            log.info("Loading XML document from: {0}", xmlFile.getPath());
        } catch (ParserConfigurationException ex) {
            throw ConnectorException.wrap(ex);
        } catch (SAXException ex) {
            throw ConnectorException.wrap(ex);
        } catch (IOException ex) {
            throw ConnectorException.wrap(ex);
        }

        log.info("Exit {0}", method);
    }

    private void buildDocument() {
        final String method = "buildDocument";
        log.info("Entry {0}", method);

        File xmlFile = config.getXmlFilePath();
        if (!xmlFile.exists()) {
            if (config.isCreateFileIfNotExists()) {
                createDocument();
            } else {
                throw new ConfigurationException("Data file does not exists: " + config.getXmlFilePath().toString());
            }
        } else {
            loadDocument(xmlFile);
        }
        entryIndex.rebuild(document);

        log.info("Exit {0}", method);
    }

    // Not private: tests override it to make update fail midway.
    Element createDomElement(String elementName, String value) {

        Element element = null;

        if (icfSchema.getElementDecls().containsKey(elementName)) {
            element = getDocument().createElementNS(icfSchema.getTargetNamespace(), elementName);
            element.setPrefix(ICF_NAMESPACE_PREFIX);
        } else {
            element = getDocument().createElementNS(riSchema.getTargetNamespace(), elementName);
            element.setPrefix(RI_NAMESPACE_PREFIX);
        }

        element.setTextContent(value);

        return element;
    }

    private Element getEntry(ObjectClass objClass, Uid uid, ElementIdentifierFieldType identifierField) {
        getDocument();
        String field = identifierElement(objClass, identifierField);
        if (Uid.NAME.equals(field)) {
            List<Element> entries = entriesByUid(objClass, uid.getUidValue());
            return entries.isEmpty() ? null : entries.get(0);
        }
        return entryIndex.first(objClass.getObjectClassValue(), field, uid.getUidValue());
    }

    /**
     * The entries of {@code objClass} whose uid is {@code uid}, in document order: those with a __UID__ of that value
     * or, only if there are none, those with the name {@code uid} that {@link ConnectorObjectCreator#uidOf} gives
     * that uid, which are the entries without a __UID__ value whose last name it is. An entry with two __UID__ values
     * is found by either.
     */
    private List<Element> entriesByUid(ObjectClass objClass, String uid) {
        getDocument(); // throws if there is no document, as the XQuery search did
        String objectClass = objClass.getObjectClassValue();
        List<Element> entries = entryIndex.find(objectClass, Uid.NAME, uid);
        if (!entries.isEmpty() || !isSupportUid(objClass)) {
            return entries;
        }
        List<Element> byName = new ArrayList<Element>();
        for (Element entry : entryIndex.find(objectClass, Name.NAME, uid)) {
            if (uid.equals(ConnectorObjectCreator.uidOf(entry))) {
                byName.add(entry);
            }
        }
        return byName;
    }

    /** The identifier element to match, by the rule that XMLFilterTranslator applies to a search. */
    private String identifierElement(ObjectClass objClass, ElementIdentifierFieldType identifierField) {
        return XMLFilterTranslator.elementName(getElementIdentifierField(objClass, identifierField), isSupportUid(objClass));
    }

    private boolean entryExists(ObjectClass objClass, Uid uid, ElementIdentifierFieldType identifierField) {
        return getEntry(objClass, uid, identifierField) != null;
    }

    /** An entry that already has {@code name} keeps it, even when another entry has it too. */
    private boolean nameTakenByAnother(ObjectClass objClass, Element entry, String name) {
        List<Element> holders = entryIndex.find(objClass.getObjectClassValue(),
                identifierElement(objClass, ElementIdentifierFieldType.BY_NAME), name);
        return !holders.isEmpty() && !holders.contains(entry);
    }

    private String getElementIdentifierField(ObjectClass objClass, ElementIdentifierFieldType identifierField) {
        String elementField = "";

        if (identifierField == ElementIdentifierFieldType.BY_NAME) {
            elementField = Name.NAME;
        } else if (identifierField == ElementIdentifierFieldType.BY_UID) {
            elementField = Uid.NAME;
        } else {
            ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());
            Set<AttributeInfo> objAttrSet = objInfo.getAttributeInfo();
            Map<String, AttributeInfo> attrInfoMap = AttributeInfoUtil.toMap(objAttrSet);

            if (attrInfoMap.containsKey(Uid.NAME)) {
                elementField = Uid.NAME;
            } else {
                elementField = Name.NAME;
            }
        }

        return elementField;
    }

    private void removeChildrenFromElement(Element element, String childName) {
        for (Element e : ownElements(element, childName)) {
            element.removeChild(e);
            entryIndex.removed(e);
        }
    }

    /**
     * The child elements of {@code entry} named {@code localName}, in any namespace, in document order, by the name
     * rule {@link ConnectorObjectCreator} reads the attribute with (it also skips elements that do not start with
     * text). Walks the children, not a NodeList.
     */
    private static List<Element> ownElements(Element entry, String localName) {
        List<Element> elements = new ArrayList<Element>();
        for (Node child = entry.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                elements.add((Element) child);
            }
        }
        return elements;
    }

    /** The text of the first of {@link #ownElements}, or null if there is none. */
    private static String firstOwnText(Element entry, String localName) {
        List<Element> elements = ownElements(entry, localName);
        return elements.isEmpty() ? null : elements.get(0).getTextContent();
    }

    Document getDocument() {
        if (null == document) {
            throw new ConnectorException("Data file does not exists: " + config.getXmlFilePath().toString());
        }
        return document;
    }

    /** The operation an attribute is given to, and the schema rule that operation applies. */
    private enum Change {
        CREATE {
            @Override
            boolean allows(AttributeInfo info) {
                return info.isCreateable();
            }

            @Override
            String refusal(String attributeName) {
                return attributeName + " is not a creatable field.";
            }
        },
        UPDATE {
            @Override
            boolean allows(AttributeInfo info) {
                return info.isUpdateable();
            }

            @Override
            String refusal(String attributeName) {
                return attributeName + " is not updatable.";
            }
        };

        abstract boolean allows(AttributeInfo info);

        abstract String refusal(String attributeName);
    }

    /**
     * Checks one attribute given to create or update and returns its values as text, under the schema's name. Both
     * operations check an attribute with this method; only the operation's own schema rule differs (#192). The values
     * are read once, so a guarded value is decrypted once.
     * <p>
     * A name the schema spells exactly is that attribute. Any other name is looked up without regard to case, as the
     * framework names attributes, while the file and the XSD name them exactly: so from here on the attribute is named,
     * written and reported as the schema spells it (#212). An XSD may declare two names that differ only in case, and
     * the case-insensitive map holds one of the two for both, so the exact lookup comes first.
     */
    private static CheckedAttribute checkAttribute(Attribute attribute, Map<String, AttributeInfo> exactAttributes,
            Map<String, AttributeInfo> anyCaseAttributes, Change change) {
        AttributeInfo attributeInfo = exactAttributes.get(attribute.getName());
        if (attributeInfo == null) {
            attributeInfo = anyCaseAttributes.get(attribute.getName());
        }
        if (attributeInfo == null) {
            throw new IllegalArgumentException("Data field: " + attribute.getName() + " is not supported.");
        }
        String attributeName = attributeInfo.getName();
        if (!change.allows(attributeInfo)) {
            throw new IllegalArgumentException(change.refusal(attributeName));
        }

        // The only type and null check of the values.
        List<String> values = AttributeTypeUtil.findAttributeValue(attribute, attributeInfo);

        // create() requires __NAME__ whatever the XSD says, and an object without a name cannot be built, so a
        // cleared __NAME__ would leave an entry that no search of the class can read back.
        if (attributeInfo.isRequired() || Name.NAME.equals(attributeName)) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("No values provided for required attribute: " + attributeName);
            }
            for (String value : values) {
                Assertions.blankCheck(value, attributeName);
            }
        }

        if (!attributeInfo.isMultiValued() && values.size() > 1) {
            throw new IllegalArgumentException("Data field: " + attributeName + " is not multivalued  can not have more than one value");
        }
        return new CheckedAttribute(attributeName, values);
    }

    /**
     * Checks the attributes given to create or update, in the order given, and refuses a schema attribute given more than
     * once. The framework names attributes in any case, so lastname and LastName are one attribute unless the schema
     * has both (#212), and an attribute set can hold exactly one name twice with other values: either way neither value
     * is surely the one meant. The framework's CreateImpl refuses only exactly one name given twice, and UpdateImpl
     * neither.
     */
    private static List<CheckedAttribute> checkAttributes(Set<Attribute> attributes, ObjectClassInfo objInfo,
            Change change) {
        Map<String, AttributeInfo> exactAttributes = new HashMap<String, AttributeInfo>();
        for (AttributeInfo attributeInfo : objInfo.getAttributeInfo()) {
            exactAttributes.put(attributeInfo.getName(), attributeInfo);
        }
        Map<String, AttributeInfo> anyCaseAttributes = AttributeInfoUtil.toMap(objInfo.getAttributeInfo());

        List<CheckedAttribute> checkedAttributes = new ArrayList<CheckedAttribute>();
        Map<String, String> givenNames = new HashMap<String, String>();
        for (Attribute attribute : attributes) {
            CheckedAttribute checked = checkAttribute(attribute, exactAttributes, anyCaseAttributes, change);
            String firstGiven = givenNames.put(checked.name, attribute.getName());
            if (firstGiven != null) {
                throw new IllegalArgumentException("Data field: " + checked.name + " is given more than once, as "
                        + firstGiven + " and as " + attribute.getName() + ".");
            }
            checkedAttributes.add(checked);
        }
        return checkedAttributes;
    }

    /** An attribute that passed every check, under the schema's name, with the values to write. */
    private static final class CheckedAttribute {
        final String name;
        final List<String> values;

        CheckedAttribute(String name, List<String> values) {
            this.name = name;
            this.values = values;
        }
    }
}
