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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SearchResultsHandler;
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

    private File dir;
    private File csv;
    private CSVFileConnector connector;

    @BeforeMethod
    public void before() throws Exception {
        // a directory of its own, so that the copy can be shown to be made next to the CSV
        dir = Files.createTempDirectory("rewrite").toFile();
        csv = new File(dir, "accounts.csv");
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
        for (File file : dir.listFiles()) {
            file.delete();
        }
        dir.delete();
    }

    @Test
    public void updateKeepsFilePermissions() throws Exception {
        Set<PosixFilePermission> mode = setDistinctMode();

        connector.update(ObjectClass.ACCOUNT, new Uid("vilo"), lastName("updated"), null);

        assertEquals(Files.getPosixFilePermissions(csv.toPath()), mode);
        assertNoRewriteFileLeft();
    }

    @Test
    public void deleteKeepsFilePermissions() throws Exception {
        Set<PosixFilePermission> mode = setDistinctMode();

        connector.delete(ObjectClass.ACCOUNT, new Uid("vilo"), null);

        assertEquals(Files.getPosixFilePermissions(csv.toPath()), mode);
        assertNoRewriteFileLeft();
    }

    @Test
    public void rewriteCopyIsPrivateAndNextToTheFile() throws Exception {
        setDistinctMode(); // skips where POSIX permissions are not supported

        File tmp = connector.createRewriteFile();
        try {
            assertEquals(tmp.getParentFile(), csv.getAbsoluteFile().getParentFile());
            assertEquals(Files.getPosixFilePermissions(tmp.toPath()),
                    PosixFilePermissions.fromString("rw-------"));
        } finally {
            tmp.delete();
        }
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
            assertNoRewriteFileLeft();
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
            assertNoRewriteFileLeft();
        }
    }

    @Test
    public void failedDeleteKeepsRowCount() throws Exception {
        assertEquals(pagedTotal(), 2);

        try {
            connector.delete(ObjectClass.ACCOUNT, new Uid("nobody"), null);
            fail("Expected an unknown uid to fail the delete");
        } catch (UnknownUidException expected) {
            assertEquals(pagedTotal(), 2, "a failed delete changed the row count");
        }

        connector.delete(ObjectClass.ACCOUNT, new Uid("vilo"), null);
        assertEquals(pagedTotal(), 1);
    }

    @Test
    public void deleteBeforeFirstSearchKeepsRowCountUncounted() throws Exception {
        connector.delete(ObjectClass.ACCOUNT, new Uid("vilo"), null);

        assertEquals(pagedTotal(), 1);
    }

    @Test
    public void createBeforeFirstSearchKeepsRowCountUncounted() throws Exception {
        Set<Attribute> jano = new HashSet<Attribute>();
        jano.add(new Uid("jano"));
        jano.add(AttributeBuilder.build("firstName", "jan"));
        connector.create(ObjectClass.ACCOUNT, jano, null);

        assertEquals(pagedTotal(), 3);
    }

    private Set<PosixFilePermission> setDistinctMode() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("POSIX file permissions are not supported here");
        }
        // neither a new temporary file (rw-------) nor a 022 umask (rw-r--r--)
        // yields this mode: the CSV keeps it only if the rewrite copies it
        Set<PosixFilePermission> mode = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(csv.toPath(), mode);
        return mode;
    }

    private void assertNoRewriteFileLeft() {
        for (String name : dir.list()) {
            assertFalse(name.startsWith(csv.getName() + ".") && name.endsWith(".tmp"),
                    "a copy of the rewrite was left behind: " + name);
        }
    }

    /** The total row count a paged search reports. */
    private int pagedTotal() {
        final List<SearchResult> results = new ArrayList<SearchResult>();
        connector.executeQuery(ObjectClass.ACCOUNT, null, new SearchResultsHandler() {
            @Override
            public boolean handle(ConnectorObject object) {
                return true;
            }

            @Override
            public void handleResult(SearchResult result) {
                results.add(result);
            }
        }, OperationOptionsBuilder.create().setPageSize(10).build());
        assertEquals(results.size(), 1, "the paged search reported no row count");
        return results.get(0).getTotalPagedResults();
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
