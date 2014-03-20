/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011-2012 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Portions Copyrighted 2011 Viliam Repan (lazyman)
 * Portions Copyrighted 2011 Radovan Semancik
 *
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import org.forgerock.openicf.csvfile.sync.Change;
import org.forgerock.openicf.csvfile.sync.InMemoryDiff;
import org.forgerock.openicf.csvfile.util.CSVSchemaException;
import org.forgerock.openicf.csvfile.util.CsvItem;
import org.forgerock.openicf.csvfile.util.TokenFileNameFilter;
import org.forgerock.openicf.csvfile.util.Utils;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;

import java.io.*;
import java.nio.channels.FileLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import static org.forgerock.openicf.csvfile.util.Utils.*;

/**
 * Main implementation of the CSVFile Connector
 *
 * @author Viliam Repan (lazyman)
 * @author $author$
 * @version $Revision$ $Date$
 */
@ConnectorClass(displayNameKey = "UI_CONNECTOR_NAME",
configurationClass = CSVFileConfiguration.class)
public class CSVFileConnector implements Connector, AuthenticateOp, ResolveUsernameOp, CreateOp, DeleteOp, SchemaOp,
        SearchOp<String>, SyncOp, TestOp, UpdateAttributeValuesOp {

    /**
     * Setup logging for the {@link CSVFileConnector}.
     */
    private static final Log log = Log.getLog(CSVFileConnector.class);
    public static final String TMP_EXTENSION = ".tmp";

    private static enum Operation {

        DELETE, UPDATE, ADD_ATTR_VALUE, REMOVE_ATTR_VALUE;
    }
    private static final Object lock = new Object();
    private static final DateFormat FORMAT = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
    private Pattern linePattern;
    /**
     * Place holder for the {@link org.identityconnectors.framework.spi.Configuration} passed into the init() method
     * {@link CSVFileConnector#init(org.identityconnectors.framework.spi.Configuration)}.
     */
    private CSVFileConfiguration configuration;

    /**
     * Gets the Configuration context for this connector.
     */
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link org.identityconnectors.framework.spi.Configuration}.
     *
     * @see org.identityconnectors.framework.spi.Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    public void init(Configuration initialConfiguration1) {
        notNullArgument(initialConfiguration1, "configuration");

        this.configuration = (CSVFileConfiguration) initialConfiguration1;

        String fieldDelimiter = Utils.escapeFieldDelimiter(configuration.getFieldDelimiter());

        // regexp with ," chars is (?:^|,)(\"(?:[^\"]+|\"\")*\"|[^,]*)
        StringBuilder builder = new StringBuilder();
        builder.append("(?:^|");
        builder.append(fieldDelimiter);
        builder.append(")(");
        builder.append(this.configuration.getValueQualifier());
        builder.append("(?:[^");
        builder.append(this.configuration.getValueQualifier());
        builder.append("]+|");
        builder.append(this.configuration.getValueQualifier());
        builder.append(this.configuration.getValueQualifier());
        builder.append(")*");
        builder.append(this.configuration.getValueQualifier());
        builder.append("|[^");
        builder.append(fieldDelimiter);
        builder.append("]*)");
        linePattern = Pattern.compile(builder.toString());
    }

    /**
     * Disposes of the {@link CSVFileConnector}'s resources.
     *
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    public void dispose() {
    }

    /**
     * ****************
     * SPI Operations
     *
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods.
     *****************
     */
    /**
     * {@inheritDoc}
     */
    public Uid authenticate(final ObjectClass objectClass, final String userName, final GuardedString password, final OperationOptions options) {
        log.ok("authenticate::begin");
        Uid uid = realAuthenticate(objectClass, userName, password, true);
        log.ok("authenticate::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid resolveUsername(final ObjectClass objectClass, final String userName, final OperationOptions options) {
        log.ok("resolveUsername::begin");
        Uid uid = realAuthenticate(objectClass, userName, null, false);
        log.ok("resolveUsername::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes, final OperationOptions options) {
        log.ok("create::begin");
        isAccount(objectClass);
        notNull(createAttributes, "Attribute set must not be null.");

        Attribute uidAttr = getAttribute(configuration.getUniqueAttribute(), createAttributes);
        if (uidAttr == null || uidAttr.getValue().isEmpty() || uidAttr.getValue().get(0) == null) {
            throw new UnknownUidException("Unique attribute not defined or is empty.");
        }
        Uid uid = new Uid(uidAttr.getValue().get(0).toString());

        BufferedReader reader = null;
        BufferedWriter writer = null;
        synchronized (lock) {
            try {
                reader = createReader(configuration);
                List<String> header = readHeader(reader, linePattern, configuration);

                CsvItem account = findAccount(reader, header, uid.getUidValue());
                closeReader(reader, null);

                if (account != null) {
                    throw new AlreadyExistsException("Account already exists '" + uid.getUidValue() + "'.");
                }

                StringBuilder record = createRecord(header, createAttributes);
                if (record.length() == 0) {
                    throw new ConnectorException("Can't insert empty record.");
                }

                FileInputStream fis = new FileInputStream(configuration.getFilePath());
                fis.skip(configuration.getFilePath().length() - 1);

                byte[] chars = new byte[1];
                fis.read(chars);
                fis.close();

                writer = createWriter(true);
                if (chars[0] != 10) { // 10 is the decimal value for \n
                    writer.write('\n');
                }
                writer.append(record);
                writer.append('\n');
            } catch (Exception ex) {
                handleGenericException(ex, "Couldn't create account");
            } finally {
                lock.notify();
                closeWriter(writer, null);
            }
        }

        log.ok("create::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        log.ok("delete::begin");
        doUpdate(Operation.DELETE, objectClass, uid, null, options);
        log.ok("delete::end");
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        log.ok("schema::begin");

        List<String> headers = null;
        BufferedReader reader = null;
        synchronized (lock) {
            try {
                reader = createReader(configuration);
                headers = readHeader(reader, linePattern, configuration);
                testHeader(headers);
            } catch (Exception ex) {
                handleGenericException(ex, "Couldn't create schema");
            } finally {
                lock.notify();
                closeReader(reader, null);
            }
        }

        if (headers == null || headers.isEmpty()) {
            throw new CSVSchemaException("Schema can't be generated, header is null (probably not defined in file - first line in csv).");
        }

        ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();
        objClassBuilder.addAllAttributeInfo(createAttributeInfo(headers));

        SchemaBuilder builder = new SchemaBuilder(CSVFileConnector.class);
        builder.defineObjectClass(objClassBuilder.build());

        log.ok("schema::end");
        return builder.build();
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<String> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        log.ok("createFilterTranslator::begin");
        isAccount(objectClass);

        log.ok("createFilterTranslator::end");
        return new AbstractFilterTranslator<String>() {
        };
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, String query, ResultsHandler handler, OperationOptions options) {
        log.ok("executeQuery::begin");
        isAccount(objectClass);
        notNull(handler, "Results handled object can't be null.");

        BufferedReader reader = null;
        synchronized (lock) {
            try {
                reader = createReader(configuration);
                List<String> header = readHeader(reader, linePattern, configuration);

                String line;
                CsvItem item;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (isEmptyOrComment(line)) {
                        continue;
                    }
                    item = Utils.createCsvItem(header, line, lineNumber, linePattern, configuration);

                    ConnectorObject object = createConnectorObject(header, item);
                    if (!handler.handle(object)) {
                        break;
                    }
                }
            } catch (Exception ex) {
                handleGenericException(ex, "Can't execute query");
            } finally {
                lock.notify();
                closeReader(reader, null);
            }
        }

        log.ok("executeQuery::end");
    }

    /**
     * method use when there is not old sync files - no sync token available. New sync file with token (time) is
     * created. It means we're synchronizing from now on.
     *
     * @return new token value
     */
    private String createNewSyncFile() {
        long timestamp = configuration.getFilePath().lastModified();
        File syncFile = new File(configuration.getFilePath().getParentFile(),
                configuration.getFilePath().getName() + "." + timestamp);
        synchronized (lock) {
            try {
                copyAndReplace(configuration.getFilePath(), syncFile);
            } catch (Exception ex) {
                handleGenericException(ex, "Couldn't create file copy for sync");
            } finally {
                lock.notify();
            }
        }

        return Long.toString(timestamp);
    }

    /**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, final OperationOptions options) {
        log.ok("sync::begin");
        isAccount(objectClass);
        notNull(handler, "Sync results handler must not be null.");

        long tokenLongValue = getTokenValue(token);
        log.info("Token {0}", tokenLongValue);

        if (tokenLongValue == -1) {
            //token doesn't exist, we only create new sync file - we're synchronizing from now on
            createNewSyncFile();
            log.info("Token value was not defined {0}, only creating new sync file, synchronizing from now on.", token);
            log.ok("sync::end");
            return;
        }

        boolean hasFileChanged = false;
        if (configuration.getFilePath().lastModified() > tokenLongValue) {
            hasFileChanged = true;
            log.info("Csv file has changed on {0} which is after time {1}, based on token value {2}",
                    FORMAT.format(new Date(configuration.getFilePath().lastModified())),
                    FORMAT.format(new Date(tokenLongValue)), tokenLongValue);
        }

        if (!hasFileChanged) {
            log.info("File has not changed after {0} (token value {1}), diff will be skipped.",
                    FORMAT.format(new Date(tokenLongValue)), tokenLongValue);
            log.ok("sync::end");
            return;
        }

        syncReal(tokenLongValue, handler);
        log.ok("sync::end");
    }

    private void syncReal(long tokenLongValue, SyncResultsHandler handler) {
        long timestamp = configuration.getFilePath().lastModified();
        log.ok("Next last sync token value will be {0} ({1}).", timestamp, FORMAT.format(new Date(timestamp)));
        File syncFile = new File(configuration.getFilePath().getParentFile(),
                configuration.getFilePath().getName() + "." + timestamp + TMP_EXTENSION);
        synchronized (lock) {
            try {
                copyAndReplace(configuration.getFilePath(), syncFile);
            } catch (Exception ex) {
                handleGenericException(ex, "Could not create file copy for sync");
            } finally {
                lock.notify();
            }
        }

        File tokenSyncFile = new File(configuration.getFilePath().getParent(), configuration.getFilePath().getName()
                + "." + tokenLongValue);
        log.info("Diff actual file {0} with last file based on token {1}.", syncFile.getName(), tokenSyncFile.getName());
        InMemoryDiff memoryDiff = new InMemoryDiff(tokenSyncFile, syncFile, linePattern, configuration);
        try {
            List<Change> changes = memoryDiff.diff();
            log.info("Found {0} differences.", changes.size());
            if (changes.size() == 0) {
                //this was only phantom change, nothing was really changed, delete sync file (new token not necessary)
                log.info("Deleting file {0}.", syncFile.getName());
                syncFile.delete();
                return;
            }

            SyncToken newToken = new SyncToken(Long.toString(timestamp));
            for (Change change : changes) {
                SyncDelta delta = createSyncDelta(change, newToken);
                if (!handler.handle(delta)) {
                    break;
                }
            }

            File newFile = new File(configuration.getFilePath().getParent(), configuration.getFilePath().getName() + "." + timestamp);
            log.info("Renaming file {0} to {1}.", syncFile.getName(), newFile.getName());
            syncFile.renameTo(newFile);

            cleanupOldTokenFiles();
        } catch (Exception ex) {
            handleGenericException(ex, "Couldn't finish sync operation");
        }
    }

    private void handleGenericException(Exception ex, String message) {
        if (ex instanceof ConnectorException) {
            throw (ConnectorException) ex;
        }

        if (ex instanceof IOException) {
            throw new ConnectorIOException(message + ", IO exception occurred, reason: " + ex.getMessage(), ex);
        }

        throw new ConnectorException(message + ", reason: " + ex.getMessage(), ex);
    }

    private void cleanupOldTokenFiles() {
        String[] tokenFiles = listTokenFiles();
        Arrays.sort(tokenFiles);

        int preserve = configuration.getPreserveLastTokens();
        if (preserve <= 1) {
            log.info("Not removing old token files. Preserve last tokens: {0}.", preserve);
            return;
        }

        File parentFolder = configuration.getFilePath().getParentFile();
        for (int i = 0; i + preserve < tokenFiles.length; i++) {
            File tokenSyncFile = new File(parentFolder, tokenFiles[i]);
            if (!tokenSyncFile.exists()) {
                continue;
            }

            log.info("Deleting file {0}.", tokenSyncFile.getName());
            tokenSyncFile.delete();
        }
    }

    private String[] listTokenFiles() {
        if (!configuration.getFilePath().exists()) {
            throw new ConnectorIOException("Csv file '" + configuration.getFilePath() + "' not found.");
        }
        File parentFolder = configuration.getFilePath().getParentFile();
        if (!parentFolder.exists() || !parentFolder.isDirectory()) {
            throw new ConnectorIOException("Parent folder for '" + configuration.getFilePath()
                    + "' doesn't exist, or is not a directory.");
        }

        String csvFileName = configuration.getFilePath().getName();
        return parentFolder.list(new TokenFileNameFilter(csvFileName));
    }

    /**
     * {@inheritDoc}
     */
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        log.ok("getLatestSyncToken::begin");
        isAccount(objectClass);

        String csvFileName = configuration.getFilePath().getName();
        String[] oldCsvFiles = listTokenFiles();
        String token;
        if (oldCsvFiles.length != 0) {
            Arrays.sort(oldCsvFiles);
            String latestCsvFile = oldCsvFiles[oldCsvFiles.length - 1];
            token = latestCsvFile.replaceFirst(csvFileName + ".", "");
        } else {
            log.info("Old csv files were not found, creating token, synchronizing from \"now\".");
            token = createNewSyncFile();
        }

        log.ok("getLatestSyncToken::end, returning token {0}.", token);
        return new SyncToken(token);
    }

    /**
     * {@inheritDoc}
     */
    public void test() {
        log.ok("test::begin");
        log.info("Validating configuration.");
        configuration.validate();

        BufferedReader reader = null;
        synchronized (lock) {
            try {
                log.ok("Opening input stream to file {0}.", configuration.getFilePath());
                reader = createReader(configuration);

                List<String> headers = readHeader(reader, linePattern, configuration);
                testHeader(headers);
            } catch (Exception ex) {
                log.error("Test configuration was unsuccessful, reason: {0}.", ex.getMessage());
                handleGenericException(ex, "Test configuration was unsuccessful");
            } finally {
                log.ok("Closing file input stream.");
                lock.notify();
                closeReader(reader, null);
            }
        }

        log.info("Test configuration was successful.");
        log.ok("test::end");
    }

    /**
     * {@inheritDoc}
     */
    public Uid update(ObjectClass objectClass,
                      Uid uid,
                      Set<Attribute> replaceAttributes,
                      OperationOptions options) {
        log.ok("update::begin");
        uid = doUpdate(Operation.UPDATE, objectClass, uid, replaceAttributes, options);
        log.ok("update::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid addAttributeValues(ObjectClass objectClass,
                                  Uid uid,
                                  Set<Attribute> valuesToAdd,
                                  OperationOptions options) {
        log.ok("addAttributeValues::begin");
        uid = doUpdate(Operation.ADD_ATTR_VALUE, objectClass, uid, valuesToAdd, options);
        log.ok("addAttributeValues::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid removeAttributeValues(ObjectClass objectClass,
                                     Uid uid,
                                     Set<Attribute> valuesToRemove,
                                     OperationOptions options) {
        log.ok("removeAttributeValues::begin");
        uid = doUpdate(Operation.REMOVE_ATTR_VALUE, objectClass, uid, valuesToRemove, options);
        log.ok("removeAttributeValues::end");
        return uid;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //  Private Implementation
    //
    ///////////////////////////////////////////////////////////////////////////
    private BufferedWriter createWriter(boolean append) throws IOException {
        return createWriter(configuration.getFilePath(), append);
    }

    private BufferedWriter createWriter(File path, boolean append) throws IOException {
        log.ok("Creating writer.");
        FileOutputStream fos = new FileOutputStream(path, append);
        OutputStreamWriter out = new OutputStreamWriter(fos, configuration.getEncoding());
        return new BufferedWriter(out);
    }

    private void closeWriter(Writer writer, FileLock lock) {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            unlock(lock);
        } catch (IOException ex) {
            throw new ConnectorException("Couldn't close writer, reason: " + ex.getMessage(), ex);
        }
    }

    private void createTempFile() {
        File file = new File(configuration.getFilePath() + ".tmp");
        try {
            if (file.exists()) {
                if (!file.delete()) {
                    throw new ConnectorIOException("Couldn't delete old tmp file '" + file.getAbsolutePath() + "'.");
                }
            }
            file.createNewFile();
        } catch (IOException ex) {
            throw new ConnectorIOException("Couldn't create tmp file '" + file.getAbsolutePath()
                    + "', reason: " + ex.getMessage(), ex);
        }
    }

    private CsvItem findAccount(BufferedReader reader, List<String> header, String username) throws IOException {
        int lineNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (isEmptyOrComment(line)) {
                continue;
            }

            CsvItem item = Utils.createCsvItem(header, line, lineNumber, linePattern, configuration);

            int fieldIndex = header.indexOf(configuration.getUniqueAttribute());
            String value = item.getAttribute(fieldIndex);
            if (!StringUtil.isEmpty(value) && value.equals(username)) {
                return item;
            }
        }
        return null;
    }

    private StringBuilder createRecord(List<String> header, Set<Attribute> attributes) {
        final StringBuilder builder = new StringBuilder();

        for (String name : header) {
            if (header.indexOf(name) != 0) {
                builder.append(configuration.getFieldDelimiter());
            }

            Attribute attribute = getAttribute(name, attributes);
            if (configuration.getUniqueAttribute().equals(name)) {
                if (attribute == null || attribute.getValue().isEmpty()) {
                    throw new CSVSchemaException("Unique attribute for record is not defined.");
                }
            }
            if (attribute == null) {
                continue;
            }

            String value = appendValues(name, attribute.getValue());
            if (StringUtil.isNotEmpty(value)) {
                appendQualifiedValue(builder, value);
            }
        }

        return builder;
    }

    private Attribute getAttribute(String name, Set<Attribute> attributes) {
        if (name.equals(configuration.getPasswordAttribute())) {
            name = OperationalAttributes.PASSWORD_NAME;
        }
        if (name.equals(configuration.getNameAttribute())) {
            name = Name.NAME;
        }

        for (Attribute attribute : attributes) {
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }

        return null;
    }

    private List<AttributeInfo> createAttributeInfo(List<String> names) {
        List<AttributeInfo> infos = new ArrayList<AttributeInfo>();
        for (String name : names) {
            if (name.equals(configuration.getUniqueAttribute())) {
                continue;
            }
            if (name.equals(configuration.getNameAttribute())) {
                continue;
            }
            if (name.equals(configuration.getPasswordAttribute())) {
                infos.add(OperationalAttributeInfos.PASSWORD);
                continue;
            }

            AttributeInfoBuilder builder = new AttributeInfoBuilder(name);
            if (name.equals(configuration.getPasswordAttribute())) {
                builder.setType(GuardedString.class);
            } else {
                builder.setType(String.class);
            }
            infos.add(builder.build());
        }

        return infos;
    }

    private ConnectorObject createConnectorObject(List<String> header, CsvItem item) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        for (int i = 0; i < header.size(); i++) {
            if (StringUtil.isEmpty(item.getAttribute(i))) {
                continue;
            }
            String name = header.get(i);
            if (name.equals(configuration.getUniqueAttribute())) {
                builder.setUid(item.getAttribute(i));
//                builder.addAttribute(name, item.getAttribute(i));

                if (!configuration.isUniqueAndNameAttributeEqual()) {
                    continue;
                }
            }
            if (name.equals(configuration.getNameAttribute())) {
                builder.setName(new Name(item.getAttribute(i)));
                continue;
            }
            if (name.equals(configuration.getPasswordAttribute())) {
                builder.addAttribute(OperationalAttributes.PASSWORD_NAME, new GuardedString(item.getAttribute(i).toCharArray()));
                continue;
            }
            builder.addAttribute(name, createAttributeValues(item.getAttribute(i)));
        }

        return builder.build();
    }

    private List<String> createAttributeValues(String attributeValue) {
        List<String> values = new ArrayList<String>();
        if (!configuration.isUsingMultivalue()) {
            values.add(attributeValue);
            return values;
        }

        String[] array = attributeValue.split(configuration.getMultivalueDelimiter());
        for (String val : array) {
            if (val != null) {
                values.add(val);
            }
        }

        return values;
    }

    private Uid realAuthenticate(ObjectClass objectClass, String username, GuardedString pwd, boolean testPassword) {
        log.ok("realAuthenticate::begin");
        isAccount(objectClass);

        if (username == null) {
            throw new InvalidCredentialException("Username can't be null.");
        }

        if (testPassword && StringUtil.isEmpty(configuration.getPasswordAttribute())) {
            throw new ConfigurationException("Password attribute not defined in configuration.");
        }

        if (testPassword && pwd == null) {
            throw new InvalidPasswordException("Password can't be null.");
        }

        BufferedReader reader = null;
        synchronized (lock) {
            try {
                reader = createReader(configuration);
                List<String> header = readHeader(reader, linePattern, configuration);

                CsvItem account = findAccount(reader, header, username);
                if (account == null) {
                    String message;
                    if (testPassword) {
                        message = "Invalid username and/or password.";
                    } else {
                        message = "Invalid username.";
                    }
                    throw new InvalidCredentialException(message);
                }

                int index;
                if (testPassword) {
                    index = header.indexOf(configuration.getPasswordAttribute());
                    final String password = account.getAttribute(index);
                    if (StringUtil.isEmpty(password)) {
                        throw new InvalidPasswordException("Invalid username and/or password.");
                    }

                    pwd.access(new GuardedString.Accessor() {

                        public void access(char[] chars) {
                            if (!new String(chars).equals(password)) {
                                throw new InvalidPasswordException("Invalid username and/or password.");
                            }
                        }
                    });
                }
                index = header.indexOf(configuration.getUniqueAttribute());
                String uidAttribute = account.getAttribute(index);
                if (StringUtil.isEmpty(uidAttribute)) {
                    throw new UnknownUidException("Unique atribute doesn't have value for account '" + username + "'.");
                }
                return new Uid(uidAttribute);
            } catch (Exception ex) {
                handleGenericException(ex, "Can't authenticate '" + username + "'");
                //it won't go here
                return null;
            } finally {
                lock.notify();
                closeReader(reader, null);

                log.ok("realAuthenticate::end");
            }
        }
    }

    private Uid doUpdate(Operation operation, ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions oo) {
        log.ok("doUpdate::begin");
        isAccount(objectClass);
        notNull(uid, "Uid must not be null.");
        if (attributes == null && Operation.DELETE != operation) {
            throw new IllegalArgumentException("Attribute set can't be null.");
        }

        BufferedReader reader = null;
        BufferedWriter writer = null;
        synchronized (lock) {
            createTempFile();
            try {
                reader = createReader(configuration);
                File tmpFile = new File(configuration.getFilePath().getCanonicalPath() + TMP_EXTENSION);
                writer = createWriter(tmpFile, true);
                List<String> header = readHeader(reader, writer, linePattern, configuration);

                ConnectorObject changed = readAndUpdateFile(reader, writer, header, operation, uid, attributes);
                if (changed == null) {
                    throw new UnknownUidException("Uid '" + uid.getUidValue() + "' not found in file.");
                }
                uid = changed.getUid();

                closeReader(reader, null);
                closeWriter(writer, null);
                reader = null;
                writer = null;

                if (configuration.getFilePath().delete()) {
                    tmpFile.renameTo(configuration.getFilePath());
                } else {
                    throw new ConnectorException("Couldn't delete old file '" + configuration.getFilePath().getAbsolutePath()
                            + "' and replace it by new file '" + tmpFile.getAbsolutePath() + "'.");
                }
            } catch (Exception ex) {
                handleGenericException(ex, "Couldn't do " + operation + " on account '" + uid.getUidValue() + "'");
            } finally {
                lock.notify();
                closeReader(reader, null);
                closeWriter(writer, null);

                try {
                    File tmpFile = new File(configuration.getFilePath() + TMP_EXTENSION);
                    if (tmpFile.exists()) {
                        tmpFile.delete();
                    }
                } catch (Exception ex) {
                    //only try to cleanup tmp file, it will be replaced later, if exists
                }
            }
        }

        log.ok("doUpdate::end");
        return uid;
    }

    private ConnectorObject readAndUpdateFile(BufferedReader reader, BufferedWriter writer, List<String> header,
                                              Operation operation, Uid uid, Set<Attribute> attributes) throws IOException {
        ConnectorObject changed = null;

        String line;
        int lineNumber = 1;
        CsvItem item;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (isEmptyOrComment(line)) {
                writer.write(line);
                writer.write('\n');
                continue;
            }

            item = Utils.createCsvItem(header, line, lineNumber, linePattern, configuration);
            int fieldIndex = header.indexOf(configuration.getUniqueAttribute());
            String value = item.getAttribute(fieldIndex);
            if (!StringUtil.isEmpty(value) && value.equals(uid.getUidValue())) {
                switch (operation) {
                    case UPDATE:
                    case ADD_ATTR_VALUE:
                    case REMOVE_ATTR_VALUE:
                        line = updateLine(operation, header, item, attributes);

                        changed = createConnectorObject(header, Utils.createCsvItem(header, line, lineNumber,
                                linePattern, configuration));
                        break;
                    case DELETE:
                        changed = createConnectorObject(header, item);
                        continue;
                }
            }

            writer.write(line);
            writer.write('\n');
        }

        return changed;
    }

    private String appendValues(String attributeName, List<Object> values) {
        if (configuration.getUniqueAttribute().equals(attributeName)) {
            if (values.size() > 1) {
                throw new CSVSchemaException("Can't store unique attribute '" + attributeName
                        + "' with multiple values (" + values.size() + ").");
            } else if (values == null || values.isEmpty()) {
                throw new CSVSchemaException("Can't store unique attribute '" + attributeName + "' without values.");
            }
        }

        final StringBuilder builder = new StringBuilder();
        if (values == null || values.isEmpty()) {
            return null;
        }

        for (int i = 0; i < values.size(); i++) {
            Object object = values.get(i);
            if (object == null) {
                return null;
            }

            if (i != 0) {
                builder.append(configuration.getMultivalueDelimiter());
            }
            if (object instanceof GuardedString) {
                GuardedString pwd = (GuardedString) object;
                pwd.access(new GuardedString.Accessor() {

                    public void access(char[] chars) {
                        builder.append(chars);
                    }
                });
            } else {
                builder.append(object);
            }
        }

        return builder.toString();
    }

    private String appendMergedValues(String attributeName, final List<String> oldValues,
                                      List<Object> newValues, Operation operation) {
        List<Object> values = new ArrayList<Object>();
        if (!configuration.isUsingMultivalue()) {
            switch (operation) {
                case ADD_ATTR_VALUE:
                    return appendValues(attributeName, newValues);
                case REMOVE_ATTR_VALUE:
                    values = removeOldValues(oldValues, newValues);
                    break;
            }
        } else {
            if (operation == Operation.REMOVE_ATTR_VALUE && (newValues == null || newValues.isEmpty())) {
                return null;
            }

            if (operation == Operation.REMOVE_ATTR_VALUE) {
                values = removeOldValues(oldValues, newValues);
            } else {
                values.addAll(oldValues);
                values.addAll(newValues);
            }
        }
        String value = appendValues(attributeName, values);
        if (StringUtil.isNotEmpty(value)) {
            return value;
        }

        return "";
    }

    private List<Object> removeOldValues(List<String> oldValues, List<Object> newValues) {
        final List<Object> values = new ArrayList<Object>();
        values.addAll(oldValues);
        for (Object object : newValues) {
            if (object instanceof String) {
                values.remove((String) object);
            } else if (object instanceof GuardedString) {
                GuardedString guarded = (GuardedString) object;
                guarded.access(new GuardedString.Accessor() {

                    public void access(char[] chars) {
                        values.remove(new String(chars));
                    }
                });
            }
        }
        return values;
    }

    private String updateLine(Operation operation, List<String> headers, CsvItem item, Set<Attribute> attributes) {
        StringBuilder builder = new StringBuilder();

        String value = null;
        for (String header : headers) {
            int index = headers.indexOf(header);
            if (index != 0) {
                builder.append(configuration.getFieldDelimiter());
            }
            Attribute attribute = getAttribute(header, attributes);
            if (attribute != null) {
                switch (operation) {
                    case UPDATE:
                        value = appendValues(header, attribute.getValue());
                        break;
                    case ADD_ATTR_VALUE:
                    case REMOVE_ATTR_VALUE:
                        List<String> oldValues = new ArrayList<String>();
                        String oldValuesStr = item.getAttribute(index);
                        if (StringUtil.isNotEmpty(oldValuesStr)) {
                            if (configuration.isUsingMultivalue()) {
                                String[] array = oldValuesStr.split(String.valueOf(
                                        configuration.getMultivalueDelimiter()));
                                oldValues.addAll(Arrays.asList(array));
                            } else {
                                oldValues.add(oldValuesStr);
                            }
                        }
                        value = appendMergedValues(header, oldValues, attribute.getValue(), operation);
                        break;
                }
            } else {
                value = item.getAttribute(index);
            }

            if (StringUtil.isNotEmpty(value)) {
                appendQualifiedValue(builder, value);
            }
        }

        return builder.toString();
    }

    private void appendQualifiedValue(StringBuilder builder, String value) {
        boolean useQualifier = configuration.getAlwaysQualify() || mustUseQualifier(value);
        if (useQualifier) {
            builder.append(configuration.getValueQualifier());
        }
        builder.append(value);
        if (useQualifier) {
            builder.append(configuration.getValueQualifier());
        }
    }

    private boolean mustUseQualifier(String value) {
        return value.contains(Utils.escapeFieldDelimiter(configuration.getFieldDelimiter()));
    }

    private void testHeader(List<String> headers) {
        boolean uniqueFound = false;
        boolean passwordFound = false;

        Map<String, Integer> headerCount = new HashMap<String, Integer>();
        for (String header : headers) {
            if (!headerCount.containsKey(header)) {
                headerCount.put(header, 0);
            }

            headerCount.put(header, headerCount.get(header) + 1);
        }

        for (String header : headers) {
            int count = headerCount.containsKey(header) ? headerCount.get(header) : 0;
            if (count != 1) {
                throw new ConfigurationException("Column header '" + header
                        + "' occurs more than once (" + count + ").");
            }
            if (header.equals(configuration.getUniqueAttribute())) {
                uniqueFound = true;
                continue;
            }
            if (StringUtil.isNotEmpty(configuration.getPasswordAttribute())
                    && header.equals(configuration.getPasswordAttribute())) {
                passwordFound = true;
            }
            if (uniqueFound && passwordFound) {
                break;
            }
        }

        if (!uniqueFound) {
            throw new ConfigurationException("Header in csv file doesn't contain "
                    + "unique attribute name as defined in configuration.");
        }
        if (StringUtil.isNotEmpty(configuration.getPasswordAttribute()) && !passwordFound) {
            throw new ConfigurationException("Header in csv file doesn't contain "
                    + "password attribute name as defined in configuration.");
        }
    }

    private long getTokenValue(SyncToken token) {
        if (token == null || token.getValue() == null) {
            return -1;
        }
        String object = token.getValue().toString();
        if (!object.matches("[0-9]{13}")) {
            return -1;
        }

        return Long.parseLong(object);
    }

    private SyncDelta createSyncDelta(Change change, SyncToken token) {
        SyncDeltaBuilder builder = new SyncDeltaBuilder();
        builder.setUid(new Uid(change.getUid()));
        builder.setToken(token);

        if (Change.Type.DELETE.equals(change.getType())) {
            builder.setDeltaType(SyncDeltaType.DELETE);
        } else {
            builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);

            CsvItem item = new CsvItem(change.getAttributes());
            ConnectorObject object = createConnectorObject(change.getHeader(), item);
            builder.setObject(object);
        }

        return builder.build();
    }

    /**
     * This method is only for tests!
     *
     * @return pattern used for matcher and line parsing
     */
    Pattern getLinePattern() {
        return linePattern;
    }
}
