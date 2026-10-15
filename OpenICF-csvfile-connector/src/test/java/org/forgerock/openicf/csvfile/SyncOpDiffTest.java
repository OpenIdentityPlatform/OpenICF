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
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.supercsv.exception.SuperCsvException;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Pins the deltas {@link CSVFileConnector#sync} reports when it compares a snapshot with a copy of the live CSV file.
 * Every test works on its own files: the connector caches the header per CSV path.
 */
public class SyncOpDiffTest {

    private static final String HEADER = "uid,firstName,lastName,password";

    private static final SyncToken SNAPSHOT_TOKEN = new SyncToken(1300000000000L);

    // the second row of each uid differs from the first one, so even a fresh copy of this file reports two UPDATEs
    private static final String[] DUPLICATE_UIDS =
            { HEADER, "x,Jane,Doe,pw1", "x,Jane,Changed,pw1", "y,John,Roe,pw2", "y,John,Other,pw2" };

    private Path dir;

    private File csvFile;

    private CSVFileConnector connector;

    private List<String> deltas;

    private SyncToken resultToken;

    @BeforeMethod
    public void before() throws IOException {
        dir = Files.createTempDirectory("csv-sync");
        csvFile = dir.resolve("data.csv").toFile();
        deltas = null;
        resultToken = null;
    }

    @AfterMethod(alwaysRun = true)
    public void after() throws IOException {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    // The timeout catches a file re-scan per row, such as the old nested scan, not a quadratic in-memory lookup
    @Test(timeOut = 30000)
    public void largeFileSyncReportsExactDeltasWithoutRescanningFiles() throws Exception {
        List<String> snapshot = new ArrayList<>(Collections.singletonList(HEADER));
        List<String> live = new ArrayList<>(Collections.singletonList(HEADER));
        List<String> deletes = new ArrayList<>();
        List<String> createsAndUpdates = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            String uid = String.format("u%05d", i);
            String firstName = "First" + i;
            String lastName = "Last" + i;
            String password = "pw" + i;
            snapshot.add(String.join(",", uid, firstName, lastName, password));
            if (i % 7 == 3) {
                deletes.add(render("DELETE", uid, firstName, lastName, password));
                continue;
            }
            if (i % 11 == 5) {
                lastName += "-changed";
                createsAndUpdates.add(render("UPDATE", uid, firstName, lastName, password));
            } else if (i % 13 == 6) {
                password += "-changed";
                createsAndUpdates.add(render("UPDATE", uid, firstName, lastName, password));
            }
            live.add(String.join(",", uid, firstName, lastName, password));
            if (i % 17 == 0) {
                String newUid = String.format("n%05d", i);
                live.add(String.join(",", newUid, "New" + i, "Row" + i, "npw" + i));
                createsAndUpdates.add(render("CREATE", newUid, "New" + i, "Row" + i, "npw" + i));
            }
        }
        givenSnapshot(snapshot.toArray(new String[0]));
        givenLive(live.toArray(new String[0]));

        List<String> expected = new ArrayList<>(deletes);
        expected.addAll(createsAndUpdates);
        assertEquals(sync(SNAPSHOT_TOKEN), expected);
    }

    @Test
    public void unchangedRowsProduceNoDelta() throws Exception {
        givenSnapshot(HEADER, "\"u1\",\"Jane\",\"Doe\",\"pw1\"", "u2,John,,pw2", "u3,Ann,\"  \",pw3");
        givenLive(HEADER, "u1,Jane,Doe,pw1", "u2,John,\"\",pw2", "u3,Ann,   ,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.emptyList());
    }

    @Test
    public void nullTokenProducesNoDelta() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1", "u2,John,Roe,pw2");

        assertEquals(sync(null), Collections.emptyList());
    }

    @Test
    public void snapshotRowWithUidEqualToUidColumnNameIsDeleted() throws Exception {
        givenSnapshot(HEADER, "uid,Jane,Doe,pw1", "u2,John,Roe,pw2");
        givenLive(HEADER, "u2,John,Roe,pw2");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE uid [Jane, Doe, pw1]"));
    }

    @DataProvider
    public Object[][] rowsWithoutUid() {
        return new Object[][] {
            { ",Jane,Doe,pw1" },
            { "\"  \",Jane,Doe,pw1" }
        };
    }

    @Test(dataProvider = "rowsWithoutUid")
    public void rowWithoutUidFailsSync(String row) throws Exception {
        givenSnapshot(HEADER, row, "u2,John,Roe,pw2");
        givenLive(HEADER, row, "u2,John,Roe,pw2");

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> sync(SNAPSHOT_TOKEN));
        assertEquals(e.getMessage(), "Line 2 of " + csvFile + " has no uid value");
    }

    @Test(dataProvider = "rowsWithoutUid")
    public void rowWithoutUidFailsSyncBeforeAnyDelta(String row) throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Changed,pw1", row);

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> sync(SNAPSHOT_TOKEN));
        assertEquals(deltas, Collections.emptyList());
        assertEquals(e.getMessage(), "Line 3 of " + csvFile + " has no uid value");
    }

    @Test(dataProvider = "rowsWithoutUid")
    public void snapshotRowWithoutUidIsSkipped(String row) throws Exception {
        givenSnapshot(HEADER, "u1,Ann,Roe,pw0", row, "u3,Bob,Poe,pw3");
        givenLive(HEADER, "u3,Bob,Poe,pw3", "u2,Jane,Doe,pw1");

        assertEquals(sync(SNAPSHOT_TOKEN), Arrays.asList("DELETE u1 [Ann, Roe, pw0]", "CREATE u2 [Jane, Doe, pw1]"));
    }

    @Test
    public void skippedSnapshotRowIsLogged() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1", ",John,Roe,pw2");
        givenLive(HEADER, "u1,Jane,Doe,pw1");

        String output = captureStdOut(() -> sync(SNAPSHOT_TOKEN));

        assertTrue(output.contains("Skipping line 3 of "
                + dir.resolve(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()) + ": it has no uid value"), output);
    }

    @Test
    public void failedSyncDeletesTheSnapshotItCopied() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1", ",John,Roe,pw2");

        expectThrows(IllegalArgumentException.class, () -> sync(null));

        assertEquals(snapshots(), Collections.emptyList());
    }

    @Test
    public void failedSyncKeepsTheSnapshotOfItsToken() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1", ",John,Roe,pw2");

        expectThrows(IllegalArgumentException.class, () -> sync(SNAPSHOT_TOKEN));

        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()));
    }

    @Test
    public void successfulSyncKeepsTheSnapshotItCopied() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");

        sync(null);

        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + resultToken.getValue()));
    }

    @Test
    public void syncWhoseTokenIsNotDeliveredDeletesTheSnapshotItCopied() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");

        expectThrows(IllegalStateException.class, () -> syncWithFailingHandleResult(null, () -> { }));

        assertEquals(snapshots(), Collections.emptyList());
    }

    @Test
    public void syncWhoseTokenIsNotDeliveredDeletesTheSnapshotItCopiedAfterTheDiff() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1", "u2,John,Roe,pw2");

        expectThrows(IllegalStateException.class, () -> syncWithFailingHandleResult(SNAPSHOT_TOKEN, () -> { }));

        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()));
    }

    @Test
    public void failedSyncKeepsTheSnapshotOfADeltaItHandled() throws Exception {
        givenLive(DUPLICATE_UIDS);

        List<SyncToken> handled = syncFailingAfter(1);

        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + handled.get(0).getValue()));
    }

    @Test
    public void failedSyncKeepsTheSnapshotOfADeleteItHandled() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1", "u2,John,Roe,pw2");
        connector();
        // relies on the header cached at init: the live rows are read with it, so every live uid is a password,
        // while a fresh copy is read with its own header and reports two DELETEs
        givenLive("password,lastName,uid,firstName", "pw1,Doe,u1,Jane", "pw2,Roe,u2,John");

        List<SyncToken> handled = syncFailingAfter(1);

        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + handled.get(0).getValue()));
    }

    @Test
    public void syncWhoseFirstDeltaFailsDeletesTheSnapshotItCopied() throws Exception {
        givenLive(DUPLICATE_UIDS);

        syncFailingAfter(0);

        assertEquals(snapshots(), Collections.emptyList());
    }

    @Test
    public void snapshotThatCannotBeDeletedIsLogged() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("A read-only directory needs POSIX permissions");
        }
        final Set<PosixFilePermission> mode = Files.getPosixFilePermissions(dir);
        final Set<PosixFilePermission> readOnly = PosixFilePermissions.fromString("r-xr-xr-x");
        setDirMode(readOnly);
        final boolean writable = Files.isWritable(dir);
        setDirMode(mode);
        if (writable) {
            throw new SkipException("A read-only directory does not stop this user from deleting files");
        }
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        final File copy = new File(dir.toFile(), csvFile.getName() + "." + csvFile.lastModified());

        final String output;
        try {
            output = captureStdOut(() -> expectThrows(IllegalStateException.class,
                    () -> syncWithFailingHandleResult(null, () -> setDirMode(readOnly))));
        } finally {
            setDirMode(mode);
        }

        assertTrue(output.contains("Could not delete " + copy), output);
    }

    @Test
    public void syncThatCannotListItsSnapshotsDeletesTheSnapshotItCopied() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("An unlistable directory needs POSIX permissions");
        }
        final Set<PosixFilePermission> mode = Files.getPosixFilePermissions(dir);
        // files can still be created in the directory, but not listed
        final Set<PosixFilePermission> writeOnly = PosixFilePermissions.fromString("-wx------");
        setDirMode(writeOnly);
        final boolean listable = dir.toFile().list() != null;
        setDirMode(mode);
        if (listable) {
            throw new SkipException("A directory without read permission does not stop this user from listing it");
        }
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        connector();
        // only creating or deleting an entry in the directory moves its mtime off this value
        final FileTime untouched = FileTime.fromMillis(0);
        Files.setLastModifiedTime(dir, untouched);

        setDirMode(writeOnly);
        try {
            // the copy is made, then scrubSyncFiles fails while the sync still holds the write lock;
            // how it fails is not the point here
            expectThrows(RuntimeException.class, () -> sync(null));
        } finally {
            setDirMode(mode);
        }

        assertNotEquals(Files.getLastModifiedTime(dir), untouched, "The sync failed before it copied the CSV file");
        assertEquals(snapshots(), Collections.emptyList());
    }

    @Test
    public void rowWithWrongColumnCountFailsSyncBeforeAnyDelta() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Changed,pw1", "u2,John,Roe");

        expectThrows(SuperCsvException.class, () -> sync(SNAPSHOT_TOKEN));
        assertEquals(deltas, Collections.emptyList());
    }

    @Test
    public void duplicateUidsAreComparedWithTheFirstSnapshotRow() throws Exception {
        givenSnapshot(HEADER, "x,Jane,Doe,pw1", "x,Jane,Other,pw1", "y,John,Roe,pw2", "y,John,Roe,pw2");
        givenLive(HEADER, "x,Jane,Doe,pw1", "x,Jane,Changed,pw1");

        assertEquals(sync(SNAPSHOT_TOKEN), Arrays.asList(
                "DELETE y [John, Roe, pw2]",
                "DELETE y [John, Roe, pw2]",
                "UPDATE x [Jane, Changed, pw1]"));
    }

    @Test
    public void permutedSnapshotColumnsProduceNoDelta() throws Exception {
        givenSnapshot("password,lastName,uid,firstName", "pw1,Doe,u1,Jane", "pw2,Roe,u2,John");
        givenLive(HEADER, "u1,Jane,Doe,pw1", "u2,John,Roe,pw2");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.emptyList());
    }

    @Test
    public void emptyCellDiffersFromBlankCell() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,,pw1");
        givenLive(HEADER, "u1,Jane,\"  \",pw1");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("UPDATE u1 [Jane, , pw1]"));
    }

    @Test
    public void valueBoundariesAreSignificant() throws Exception {
        givenSnapshot(HEADER, "u1,Jan,eDoe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("UPDATE u1 [Jane, Doe, pw1]"));
    }

    @DataProvider
    public Object[][] caseOnlyColumnChanges() {
        return new Object[][] {
            { "u1,Jane,Doe,pw1,changed,b" },
            { "u1,Jane,Doe,pw1,a,changed" }
        };
    }

    @Test(dataProvider = "caseOnlyColumnChanges")
    public void columnsDifferingOnlyInCaseAreBothCompared(String liveRow) throws Exception {
        givenSnapshot(HEADER + ",Mail,mail", "u1,Jane,Doe,pw1,a,b");
        givenLive(HEADER + ",Mail,mail", liveRow);

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("UPDATE u1 [Jane, Doe, pw1]"));
    }

    @Test
    public void handlerStopEndsTheDeletePass() throws Exception {
        givenSnapshot(HEADER, "d1,Jane,Doe,pw1", "d2,John,Roe,pw2", "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN, false), Collections.singletonList("DELETE d1 [Jane, Doe, pw1]"));
        assertEquals(resultToken.getValue(), SNAPSHOT_TOKEN.getValue());
    }

    @Test
    public void handlerStopEndsTheCreateUpdatePass() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3", "c1,Jane,Doe,pw1", "c2,John,Roe,pw2");

        assertEquals(sync(SNAPSHOT_TOKEN, false), Collections.singletonList("CREATE c1 [Jane, Doe, pw1]"));
        assertEquals(resultToken.getValue(), SNAPSHOT_TOKEN.getValue());
    }

    @DataProvider
    public Object[][] singleChanges() {
        return new Object[][] {
            { "delete", new String[] { HEADER, "k,Ann,Poe,pw3", "d,Jane,Doe,pw1" }, new String[] { HEADER, "k,Ann,Poe,pw3" } },
            { "create", new String[] { HEADER, "k,Ann,Poe,pw3" }, new String[] { HEADER, "k,Ann,Poe,pw3", "c,Jane,Doe,pw1" } }
        };
    }

    @Test(dataProvider = "singleChanges")
    public void acceptedDeltaAdvancesTheToken(String change, String[] snapshot, String[] live) throws Exception {
        givenSnapshot(snapshot);
        givenLive(live);

        assertEquals(sync(SNAPSHOT_TOKEN).size(), 1, change);
        assertEquals(resultToken.getValue(), csvFile.lastModified(), change);
    }

    @Test
    public void rowDeletedDuringSyncIsReportedByTheNextSync() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "u,Jane,Doe,pw1", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3", "u,Jane,Doe,pw1");

        assertEquals(syncRewritingLive(SNAPSHOT_TOKEN, HEADER, "k,Ann,Poe,pw3"),
                Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertEquals(sync(resultToken), Collections.singletonList("DELETE u [Jane, Doe, pw1]"));
    }

    @Test
    public void rowCreatedDuringSyncIsReportedByTheNextSyncOnly() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(syncRewritingLive(SNAPSHOT_TOKEN, HEADER, "k,Ann,Poe,pw3", "c,Jane,Doe,pw1"),
                Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertEquals(sync(resultToken), Collections.singletonList("CREATE c [Jane, Doe, pw1]"));
    }

    @Test
    public void csvFileChangedAfterItsCopyIsReportedByTheNextSync() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "u,Jane,Doe,pw1", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3", "u,Jane,Doe,pw1");
        assertTrue(csvFile.setLastModified(1400000000000L));
        connector(new CSVFileConnector() {
            @Override
            File copyCsvFile() throws IOException {
                final File copy = super.copyCsvFile();
                // an external writer removes u once the copy is taken; runs on every sync, so it writes fixed content
                givenLive(HEADER, "k,Ann,Poe,pw3");
                return copy;
            }
        });

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertEquals(resultToken.getValue(), 1400000000000L);
        assertEquals(sync(resultToken), Collections.singletonList("DELETE u [Jane, Doe, pw1]"));
    }

    @Test
    public void copyOfTheCsvFileBlocksWritersButNotReaders() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        final ExecutorService other = Executors.newSingleThreadExecutor();
        final List<Future<?>> writer = new ArrayList<>();
        try {
            connector(new CSVFileConnector() {
                @Override
                File copyCsvFile() throws IOException {
                    if (writer.isEmpty()) {
                        // getLatestSyncToken takes the read lock, as a search does, and does not wait for the copy
                        assertEquals(await(other.submit(() -> getLatestSyncToken(ObjectClass.ACCOUNT))),
                                SNAPSHOT_TOKEN);
                        // a writer in this JVM waits until the sync ends
                        writer.add(other.submit(() -> delete(ObjectClass.ACCOUNT, new Uid("k"), null)));
                        expectThrows(TimeoutException.class, () -> writer.get(0).get(500, TimeUnit.MILLISECONDS));
                    }
                    return super.copyCsvFile();
                }
            });

            assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
            await(writer.get(0));
            assertEquals(sync(resultToken), Collections.singletonList("DELETE k [Ann, Poe, pw3]"));
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    public void existingSnapshotOfTheCopiedMtimeKeepsTheToken() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        final Path existing = dir.resolve(csvFile.getName() + "." + csvFile.lastModified());
        final List<String> existingLines = Arrays.asList(HEADER, "x,Ann,Poe,pw3");
        Files.write(existing, existingLines, StandardCharsets.UTF_8);

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertEquals(resultToken.getValue(), SNAPSHOT_TOKEN.getValue());
        assertEquals(Files.readAllLines(existing, StandardCharsets.UTF_8), existingLines);
        assertEquals(snapshots(), Arrays.asList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue(),
                existing.getFileName().toString()));
    }

    @Test
    public void snapshotThatCannotBeMovedInFailsSync() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("A read-only directory needs POSIX permissions");
        }
        final Set<PosixFilePermission> mode = Files.getPosixFilePermissions(dir);
        final Set<PosixFilePermission> readOnly = PosixFilePermissions.fromString("r-xr-xr-x");
        setDirMode(readOnly);
        final boolean writable = Files.isWritable(dir);
        setDirMode(mode);
        if (writable) {
            throw new SkipException("A read-only directory does not stop this user from renaming files");
        }
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        final ConnectorException e;
        try {
            // the delta is handled, then the copy cannot be moved into the read-only directory
            e = expectThrows(ConnectorException.class, () -> connector().sync(ObjectClass.ACCOUNT, SNAPSHOT_TOKEN,
                    delta -> {
                        setDirMode(readOnly);
                        return true;
                    }, null));
        } finally {
            setDirMode(mode);
        }

        assertEquals(e.getMessage(), "Unable to copy CSV file for sync operation");
        assertTrue(e.getCause() instanceof AccessDeniedException, String.valueOf(e.getCause()));
    }

    @Test
    public void snapshotTakesTheModeOfTheCsvFile() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("A file mode needs POSIX permissions");
        }
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        final Set<PosixFilePermission> mode = Files.getPosixFilePermissions(csvFile.toPath());
        if (mode.equals(PosixFilePermissions.fromString("rw-------"))) {
            throw new SkipException("The umask already gives the CSV file the mode of a rewrite file");
        }

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertEquals(Files.getPosixFilePermissions(
                dir.resolve(csvFile.getName() + "." + resultToken.getValue())), mode);
    }

    @Test(expectedExceptions = ConnectorException.class, expectedExceptionsMessageRegExp = "Headers do not match")
    public void snapshotHeaderMismatchFailsBeforeTheCurrentRowsAreRead() throws Exception {
        givenSnapshot("uid,firstName,surname,password", "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1", ",Jane,Doe,pw1");

        sync(SNAPSHOT_TOKEN);
    }

    @Test
    public void missingCsvFileFailsSync() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        connector();
        Files.delete(csvFile.toPath());

        ConnectorIOException e = expectThrows(ConnectorIOException.class, () -> sync(SNAPSHOT_TOKEN));
        assertTrue(e.getMessage().matches("File .* does not exist"), e.getMessage());
        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()));
    }

    @Test
    public void unreadableCsvFileFailsSync() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("An unreadable file needs POSIX permissions");
        }
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        connector();
        final Set<PosixFilePermission> mode = Files.getPosixFilePermissions(csvFile.toPath());

        final ConnectorException e;
        Files.setPosixFilePermissions(csvFile.toPath(), PosixFilePermissions.fromString("---------"));
        try {
            if (Files.isReadable(csvFile.toPath())) {
                throw new SkipException("A file without read permission can still be read by this user");
            }
            // the copy is the first read of the CSV file, and it fails as the copy of a sync without a token does
            e = expectThrows(ConnectorException.class, () -> sync(SNAPSHOT_TOKEN));
        } finally {
            Files.setPosixFilePermissions(csvFile.toPath(), mode);
        }

        assertEquals(e.getMessage(), "Unable to copy CSV file for sync operation");
        assertTrue(e.getCause() instanceof AccessDeniedException, String.valueOf(e.getCause()));
        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()));
    }

    @Test
    public void copyDeletedBeforeTheIndexPassFailsSyncNamingTheCsvFile() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        final List<File> copies = connectorRecordingCopies(true);

        final String output = captureStdErr(() -> {
            ConnectorIOException e = expectThrows(ConnectorIOException.class, () -> sync(SNAPSHOT_TOKEN));
            assertEquals(e.getMessage(), "File " + csvFile + " does not exist");
        });

        assertEquals(deltas, Collections.emptyList());
        assertTrue(output.contains("Copy " + copies.get(0) + " of " + csvFile + " does not exist!"), output);
    }

    @Test
    public void copyDeletedBeforeTheCreateUpdatePassFailsSyncNamingTheCsvFile() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        final List<File> copies = connectorRecordingCopies(false);

        final String output = captureStdErr(() -> {
            ConnectorIOException e = expectThrows(ConnectorIOException.class,
                    () -> sync(SNAPSHOT_TOKEN, true, () -> assertTrue(copies.get(0).delete())));
            assertEquals(e.getMessage(), "File " + csvFile + " does not exist");
        });

        assertEquals(deltas, Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertTrue(output.contains("Copy " + copies.get(0) + " of " + csvFile + " does not exist!"), output);
    }

    private void givenSnapshot(String... lines) throws IOException {
        Files.write(dir.resolve(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()), Arrays.asList(lines),
                StandardCharsets.UTF_8);
    }

    private void givenLive(String... lines) throws IOException {
        Files.write(csvFile.toPath(), Arrays.asList(lines), StandardCharsets.UTF_8);
    }

    // the connector logs through StdOutLogger, which reads System.out on every call
    private static String captureStdOut(Runnable action) {
        final PrintStream original = System.out;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    // StdOutLogger writes errors to System.err instead, which it also reads on every call
    private static String captureStdErr(Runnable action) {
        final PrintStream original = System.err;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString();
    }

    private List<String> snapshots() throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(csvFile.getName() + "."))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private void syncWithFailingHandleResult(final SyncToken token, final Runnable beforeFailing) {
        connector().sync(ObjectClass.ACCOUNT, token, new SyncTokenResultsHandler() {
            @Override
            public boolean handle(SyncDelta delta) {
                return true;
            }

            @Override
            public void handleResult(SyncToken syncToken) {
                beforeFailing.run();
                throw new IllegalStateException("token not stored");
            }
        }, null);
    }

    // syncs from no token, handles the given number of deltas and fails on the next one
    private List<SyncToken> syncFailingAfter(final int count) {
        final List<SyncToken> handled = new ArrayList<>();
        expectThrows(IllegalStateException.class, () -> connector().sync(ObjectClass.ACCOUNT, null, delta -> {
            if (handled.size() == count) {
                throw new IllegalStateException("delta not stored");
            }
            handled.add(delta.getToken());
            return true;
        }, null));
        return handled;
    }

    // waits for a task on another thread, which the sync must not keep waiting
    private static <T> T await(Future<T> task) {
        try {
            return task.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    private void setDirMode(Set<PosixFilePermission> mode) {
        try {
            Files.setPosixFilePermissions(dir, mode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CSVFileConnector connector() {
        if (connector == null) {
            connector(new CSVFileConnector());
        }
        return connector;
    }

    // records every copy of the CSV file that the connector takes; deleteAtOnce deletes each one before it is read
    private List<File> connectorRecordingCopies(final boolean deleteAtOnce) {
        final List<File> copies = new ArrayList<>();
        connector(new CSVFileConnector() {
            @Override
            File copyCsvFile() throws IOException {
                final File copy = super.copyCsvFile();
                copies.add(copy);
                if (deleteAtOnce) {
                    Files.delete(copy.toPath());
                }
                return copy;
            }
        });
        return copies;
    }

    private CSVFileConnector connector(CSVFileConnector instance) {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setCsvFile(csvFile);
        config.setHeaderUid("uid");
        config.setHeaderPassword("password");
        instance.init(config);
        connector = instance;
        return connector;
    }

    private List<String> sync(SyncToken token) {
        return sync(token, true);
    }

    private List<String> sync(SyncToken token, final boolean accept) {
        return sync(token, accept, () -> { });
    }

    // syncs like sync(token), while an external writer replaces the live file with the given lines on the first delta
    private List<String> syncRewritingLive(SyncToken token, final String... live) {
        return sync(token, true, () -> {
            try {
                givenLive(live);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private List<String> sync(SyncToken token, final boolean accept, final Runnable onFirstDelta) {
        deltas = new ArrayList<>();
        connector().sync(ObjectClass.ACCOUNT, token, new SyncTokenResultsHandler() {
            @Override
            public boolean handle(SyncDelta delta) {
                if (deltas.isEmpty()) {
                    onFirstDelta.run();
                }
                deltas.add(render(delta));
                return accept;
            }

            @Override
            public void handleResult(SyncToken syncToken) {
                resultToken = syncToken;
            }
        }, null);
        return deltas;
    }

    private static String render(SyncDelta delta) {
        ConnectorObject object = delta.getObject();
        return render(delta.getDeltaType().name(), delta.getUid().getUidValue(), value(object, "firstName"),
                value(object, "lastName"), value(object, OperationalAttributes.PASSWORD_NAME));
    }

    private static String render(String type, String uid, Object firstName, Object lastName, Object password) {
        return type + " " + uid + " " + Arrays.asList(firstName, lastName, password);
    }

    private static Object value(ConnectorObject object, String name) {
        Attribute attribute = object.getAttributeByName(name);
        if (attribute == null) {
            return null;
        }
        Object value = AttributeUtil.getSingleValue(attribute);
        return value instanceof GuardedString ? SecurityUtil.decrypt((GuardedString) value) : value;
    }
}
