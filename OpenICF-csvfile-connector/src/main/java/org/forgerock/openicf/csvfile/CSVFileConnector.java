/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
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
 * $Id$
 */
package org.forgerock.openicf.csvfile;

import org.identityconnectors.framework.spi.operations.*;
import org.identityconnectors.framework.common.objects.*;

import org.forgerock.openicf.csvfile.sync.Change;
import org.forgerock.openicf.csvfile.util.CsvItem;
import org.forgerock.openicf.csvfile.sync.DiffException;
import org.forgerock.openicf.csvfile.sync.InMemoryDiff;
import static org.forgerock.openicf.csvfile.util.Utils.*;
import java.io.File;
import java.util.regex.Pattern;
import java.util.List;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.FileLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;

/**
 * Main implementation of the CSVFile Connector
 *
 * @author $author$
 * @version $Revision$ $Date$
 */
@ConnectorClass(displayNameKey = "UI_CONNECTOR_NAME",
configurationClass = CSVFileConfiguration.class)
public class CSVFileConnector implements Connector, AuthenticateOp, ResolveUsernameOp, CreateOp, DeleteOp, SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp, SearchOp<String>, SyncOp, TestOp, UpdateAttributeValuesOp {

    /**
     * Setup logging for the {@link CSVFileConnector}.
     */
    private static final Log log = Log.getLog(CSVFileConnector.class);
    public static final String TMP_EXTENSION = ".tmp";
    private static final String ATTRIBUTE_NAME = "__NAME__";
    private static final String ATTRIBUTE_PASSWORD = "__PASSWORD__";
    private static final String ATTRIBUTE_UID = "__UID__";

    private static enum Operation {

        DELETE, UPDATE, ADD_ATTR_VALUE, REMOVE_ATTR_VALUE;
    }
    private static final Object lock = new Object();
    private Pattern linePattern;
    /**
     * Place holder for the {@link Configuration} passed into the init() method
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
     * Callback method to receive the {@link Configuration}.
     *
     * @see Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    public void init(Configuration initialConfiguration1) {
        notNullArgument(initialConfiguration1, "configuration");

        this.configuration = (CSVFileConfiguration) initialConfiguration1;
        // regexp with ," chars is (?:^|,)(\"(?:[^\"]+|\"\")*\"|[^,]*)
        StringBuilder builder = new StringBuilder();
        builder.append("(?:^|");
        builder.append(this.configuration.getFieldDelimiter());
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
        builder.append(this.configuration.getFieldDelimiter());
        builder.append("]*)");
        linePattern = Pattern.compile(builder.toString());
    }

    /**
     * Disposes of the {@link CSVFileConnector}'s resources.
     *
     * @see Connector#dispose()
     */
    public void dispose() {
    }

