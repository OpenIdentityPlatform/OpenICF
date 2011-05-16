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
package org.forgerock.openicf.csvfile;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import static org.testng.Assert.*;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.forgerock.openicf.csvfile.util.Utils;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.forgerock.openicf.csvfile.util.Base64;

import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

import java.io.File;

import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.testng.annotations.Test;

/**
 * @author lazyman
 */
public class CreateOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        File file = TestUtils.getTestFile("create.csv");
        File backup = TestUtils.getTestFile("create-backup.csv");
        Utils.copyAndReplace(backup, file);

        file = TestUtils.getTestFile("create-empty.csv");
        backup = TestUtils.getTestFile("create-backup-empty.csv");
        Utils.copyAndReplace(backup, file);
    }

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    @AfterClass
    public static void afterClass() throws Exception {
//        File file = TestUtils.getTestFile("create.csv");
//        file.delete();
//        file = TestUtils.getTestFile("create-empty.csv");
//        file.delete();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClass() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("create.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        connector.create(null, createAttributeSet(), null);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void badObjectClass() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("create.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        connector.create(ObjectClass.GROUP, createAttributeSet(), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullAttributes() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("create.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        connector.create(ObjectClass.ACCOUNT, null, null);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void badWithoutUniqueAttribute() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("create.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(createAttribute("firstName", "viliam"));
        attributes.add(createAttribute("lastName", "repan", "repan2"));
        attributes.add(AttributeBuilder.buildPassword(new GuardedString(Base64.encode("asdf").toCharArray())));
        connector.create(ObjectClass.ACCOUNT, attributes, null);
    }

    @Test(expectedExceptions = AlreadyExistsException.class)
    public void uidAlreadyExists() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("create.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");

        connector = new CSVFileConnector();
        connector.init(config);
        Uid uid = connector.create(ObjectClass.ACCOUNT, createAttributeSet(), null);

        assertNotNull(uid);
        assertEquals("vilo", uid.getUidValue());

        String result = TestUtils.compareFiles(config.getFilePath(),
                TestUtils.getTestFile("create-result-with-pwd.csv"));
        assertNull("File updated incorrectly: " + result, result);
    }

    @Test
    public void createWithoutUid() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("create-empty.csv"));
        config.setUniqueAttribute("uid");
        config.setPasswordAttribute("password");
        config.setNameAttribute("lastName");

        connector = new CSVFileConnector();
        connector.init(config);

        final String uidValue = "uid=vilo,dc=example,dc=com";
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(new Name("vilo repan"));
        attributes.add(createAttribute("firstName", "vilo"));
        attributes.add(createAttribute("uid", uidValue));
        attributes.add(AttributeBuilder.buildPassword(new GuardedString(Base64.encode("asdf").toCharArray())));
        Uid uid = connector.create(ObjectClass.ACCOUNT, attributes, null);
        assertNotNull(uid);
        assertEquals(uidValue, uid.getUidValue());
    }

    private Set<Attribute> createAttributeSet() {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(new Name("vilo"));
        attributes.add(createAttribute("firstName", "viliam"));
        attributes.add(createAttribute("lastName", "repan", "repan2"));
        attributes.add(AttributeBuilder.buildPassword(new GuardedString(Base64.encode("asdf").toCharArray())));

        return attributes;
    }

    private Attribute createAttribute(String name, Object... values) {
        return AttributeBuilder.build(name, values);
    }
}
