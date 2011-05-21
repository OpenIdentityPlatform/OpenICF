/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
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
 *
 * $Id$
 */
package com.forgerock.openicf.xml;

import com.forgerock.openicf.xml.util.AttributeTypeUtil;
import com.forgerock.openicf.xml.util.ElementIdentifierFieldType;
import com.forgerock.openicf.xml.util.GuardedStringAccessor;
import com.forgerock.openicf.xml.util.NamespaceLookupUtil;
import com.forgerock.openicf.xml.util.XmlHandlerUtil;
import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.query.QueryBuilder;
import com.forgerock.openicf.xml.query.XQueryHandler;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQResultSequence;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
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
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XMLHandlerImpl implements XMLHandler {

    /**
     * Setup logging for the {@link XMLHandlerImpl}.
     */
    private static final Log log = Log.getLog(XMLHandlerImpl.class);
    private XMLConfiguration config;
    private volatile Document document;
    private Schema connSchema;
    private XSSchema icfSchema;
    private XSSchema riSchema;
    private long lastModified = 0l;
    private volatile long version = 0l;
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

        NamespaceLookupUtil.INSTANCE.initialize(icfSchema, riSchema);
    }

    public XMLHandler init() {
        buildDocument();
        return this;
    }

    @Override
    public Uid create(final ObjectClass objClass, final Set<Attribute> attributes) {
        final String method = "create";
        log.info("Entry {0}", method);

        // Validate object type
        XmlHandlerUtil.checkObjectType(objClass, riSchema);

        ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());
        Set<AttributeInfo> objAttributes = null;
        Map<String, AttributeInfo> supportedAttributeInfoMap = null;
        Map<String, Attribute> providedAttributesMap = null;
        String uidValue = null;

        if (attributes != null) {
            objAttributes = objInfo.getAttributeInfo();
            supportedAttributeInfoMap = new HashMap<String, AttributeInfo>(AttributeInfoUtil.toMap(objAttributes));
            providedAttributesMap = new HashMap<String, Attribute>(AttributeUtil.toMap(attributes));
        }

        // Check if __NAME__ is defined
        if (providedAttributesMap == null || !providedAttributesMap.containsKey(Name.NAME) || providedAttributesMap.get(Name.NAME).getValue().isEmpty()) {
            throw new IllegalArgumentException(Name.NAME + " must be defined.");
        }

        Name name = AttributeUtil.getNameFromAttributes(attributes);

        // Check if entry already exists
        if (entryExists(objClass, new Uid(name.getNameValue()), ElementIdentifierFieldType.BY_NAME)) {
            throw new AlreadyExistsException("Could not create entry. An entry with the " + Uid.NAME + " of "
                    + name.getNameValue() + " already exists.");
        }

        // Create or get UID
        if (supportedAttributeInfoMap.containsKey(Uid.NAME)) {
            uidValue = UUID.randomUUID().toString();
        } else {
            uidValue = name.getNameValue();
        }

        // Create object type element
        Element objElement = document.createElementNS(riSchema.getTargetNamespace(), objClass.getObjectClassValue());
        objElement.setPrefix(RI_NAMESPACE_PREFIX);

        // Add child elements
        for (AttributeInfo attributeInfo : objAttributes) {

            String attributeName = attributeInfo.getName();

            List<String> values = AttributeTypeUtil.findAttributeValue(providedAttributesMap.get(attributeName), attributeInfo);

            // Check if required attributes contain values
            if (attributeInfo.isRequired()) {
                if (providedAttributesMap.containsKey(attributeName) && !values.isEmpty()) {
                    for (String value : values) {
                        Assertions.blankCheck(value, attributeName);
                    }
                } else {
                    throw new IllegalArgumentException("Missing required field: " + attributeName);
                }
            }

            if (!attributeInfo.isMultiValued() && values.size() > 1) {
                throw new IllegalArgumentException("Attribute field: " + attributeName + " is not multivalued and can not contain more than one value");
            }

            if (!supportedAttributeInfoMap.containsKey(attributeName)) {
                continue;
            }

            if (!attributeInfo.isCreateable() && providedAttributesMap.containsKey(attributeName)) {
                throw new IllegalArgumentException(attributeName + " is not a creatable field.");
            }

            Element childElement = null;

            if (attributeName.equals(Uid.NAME)) {
                childElement = createDomElement(attributeName, uidValue);
                objElement.appendChild(childElement);
            } else if (providedAttributesMap.containsKey(attributeName)) {
                // Check if provided value is instance of the class defined in schema
                Class expectedClass = attributeInfo.getType();
                if (!valuesAreExpectedClass(expectedClass, providedAttributesMap.get(attributeName).getValue())) {
                    throw new IllegalArgumentException(attributeName + " contains values of illegal type");
                }
                // Create elements
                for (String value : values) {
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

        document.getDocumentElement().appendChild(objElement);

        log.info("Exit {0}", method);

        return new Uid(uidValue);
    }

    @Override
    public Uid update(ObjectClass objClass, Uid uid, Set<Attribute> replaceAttributes) {
        final String method = "update";
        log.info("Entry {0}", method);

        XmlHandlerUtil.checkObjectType(objClass, riSchema);

        ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());
        Map<String, AttributeInfo> objAttributes = AttributeInfoUtil.toMap(objInfo.getAttributeInfo());

        if (entryExists(objClass, uid, ElementIdentifierFieldType.AUTO)) {

            Element entry = getEntry(objClass, uid, ElementIdentifierFieldType.AUTO);

            for (Attribute attribute : replaceAttributes) {

                if (!objAttributes.containsKey(attribute.getName())) {
                    throw new IllegalArgumentException("Data field: " + attribute.getName() + " is not supported.");
                }

                AttributeInfo attributeInfo = objAttributes.get(attribute.getName());
                String attributeName = attribute.getName();

                if (!attributeInfo.isUpdateable()) {
                    throw new IllegalArgumentException(attributeName + " is not updatable.");
                }

                if (attributeInfo.isRequired()) {
                    List<String> values = AttributeTypeUtil.findAttributeValue(attribute, attributeInfo);
                    if (values.isEmpty()) {
                        throw new IllegalArgumentException("No values provided for required attribute: " + attributeName);
                    }
                    for (String value : values) {
                        Assertions.blankCheck(value, attributeName);
                        Assertions.nullCheck(value, attributeName);
                    }
                }

                // Check if the provided value is the same as the class defined in schema
                Class expectedClass = attributeInfo.getType();

                if (attribute.getValue() != null) {
                    if (!valuesAreExpectedClass(expectedClass, attribute.getValue())) {
                        throw new IllegalArgumentException(attributeName + " contains values of illegal type");
                    }
                }

                // Remove existing nodes from entry
                removeChildrenFromElement(entry, prefixAttributeName(attributeName));

                // Add updated nodes to entry
                List<String> values = AttributeTypeUtil.findAttributeValue(attribute, attributeInfo);

                if (!attributeInfo.isMultiValued() && values.size() > 1) {
                    throw new IllegalArgumentException("Data field: " + attributeName + " is not multivalued  can not have more than one value");
                }

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
        } else {
            throw new UnknownUidException("Could not update entry. No entry of type " + objClass.getObjectClassValue() + " with the id " + uid.getUidValue() + " found.");
        }

        log.info("Exit {0}", method);

        return uid;
    }

    @Override
    public void delete(final ObjectClass objClass, final Uid uid) {
        final String method = "delete";
        log.info("Entry {0}", method);

        XmlHandlerUtil.checkObjectType(objClass, riSchema);

        if (entryExists(objClass, uid, ElementIdentifierFieldType.AUTO)) {
            Element elementToRemove = getEntry(objClass, uid, ElementIdentifierFieldType.AUTO);
            document.getDocumentElement().removeChild(elementToRemove);
            log.info("Deleting entry: " + elementToRemove.toString());
        } else {
            throw new UnknownUidException("Deleting entry failed. Could not find an entry of type " + objClass.getObjectClassValue() + " with the uid " + uid.getUidValue());
        }

        log.info("Exit {0}", method);
    }

    @Override
    public Collection<ConnectorObject> search(String query, ObjectClass objClass) {
        final String method = "search";
        log.info("Entry {0}", method);

        List<ConnectorObject> results = new ArrayList<ConnectorObject>();

        if (query != null && !query.isEmpty() && objClass != null) {

            ObjectClassInfo objInfo = connSchema.findObjectClassInfo(objClass.getObjectClassValue());
            Set<AttributeInfo> objAttributes = objInfo.getAttributeInfo();

            // Map with the attribute-names and what class they are
            HashMap<String, String> attributeClassMap = new HashMap<String, String>();
            for (AttributeInfo info : objAttributes) {
                attributeClassMap.put(info.getName(), info.getType().getSimpleName());
            }

            // Map with the AttributeInfo for each attribute
            HashMap<String, AttributeInfo> attributeInfoMap =
                    new HashMap<String, AttributeInfo>(AttributeInfoUtil.toMap(objInfo.getAttributeInfo()));

            XQueryHandler xqHandler = null;
            try {
                xqHandler = new XQueryHandler(query, document);
                XQResultSequence queryResult = xqHandler.getResultSequence();


                ConnectorObjectCreator conObjCreator =
                        new ConnectorObjectCreator(attributeClassMap, attributeInfoMap, objClass);

                while (queryResult.next()) {

                    Node resultNode = queryResult.getItem().getNode();

                    NodeList nodes = resultNode.getChildNodes();

                    ConnectorObject conObj = conObjCreator.createConnectorObject(nodes);
                    results.add(conObj);
                }
            }
            catch (XQException ex) {
                log.error("Error while searching: {0}", ex);
                throw new ConnectorException(ex);
            }
            finally {
                if (null != xqHandler) {
                    xqHandler.close();
                }
            }
        }
        log.info("Exit {0}", method);

        return results;
    }

    private boolean isExternallyModified() {
        boolean modified = false;
        if (config.getXmlFilePath().exists()) {
            modified = lastModified != config.getXmlFilePath().lastModified();
        }
        return modified;
    }

    @Override
    public void dispose() {
        final String method = "serialize";
        log.info("Entry {0}", method);
        if (version != lastModified && isExternallyModified()) {
            log.error("UPDATE COLLUSION: File has been modified after read into memory and the data in memory has not been synced before.");
        }

        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(document);
            /* Running this code in java 5 we had to change
            StreamResult result = new StreamResult(config.getXmlFilePath());
            into
            StreamResult result = new StreamResult(config.getXmlFilePath().getPath());
            Otherwise you get the following error:
            javax.xml.transform.TransformerException: java.io.FileNotFoundException:
             */
            /*
             * If the safePath is not escaped then it throws
             * net.sf.saxon.trans.XPathException: java.net.URISyntaxException:
             * Illegal character in safePath at index 9: /temp/XML Connector/test.xml
             * String safePath = config.getXmlFilePath().getPath().replaceAll(" ", "%20");
             */
            FileOutputStream fos = new FileOutputStream(config.getXmlFilePath());
            StreamResult result = new StreamResult(fos);

            transformer.transform(source, result);

            log.info("Saving changes to xml file");
        }
        catch (TransformerException ex) {
            log.error("Failed saving changes to xml file: {0}", ex);
            throw ConnectorException.wrap(ex);
        }
        catch (FileNotFoundException ex) {
            log.error("Failed saving changes to xml file: {0}", ex);
            throw ConnectorException.wrap(ex);
        }

        log.info("Entry {0}", method);
    }

    @Override
    public Uid authenticate(String username, GuardedString password) {
        final String method = "authenticate";
        log.info("Entry {0}", method);

        Uid uid = null;

        Element entry = getEntry(ObjectClass.ACCOUNT, new Uid(username), ElementIdentifierFieldType.BY_NAME);

        if (entry != null) {
            NodeList passwordElements = entry.getElementsByTagName(ICF_NAMESPACE_PREFIX + ":__PASSWORD__");

            String xmlPassword = passwordElements.item(0).getTextContent();

            GuardedStringAccessor accessor = new GuardedStringAccessor();

            password.access(accessor);

            StringBuilder sb = new StringBuilder();
            sb.append(accessor.getArray());

            String userPassword = sb.toString();

            if (xmlPassword.equals(userPassword)) {
                NodeList uidElements = entry.getElementsByTagName(ICF_NAMESPACE_PREFIX + ":" + Uid.NAME);

                if (uidElements.getLength() >= 1) {
                    uid = new Uid(uidElements.item(0).getTextContent());
                } else {
                    NodeList nameElements = entry.getElementsByTagName(ICF_NAMESPACE_PREFIX + ":" + Name.NAME);
                    uid = new Uid(nameElements.item(0).getTextContent());
                }
            }
        }

        log.info("Exit {0}", method);

        return uid;
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
        }
        catch (ParserConfigurationException ex) {
            log.error("Filed creating XML document: {0}", ex);
            throw ConnectorException.wrap(ex);
        }

        DOMImplementation implementation = builder.getDOMImplementation();
        document = implementation.createDocument(icfSchema.getTargetNamespace(), ICF_CONTAINER_TAG, null);

        Element root = document.getDocumentElement();
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

        log.info("Exit {0}", method);
    }

    private void loadDocument(File xmlFile) {
        final String method = "loadDocument";
        log.info("Entry {0}", method);

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder;

        try {
            docBuilder = docBuilderFactory.newDocumentBuilder();
            document = docBuilder.parse(xmlFile);
            lastModified = xmlFile.lastModified();
            version = lastModified;
            log.info("Loading XML document from: {0}", xmlFile.getPath());
        }
        catch (ParserConfigurationException ex) {
            throw ConnectorException.wrap(ex);
        }
        catch (SAXException ex) {
            throw ConnectorException.wrap(ex);
        }
        catch (IOException ex) {
            throw ConnectorException.wrap(ex);
        }

        log.info("Exit {0}", method);
    }

    private void buildDocument() {
        final String method = "buildDocument";
        log.info("Entry {0}", method);

        File xmlFile = config.getXmlFilePath();
        if (!xmlFile.exists()) {
            createDocument();
        } else {
            loadDocument(xmlFile);
        }

        log.info("Exit {0}", method);
    }

    private Element createDomElement(String elementName, String value) {

        Element element = null;

        if (icfSchema.getElementDecls().containsKey(elementName)) {
            element = document.createElementNS(icfSchema.getTargetNamespace(), elementName);
            element.setPrefix(ICF_NAMESPACE_PREFIX);
        } else {
            element = document.createElementNS(riSchema.getTargetNamespace(), elementName);
            element.setPrefix(RI_NAMESPACE_PREFIX);
        }

        element.setTextContent(value);

        return element;
    }

    private Element getEntry(ObjectClass objClass, Uid uid, ElementIdentifierFieldType identifierField) {
        final String method = "getEntry";
        log.info("Entry {0}", method);

        Element result = null;

        // Build search query
        XMLFilterTranslator translator = new XMLFilterTranslator();
        String idField = getElementIdentifierField(objClass, identifierField);
        AttributeBuilder builder = new AttributeBuilder();
        builder.setName(idField);
        builder.addValue(uid.getUidValue());
        EqualsFilter equals = new EqualsFilter(builder.build());
        Query query = translator.createEqualsExpression(equals, false);
        QueryBuilder queryBuilder = new QueryBuilder(query, objClass);

        // Execute query
        XQueryHandler xqHandler = null;

        try {
            xqHandler = new XQueryHandler(queryBuilder.toString(), document);
            XQResultSequence results = xqHandler.getResultSequence();

            if (results.next()) {
                result = (Element) results.getItem().getNode();
                log.info("Entry found: ", result.toString());
            }
        }
        catch (XQException ex) {
            throw ConnectorException.wrap(ex);
        }
        finally {
            if (null != xqHandler) {
                xqHandler.close();
            }
        }

        log.info("Exit {0}", method);

        return result;
    }

    private boolean entryExists(ObjectClass objClass, Uid uid, ElementIdentifierFieldType identifierField) {
        if (getEntry(objClass, uid, identifierField) != null) {
            return true;
        }

        return false;
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

    private boolean valuesAreExpectedClass(Class expectedClass, List<Object> values) {
        if (expectedClass.isPrimitive()) {
            expectedClass = AttributeTypeUtil.convertPrimitiveToWrapper(expectedClass.getName());
        }

        for (Object obj : values) {
            if (expectedClass != obj.getClass()) {
                return false;
            }
        }

        return true;
    }

    private void removeChildrenFromElement(Element element, String childName) {
        NodeList oldNodes = element.getElementsByTagName(childName);
        List<Element> elementsToRemove = new ArrayList<Element>();

        for (int i = 0; i < oldNodes.getLength(); i++) {
            elementsToRemove.add((Element) oldNodes.item(i));
        }

        for (Element e : elementsToRemove) {
            element.removeChild(e);
        }
    }

    private String prefixAttributeName(String attrName) {
        String prefix = "";

        if (icfSchema.getElementDecls().containsKey(attrName)) {
            prefix = ICF_NAMESPACE_PREFIX + ":" + attrName;
        } else {
            prefix = RI_NAMESPACE_PREFIX + ":" + attrName;
        }

        return prefix;
    }
}
