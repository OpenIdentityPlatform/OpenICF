/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.common;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.testng.annotations.Test;

public class IOUtilsTests {

    // =======================================================================
    // JUnit Tests
    // =======================================================================
    @Test
    public void quietClose() {
        // test reader
        ExceptionReader rdr = new ExceptionReader();
        IOUtil.quietClose(rdr);
        assertTrue(rdr.closeCalled);
        // test input stream
        ExceptionInputStream ins = new ExceptionInputStream();
        IOUtil.quietClose(ins);
        assertTrue(ins.closeCalled);
        // test outputstream
        ExceptionOutputStream os = new ExceptionOutputStream();
        IOUtil.quietClose(os);
        assertTrue(os.closeCalled);
        // test writer
        ExceptionWriter wrt = new ExceptionWriter();
        IOUtil.quietClose(wrt);
        assertTrue(wrt.closeCalled);
    }

    @Test
    public void resourcePath() {
        // test resource path returns the right thing..
    }

    @Test
    public void resolveEntryReturnsFileInsideDirectory() throws IOException {
        File dir = Files.createTempDirectory("IOUtilsTests").toFile();
        try {
            assertEquals(IOUtil.resolveEntry(dir, "lib/a.jar").getCanonicalFile(), new File(dir,
                    "lib/a.jar").getCanonicalFile());
        } finally {
            IOUtil.delete(dir);
        }
    }

    @Test
    public void resolveEntryReturnsTheCanonicalFile() throws IOException {
        File dir = Files.createTempDirectory("IOUtilsTests").toFile();
        try {
            assertEquals(IOUtil.resolveEntry(dir, "lib/../lib/a.jar"), new File(dir, "lib/a.jar")
                    .getCanonicalFile());
        } finally {
            IOUtil.delete(dir);
        }
    }

    @Test
    public void resolveEntryAcceptsTheDirectoryItself() throws IOException {
        // the directory entry of an archive root, e.g. "shared/" once its
        // prefix is stripped, maps to the directory itself
        File dir = Files.createTempDirectory("IOUtilsTests").toFile();
        try {
            assertEquals(IOUtil.resolveEntry(dir, "").getCanonicalFile(), dir.getCanonicalFile());
        } finally {
            IOUtil.delete(dir);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void resolveEntryRejectsEntryEscapingDirectory() throws IOException {
        File dir = Files.createTempDirectory("IOUtilsTests").toFile();
        try {
            IOUtil.resolveEntry(dir, "lib/../../escaped.jar");
        } finally {
            IOUtil.delete(dir);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void resolveEntryRejectsSiblingSharingTheDirectoryNamePrefix() throws IOException {
        // "<dir>2" starts with the name of "<dir>", but is not below it
        File dir = Files.createTempDirectory("IOUtilsTests").toFile();
        try {
            IOUtil.resolveEntry(dir, "../" + dir.getName() + "2/evil.jar");
        } finally {
            IOUtil.delete(dir);
        }
    }

    @Test
    public void unjarRefusesEntryEscapingTargetDirectory() throws IOException {
        File root = Files.createTempDirectory("IOUtilsTests").toFile();
        try {
            File toDir = new File(root, "out");
            assertTrue(toDir.mkdir());
            File jar = new File(root, "evil.jar");
            JarOutputStream out = new JarOutputStream(new FileOutputStream(jar));
            try {
                out.putNextEntry(new JarEntry("../escaped.txt"));
                out.write("escaped".getBytes(UTF8_NAME));
                out.closeEntry();
            } finally {
                out.close();
            }
            JarFile jarFile = new JarFile(jar);
            try {
                IOUtil.unjar(jarFile, toDir);
                fail("Expected the entry escaping " + toDir + " to be refused");
            } catch (IOException expected) {
                assertFalse(new File(root, "escaped.txt").exists(), "entry written outside "
                        + toDir);
            } finally {
                jarFile.close();
            }
        } finally {
            IOUtil.delete(root);
        }
    }

    private static final String UTF8_NAME = "UTF-8";

    // public static String getResourcePath(Class<?> c, String res) {
    // public static InputStream getResourceAsStream(Class<?> clazz, String res)
    // {
    // public static byte[] getResourceAsBytes(Class<?> clazz, String res) {
    // public static String getResourceAsString(Class<?> clazz, String res,
    // Charset charset) {
    // public static String getResourceAsString(Class<?> clazz, String res) {

    static class ExceptionReader extends Reader {
        boolean closeCalled;

        @Override
        public void close() throws IOException {
            closeCalled = true;
            throw new IOException();
        }

        @Override
        public int read(char[] arg0, int arg1, int arg2) throws IOException {
            return 0;
        }
    }

    static class ExceptionInputStream extends InputStream {
        boolean closeCalled;

        @Override
        public void close() throws IOException {
            closeCalled = true;
            throw new IOException();
        }

        @Override
        public int read() throws IOException {
            return 0;
        }
    }

    static class ExceptionWriter extends Writer {
        boolean closeCalled;

        @Override
        public void close() throws IOException {
            closeCalled = true;
            throw new IOException();
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void write(char[] arg0, int arg1, int arg2) throws IOException {
        }
    }

    static class ExceptionOutputStream extends OutputStream {
        boolean closeCalled;

        @Override
        public void close() throws IOException {
            closeCalled = true;
            throw new IOException();
        }

        @Override
        public void write(int arg0) throws IOException {
        }
    }
}
