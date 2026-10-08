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
 * Portions Copyrighted 2026 3A Systems, LLC
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.w3c.dom.NodeList;

public class XmlConnectorTestUtil {

    public final static File XSD_SCHEMA_FILEPATH;
    public final static File ICF_SCHEMA_FILEPATH;
    // Object types
    public static final String ACCOUNT_TYPE = "__ACCOUNT__";
    public static final String GROUP_TYPE = "__GROUP__";
    public static final String OPEN_ICF_CONTAINER_TYPE = "OpenICFContainer";
    // ICF attribute fields
    public static final String ATTR_UID = Uid.NAME;
    public static final String ATTR_NAME = Name.NAME;
    public static final String ATTR_PASSWORD = "__PASSWORD__";
    public static final String ATTR_LAST_LOGIN_DATE = "__LAST_LOGIN_DATE__";
    public static final String ATTR_DESCRIPTION = "__DESCRIPTION__";
    public static final String ATTR_DISABLE_DATE = "__DISABLE_DATE__";
    public static final String ATTR_ENABLE_DATE = "__ENABLE_DATE__";
    public static final String ATTR_ENABLE = "__ENABLE__";
    public static final String ATTR_GROUPS = "__GROUPS__";
    public static final String ATTR_SHORT_NAME = "__SHORT_NAME__";
    // Account attribute fields
    public static final String ATTR_ACCOUNT_FIRST_NAME = "firstname";
    public static final String ATTR_ACCOUNT_LAST_NAME = "lastname";
    public static final String ATTR_ACCOUNT_EMAIL = "email";
    public static final String ATTR_ACCOUNT_ADDRESS = "address";
    public static final String ATTR_ACCOUNT_EMPLOYEE_NUMBER = "employee-number";
    public static final String ATTR_ACCOUNT_EMPLOYEE_TYPE = "employee-type";
    public static final String ATTR_ACCOUNT_SECRET_ANSWER = "password-secret-answer";
    public static final String ATTR_ACCOUNT_IS_DELETED = "is-deleted";
    public static final String ATTR_ACCOUNT_PHOTO = "jpegPhoto";
    public static final String ATTR_ACCOUNT_LAST_LOGOFF_DATE = "last-logoff";
    public static final String ATTR_ACCOUNT_CREATED_TIMESTAMP = "account-created-timestamp";
    public static final String ATTR_ACCOUNT_MS_EMPLOYED = "ms-employed";
    public static final String ATTR_ACCOUNT_FIRST_LETTER_LAST_NAME = "lastname-first-letter";
    public static final String ATTR_ACCOUNT_GENDER = "gender";
    public static final String ATTR_ACCOUNT_HOURLY_WAGE = "hourly-wage";
    public static final String ATTR_ACCOUNT_OVERTIME_COMISSION = "overtime-commission";
    public static final String ATTR_ACCOUNT_AVERAGE_WAGE = "avg-wage";
    public static final String ATTR_ACCOUNT_OFFICE_SQUARE_FEET = "office-square-feet";
    public static final String ATTR_ACCOUNT_AGE = "age";
    public static final String ATTR_ACCOUNT_YEARS_EMPLOYED = "years-employed";
    public static final String ATTR_ACCOUNT_SIXTH_SENSE = "has-sixth-sense";
    public static final String ATTR_ACCOUNT_PERMANTENT_EMPLOYEE = "permanent-employee";
    public static final String ATTR_ACCOUNT_YEARLY_WAGE = "yearly-wage";
    public static final String ATTR_ACCOUNT_MAX_STORAGE = "max-storage";
    public static final String ATTR_ACCOUNT_USER_CERTIFICATE = "userCertificate";
    public static final String ATTR_ACCOUNT_SECRET_PIN = "secret-pin";
    //Attribute values for test account
    public static final String ATTR_ACCOUNT_VALUE_NAME = "vaderUID";
    public static final String ATTR_ACCOUNT_VALUE_PASSWORD = "secret";
    public static final String ATTR_ACCOUNT_VALUE_LAST_NAME = "Vader's";
    public static final String ATTR_ACCOUNT_VALUE_FIRST_NAME = "Darth";
    public static final char ATTR_ACCOUNT_VALUE_FIRST_LETTER_LAST_NAME = 'V';
    public static final String ATTR_ACCOUNT_VALUE_EMPLOYEE_TYPE = "bad-ass";
    public static final String ATTR_ACCOUNT_VALUE_EMAIL_1 = "darth@deathstar.org";
    public static final String ATTR_ACCOUNT_VALUE_EMAIL_2 = "vader@deathstar.org";
    public static final String ATTR_ACCOUNT_VALUE_SECRET_PIN = "123";
    public static final double ATTR_ACCOUNT_VALUE_OVERTIME_COMMISSION = new Double(20.20);
    public static final boolean ATTR_ACCOUNT_VALUE_PERMANENT_EPLOYEE = true;
    public static final int ATTR_ACCOUNT_VALUE_YEARS_EPLOYED = 200;
    public static final boolean ATTR_ACCOUNT_VALUE_IS_DLETED = true;
    public static final Long ATTR_ACCOUNT_VALUE_MS_EMPLOYED = new Long(999999);
    public static final boolean ATTR_ACCOUNT_VALUE_SIXTH_SENSE = true;
    // Attribute values for test group
    public static final String ATTR_GROUP_VALUE_NAME = "The Empire";
    public static final String ATTR_GROUP_VALUE_DESCRIPTION = "The cool guys";
    public static final String ATTR_GROUP_VALUE_SHORT_NAME = "TE";

