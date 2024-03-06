/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for
 * the specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file
 * and include the License file at legal/CDDLv1.0.txt. If applicable, add the following
 * below the CDDL Header, with the fields enclosed by brackets [] replaced by your
 * own identifying information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.openicf.csvfile;

import java.io.File;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Extends the {@link AbstractConfiguration} class to provide all of the possible
 * configuration parameters consumed by the CSVFile connector.
 */
public class CSVFileConfiguration extends AbstractConfiguration {

    private static final Log log = Log.getLog(CSVFileConfiguration.class);

    /**
     * The pathname of the CSV file this connector should consume.
     */
    private File csvFile = null;

    /**
     * The CSV header that maps to the uid for each row.
     */
    private String headerUid = "uid";

    /**
     * The CSV header that maps to the password for each row.
     */
    private String headerPassword = "password";

    /**
     * The character in the CSV used to encapsulate strings.
     */
    private String quoteCharacter = "\"";

    /**
     * The character used in the CSV to separate field values.
     */
    private String fieldDelimiter = ",";

    /**
     * The character used in the CSV to terminate each line.
     */
    private String newlineString = "\n";

    /**
     * The number of historical copies of the CSV to retain when performing sync.
     */
    private int syncFileRetentionCount = 3;



    @ConfigurationProperty(displayMessageKey = "csv_filepath.display",
            helpMessageKey = "csv_filepath.help",
            required = true)
    public File getCsvFile() {
        return csvFile;
    }

    public void setCsvFile(File csvFile) {
        this.csvFile = csvFile;
    }

    @ConfigurationProperty(displayMessageKey = "csv_header_uid.display",
            helpMessageKey = "csv_header_uid.help")
    public String getHeaderUid() {
        return headerUid;
    }

    public void setHeaderUid(String headerUid) {
        this.headerUid = headerUid;
    }

    @ConfigurationProperty(displayMessageKey = "csv_header_password.display",
            helpMessageKey = "csv_header_password.help")
    public String getHeaderPassword() {
        return headerPassword;
    }

    public void setHeaderPassword(String headerPassword) {
        this.headerPassword = headerPassword;
    }

    @ConfigurationProperty(displayMessageKey = "csv_quote_character.display",
            helpMessageKey = "csv_quote_character.help")
    public String getQuoteCharacter() {
        return quoteCharacter;
    }

    public void setQuoteCharacter(String quoteCharacter) {
        this.quoteCharacter = quoteCharacter;
    }

    @ConfigurationProperty(displayMessageKey = "csv_field_delimiter.display",
            helpMessageKey = "csv_field_delimiter.help")
    public String getFieldDelimiter() {
        return fieldDelimiter;
    }

    public void setFieldDelimiter(String delimiterCharacter) {
        this.fieldDelimiter = delimiterCharacter;
    }

    @ConfigurationProperty(displayMessageKey = "csv_lineend_character.display",
            helpMessageKey = "csv_lineend_character.help")
    public String getNewlineString() {
        return newlineString;
    }

    public void setNewlineString(String newlineString) {
        this.newlineString = newlineString;
    }

    @ConfigurationProperty(displayMessageKey = "sync_file_retention_count.display",
            helpMessageKey = "sync_file_retention_count.help")
    public int getSyncFileRetentionCount() {
        return syncFileRetentionCount;
    }

    public void setSyncFileRetentionCount(int syncFileRetentionCount) {
        this.syncFileRetentionCount = syncFileRetentionCount;
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {
        log.ok("begin");

        if (csvFile == null || StringUtil.isEmpty(csvFile.toString())) {
            throw new ConfigurationException("CSV file path is not defined");
        }

        if (!csvFile.exists()) {
            throw new ConfigurationException("File '" + csvFile
                    + "' doesn't exist. A file containing a csv header must exist.");
        }
        if (csvFile.isDirectory()) {
            throw new ConfigurationException("File path '" + csvFile + "' points to a directory.");
        }
        if (!csvFile.canRead()) {
            throw new ConfigurationException("File '" + csvFile + "' can't be read.");
        }
        if (!csvFile.canWrite()) {
            throw new ConfigurationException("Can't write to file '" + csvFile.getAbsolutePath() + "'.");
        }

        log.ok("end");
    }
}
