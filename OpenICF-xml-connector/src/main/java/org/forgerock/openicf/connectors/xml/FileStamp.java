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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * What the store file looked like at one moment: its modification time, size and file key, or that
 * it did not exist. Stamps that differ in any of these mean the file has changed; an older
 * modification time counts too, because {@code cp -p} sets one.
 * <p>
 * A file rewritten in place with the same size within one tick of the modification time clock
 * keeps all of these. That can only go unnoticed while the stamp is racy: when it was taken, the
 * modification time was less than {@link #RACY_WINDOW_MILLIS} before the clock, or after it.
 * Callers compare the content ({@link #checksum}) behind a racy stamp.
 */
final class FileStamp {

    /** The coarsest common tick: 2 s on FAT; 1 s on HFS+ and ext3. */
    static final long RACY_WINDOW_MILLIS = 2000L;

    /** No file: no modification time, which every existing file has. */
    static final FileStamp MISSING = new FileStamp(null, -1L, null, false);

    /** The file could not be read: matches nothing, not even itself. */
    private static final FileStamp UNKNOWN = new FileStamp(null, -1L, null, true);

    private final FileTime lastModified;
    private final long size;
    private final Object fileKey;
    private final boolean racy;

    private FileStamp(FileTime lastModified, long size, Object fileKey, boolean racy) {
        this.lastModified = lastModified;
        this.size = size;
        this.fileKey = fileKey;
        this.racy = racy;
    }

    static FileStamp read(File file) {
        long now = System.currentTimeMillis();
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            FileTime modified = attributes.lastModifiedTime();
            return new FileStamp(modified, attributes.size(), attributes.fileKey(),
                    now - modified.toMillis() < RACY_WINDOW_MILLIS);
        } catch (NoSuchFileException e) {
            return MISSING;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    /** The CRC-32 of the file's content. */
    static long checksum(File file) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = new CheckedInputStream(Files.newInputStream(file.toPath()), crc)) {
            byte[] buffer = new byte[64 * 1024];
            while (in.read(buffer) != -1) {
                // the stream updates the checksum
            }
        }
        return crc.getValue();
    }

    boolean isRacy() {
        return racy;
    }

    /** Whether the file could be read, or was known to be missing, when the stamp was taken. */
    boolean isKnown() {
        return this != UNKNOWN;
    }

    boolean sameState(FileStamp other) {
        if (this == UNKNOWN || other == UNKNOWN) {
            return false;
        }
        return Objects.equals(lastModified, other.lastModified)
                && size == other.size
                && Objects.equals(fileKey, other.fileKey);
    }
}
