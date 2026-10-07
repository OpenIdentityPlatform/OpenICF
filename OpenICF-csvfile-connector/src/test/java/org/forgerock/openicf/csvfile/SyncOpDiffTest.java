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
import static org.testng.Assert.expectThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.supercsv.exception.SuperCsvException;
import org.testng.annotations.Test;

/**
 * Pins the deltas {@link CSVFileConnector#sync} reports when it compares a snapshot with the live CSV file.
 * Every test works on its own files: the connector caches the header per CSV path.
 */
public class SyncOpDiffTest {

    private static final String HEADER = "uid,firstName,lastName,password";

    private static final SyncToken SNAPSHOT_TOKEN = new SyncToken(1300000000000L);

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
