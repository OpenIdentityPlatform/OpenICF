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
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

    private Integer retentionCount;

    @BeforeMethod
    public void before() throws IOException {
        dir = Files.createTempDirectory("csv-sync");
        csvFile = dir.resolve("data.csv").toFile();
        deltas = null;
        resultToken = null;
        retentionCount = null;
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
    public void failedSyncReleasesTheReadLockWhenItsCleanupFails() throws Exception {
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
        connector();

        final PrintStream original = System.out;
        try {
            // the snapshot this sync copied cannot be deleted, and the WARN about it throws, standing in for an Error
            expectThrows(UncheckedIOException.class, () -> syncWithFailingHandleResult(null, () -> {
                setDirMode(readOnly);
                System.setOut(new PrintStream(new OutputStream() {
                    @Override
                    public void write(int b) {
                        throw new UncheckedIOException(new IOException("Standard output is closed"));
                    }
                }));
            }));
        } finally {
            System.setOut(original);
            setDirMode(mode);
        }

        // otherwise every later write and sync of this CSV file in this JVM waits for the write lock for good
        assertEquals(lockMap().get(csvFile.getAbsolutePath()).getReadLockCount(), 0);
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
    public void syncQueuedBehindAFailingSyncGetsATokenWithASnapshot() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        connector();
        final Thread failing = Thread.currentThread();
        final List<Future<?>> queued = new ArrayList<>();
        final CountDownLatch baselineTaken = new CountDownLatch(1);
        // once the failing sync releases the read lock, it waits until the queued sync has taken its baseline and
        // released the write lock, so a snapshot the failing sync deletes only after the release is already reused
        lockMap().put(csvFile.getAbsolutePath(), new ReentrantReadWriteLock() {
            private final ReadLock readLock = new ReadLock(this) {
                @Override
                public void unlock() {
                    super.unlock();
                    if (Thread.currentThread() == failing && !queued.isEmpty()) {
                        await(baselineTaken);
                    }
                }
            };

            private final WriteLock writeLock = new WriteLock(this) {
                @Override
                public void unlock() {
                    super.unlock();
                    if (Thread.currentThread() != failing) {
                        baselineTaken.countDown();
                    }
                }
            };

            @Override
            public ReadLock readLock() {
                return readLock;
            }

            @Override
            public WriteLock writeLock() {
                return writeLock;
            }
        });
        final ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            expectThrows(IllegalStateException.class, () -> syncWithFailingHandleResult(null,
                    () -> queued.add(other.submit(() -> sync(null)))));
            await(queued.get(0));

            // the old code deleted the snapshot of the failing sync after the queued sync had reused it
            assertEquals(sync(resultToken), Collections.emptyList());
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    public void existingSnapshotOfTheCopiedMtimeAdvancesTheToken() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        // the bytes of the live file, as an earlier sync of the same changes copied them
        final Path existing = givenFile(csvFile.getName() + "." + csvFile.lastModified(), HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        assertEquals(resultToken.getValue(), csvFile.lastModified());
        assertEquals(snapshots(), Arrays.asList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue(),
                existing.getFileName().toString()));
    }

    @DataProvider
    public Object[][] laterWrites() {
        return new Object[][] {
            { "w,Will,Woe,pw4", "k,Ann,Poe,pw3", "CREATE w [Will, Woe, pw4]" },
            // the same length, and past the first 8 KiB the comparison reads at once
            { null, "k,Ann,Poe,pw4", "UPDATE k [Ann, Poe, pw4]" }
        };
    }

    @Test(dataProvider = "laterWrites")
    public void existingSnapshotOfTheCopiedMtimeWithOtherBytesKeepsTheToken(String added, String k, String delta)
            throws Exception {
        final List<String> rows = new ArrayList<>(Collections.singletonList(HEADER));
        for (int i = 0; i < 400; i++) {
            rows.add(String.format("u%03d,First,Last,pw%03d", i, i));
        }
        rows.add("k,Ann,Poe,pw3");
        givenLive(rows.toArray(new String[0]));
        rows.add("d,John,Roe,pw2");
        givenSnapshot(rows.toArray(new String[0]));
        // another JVM read the same mtime, but copied the file only after a writer had made a change
        final List<String> theirs = new ArrayList<>(rows.subList(0, rows.size() - 2));
        theirs.add(k);
        if (added != null) {
            theirs.add(added);
        }
        final Path existing = givenFile(csvFile.getName() + "." + csvFile.lastModified(),
                theirs.toArray(new String[0]));

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        // as the new token, the existing snapshot would hide that change from this consumer for good
        assertEquals(resultToken, SNAPSHOT_TOKEN);
        assertEquals(Files.readAllLines(existing, StandardCharsets.UTF_8), theirs);

        givenLive(theirs.toArray(new String[0]));
        assertEquals(sync(resultToken), Arrays.asList("DELETE d [John, Roe, pw2]", delta));
    }

    @Test
    public void existingSnapshotOfTheCopiedMtimeThatCannotBeReadKeepsTheToken() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("An unreadable file needs POSIX permissions");
        }
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        // the bytes of the live file, so only the failed read keeps the token
        final Path existing = givenFile(csvFile.getName() + "." + csvFile.lastModified(), HEADER, "k,Ann,Poe,pw3");
        Files.setPosixFilePermissions(existing, PosixFilePermissions.fromString("---------"));
        if (Files.isReadable(existing)) {
            throw new SkipException("A file without read permission can still be read by this user");
        }
        connector();

        final String output = captureStdOut(() -> assertEquals(sync(SNAPSHOT_TOKEN),
                Collections.singletonList("DELETE d [John, Roe, pw2]")));

        assertEquals(resultToken, SNAPSHOT_TOKEN);
        assertTrue(output.contains("Could not compare " + existing.toFile()), output);
    }

    @Test
    public void failedSyncKeepsTheExistingSnapshotOfTheCopiedMtime() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        final Path existing = givenFile(csvFile.getName() + "." + csvFile.lastModified(), HEADER, "k,Ann,Poe,pw3");

        expectThrows(IllegalStateException.class, () -> syncWithFailingHandleResult(SNAPSHOT_TOKEN, () -> { }));

        assertTrue(Files.exists(existing));
    }

    @Test
    public void secondConsumerOfTheSameChangesGetsTheNewTokenToo() throws Exception {
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        final SyncToken first = resultToken;
        // another consumer stored the same token; the old code gave it that token back, so it got this delta forever
        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));

        assertEquals(resultToken, first);
        assertEquals(sync(resultToken), Collections.emptyList());
    }

    @Test
    public void secondSyncWithoutATokenOfAnUnchangedFileReusesItsSnapshot() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        final List<File> copies = connectorRecordingCopies(false);

        assertEquals(sync(null), Collections.emptyList());
        final SyncToken first = resultToken;
        // the old code failed here: its copy found the snapshot of the first sync in the way
        assertEquals(sync(null), Collections.emptyList());

        assertEquals(resultToken, first);
        assertEquals(first.getValue(), csvFile.lastModified());
        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + first.getValue()));
        // the snapshot and the copy for the diff, then only the copy for the diff
        assertEquals(copies.size(), 3);
    }

    @Test
    public void failedSyncKeepsTheSnapshotItReused() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        sync(null);
        final List<String> taken = snapshots();

        expectThrows(IllegalStateException.class, () -> syncWithFailingHandleResult(null, () -> { }));

        assertEquals(snapshots(), taken);
    }

    @Test
    public void snapshotTakenMeanwhileByAnotherProcessIsKept() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        final Path snapshot = dir.resolve(csvFile.getName() + "." + csvFile.lastModified());
        // the live row, written differently, so that a replaced snapshot shows
        final List<String> theirs = Arrays.asList(HEADER, "\"u1\",\"Jane\",\"Doe\",\"pw1\"");
        final List<File> copies = new ArrayList<>();
        connector(new CSVFileConnector() {
            @Override
            File copyCsvFile() throws IOException {
                final File copy = super.copyCsvFile();
                if (copies.isEmpty()) {
                    // another process takes the snapshot of this mtime while the sync copies the CSV file for it
                    Files.write(snapshot, theirs, StandardCharsets.UTF_8);
                }
                copies.add(copy);
                return copy;
            }
        });

        expectThrows(IllegalStateException.class, () -> syncWithFailingHandleResult(null, () -> { }));

        assertEquals(Files.readAllLines(snapshot, StandardCharsets.UTF_8), theirs);
        // the copy that lost the race is deleted
        assertEquals(snapshots(), Collections.singletonList(snapshot.getFileName().toString()));
    }

    @Test
    public void syncDeletesTheSnapshotsBeyondTheRetentionCount() throws Exception {
        retentionCount = 2;
        givenFile(csvFile.getName() + ".1100000000000", HEADER, "k,Ann,Poe,pw3");
        givenFile(csvFile.getName() + ".1200000000000", HEADER, "k,Ann,Poe,pw3");
        givenSnapshot(HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.emptyList());

        assertEquals(snapshots(), Arrays.asList(csvFile.getName() + ".1200000000000",
                csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()));
    }

    @Test
    public void syncKeepsTheSnapshotItReadsWhenItIsTheOldest() throws Exception {
        retentionCount = 1;
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenFile(csvFile.getName() + ".1400000000000", HEADER, "k,Ann,Poe,pw3");
        givenFile(csvFile.getName() + ".1500000000000", HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));

        // the newest snapshot and the one this sync read are kept, then the sync adds its own
        assertEquals(snapshots(), Arrays.asList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue(),
                csvFile.getName() + ".1500000000000", csvFile.getName() + "." + resultToken.getValue()));
    }

    @Test
    public void syncKeepsTheTokenOfAnotherConsumerWhenTheCsvFileMtimeMovesBack() throws Exception {
        retentionCount = 2;
        // the tokens of consumers B and A, each taken while the CSV file had the mtime in its name
        final SyncToken tokenOfA = new SyncToken(1400000000000L);
        givenSnapshotTakenAt(SNAPSHOT_TOKEN, HEADER, "k,Ann,Poe,pw3");
        givenSnapshotTakenAt(tokenOfA, HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        // then the CSV file is replaced by one with an older mtime, such as by a restore from a backup
        givenLive(HEADER, "k,Ann,Poe,pw3");
        Files.setLastModifiedTime(csvFile.toPath(), FileTime.fromMillis(1200000000000L));

        assertEquals(sync(tokenOfA), Collections.singletonList("DELETE d [John, Roe, pw2]"));
        final SyncToken nextOfA = resultToken;
        assertEquals(nextOfA.getValue(), 1200000000000L);
        // B's scrub spares the snapshot it reads, the one taken first; ordered by name, it deleted A's
        assertEquals(sync(SNAPSHOT_TOKEN), Collections.emptyList());
        assertEquals(snapshots().size(), 3);

        assertEquals(sync(nextOfA), Collections.emptyList());
    }

    @Test
    public void snapshotsTakenInOneTickAreScrubbedInTheOrderOfTheirNames() throws Exception {
        retentionCount = 2;
        // a file system clock that ticks once a second or slower gives snapshots taken in a row one mtime
        final FileTime tick = FileTime.fromMillis(1500000000000L);
        for (Object mtime : Arrays.asList(1100000000000L, 1200000000000L, SNAPSHOT_TOKEN.getValue())) {
            Files.setLastModifiedTime(givenFile(csvFile.getName() + "." + mtime, HEADER, "k,Ann,Poe,pw3"), tick);
        }
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.emptyList());

        assertEquals(snapshots(), Arrays.asList(csvFile.getName() + ".1200000000000",
                csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue()));
    }

    @DataProvider
    public Object[][] retentionCountsBelowOne() {
        return new Object[][] { { 0 }, { -1 } };
    }

    @Test(dataProvider = "retentionCountsBelowOne")
    public void syncFromATokenKeepsOnlyTheSnapshotItReads(int count) throws Exception {
        retentionCount = count;
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenFile(csvFile.getName() + ".1400000000000", HEADER, "k,Ann,Poe,pw3");
        givenFile(csvFile.getName() + ".1500000000000", HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));

        // then the sync adds its own
        assertEquals(snapshots(), Arrays.asList(csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue(),
                csvFile.getName() + "." + resultToken.getValue()));
    }

    @Test(dataProvider = "retentionCountsBelowOne")
    public void syncWithoutATokenKeepsOnlyItsBaseline(int count) throws Exception {
        retentionCount = count;
        givenFile(csvFile.getName() + ".1400000000000", HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(null), Collections.emptyList());

        // with a count of 1 or more the baseline is the newest snapshot, so only a count below 1 reaches it
        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + resultToken.getValue()));
    }

    @Test
    public void syncKeepsTheSnapshotsOfOtherFiles() throws Exception {
        retentionCount = 1;
        givenSnapshot(HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        // each would be the oldest snapshot under the old pattern, which was not anchored and did not quote the dot
        final List<String> others = Arrays.asList("old_data.csv.1100000000000", "dataXcsv.1200000000000");
        for (String other : others) {
            givenFile(other, HEADER, "k,Ann,Poe,pw3");
        }

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.emptyList());

        for (String other : others) {
            assertTrue(Files.exists(dir.resolve(other)), other);
        }
    }

    @Test
    public void snapshotThatCannotBeScrubbedIsLogged() throws Exception {
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
        retentionCount = 1;
        final Path old = givenFile(csvFile.getName() + ".1200000000000", HEADER, "k,Ann,Poe,pw3");
        givenSnapshot(HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");
        connector();

        final List<ConnectorException> failures = new ArrayList<>();
        final String output;
        setDirMode(readOnly);
        try {
            // the scrub only logs; then the copy for the diff fails in the read-only directory
            output = captureStdOut(() -> failures.add(expectThrows(ConnectorException.class,
                    () -> sync(SNAPSHOT_TOKEN))));
        } finally {
            setDirMode(mode);
        }

        assertTrue(output.contains("Could not delete " + old.toFile()), output);
        assertEquals(failures.get(0).getMessage(), "Unable to copy CSV file for sync operation");
        assertTrue(Files.exists(old));
    }

    @Test
    public void syncFromATokenWhoseSnapshotIsGoneFails() throws Exception {
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        final File snapshot = new File(dir.toFile(), csvFile.getName() + "." + SNAPSHOT_TOKEN.getValue());

        ConnectorException e = expectThrows(ConnectorException.class, () -> sync(SNAPSHOT_TOKEN));

        assertEquals(e.getMessage(), "Snapshot " + snapshot + " of sync token " + SNAPSHOT_TOKEN.getValue()
                + " does not exist, so the changes since that token cannot be reported; reset the sync token");
        assertEquals(deltas, Collections.emptyList());
        assertEquals(snapshots(), Collections.emptyList());
    }

    @Test
    public void latestSyncTokenWithoutASnapshotTakesOne() throws Exception {
        givenLive(HEADER, "k,Ann,Poe,pw3");

        final SyncToken token = connector().getLatestSyncToken(ObjectClass.ACCOUNT);

        assertEquals(token.getValue(), csvFile.lastModified());
        assertEquals(snapshots(), Collections.singletonList(csvFile.getName() + "." + token.getValue()));
        // the first sync from the token reports a change made after the call; the old code lost it
        givenLive(HEADER, "k,Ann,Poe,pw3", "c,Jane,Doe,pw1");
        assertEquals(sync(token), Collections.singletonList("CREATE c [Jane, Doe, pw1]"));
    }

    @Test
    public void latestSyncTokenIsThatOfTheSnapshotTakenLast() throws Exception {
        givenSnapshotTakenAt(new SyncToken(1400000000000L), HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        // taken now, after the CSV file was replaced by one with an older mtime
        givenFile(csvFile.getName() + ".1200000000000", HEADER, "k,Ann,Poe,pw3");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        final SyncToken token = connector().getLatestSyncToken(ObjectClass.ACCOUNT);

        // the token of the newest name reported a delete made before the call, and the scrub deleted it first
        assertEquals(token.getValue(), 1200000000000L);
        assertEquals(sync(token), Collections.emptyList());
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

    @DataProvider
    public Object[][] otherFileNames() {
        return new Object[][] {
            // the old pattern was not anchored at the start
            { "old_data.csv" },
            // and did not quote the dot
            { "dataXcsv" }
        };
    }

    @Test(dataProvider = "otherFileNames")
    public void latestSyncTokenIgnoresSnapshotsOfOtherFiles(String otherName) throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        givenFile(otherName + ".1600000000000", HEADER);

        assertEquals(connector().getLatestSyncToken(ObjectClass.ACCOUNT), SNAPSHOT_TOKEN);
    }

    @DataProvider
    public Object[][] regexMetacharacterNames() {
        return new Object[][] {
            // never matched its own snapshots
            { "data(1).csv" },
            // made the pattern invalid
            { "data[1.csv" }
        };
    }

    @Test(dataProvider = "regexMetacharacterNames")
    public void latestSyncTokenFindsTheSnapshotsOfAFileWithRegexMetacharactersInItsName(String name)
            throws Exception {
        csvFile = dir.resolve(name).toFile();
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1");

        assertEquals(connector().getLatestSyncToken(ObjectClass.ACCOUNT), SNAPSHOT_TOKEN);
    }

    @Test
    public void syncOfAFileWhoseNameIsAnInvalidRegexReportsItsChanges() throws Exception {
        csvFile = dir.resolve("data[1.csv").toFile();
        givenSnapshot(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2");
        givenLive(HEADER, "k,Ann,Poe,pw3");

        assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
    }

    @Test
    public void syncOfAFileNamedWithoutADirectoryKeepsTheSnapshotItReads() throws Exception {
        // the name resolves against the working directory, but its File has no parent
        final Path workingDir = Paths.get("").toAbsolutePath();
        final String name = Files.createTempFile(workingDir, "csv-sync-", ".csv").getFileName().toString();
        try {
            csvFile = new File(name);
            // the scrub reaches the snapshot the sync reads, so it has to recognise that snapshot in its own listing
            retentionCount = 1;
            Files.write(workingDir.resolve(name + "." + SNAPSHOT_TOKEN.getValue()),
                    Arrays.asList(HEADER, "k,Ann,Poe,pw3", "d,John,Roe,pw2"), StandardCharsets.UTF_8);
            Files.write(workingDir.resolve(name + ".1400000000000"), Arrays.asList(HEADER, "k,Ann,Poe,pw3"),
                    StandardCharsets.UTF_8);
            givenLive(HEADER, "k,Ann,Poe,pw3");

            // the old code failed with a NullPointerException in the scrub, and so did getLatestSyncToken
            assertEquals(sync(SNAPSHOT_TOKEN), Collections.singletonList("DELETE d [John, Roe, pw2]"));
            assertEquals(connector.getLatestSyncToken(ObjectClass.ACCOUNT), resultToken);

            assertEquals(snapshots(workingDir), Arrays.asList(name + "." + SNAPSHOT_TOKEN.getValue(),
                    name + ".1400000000000", name + "." + resultToken.getValue()));
        } finally {
            try (Stream<Path> paths = Files.list(workingDir)) {
                for (Path file : paths.filter(path -> path.getFileName().toString().startsWith(name))
                        .collect(Collectors.toList())) {
                    Files.delete(file);
                }
            }
        }
    }

    @Test
    public void latestSyncTokenOfAnUnlistableDirectoryFails() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("An unlistable directory needs POSIX permissions");
        }
        final Set<PosixFilePermission> mode = Files.getPosixFilePermissions(dir);
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        connector();

        setDirMode(PosixFilePermissions.fromString("-wx------"));
        try {
            if (dir.toFile().list() != null) {
                throw new SkipException("A directory without read permission does not stop this user from listing it");
            }
            // the old code failed with a NullPointerException
            ConnectorIOException e = expectThrows(ConnectorIOException.class,
                    () -> connector.getLatestSyncToken(ObjectClass.ACCOUNT));
            assertEquals(e.getMessage(), "Unable to list the files of " + dir.toFile());
        } finally {
            setDirMode(mode);
        }
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

    private Path givenFile(String name, String... lines) throws IOException {
        return Files.write(dir.resolve(name), Arrays.asList(lines), StandardCharsets.UTF_8);
    }

    // a snapshot gets the time of its copy as its mtime, here the mtime of the CSV file that its name holds
    private void givenSnapshotTakenAt(SyncToken token, String... lines) throws IOException {
        Files.setLastModifiedTime(givenFile(csvFile.getName() + "." + token.getValue(), lines),
                FileTime.fromMillis((Long) token.getValue()));
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
        return snapshots(dir);
    }

    private List<String> snapshots(Path in) throws IOException {
        try (Stream<Path> paths = Files.list(in)) {
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

    // waits for another thread to count the latch down
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + latch);
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ReentrantReadWriteLock> lockMap() throws ReflectiveOperationException {
        final Field field = CSVFileConnector.class.getDeclaredField("fileNameToLockMap");
        field.setAccessible(true);
        return (Map<String, ReentrantReadWriteLock>) field.get(null);
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
        if (retentionCount != null) {
            config.setSyncFileRetentionCount(retentionCount);
        }
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
