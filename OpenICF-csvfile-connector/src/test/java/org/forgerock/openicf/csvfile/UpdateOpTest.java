/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for
 * the specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file
 * and include the License file at legal/CDDLv1.0.txt. If applicable, add the following
 * below the CDDL Header, with the fields enclosed by brackets [] replaced by your
 * own identifying information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2010-2016 ForgeRock AS.
 * Portions Copyrighted 2011 Viliam Repan (lazyman)
 */
package org.forgerock.openicf.csvfile;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import static org.testng.Assert.*;
import org.forgerock.openicf.csvfile.util.TestUtils;
import java.util.Set;
import java.io.File;
import java.util.HashSet;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

public class UpdateOpTest {

    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        File file = TestUtils.getTestFile("update.csv");
        File backup = TestUtils.getTestFile("update-backup.csv");
        TestUtils.copyAndReplace(backup, file);

        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(TestUtils.getTestFile("update.csv"));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        connector = new CSVFileConnector();
        connector.init(config);
    }

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    @AfterClass
    public static void afterClass() throws Exception {
        File file = TestUtils.getTestFile("update.csv");
        file.delete();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullObjectClass() {
        connector.update(null, new Uid("vilo"), new HashSet<Attribute>(), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void badObjectClass() {
        connector.update(ObjectClass.GROUP, new Uid("vilo"), new HashSet<Attribute>(), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullUid() {
        connector.update(ObjectClass.ACCOUNT, null, new HashSet<Attribute>(), null);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void notExistingUid() {
        connector.update(ObjectClass.ACCOUNT, new Uid("unknown"), new HashSet<Attribute>(), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullAttributeSet() {
        connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), null, null);
    }

    @Test
    public void updateNonExistingAttribute() throws Exception {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(AttributeBuilder.build("nonExisting", "repantest"));
        Uid uid = connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), attributes, null);
        assertNotNull(uid);
        assertEquals("vilo", uid.getUidValue());

        String result = TestUtils.compareFiles(TestUtils.getTestFile("update.csv"),
                TestUtils.getTestFile("update-result-non-existing.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }

    @Test
    public void updateAttributeDelete() throws Exception {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(AttributeBuilder.build("lastName"));
        Uid uid = connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), attributes, null);
        assertNotNull(uid);
        assertEquals("vilo", uid.getUidValue());

        String result = TestUtils.compareFiles(TestUtils.getTestFile("update.csv"),
                TestUtils.getTestFile("update-result-delete.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }

    @Test
    public void updateAttributeAdd() throws Exception {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(AttributeBuilder.build("lastName", "repantest"));
        Uid uid = connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), attributes, null);
        assertNotNull(uid);
        assertEquals("vilo", uid.getUidValue());

        String result = TestUtils.compareFiles(TestUtils.getTestFile("update.csv"),
                TestUtils.getTestFile("update-result-add.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }

    @Test
    public void renameWhenUniqueEqualsNamingAttribute() throws Exception {
        Set<Attribute> attributes = new HashSet<Attribute>();

        attributes.add(new Name("troll"));
        Uid uid = connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), attributes, null);
        assertNotNull(uid);
        assertEquals(uid.getUidValue(), "troll");

        String result = TestUtils.compareFiles(TestUtils.getTestFile("update.csv"),
                TestUtils.getTestFile("update-result-rename.csv"));
        assertNull(result, "File updated incorrectly: " + result);
    }
}
