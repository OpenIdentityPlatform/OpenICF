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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.supercsv.exception.SuperCsvException;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Pins the deltas {@link CSVFileConnector#sync} reports when it compares a snapshot with the live CSV file.
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

        setDirMode(writeOnly);
        try {
            // the copy is made, then scrubSyncFiles fails while the sync still holds the write lock
            expectThrows(NullPointerException.class, () -> sync(null));
        } finally {
            setDirMode(mode);
        }

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

    @Test(expectedExceptions = ConnectorException.class, expectedExceptionsMessageRegExp = "Headers do not match")
    public void snapshotHeaderMismatchFailsBeforeTheLiveFileIsRead() throws Exception {
        givenSnapshot("uid,firstName,surname,password", "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1", ",Jane,Doe,pw1");

        sync(SNAPSHOT_TOKEN);
    }

    @Test(expectedExceptions = ConnectorIOException.class, expectedExceptionsMessageRegExp = "File .* does not exist")
    public void missingCsvFileFailsSync() throws Exception {
        givenSnapshot(HEADER, "u1,Jane,Doe,pw1");
        givenLive(HEADER, "u1,Jane,Doe,pw1");
        connector();
        Files.delete(csvFile.toPath());

        sync(SNAPSHOT_TOKEN);
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

    private void setDirMode(Set<PosixFilePermission> mode) {
        try {
            Files.setPosixFilePermissions(dir, mode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CSVFileConnector connector() {
        if (connector == null) {
            CSVFileConfiguration config = new CSVFileConfiguration();
            config.setCsvFile(csvFile);
            config.setHeaderUid("uid");
            config.setHeaderPassword("password");
            connector = new CSVFileConnector();
            connector.init(config);
        }
        return connector;
    }

    private List<String> sync(SyncToken token) {
        return sync(token, true);
    }

    private List<String> sync(SyncToken token, final boolean accept) {
        deltas = new ArrayList<>();
        connector().sync(ObjectClass.ACCOUNT, token, new SyncTokenResultsHandler() {
            @Override
            public boolean handle(SyncDelta delta) {
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