    /******************
     * SPI Operations
     *
     * Implement the following operations using the contract and
     * description found in the Javadoc for these methods.
     ******************/
    /**
     * {@inheritDoc}
     */
    public Uid authenticate(final ObjectClass objectClass, final String userName, final GuardedString password, final OperationOptions options) {
        log.info("authenticate::begin");
        Uid uid = realAuthenticate(objectClass, userName, password, true);
        log.info("authenticate::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid resolveUsername(final ObjectClass objectClass, final String userName, final OperationOptions options) {
        log.info("resolveUsername::begin");
        Uid uid = realAuthenticate(objectClass, userName, null, false);
        log.info("resolveUsername::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes, final OperationOptions options) {
        log.info("create::begin");
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

                reader = createReader(configuration);
                reader.skip(configuration.getFilePath().length() - 1);

                char[] chars = new char[1];
                reader.read(chars);

                writer = createWriter(true);
                if (chars[0] != '\n') {
                    writer.write('\n');
                }
                writer.append(record);
                writer.append('\n');
            }
            catch (ConnectorException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new ConnectorException("Couldn't create account, reason: " + ex.getMessage(), ex);
            }
            finally {
                lock.notify();
                closeWriter(writer, null);
            }
        }

        log.info("create::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        log.info("delete::begin");
        doUpdate(Operation.DELETE, objectClass, uid, null, options);
        log.info("delete::end");
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        log.info("schema::begin");

        List<String> headers = null;
        BufferedReader reader = null;
        synchronized (lock) {
            try {
                reader = createReader(configuration);
                headers = readHeader(reader, linePattern, configuration);
                testHeader(headers);
            }
            catch (ConnectorException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new ConnectorException("Couldn't read csv file header, reason: " + ex.getMessage(), ex);
            }
            finally {
                lock.notify();
                closeReader(reader, null);
            }
        }

        if (headers == null || headers.isEmpty()) {
            throw new ConnectorException("Schema can't be generated, header is null (proably not defined in file - first line in csv).");
        }

        ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();
        objClassBuilder.addAllAttributeInfo(createAttributeInfo(headers));

        SchemaBuilder builder = new SchemaBuilder(CSVFileConnector.class);
        builder.defineObjectClass(objClassBuilder.build());

        log.info("schema::end");
        return builder.build();
    }

    /**
     * {@inheritDoc}
     */
    public Object runScriptOnConnector(ScriptContext request, OperationOptions options) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<String> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        log.info("createFilterTranslator::begin");
        isAccount(objectClass);

        log.info("createFilterTranslator::end");
        return new AbstractFilterTranslator<String>() {
        };
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, String query, ResultsHandler handler, OperationOptions options) {
        log.info("executeQuery::begin");
        isAccount(objectClass);
        notNull(handler, "Results handled object can't be null.");

        BufferedReader reader = null;
        synchronized (lock) {
            try {
                reader = createReader(configuration);
                List<String> header = readHeader(reader, linePattern, configuration);

                String line = null;
                CsvItem item = null;
                while (( line = reader.readLine() ) != null) {
                    if (isEmptyOrComment(line)) {
                        continue;
                    }
                    item = createCsvItem(line);

                    ConnectorObject object = createConnectorObject(header, item);
                    if (!handler.handle(object)) {
                        break;
                    }
                }
            }
            catch (ConnectorException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new ConnectorException("Can't execute query, reason: " + ex.getMessage(), ex);
            }
            finally {
                lock.notify();
                closeReader(reader, null);
            }
        }

        log.info("executeQuery::end");
    }

    /**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, final OperationOptions options) {
        log.info("sync::begin");
        isAccount(objectClass);
        notNull(handler, "Sync results handler must not be null.");
        if (token == null || token.getValue() == null) {
            token = new SyncToken("-1");
        }
        String tokenValue = getTokenValue(token);
        long tokenLongValue = Long.parseLong(tokenValue);

        boolean hasFileChanged = false;
        final DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
        if (configuration.getFilePath().lastModified() > tokenLongValue) {
            hasFileChanged = true;
            log.info("Csv file has changed on {0} which is after time {1}, based on token value {2}",
                    df.format(new Date(configuration.getFilePath().lastModified())), df.format(new Date(tokenLongValue)), tokenLongValue);
        }

        if (hasFileChanged) {
            long timestamp = configuration.getFilePath().lastModified();
            log.info("Next last sync token value will be {0} ({1}).", timestamp, df.format(new Date(timestamp)));
            File syncFile = new File(configuration.getFilePath().getParentFile(), configuration.getFilePath().getName() + "." + timestamp + TMP_EXTENSION);
            synchronized (lock) {
                try {
                    copyAndReplace(configuration.getFilePath(), syncFile);
                }
                catch (ConnectorException ex) {
                    throw ex;
                }
                catch (Exception ex) {
                    throw new ConnectorException("Could not create file copy for sync, reason: " + ex.getMessage(), ex);
                }
                finally {
                    lock.notify();
                }
            }

            File oldFile = null;
            if (!"-1".equals(tokenValue)) {
                oldFile = new File(configuration.getFilePath().getParent(), configuration.getFilePath().getName() + "." + tokenValue);
            }
            InMemoryDiff memoryDiff = new InMemoryDiff(oldFile, syncFile, linePattern, configuration);
            try {
                List<Change> changes = memoryDiff.diff();
                log.info("Found {0} differences.", changes.size());
                SyncToken newToken = new SyncToken(Long.toString(timestamp));
                for (Change change : changes) {
                    SyncDelta delta = createSyncDelta(change, newToken);
                    if (!handler.handle(delta)) {
                        break;
                    }
                }
                syncFile.renameTo(new File(configuration.getFilePath().getParent(), configuration.getFilePath().getName() + "." + timestamp));
            }
            catch (DiffException ex) {
                throw new ConnectorException("Could not create csv diff, reason: " + ex.getMessage(), ex);
            }
            catch (Exception ex) {
                throw new ConnectorException("Could not finish sync operation, reason: " + ex.getMessage(), ex);
            }
            finally {
                if (syncFile.exists()) {
                    syncFile.delete();
                }
            }
        } else {
            String date = "Unknown";
            if (tokenLongValue != -1) {
                date = df.format(new Date(tokenLongValue));
            }
            log.info("File has not changed after {0} (token value {1}), diff will be skippend.", date, tokenValue);
        }
        log.info("sync::end");
    }

    /**
     * {@inheritDoc}
     */
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        log.info("getLatestSyncToken::begin");
        isAccount(objectClass);

