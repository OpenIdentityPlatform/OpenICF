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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.annotations.Test;

public class XMLConnectorScaleTests {

    private static final int ENTRIES = 20_000;
    private static final int LOOKUPS = 20;
    /** About 1 s with the index; quadratic lookups need well over a minute. */
    private static final long LIMIT_MILLIS = 10_000L;

    @Test(timeOut = 120_000L)
    public void identifierLookupsOnALargeFileStayFast() throws Exception {
        File file = getRandomXMLFile();
        String[] names = new String[ENTRIES];
        for (int i = 0; i < ENTRIES; i++) {
            names[i] = "user-" + i;
        }
        writeAccounts(file, names);
        // An old mtime keeps the stamp out of the racy window, so the file is parsed once.
        setModified(file, System.currentTimeMillis() - 3_600_000L);

        XMLConfiguration config = new XMLConfiguration();
        config.setXmlFilePath(file);
        config.setXsdFilePath(XSD_SCHEMA_FILEPATH);
        ConnectorFacade facade = ConnectorFacadeFactory.getInstance()
                .newInstance(TestHelpers.createTestConfiguration(XMLConnector.class, config));
        facade.test(); // parses the file and builds the index; not timed

        long start = System.nanoTime();
        for (int i = 0; i < LOOKUPS; i++) {
            String name = names[ENTRIES - 1 - i * (ENTRIES / LOOKUPS)];
            TestResultsHandler byName = new TestResultsHandler();
            facade.search(ObjectClass.ACCOUNT, new EqualsFilter(new Name(name)), byName, null);
            assertEquals(byName.getResultSize(), 1, name);
            assertNotNull(facade.getObject(ObjectClass.ACCOUNT, new Uid("uid-" + name), null), name);
            assertEquals(facade.authenticate(ObjectClass.ACCOUNT, name,
                    new GuardedString(("secret-" + name).toCharArray()), null).getUidValue(), "uid-" + name);
        }
        TestResultsHandler missing = new TestResultsHandler();
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(new Name("no-such-user")), missing, null);
        assertEquals(missing.getResultSize(), 0);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis < LIMIT_MILLIS,
                (3 * LOOKUPS + 1) + " lookups in " + ENTRIES + " entries took " + elapsedMillis + " ms");
    }
}
