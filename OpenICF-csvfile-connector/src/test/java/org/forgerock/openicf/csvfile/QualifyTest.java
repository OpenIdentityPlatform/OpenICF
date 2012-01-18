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
 * Portions Copyrighted 2012 Viliam Repan (lazyman)
 *
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.forgerock.openicf.csvfile.util.TestUtils;
import org.forgerock.openicf.csvfile.util.Utils;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 *
 * @author lazyman
 */
public class QualifyTest {

    @Test
    public void simpleAlwaysQualifyTrueTest() throws Exception {
        File file = TestUtils.getTestFile("qualify-true.csv");
        File backup = TestUtils.getTestFile("qualify-empty.csv");
        Utils.copyAndReplace(backup, file);

        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(file);
        config.setUniqueAttribute("EmployeeNumber");
        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        createRecord(connector, "Paul", "Davis", "123451", "paul.davis@example.com", "New Zealand");
        createRecord(connector, "James", "Rogers", "123452", "james.rogers@example.com", "WA, USA");
        createRecord(connector, "Wayne", "Smith", "123453", "wayne.smith@example.com", "Paris, France");

        String result = TestUtils.compareFiles(config.getFilePath(),
                TestUtils.getTestFile("qualify-true-result.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }

    private void createRecord(CSVFileConnector connector, String firstName, String lastName,
            String employeeNumber, String emailAddress, String location) {

        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(createAttribute("FirstName", firstName));
        attributes.add(createAttribute("LastName", lastName));
        attributes.add(new Name(employeeNumber));
        attributes.add(createAttribute("EmailAddress", emailAddress));
        attributes.add(createAttribute("Location", location));

        connector.create(ObjectClass.ACCOUNT, attributes, null);
    }

    private Attribute createAttribute(String name, Object... values) {
        return AttributeBuilder.build(name, values);
    }    

//    @Test
    public void simpleAlwaysQualifyFalseTest() throws Exception {
        File file = TestUtils.getTestFile("qualify-false.csv");
        File backup = TestUtils.getTestFile("qualify-empty.csv");
        Utils.copyAndReplace(backup, file);

        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(file);
        config.setUniqueAttribute("EmployeeNumber");
        config.setAlwaysQualify(false);
        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        createRecord(connector, "Paul", "Davis", "123451", "paul.davis@example.com", "New Zealand");
        createRecord(connector, "James", "Rogers", "123452", "james.rogers@example.com", "WA, USA");
        createRecord(connector, "Wayne", "Smith", "123453", "wayne.smith@example.com", "Paris, France");

        String result = TestUtils.compareFiles(config.getFilePath(),
                TestUtils.getTestFile("qualify-false-result.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }
}