        if (!configuration.getFilePath().exists()) {
            throw new ConnectorException("Csv file '" + configuration.getFilePath() + "' not found.");
        }
        File parentFolder = configuration.getFilePath().getParentFile();
        if (!parentFolder.exists() || !parentFolder.isDirectory()) {
            throw new ConnectorException("Parent folder for '" + configuration.getFilePath()
                    + "' doesn't exist, or is not a directory.");
        }

        final String csvFileName = configuration.getFilePath().getName();
        String[] oldCsvFiles = parentFolder.list(new FilenameFilter() {

            @Override
            public boolean accept(File parent, String fileName) {
                File file = new File(parent, fileName);
                if (file.isDirectory()) {
                    return false;
                }

                if (fileName.matches(csvFileName.replaceAll("\\.", "\\\\.") + "\\.[0-9]{13}$")) {
                    return true;
                }

                return false;
            }
        });
        String token = "-1";
        if (oldCsvFiles.length != 0) {
            Arrays.sort(oldCsvFiles);
            String latestCsvFile = oldCsvFiles[oldCsvFiles.length - 1];
            token = latestCsvFile.replaceFirst(csvFileName + ".", "");
        } else {
            log.info("Old csv files was not found, returning default token '-1'.");
        }

        log.info("getLatestSyncToken::end, returning token {0}.", token);
        return new SyncToken(token);
    }

    /**
     * {@inheritDoc}
     */
    public void test() {
        log.info("test::begin");
        log.info("Validating configuration.");
        configuration.validate();

        BufferedReader reader = null;
        synchronized (lock) {
            try {
                log.info("Openin input stream to file {0}.", configuration.getFilePath());
                reader = createReader(configuration);

                List<String> headers = readHeader(reader, linePattern, configuration);
                testHeader(headers);
            }
//            catch (IOException ex) {
//                log.error("Test configuration was unsuccessful, reason: {0}.", ex.getMessage());
//                throw new ConnectorException("I/O error occured, reason: " + ex.getMessage(), ex);
//            }
            catch (ConnectorException ex) {
                log.error("Connector exception occured, reason: {0}.", ex.getMessage());
                throw ex;
            }
            catch (Exception ex) {
                log.error("Test configuration was unsuccessful, reason: {0}.", ex.getMessage());
                throw new ConnectorException("Uknown error occured during test connection, " + "reason: " + ex.getMessage(), ex);
            }
            finally {
                log.info("Closing file input stream.");
                lock.notify();
                closeReader(reader, null);
            }
        }

        log.info("Test configuration was successful.");
        log.info("test::end");
    }

    /**
     * {@inheritDoc}
     */
    public Uid update(ObjectClass objectClass,
            Uid uid,
            Set<Attribute> replaceAttributes,
            OperationOptions options) {
        log.info("update::begin");
        uid = doUpdate(Operation.UPDATE, objectClass, uid, replaceAttributes, options);
        log.info("update::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid addAttributeValues(ObjectClass objectClass,
            Uid uid,
            Set<Attribute> valuesToAdd,
            OperationOptions options) {
        log.info("addAttributeValues::begin");
        uid = doUpdate(Operation.ADD_ATTR_VALUE, objectClass, uid, valuesToAdd, options);
        log.info("addAttributeValues::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid removeAttributeValues(ObjectClass objectClass,
            Uid uid,
            Set<Attribute> valuesToRemove,
            OperationOptions options) {
        log.info("removeAttributeValues::begin");
        uid = doUpdate(Operation.REMOVE_ATTR_VALUE, objectClass, uid, valuesToRemove, options);
        log.info("removeAttributeValues::end");
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
        log.info("Creating writer.");
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
        }
        catch (IOException ex) {
            throw new ConnectorException("Couldn't close writer, reason: " + ex.getMessage(), ex);
        }
    }

    private void createTempFile() {
        File file = new File(configuration.getFilePath() + ".tmp");
        try {
            if (file.exists()) {
                if (!file.delete()) {
                    throw new ConnectorException("Couldn't delete old tmp file '" + file.getAbsolutePath() + "'.");
                }
            }
            file.createNewFile();
        }
        catch (IOException ex) {
            throw new ConnectorException("Couldn't create tmp file '" + file.getAbsolutePath()
                    + "', reason: " + ex.getMessage(), ex);
        }
    }

    private void writeHeader(Writer writer, List<String> headers) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String header : headers) {
            if (headers.indexOf(header) != 0) {
                builder.append(configuration.getFieldDelimiter());
            }
            builder.append(configuration.getValueQualifier());
            builder.append(header);
            builder.append(configuration.getValueQualifier());
        }
        writer.write(builder.toString());
        writer.write('\n');
    }

    private CsvItem findAccount(BufferedReader reader, List<String> header, String username) throws IOException {
        String line = null;
        while (( line = reader.readLine() ) != null) {
            if (isEmptyOrComment(line)) {
                continue;
            }

            CsvItem item = createCsvItem(line);

            int fieldIndex = header.indexOf(configuration.getUniqueAttribute());
            String value = item.getAttribute(fieldIndex);
            if (!StringUtil.isEmpty(value) && value.equals(username)) {
                return item;
            }
        }
        return null;
    }

    private CsvItem createCsvItem(String line) {
        CsvItem item = new CsvItem(parseValues(line, linePattern, configuration));

        return item;
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
                    throw new ConnectorException("Unique attribute for record is not defined.");
                }
            }
            if (attribute == null) {
                continue;
            }

            String value = appendValues(name, attribute.getValue());
            if (StringUtil.isNotEmpty(value)) {
                builder.append(configuration.getValueQualifier());
                builder.append(value);
                builder.append(configuration.getValueQualifier());
            }
        }

        return builder;
    }

    private Attribute getAttribute(String name, Set<Attribute> attributes) {
        if (name.equals(configuration.getPasswordAttribute())) {
            name = ATTRIBUTE_PASSWORD;
        }
        if (name.equals(configuration.getNameAttribute())) {
            name = ATTRIBUTE_NAME;
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
            if (name.equals(configuration.getNameAttribute())) {
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
                builder.addAttribute(name, item.getAttribute(i));

                if (!configuration.isUniqueAndNameAttributeEqual()) {
                    continue;
                }
            }
            if (name.equals(configuration.getNameAttribute())) {
                builder.setName(new Name(item.getAttribute(i)));
                continue;
            }
            if (name.equals(configuration.getPasswordAttribute())) {
                builder.addAttribute(ATTRIBUTE_PASSWORD, new GuardedString(item.getAttribute(i).toCharArray()));
                continue;
            }
            builder.addAttribute(name, item.getAttribute(i));
        }

        return builder.build();
    }

    private Uid realAuthenticate(ObjectClass objectClass, String username, GuardedString pwd, boolean testPassword) {
        log.info("realAuthenticate::begin");
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

                        @Override
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
            }
            catch (ConnectorException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new ConnectorException("Can't authenticate '" + username + "', reason: " + ex.getMessage(), ex);
            }
            finally {
                lock.notify();
                closeReader(reader, null);

                log.info("realAuthenticate::end");
            }
        }
    }

    private Uid doUpdate(Operation operation, ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions oo) {
        log.info("doUpdate::begin");
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
                List<String> header = readHeader(reader, linePattern, configuration);
                writeHeader(writer, header);

                boolean found = readAndUpdateFile(reader, writer, header, operation, uid, attributes);
                if (!found) {
                    throw new UnknownUidException("Uid '" + uid.getUidValue() + "' not found in file.");
                }

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
            }
            catch (ConnectorException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new ConnectorException("Couldn't do " + operation + " on account '" + uid.getUidValue()
                        + "', reason: " + ex.getMessage(), ex);
            }
            finally {
                lock.notify();
                closeReader(reader, null);
                closeWriter(writer, null);

                try {
                    File tmpFile = new File(configuration.getFilePath() + TMP_EXTENSION);
                    if (tmpFile.exists()) {
                        tmpFile.delete();
                    }
                }
                catch (Exception ex) {
                    //only try to cleanup tmp file, it will be replaced later, if exists
                }
            }
        }

        log.info("doUpdate::end");
        return uid;
    }

    private boolean readAndUpdateFile(BufferedReader reader, BufferedWriter writer, List<String> header,
            Operation operation, Uid uid, Set<Attribute> attributes) throws IOException {
        boolean found = false;
        String line = null;
        CsvItem item = null;
        while (( line = reader.readLine() ) != null) {
            if (isEmptyOrComment(line)) {
                writer.write(line);
                writer.write('\n');
                continue;
            }

            item = createCsvItem(line);
            int fieldIndex = header.indexOf(configuration.getUniqueAttribute());
            String value = item.getAttribute(fieldIndex);
            if (!StringUtil.isEmpty(value) && value.equals(uid.getUidValue())) {
                found = true;

                switch (operation) {
                    case UPDATE:
                    case ADD_ATTR_VALUE:
                    case REMOVE_ATTR_VALUE:
                        line = updateLine(operation, header, item, attributes);
                        break;
                    case DELETE:
                        continue;
                }
            }

            writer.write(line);
            writer.write('\n');
        }
        return found;
    }

    private String appendValues(String attributeName, List<Object> values) {
        final StringBuilder builder = new StringBuilder();
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (int i = 0; i < values.size(); i++) {
            Object object = values.get(i);

            if (object == null) {
                return null;
            }
            //for unique attribute insert first value (not multivalue)
            if (configuration.getUniqueAttribute().equals(attributeName)) {
                builder.append(object.toString());
                break;
            }

            if (i != 0) {
                builder.append(configuration.getMultivalueDelimiter());
            }
            if (object instanceof GuardedString) {
                GuardedString pwd = (GuardedString) object;
                pwd.access(new GuardedString.Accessor() {

                    @Override
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
            if (operation == Operation.REMOVE_ATTR_VALUE && ( newValues == null || newValues.isEmpty() )) {
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

                    @Override
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
                        if (StringUtil.isNotEmpty(value)) {
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
                builder.append(configuration.getValueQualifier());
                builder.append(value);
                builder.append(configuration.getValueQualifier());
            }
        }

        return builder.toString();
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

    private String getTokenValue(SyncToken token) {
        if (token == null || token.getValue() == null) {
            return "-1";
        }
        String object = token.getValue().toString();
        if (!object.matches("[0-9]{13}")) {
            return "-1";
        }

        return object;
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
     * Only for tests!
     * @return
     * @deprecated
     */
    @Deprecated
    Pattern getLinePattern() {
        return linePattern;
    }
}
