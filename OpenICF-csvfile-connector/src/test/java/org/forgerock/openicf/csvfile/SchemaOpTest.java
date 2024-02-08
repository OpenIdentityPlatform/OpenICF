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

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.testng.annotations.AfterMethod;
import static org.testng.Assert.*;
import java.util.Iterator;
import org.identityconnectors.framework.common.objects.ObjectClass;
import java.util.Set;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.Test;

public class SchemaOpTest {

    private CSVFileConnector connector;

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void badUniqueFileSchema() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
//        URL testFile = UtilsTest.class.getResource("/files/update-attribute.csv");
//        config.setFilePath(new File(testFile.toURI()));
        config.setCsvFile(TestUtils.getTestFile("schema-bad-unique.csv"));
        config.setHeaderUid("uid");

        connector = new CSVFileConnector();
        connector.init(config);
        connector.schema();
    }

    @Test
    public void goodFileSchema() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
//        URL testFile = UtilsTest.class.getResource("/files/update-attribute.csv");
//        config.setFilePath(new File(testFile.toURI()));
        config.setCsvFile(TestUtils.getTestFile("schema-good.csv"));
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        connector = new CSVFileConnector();
        connector.init(config);

        Schema schema = connector.schema();
        assertNotNull(schema);
        Set<ObjectClassInfo> objClassInfos = schema.getObjectClassInfo();
        assertNotNull(objClassInfos);
        assertEquals(1, objClassInfos.size());

        ObjectClassInfo info = objClassInfos.iterator().next();
        assertNotNull(info);
        assertEquals(ObjectClass.ACCOUNT.getObjectClassValue(), info.getType());
        assertFalse(info.isContainer());
        Set<AttributeInfo> attrInfos = info.getAttributeInfo();
        assertNotNull(attrInfos);
        assertEquals(attrInfos.size(), 5);

        testAttribute("__NAME__", attrInfos, false);
        testAttribute("lastName", attrInfos, false);
        testAttribute("__UID__", attrInfos, false);
        testAttribute("__PASSWORD__", attrInfos, true);
    }

    private void testAttribute(String name, Set<AttributeInfo> attrInfos, boolean password) {
        Iterator<AttributeInfo> iterator = attrInfos.iterator();

        boolean found = false;
        while (iterator.hasNext()) {
            AttributeInfo info = iterator.next();
            assertNotNull(info);

            if (!name.equals(info.getName())) {
                continue;
            }
            found = true;

            if (password) {
                assertEquals(GuardedString.class, info.getType());
            } else {
                assertEquals(String.class, info.getType());
            }
        }

        assertTrue(found);
    }
}
