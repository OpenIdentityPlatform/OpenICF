/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.forgerock.openicf.csvfile.CSVFileConfiguration;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;

/**
 *
 * @author Viliam Repan (lazyman)
 */
public class Utils {

    public static String escapeFieldDelimiter(String delimiter) {
        String[] specials = new String[]{"|", ".", "\\", "^", "$", "[", "]", "(", ")"};
        for (String special : specials) {
            if (special.equals(delimiter)) {
                return "\\" + delimiter;
            }
        }
        return delimiter;
    }

    public static void isAccount(ObjectClass oc) {
        if (oc == null) {
            throw new IllegalArgumentException("Object class must not be null.");
        }
        if (!ObjectClass.ACCOUNT.is(oc.getObjectClassValue())) {
            throw new ConnectorException("Can't work with resource object different than account.");
        }
    }

    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notNullArgument(Object object, String arg) {
        notNull(object, "Argument '" + arg + "' can't be null.");
    }

    public static void notEmpty(String value, String message) {
        notNull(value, message);

        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notEmptyArgument(String value, String arg) {
        notEmpty(value, "Argument '" + arg + "' can't be empty.");
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

    public static BufferedReader createReader(CSVFileConfiguration configuration) throws IOException {
        return createReader(configuration.getFilePath(), configuration);
    }

    public static BufferedReader createReader(File path, CSVFileConfiguration configuration) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        InputStreamReader in = new InputStreamReader(fis, configuration.getEncoding());
        return new BufferedReader(in);
    }

    public static void closeReader(Reader reader, FileLock lock) {
        try {
            if (reader != null) {
                reader.close();
            }
            unlock(lock);
        }
        catch (IOException ex) {
            throw new ConnectorException("Couldn't close reader, reason: " + ex.getMessage(), ex);
        }
    }

    public static void unlock(FileLock lock) {
        if (lock != null && lock.isValid()) {
            try {
                lock.release();
            }
            catch (IOException ex) {
                throw new ConnectorException("Couldn't release file lock, reason: " + ex.getMessage(), ex);
            }
        }
    }

    public static List<String> parseValues(String line, Pattern linePattern, CSVFileConfiguration configuration) {
        List<String> values = new ArrayList<String>();
        if (line == null || line.isEmpty()) {
            return values;
        }
        //this if is hack for regexp - if line starts with field delimiter regexp
        //will fail, so if we find that we put "empty field" as it was expected
        if (line.matches("^" + escapeFieldDelimiter(configuration.getFieldDelimiter()) + ".*$")) {
            StringBuilder builder = new StringBuilder();
            builder.append(configuration.getValueQualifier());
            builder.append(configuration.getValueQualifier());
            builder.append(line);
            line = builder.toString();
        }
        Matcher matcher = linePattern.matcher(line);
        while (matcher.find()) {
            String value = matcher.group(1).trim().replace(configuration.getValueQualifier(), "");
            values.add(value);
        }
        return values;
    }

    public static List<String> readHeader(BufferedReader reader, Pattern linePattern, CSVFileConfiguration configuration) throws IOException {
        String line = null;
        do {
            line = reader.readLine();
        } while (( line != null ) && isEmptyOrComment(line));

        if (line == null) {
            throw new ConnectorException("Csv file '" + configuration.getFilePath() + "' doesn't contain header.");
        }

        return parseValues(line, linePattern, configuration);
    }

    public static boolean isEmptyOrComment(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Test line can't be null. " + "It's must be a record, commend or empty line.");
        }
        if (line.isEmpty() || line.startsWith("#")) {
            return true;
        }
        return false;
    }
}
