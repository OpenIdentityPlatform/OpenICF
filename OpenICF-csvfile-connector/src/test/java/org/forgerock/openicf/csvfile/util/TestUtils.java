/*
 *
 * Copyright 2010-2015 ForgeRock
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * Portions Copyrighted 2011 Viliam Repan (lazyman)
 *
 * $Id$
 */
package org.forgerock.openicf.csvfile.util;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.regex.Pattern;

import org.testng.Assert;

public class TestUtils {

    public static File getTestFile(String name) throws IOException, URISyntaxException {
        Assert.assertNotNull(name);
        URL filesURL = TestUtils.class.getResource("/files/");
        Assert.assertNotNull(filesURL);
        return new File(filesURL.toURI().resolve(name));
    }

    public static String compareFiles(File file1, File file2) throws IOException {
        BufferedReader reader1 = createReader(file1);
        BufferedReader reader2 = createReader(file2);

        String retVal = null;
        try {
            // discard the header lines
            String line1 = reader1.readLine();
            String line2 = reader2.readLine();
            while (true) {
                line1 = reader1.readLine();
                line2 = reader2.readLine();

                boolean equal = line1 == null ? line2 == null : line1.equals(line2);
                if (!equal) {
                    retVal = "'" + line1 + "'<>'" + line2 + "'";
                    break;
                }

                if (line1 == null) {
                    break;
                }
            }
        }
        finally {
            if (reader1 != null) {
                reader1.close();
            }
            if (reader2 != null) {
                reader2.close();
            }
        }

        return retVal;
    }

    private static BufferedReader createReader(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader in = new InputStreamReader(fis, "utf-8");
        return new BufferedReader(in);
    }

    public static void copyAndReplace(File from, File to) throws IOException {
        if (to.exists()) {
            to.delete();
        }
        to.createNewFile();

        FileChannel inChannel = new FileInputStream(from).getChannel();
        FileChannel outChannel = new FileOutputStream(to).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
        finally {
            if (inChannel != null) {
                inChannel.close();
            }
            if (outChannel != null) {
                outChannel.close();
            }
        }
    }

    /**
     * Remove one or more file
     *
     * @param dir Directory to search for matches
     * @param pattern File pattern to match
     * @throws IOException on failure to remove the file
     */
    public static void remove(File dir, final String pattern) throws IOException {
        final File[] files = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                Pattern pat = Pattern.compile(pattern);
                return pat.matcher(name).matches();
            }
        });
        for (final File file : files) {
            if ( !file.delete() ) {
                System.err.println( "Can't remove " + file.getAbsolutePath() );
            }
        }
    }
}
