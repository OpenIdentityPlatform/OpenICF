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

import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.getRandomXMLFile;
import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.setModified;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.CRC32;

import org.testng.SkipException;
import org.testng.annotations.Test;

public class FileStampTests {

    private static final long HOUR = 3_600_000L;

    @Test
    public void unchangedFileMatches() throws IOException {
        File file = write(getRandomXMLFile(), "abc", now() - HOUR);
        assertTrue(FileStamp.read(file).sameState(FileStamp.read(file)));
    }

    @Test
    public void olderMtimeIsAChange() throws IOException {
        File file = write(getRandomXMLFile(), "abc", now() - HOUR);
        FileStamp before = FileStamp.read(file);
        setModified(file, now() - 2 * HOUR);
        assertFalse(before.sameState(FileStamp.read(file)));
    }

    @Test
    public void sizeChangeWithTheSameMtimeIsAChange() throws IOException {
        long past = now() - HOUR;
        File file = write(getRandomXMLFile(), "abc", past);
        FileStamp before = FileStamp.read(file);
        write(file, "abcd", past);
        assertFalse(before.sameState(FileStamp.read(file)));
    }

    @Test
    public void replacedFileIsAChange() throws IOException {
        long past = now() - HOUR;
        File file = write(getRandomXMLFile(), "abc", past);
        if (Files.readAttributes(file.toPath(), BasicFileAttributes.class).fileKey() == null) {
            throw new SkipException("The file system reports no file key");
        }
        FileStamp before = FileStamp.read(file);
        File copy = write(getRandomXMLFile(), "abc", past);
        Files.move(copy.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        setModified(file, past);
        assertFalse(before.sameState(FileStamp.read(file)));
    }

    @Test
    public void unreadableStampMatchesNothing() throws IOException {
        File loop = getRandomXMLFile();
        try {
            Files.createSymbolicLink(loop.toPath(), loop.toPath().getFileName()); // a link to itself
        } catch (IOException | UnsupportedOperationException e) {
            throw new SkipException("Cannot create a symbolic link here: " + e);
        }
        try {
            Files.readAttributes(loop.toPath(), BasicFileAttributes.class);
            throw new SkipException("The file system resolves a link to itself");
        } catch (NoSuchFileException e) {
            throw new SkipException("The file system reports a link to itself as missing");
        } catch (IOException expected) {
            // ELOOP: the path is neither readable nor known to be missing
        }
        assertFalse(FileStamp.read(loop).sameState(FileStamp.read(loop)));
        assertFalse(FileStamp.read(loop).sameState(FileStamp.MISSING));
        assertFalse(FileStamp.MISSING.sameState(FileStamp.read(loop)));
        assertFalse(FileStamp.read(loop).isKnown());
    }

    @Test
    public void readableAndMissingFilesAreKnown() throws IOException {
        assertTrue(FileStamp.read(write(getRandomXMLFile(), "abc", now() - HOUR)).isKnown());
        assertTrue(FileStamp.read(getRandomXMLFile()).isKnown());
    }

    @Test
    public void missingFileMatchesMissing() {
        assertTrue(FileStamp.read(getRandomXMLFile()).sameState(FileStamp.MISSING));
    }

    @Test
    public void stampIsRacyOnlyNearTheClock() throws IOException {
        File file = write(getRandomXMLFile(), "abc", now() + HOUR);
        assertTrue(FileStamp.read(file).isRacy());
        setModified(file, now() - HOUR);
        assertFalse(FileStamp.read(file).isRacy());
    }

    @Test
    public void stampIsRacyJustAfterAWrite() throws IOException {
        // An outside write of the same size in the same tick right after a load or save looks like no change.
        File file = write(getRandomXMLFile(), "abc", now() - 500);
        assertTrue(FileStamp.read(file).isRacy());
        setModified(file, now() - 3_000);
        assertFalse(FileStamp.read(file).isRacy());
    }

    @Test
    public void checksumFollowsTheContent() throws IOException {
        File file = write(getRandomXMLFile(), "abc", now() - HOUR);
        CRC32 crc = new CRC32();
        crc.update("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(FileStamp.checksum(file), crc.getValue());
        write(file, "abd", now() - HOUR);
        assertNotEquals(FileStamp.checksum(file), crc.getValue());
    }

    @Test
    public void deletedFileIsAChange() throws IOException {
        File file = write(getRandomXMLFile(), "abc", now() - HOUR);
        FileStamp before = FileStamp.read(file);
        Files.delete(file.toPath());
        assertFalse(before.sameState(FileStamp.read(file)));
    }

    private static File write(File file, String content, long modified) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        setModified(file, modified);
        return file;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
