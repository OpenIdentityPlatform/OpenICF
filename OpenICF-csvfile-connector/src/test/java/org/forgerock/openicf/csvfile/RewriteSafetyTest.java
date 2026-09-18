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
package org.forgerock.openicf.csvfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Update and delete rewrite the whole CSV file through a temporary copy.
 * That copy must not weaken the permissions of the data file, and a rewrite
 * that fails half-way must not replace the data file with the fragment.
 */
public class RewriteSafetyTest {

    private static final String HEADER = "firstName,uid,lastName,password\n";
    private static final String VILO = "\"viliam\",\"vilo\",\"repan\",\"Z29vZA==\"\n";
    private static final String MALFORMED = "\"too\",\"few\"\n";
    private static final String MISO = "\"michal\",\"miso\",\"kovac\",\"Z29vZA==\"\n";

    private File csv;
    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        csv = File.createTempFile("rewrite", ".csv");
        write(HEADER + VILO + MISO);

        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(csv);
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");

        connector = new CSVFileConnector();
        connector.init(config);
    }

    @AfterMethod
    public void after() {
        connector.dispose();
        connector = null;
        csv.delete();
    }

    @Test
    public void updateKeepsFilePermissions() throws Exception {
        Set<PosixFilePermission> ownerOnly = restrictToOwner();

        connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), lastName("updated"), null);

        assertEquals(Files.getPosixFilePermissions(csv.toPath()), ownerOnly);
    }

    @Test
    public void deleteKeepsFilePermissions() throws Exception {
        Set<PosixFilePermission> ownerOnly = restrictToOwner();

        connector.delete(ObjectClass.ACCOUNT, new Uid("vilo"), null);

        assertEquals(Files.getPosixFilePermissions(csv.toPath()), ownerOnly);
    }

    @Test
    public void failedUpdateLeavesFileUntouched() throws Exception {
        String original = HEADER + VILO + MALFORMED + MISO;
        write(original);
        try {
            connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), lastName("updated"), null);
            fail("Expected the malformed row to fail the update");
        } catch (RuntimeException expected) {
            assertEquals(read(), original, "the data file was replaced by the partial rewrite");
        }
    }

    @Test
    public void failedDeleteLeavesFileUntouched() throws Exception {
        String original = HEADER + VILO + MALFORMED + MISO;
        write(original);
        try {
            connector.delete(ObjectClass.ACCOUNT, new Uid("vilo"), null);
            fail("Expected the malformed row to fail the delete");
        } catch (RuntimeException expected) {
            assertEquals(read(), original, "the data file was replaced by the partial rewrite");
        }
    }

    private Set<PosixFilePermission> restrictToOwner() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("POSIX file permissions are not supported here");
        }
        Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(csv.toPath(), ownerOnly);
        return ownerOnly;
    }

    private static Set<Attribute> lastName(String value) {
        return Collections.singleton(AttributeBuilder.build("lastName", value));
    }

    private void write(String content) throws Exception {
        Files.write(csv.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String read() throws Exception {
        return new String(Files.readAllBytes(csv.toPath()), StandardCharsets.UTF_8);
    }
}