    static {
        XSD_SCHEMA_FILEPATH = getTestFile("ef2bc95b-76e0-48e2-86d6-4d4f44d4e4a4.xsd");
        ICF_SCHEMA_FILEPATH = getTestFile("resource-schema-1.xsd");
    }

    public static final String ICF_NAMESPACE = "http://openidm.forgerock.com/xml/ns/public/resource/openicf/resource-schema-1.xsd";
    public static final String RI_NAMESPACE = "http://openidm.forgerock.com/xml/ns/public/resource/instances/ef2bc95b-76e0-48e2-86d6-4d4f44d4e4a4";

    /**
     * Writes a store file with one account per name: __UID__ uid-NAME, __NAME__ NAME, lastname
     * Last-NAME and __PASSWORD__ secret-NAME. Names of equal length give files of equal size. An
     * existing file is rewritten in place, so it keeps its file key.
     */
    public static void writeAccounts(File file, String... names) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<icf:OpenICFContainer xmlns:icf=\"").append(ICF_NAMESPACE)
                .append("\" xmlns:ri=\"").append(RI_NAMESPACE).append("\">\n");
        for (String name : names) {
            xml.append("  <ri:__ACCOUNT__>\n")
                    .append("    <icf:__UID__>uid-").append(name).append("</icf:__UID__>\n")
                    .append("    <icf:__NAME__>").append(name).append("</icf:__NAME__>\n")
                    .append("    <ri:lastname>Last-").append(name).append("</ri:lastname>\n")
                    .append("    <icf:__PASSWORD__>secret-").append(name).append("</icf:__PASSWORD__>\n")
                    .append("  </ri:__ACCOUNT__>\n");
        }
        xml.append("</icf:OpenICFContainer>\n");
        Files.write(file.toPath(), xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void setModified(File file, long millis) throws IOException {
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(millis));
    }

    /** The __NAME__ values in the file, in document order, read by a parser of its own. */
    public static List<String> namesInFile(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        NodeList names = factory.newDocumentBuilder().parse(file).getElementsByTagNameNS(ICF_NAMESPACE, Name.NAME);
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < names.getLength(); i++) {
            result.add(names.item(i).getTextContent());
        }
        return result;
    }

    /** The attributes of a new account with the values {@link #writeAccounts} writes; create assigns its own __UID__. */
    public static Set<Attribute> account(String name) {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(AttributeBuilder.build(Name.NAME, name));
        attributes.add(AttributeBuilder.buildPassword(("secret-" + name).toCharArray()));
        attributes.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Last-" + name));
        return attributes;
    }

    public static File getRandomXMLFile() {
        return getTestFile(UUID.randomUUID().toString() + ".xml");
    }

    public static File getTestFile(String fileName) {
        try {
            URL root = XmlConnectorTestUtil.class.getResource("/");
            return new File(root.toURI().resolve("test/xml_store/" + fileName));
        }
        catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Testfile URL is invalid", ex);
        }
    }

    public static Set<Attribute> getRequiredAccountAttributes() {
        Set<Attribute> requiredAttrSet = new HashSet<Attribute>();
        requiredAttrSet.add(AttributeBuilder.build(ATTR_NAME, ATTR_ACCOUNT_VALUE_NAME));
        requiredAttrSet.add(AttributeBuilder.buildPassword(ATTR_ACCOUNT_VALUE_PASSWORD.toCharArray()));
        requiredAttrSet.add(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, ATTR_ACCOUNT_VALUE_LAST_NAME));

        return requiredAttrSet;
    }

    public static Set<Attribute> getRequiredGroupAttributes() {
        Set<Attribute> requiredAttrSet = new HashSet<Attribute>();

        requiredAttrSet.add(AttributeBuilder.build(ATTR_NAME, ATTR_GROUP_VALUE_NAME));
        requiredAttrSet.add(AttributeBuilder.build(ATTR_DESCRIPTION, ATTR_GROUP_VALUE_DESCRIPTION));
        requiredAttrSet.add(AttributeBuilder.build(ATTR_SHORT_NAME, ATTR_GROUP_VALUE_SHORT_NAME));

        return requiredAttrSet;
    }

    public static Map<String, Attribute> convertToAttributeMap(Set<Attribute> attrSet) {
        return new HashMap<String, Attribute>(AttributeUtil.toMap(attrSet));
    }

    public static Set<Attribute> convertToAttributeSet(Map<String, Attribute> attrMap) {
        return new HashSet<Attribute>(attrMap.values());
    }

    public static class TestResultsHandler implements ResultsHandler {

        private Set<ConnectorObject> results;

        public TestResultsHandler() {
            results = new HashSet<ConnectorObject>();
        }

        public boolean handle(ConnectorObject connObject) {
            results.add(connObject);

            return true;
        }

        public int getResultSize() {
            return results.size();
        }

        public void setResults(Collection<ConnectorObject> results) {
            this.results = new HashSet<ConnectorObject>(results);
        }
    }
}
