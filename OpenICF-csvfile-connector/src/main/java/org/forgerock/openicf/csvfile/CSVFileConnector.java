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
 * Copyright 2015-2016 ForgeRock AS
 * Portions Copyright 2011 Viliam Repan
 * Portions Copyright 2011 Radovan Semancik
 */
package org.forgerock.openicf.csvfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchEmptyResult;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.BatchTaskExecutor;
import org.identityconnectors.framework.api.operations.batch.CreateBatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateType;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.BatchOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ift.BoolCellProcessor;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.cellprocessor.ift.DateCellProcessor;
import org.supercsv.cellprocessor.ift.DoubleCellProcessor;
import org.supercsv.cellprocessor.ift.LongCellProcessor;
import org.supercsv.cellprocessor.ift.StringCellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.quote.AlwaysQuoteMode;
import org.supercsv.util.CsvContext;

/**
 * Main implementation of the CSVFile Connector
 */
@ConnectorClass(displayNameKey = "connector_name.display",
        configurationClass = CSVFileConfiguration.class)
public class CSVFileConnector implements Connector, BatchOp, AuthenticateOp, CreateOp, DeleteOp, ResolveUsernameOp,
        SchemaOp, SearchOp<Filter>, SyncOp, TestOp, UpdateAttributeValuesOp {

    private static final Log log = Log.getLog(CSVFileConnector.class);

    /**
     * CSV file-name to {@link ReentrantReadWriteLock} lookup map. All read/write accesses must be protected by
     * these file-specific locks.
     */
    private static final ConcurrentHashMap<String, ReentrantReadWriteLock> fileNameToLockMap =
            new ConcurrentHashMap<String, ReentrantReadWriteLock>();

    /**
     * CSV file-name to CSV-header lookup map.
     */
    private static final ConcurrentHashMap<String, String[]> fileNameToHeaderMap =
            new ConcurrentHashMap<String, String[]>();

    /**
     * CSV file-name to CSV-header {@link Set}, which is used for fast "contains header" checks.
     */
    private static final ConcurrentHashMap<String, Set<String>> fileNameToHeaderSetMap =
            new ConcurrentHashMap<String, Set<String>>();

    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link CSVFileConnector#init(org.identityconnectors.framework.spi.Configuration)}
     * .
     */
    private CSVFileConfiguration config;

    /**
     * CSV file's path, used for lookup purposes.
     */
    private String csvFilePath;

    /**
     * Encapsulates the quote, delimiter and newline preferences.
     */
    private CsvPreference csvPreference = CsvPreference.STANDARD_PREFERENCE;

    /**
     * Records the number of rows in the CSV file.
     */
    private static final Map<String, Integer> totalRowCount = new ConcurrentHashMap<String, Integer>();

    /**
     * Gets the Configuration context for this connector.
     *
     * @return The current {@link Configuration}
     */
    public Configuration getConfiguration() {
        return this.config;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @param config
     *            the new {@link Configuration}
     * @see org.identityconnectors.framework.spi.Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    public void init(final Configuration config) {
        this.config = (CSVFileConfiguration) config;
        csvPreference = new CsvPreference.Builder(
                ((CSVFileConfiguration) config).getQuoteCharacter().charAt(0),
                ((CSVFileConfiguration) config).getFieldDelimiter().charAt(0),
                ((CSVFileConfiguration) config).getNewlineString())
                .useQuoteMode(new AlwaysQuoteMode())
                .build();

        // initialize per-file locks and caches, but only if not already initialized
        final File csvFile = ((CSVFileConfiguration) config).getCsvFile();
        csvFilePath = csvFile.getAbsolutePath();
        if (fileNameToLockMap.get(csvFilePath) == null) {
            // we don't care if multiple threads call init() at same time, because only 1 will win at end of this block
            final ICsvMapReader reader = getReader(csvFile);
            final String [] header;
            try {
                header = readHeader(reader);
                fileNameToHeaderMap.put(csvFilePath, header);
            } finally {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file reader");
                }
            }
            fileNameToHeaderSetMap.put(csvFilePath,
                    Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(header))));

            totalRowCount.put(csvFilePath, -1);

            // this line must be last within this if-block, to preserve concurrency semantics
            fileNameToLockMap.putIfAbsent(csvFilePath, new ReentrantReadWriteLock());
        }
    }

    /**
     * Disposes of the {@link CSVFileConnector}'s resources.
     *
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    public void dispose() {
        config = null;
    }

    /******************
     * SPI Operations
     *
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods.
     ******************/

    /**
     * {@inheritDoc}
     */
    public Uid authenticate(final ObjectClass objectClass, final String userName, final GuardedString password,
            final OperationOptions options) {
        isAccount(objectClass);
        if (password == null) {
            throw new InvalidPasswordException("Password cannot be null.");
        }
        return testCredentials(userName, password, options);
    }

    /**
     * {@inheritDoc}
     */
    public Uid resolveUsername(final ObjectClass objectClass, final String userName, final OperationOptions options) {
        isAccount(objectClass);
        return testCredentials(userName, null, options);
    }

    private Uid testCredentials(String name, GuardedString password, OperationOptions options) {
        if (name == null) {
            throw new InvalidCredentialException("Name cannot be null.");
        }
        Uid uid = findAccount(new Uid(name), password, options);
        if (uid == null) {
            throw new InvalidCredentialException(String.format("Account %s does not exist.", name));
        }
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes,
            final OperationOptions options) {
        isAccount(objectClass);
        return doCreate(createAttributes, options);
    }

    /**
     * {@inheritDoc}
     */
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        isAccount(objectClass);
        doDelete(uid, options);
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        final String[] header = getHeader();
        ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();
        objClassBuilder.addAllAttributeInfo(createAttributeInfo(header));

        SchemaBuilder builder = new SchemaBuilder(CSVFileConnector.class);
        builder.defineObjectClass(objClassBuilder.build());

        Schema schema = builder.build();
        Set<String> headerNames = fileNameToHeaderSetMap.get(csvFilePath);
        for (AttributeInfo info : ((ObjectClassInfo) schema.getObjectClassInfo().toArray()[0]).getAttributeInfo()) {
            String name = info.getName().equals(Name.NAME)
                    ? config.getHeaderUid()
                    : info.getName().equals(Uid.NAME)
                        ? config.getHeaderUid()
                        : info.getName().equals("__PASSWORD__")
                            ? config.getHeaderPassword()
                            : info.getName();
            if (!headerNames.contains(name)) {
                throw new ConfigurationException("CSV file does not contain required " + name + " column");
            }
        }

        return schema;
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        isAccount(objectClass);
        return CSVFilterTranslator.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        isAccount(objectClass);
        if (handler == null) {
            throw new IllegalArgumentException("ResultsHandler cannot be null");
        }

        final String[] header = getHeader();
        ICsvMapReader reader = null;

        final ReadLock lock = fileNameToLockMap.get(csvFilePath).readLock();
        lock.lock();
        try {
            if (handler instanceof SearchResultsHandler && totalRowCount.get(csvFilePath) == -1) {
                BufferedReader rowReader = null;
                try {
                    rowReader = new BufferedReader(new FileReader(csvFilePath));
                    while (rowReader.readLine() != null) {
                        totalRowCount.put(csvFilePath, totalRowCount.get(csvFilePath) + 1);
                    }
                } catch (Exception e) {
                    // just leave count at -1
                } finally {
                    if (rowReader != null) {
                        try {
                            rowReader.close();
                        } catch (IOException e) {
                            log.error(e, "Failed to close file {0}", csvFilePath);
                        }
                    }
                }
            }

            reader = new CsvMapReader(new FileReader(config.getCsvFile()), csvPreference);

            final CellProcessor[] processors = getProcessors(header);

            Map<String, Object> entry;
            reader.read(header, processors); // consume the header

            int pageSize = options == null || options.getPageSize() == null ? 0 : options.getPageSize();
            int rowOffset = 0;
            if (options != null) {
                if (options.getPagedResultsOffset() != null) {
                    rowOffset = options.getPagedResultsOffset();
                } else if (options.getPagedResultsCookie() != null) {
                    rowOffset = Integer.valueOf(options.getPagedResultsCookie());
                }
            }
            int nextPageOffset = (pageSize > 0)
                    ? rowOffset + pageSize
                    : 0;
            int resultsHandled = pageSize;

            while ((entry = reader.read(header, processors)) != null) {
                ConnectorObject object = newConnectorObject(entry);
                if (query == null || query.accept(object)) {
                    if (rowOffset-- <= 0) {
                        if (!handler.handle(newConnectorObject(entry))) {
                            break;
                        }
                        if (pageSize > 0 && --resultsHandled <= 0) {
                            break;
                        }
                    }
                }
            }

            if (handler instanceof SearchResultsHandler && totalRowCount.get(csvFilePath) > -1) {
                SearchResult searchResult = new SearchResult(
                        (nextPageOffset > totalRowCount.get(csvFilePath))
                                ? null
                                : String.valueOf(nextPageOffset),
                        SearchResult.CountPolicy.EXACT,
                        totalRowCount.get(csvFilePath),
                        Math.max(0, totalRowCount.get(csvFilePath) - nextPageOffset));
                ((SearchResultsHandler) handler).handleResult(searchResult);
            }
        } catch (FileNotFoundException e) {
            log.error(e, "File {0} does not exist!", config.getCsvFile().toString());
            throw new ConnectorIOException("File " + config.getCsvFile().toString() + " does not exist", e);
        } catch (IOException e) {
            log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
            throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file reader");
                }
            }
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, OperationOptions options) {
        isAccount(objectClass);
        if (handler == null) {
            throw new IllegalArgumentException("ResultsHandler cannot be null");
        }

        final String[] header = getHeader();
        File syncOrigin = null;
        boolean changesProcessed = false;

        // start with a write-lock
        final ReentrantReadWriteLock rwLock = fileNameToLockMap.get(csvFilePath);
        rwLock.writeLock().lock();
        try {
            if (token != null && token.getValue() != null) {
                syncOrigin = new File(config.getCsvFile().getParentFile(),
                        config.getCsvFile().getName() + "." + token.getValue());
            }
            if (syncOrigin == null || !syncOrigin.exists()) {
                long timestamp = config.getCsvFile().lastModified();
                syncOrigin = new File(config.getCsvFile().getParentFile(),
                        config.getCsvFile().getName() + "." + timestamp);
                try {
                    Files.copy(config.getCsvFile().toPath(), syncOrigin.toPath());
                } catch (IOException e) {
                    throw new ConnectorException("Unable to copy CSV file for sync operation", e);
                }
                token = new SyncToken(timestamp);
            }
            scrubSyncFiles();

            // downgrade to a read-lock
            rwLock.readLock().lock();
        } finally {
            rwLock.writeLock().unlock();
        }

        // we now have a read-lock unless the above block threw an Exception
        try {
            // Sync deletes
            ICsvMapReader reader = null;
            try {
                reader = new CsvMapReader(new FileReader(syncOrigin), csvPreference);
                String[] originHeader = readHeader(reader);
                compareHeaders(originHeader);

                final CellProcessor[] processors = getProcessors(originHeader);

                Map<String, Object> entry;
                while ((entry = reader.read(originHeader, processors)) != null) {
                    ConnectorObject originObject = newConnectorObject(entry);
                    ConnectorObject currentObject = findObjectInFile(null, originObject.getUid());
                    if (currentObject == null) {
                        SyncDelta delta = generateSyncDelta(originObject, null, token);
                        if (delta != null) {
                            if (!handler.handle(delta)) {
                                break;
                            }
                            changesProcessed = true;
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                log.error(e, "File {0} does not exist!", syncOrigin.toString());
                throw new ConnectorIOException("File " + syncOrigin.toString() + " does not exist", e);
            } catch (IOException e) {
                log.error(e, "Error reading from {0}!", syncOrigin.toString());
                throw new ConnectorIOException("Error reading from file " + syncOrigin.toString(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        log.error(e, "Error closing file reader");
                    }
                }
            }

            // Sync creates/updates
            reader = null;
            try {
                reader = new CsvMapReader(new FileReader(config.getCsvFile()), csvPreference);

                final CellProcessor[] processors = getProcessors(header);

                Map<String, Object> entry;
                reader.read(header, processors); //consume header
                while ((entry = reader.read(header, processors)) != null) {
                    ConnectorObject currentObject = newConnectorObject(entry);
                    ConnectorObject originObject = findObjectInFile(syncOrigin, currentObject.getUid());
                    SyncDelta delta = generateSyncDelta(originObject, currentObject, token);
                    if (delta != null) {
                        if (!handler.handle(delta)) {
                            break;
                        }
                        changesProcessed = true;
                    }
                }
            } catch (FileNotFoundException e) {
                log.error(e, "File {0} does not exist!", config.getCsvFile().toString());
                throw new ConnectorIOException("File " + config.getCsvFile().toString() + " does not exist", e);
            } catch (IOException e) {
                log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
                throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        log.error(e, "Error closing file reader");
                    }
                }
            }

            if (changesProcessed) {
                long timestamp = config.getCsvFile().lastModified();
                syncOrigin = new File(config.getCsvFile().getParentFile(),
                        config.getCsvFile().getName() + "." + timestamp);
                if (!syncOrigin.exists()) {
                    try {
                        Files.copy(config.getCsvFile().toPath(), syncOrigin.toPath());
                    } catch (IOException e) {
                        throw new ConnectorException("Unable to copy CSV file for sync operation", e);
                    }
                    token = new SyncToken(timestamp);
                }
            }

            if (handler instanceof SyncTokenResultsHandler) {
                ((SyncTokenResultsHandler) handler).handleResult(token);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        isAccount(objectClass);

        File dataDir = config.getCsvFile().getParentFile();
        Map<Long, String> files = new TreeMap<Long, String>();

        final ReadLock lock = fileNameToLockMap.get(csvFilePath).readLock();
        lock.lock();
        try {
            Pattern filePattern = Pattern.compile("(" + config.getCsvFile().getName() + ")(\\.[0-9]{13})$");
            for (String filename : dataDir.list()) {
                Matcher matcher = filePattern.matcher(filename);
                if (matcher.find()) {
                    Long timestamp = Long.valueOf(matcher.group(2).substring(1));
                    files.put(timestamp, filename);
                }
            }
            if (files.size() > 0) {
                List<Long> keys = new LinkedList<Long>(files.keySet());
                return new SyncToken(keys.get(keys.size() - 1));
            }
            return new SyncToken(new Date().getTime());
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void test() {
        config.validate();

        // validate header
        if (fileNameToHeaderSetMap.get(csvFilePath).size() != getHeader().length) {
            throw new ConnectorException("Header contains duplicate columns");
        }
    }

    /**
     * {@inheritDoc}
     */
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options) {
        isAccount(objectClass);
        return doUpdate(UpdateType.UPDATE, uid, attributes, options);
    }

    /**
     * {@inheritDoc}
     */
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> attributes,
            OperationOptions options) {
        isAccount(objectClass);
        return doUpdate(UpdateType.ADDVALUES, uid, attributes, options);
    }

    /**
     * {@inheritDoc}
     */
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> attributes,
            OperationOptions options) {
        isAccount(objectClass);
        return doUpdate(UpdateType.REMOVEVALUES, uid, attributes, options);
    }

    /**
     * {@inheritDoc}
     */
    public Subscription executeBatch(List<BatchTask> batchTasks, Observer<BatchResult> observer,
            OperationOptions operationOptions) {
        if (observer == null) {
            throw new ConnectorException("BatchResult Observer cannot be null");
        }
        BatchTaskExecutorImpl executor = new BatchTaskExecutorImpl();
        int taskId = 0;
        for (int i = 0; i < batchTasks.size(); i++) {
            BatchTask task = batchTasks.get(i);
            try {
                observer.onNext(new BatchResult(task.execute(executor), null, String.valueOf(taskId++),
                        i == batchTasks.size() - 1, false));
            } catch (Exception e) {
                observer.onError(e);
            }
        }
        observer.onCompleted();
        return new Subscription() {
            public void close() {}

            public boolean isUnsubscribed() {
                return true;
            }

            public Object getReturnValue() {
                return null;
            }
        };
    }

    private class BatchTaskExecutorImpl implements BatchTaskExecutor {
        public Uid execute(CreateBatchTask task) {
            return doCreate(task.getCreateAttributes(), task.getOptions());
        }

        public BatchEmptyResult execute(DeleteBatchTask task) {
            doDelete(task.getUid(), task.getOptions());
            return null;
        }

        public Uid execute(UpdateBatchTask task) {
            return doUpdate(task.getUpdateType(), task.getUid(), task.getAttributes(), task.getOptions());
        }
    }

    /**
     * {@inheritDoc}
     */
    public Subscription queryBatch(BatchToken batchToken, Observer<BatchResult> observer,
            OperationOptions operationOptions) {
        return null;
    }



    private void isAccount(ObjectClass objectClass) {
        if (objectClass == null || !objectClass.equals(ObjectClass.ACCOUNT)) {
            throw new IllegalArgumentException(String.format("Operation requires ObjectClass %s, received %s",
                    ObjectClass.ACCOUNT_NAME, objectClass));
        }
    }

    private Uid findAccount(final Uid uid, final GuardedString password,
            final OperationOptions options) {
        if ((password != null && config.getHeaderPassword() == null)
                || (uid == null && config.getHeaderUid() == null)) {
            return null;
        }

        final String[] header = getHeader();
        final Set<String> headerSet = fileNameToHeaderSetMap.get(csvFilePath);
        ICsvMapReader reader = null;

        final ReadLock lock = fileNameToLockMap.get(csvFilePath).readLock();
        lock.lock();
        try {
            reader = new CsvMapReader(new FileReader(config.getCsvFile()), csvPreference);

            if (password != null && !headerSet.contains(config.getHeaderPassword())) {
                throw new ConfigurationException("Password column must be defined and exist in the CSV.");
            }

            final CellProcessor[] processors = getProcessors(header);

            Map<String, Object> entry;
            while ((entry = reader.read(header, processors)) != null) {
                if (uid != null && uid.getUidValue().equals(entry.get(config.getHeaderUid()))) {
                    Uid foundUid = new Uid((String) entry.get(config.getHeaderUid()));
                    if (password == null) {
                        return foundUid;
                    }
                    final Map<String, Object> finalEntry = entry;
                    password.access(new GuardedString.Accessor() {
                        public void access(char[] chars) {
                            if (!new String(chars).equals(finalEntry.get(config.getHeaderPassword()))) {
                                throw new InvalidPasswordException("Invalid username and/or password.");
                            }
                        }
                    });
                    return foundUid;
                }
            }
        } catch (FileNotFoundException e) {
            log.error(e, "File {0} does not exist!", config.getCsvFile().toString());
            throw new ConnectorIOException("File " + config.getCsvFile().toString() + " does not exist", e);
        } catch (IOException e) {
            log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
            throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file reader");
                }
            }
            lock.unlock();
        }
        return null;
    }

    private String[] getHeader() {
        return fileNameToHeaderMap.get(csvFilePath);
    }

    private String[] readHeader(ICsvMapReader reader) {
        String[] hdr;

        try {
            hdr = reader.getHeader(true);
        } catch (IOException e) {
            log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
            throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
        }

        for (int i = 0; hdr != null && i < hdr.length; i++) {
            if (null == hdr[i]) {
                // It might be null if "" is used as a header or if an extra delimiter is in the header.
                throw new ConfigurationException(
                        "Invalid empty header.  Verify the format in the file " + config.getCsvFile().toString());
            }
            hdr[i] = hdr[i].trim();
        }

        return hdr;
    }

    private ICsvMapReader getReader(File file) {
        try {
            return new CsvMapReader(new FileReader(file), csvPreference);
        } catch (FileNotFoundException e) {
            log.error(e, "File {0} does not exist!", file.toString());
            throw new ConnectorIOException("File " + file.toString() + " does not exist", e);
        }
    }

    private List<AttributeInfo> createAttributeInfo(String[] names) {
        List<AttributeInfo> infos = new ArrayList<AttributeInfo>();
        for (String name : names) {
            if (name.equals(config.getHeaderUid())) {
                infos.add(new AttributeInfoBuilder(Uid.NAME).build());
                continue;
            }
            if (name.equals(config.getHeaderPassword())) {
                infos.add(OperationalAttributeInfos.PASSWORD);
                continue;
            }

            AttributeInfoBuilder builder = new AttributeInfoBuilder(name);
            if (name.equals(config.getHeaderPassword())) {
                builder.setType(GuardedString.class);
            } else {
                builder.setType(String.class);
            }
            infos.add(builder.build());
        }

        return infos;
    }

    private ConnectorObject newConnectorObject(Map<String,Object> entry) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        for (String col : getHeader()) {
            if (entry.containsKey(col) && entry.get(col) != null) {
                if (col.equals(config.getHeaderUid())) {
                    builder.setUid((String) entry.get(col));
                    builder.setName((String) entry.get(col));
                    builder.addAttribute(col, entry.get(col));
                } else if (col.equals(config.getHeaderPassword())) {
                    builder.addAttribute(OperationalAttributes.PASSWORD_NAME,
                            new GuardedString(((String) entry.get(col)).toCharArray()));
                } else {
                    builder.addAttribute(col, entry.get(col));
                }
            }
        }
        return builder.build();
    }

    private ConnectorObject findObjectInFile(File file, Uid uid) {
        ICsvMapReader reader = null;

        final ReentrantReadWriteLock rwLock =
                fileNameToLockMap.get(file == null ? csvFilePath : file.getAbsolutePath());
        if (rwLock != null) {
            // CSVFileConnector manages locks for the given file
            rwLock.readLock().lock();
        }
        try {
            reader = new CsvMapReader(new FileReader(file == null ? config.getCsvFile() : file),
                    csvPreference);
            final String[] header = rwLock != null ? getHeader() : readHeader(reader);

            final CellProcessor[] processors = getProcessors(header);

            Map<String, Object> entry;
            while ((entry = reader.read(header, processors)) != null) {
                ConnectorObject object = newConnectorObject(entry);
                if (object.getUid().getUidValue().equals(uid.getUidValue())) {
                    return object;
                }
            }
        } catch (FileNotFoundException e) {
            log.error(e, "File {0} does not exist!", config.getCsvFile().toString());
            throw new ConnectorIOException("File " + config.getCsvFile().toString() + " does not exist", e);
        } catch (IOException e) {
            log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
            throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file reader");
                }
            }
            if (rwLock != null) {
                rwLock.readLock().unlock();
            }
        }
        return null;
    }

    private SyncDelta generateSyncDelta(ConnectorObject origin, ConnectorObject current, SyncToken token) {
        SyncDeltaBuilder builder = new SyncDeltaBuilder();
        builder.setUid(origin == null ? current.getUid() : origin.getUid());
        builder.setToken(token);

        if (current == null) {
            builder.setDeltaType(SyncDeltaType.DELETE);
            builder.setObject(origin);
        } else if (origin == null) {
            builder.setDeltaType(SyncDeltaType.CREATE);
            builder.setObject(current);
        } else if (objectsDiffer(origin, current)) {
            builder.setDeltaType(SyncDeltaType.UPDATE);
            builder.setObject(current);
        } else {
            return null;
        }

        return builder.build();
    }

    private boolean objectsDiffer(ConnectorObject left, ConnectorObject right) {
        for (Attribute attrL : left.getAttributes()) {
            Attribute attrR = right.getAttributeByName(attrL.getName());
            if (attrR == null || !attrR.equals(attrL)) {
                return true;
            }
        }
        for (Attribute attrR : right.getAttributes()) {
            Attribute attrL = left.getAttributeByName(attrR.getName());
            if (attrL == null || !attrL.equals(attrR)) {
                return true;
            }
        }
        return left.getUid().getUidValue().equals(right.getUid().getUidValue())
                && left.getObjectClass().equals(right.getObjectClass())
                && left.getName().getNameValue().equals(right.getName().getNameValue());
    }

    private void scrubSyncFiles() {
        File dataDir = config.getCsvFile().getParentFile();
        Map<Long, String> files = new TreeMap<Long, String>();

        Pattern filePattern = Pattern.compile("(" + config.getCsvFile().getName() + ")(\\.[0-9]{13})$");
        for (String filename : dataDir.list()) {
            Matcher matcher = filePattern.matcher(filename);
            if (matcher.find()) {
                Long timestamp = Long.valueOf(matcher.group(2).substring(1));
                files.put(timestamp, filename);
            }
        }
        while (files.size() > config.getSyncFileRetentionCount()) {
            Long key = (Long) files.keySet().toArray()[0];
            files.remove(key);
        }
    }

    private void compareHeaders(String[] header2) {
        List<String> hdr1 = Arrays.asList(getHeader());
        List<String> hdr2 = Arrays.asList(header2);

        for (String col : hdr1) {
            if (!hdr2.contains(col)) {
                throw new ConnectorException("Headers do not match");
            }
        }
        for (String col : hdr2) {
            if (!hdr1.contains(col)) {
                throw new ConnectorException("Headers do not match");
            }
        }
    }

    private Uid doCreate(Set<Attribute> attributes, OperationOptions options) {
        if (attributes == null || attributes.size() == 0) {
            throw new IllegalArgumentException("Attributes may not be null or empty.");
        }
        Uid uid = null;
        Map<String,Object> attrMap = new HashMap<String, Object>();
        for (Attribute attr : attributes) {
            if (attr.getName().equals(Uid.NAME) || attr.getName().equals(Name.NAME) ||
                    attr.getName().equals(config.getHeaderUid())) {
                uid = new Uid((String) attr.getValue().get(0));
            }
            if (attr.getName().equals(OperationalAttributes.PASSWORD_NAME)
                    && attr.getValue().get(0) instanceof GuardedString) {
                attrMap.put(getHeaderNameForAttrName(attr.getName()),
                        SecurityUtil.decrypt((GuardedString) attr.getValue().get(0)));
            } else {
                attrMap.put(getHeaderNameForAttrName(attr.getName()), attr.getValue().get(0));
            }
        }

        if (uid == null) {
            uid = new Uid(UUID.randomUUID().toString());
        } else if (findAccount(uid, null, options) != null) {
            throw new AlreadyExistsException(String.format("Account %s already exists.", uid.getUidValue()));
        }

        final String[] header = getHeader();

        // Order the attributes for insertion
        Map<String, Object> colMap = new LinkedHashMap<String, Object>();
        for (String col : header) {
            if (col.equals(config.getHeaderUid())) {
                colMap.put(col, uid.getUidValue());
            } else {
                colMap.put(col, attrMap.containsKey(col) ? attrMap.get(col) : null);
            }
        }

        ICsvMapWriter mapWriter = null;

        final WriteLock lock = fileNameToLockMap.get(csvFilePath).writeLock();
        lock.lock();
        try {
            mapWriter = new CsvMapWriter(new FileWriter(config.getCsvFile(), true), csvPreference);
            final CellProcessor[] processors = getProcessors(header);
            mapWriter.write(colMap, header, processors);
            totalRowCount.put(csvFilePath, totalRowCount.get(csvFilePath) + 1);
        } catch (IOException e) {
            throw new ConnectorException("Failed to create object", e);
        } finally {
            if (mapWriter != null) {
                try {
                    mapWriter.close();
                } catch (IOException e) {
                    log.error(e, "Failed to close CSV file after create");
                }
            }
            lock.unlock();
        }

        return uid;
    }

    private void doDelete(Uid uid, OperationOptions options) {
        if (uid == null) {
            throw new IllegalArgumentException("Uid cannot be null");
        }

        final String[] header = getHeader();
        ICsvMapReader reader = null;
        ICsvMapWriter writer = null;
        File tmp = null;

        final WriteLock lock = fileNameToLockMap.get(csvFilePath).writeLock();
        lock.lock();
        try {
            reader = new CsvMapReader(new FileReader(config.getCsvFile()), csvPreference);
            tmp = File.createTempFile("csvfile", "tmp");
            writer = new CsvMapWriter(new FileWriter(tmp), csvPreference);

            final CellProcessor[] processors = getProcessors(header);

            Map<String, Object> entry;
            boolean found = false;
            while ((entry = reader.read(header, processors)) != null) {
                String sUid = (String) entry.get(config.getHeaderUid());
                if (!uid.getUidValue().equals(sUid)) {
                    writer.write(entry, header, processors);
                } else {
                    found = true;
                }
            }
            if (!found) {
                tmp.delete();
                throw new UnknownUidException("Object for uid " + uid.toString() + " does not exist");
            }
            totalRowCount.put(csvFilePath, totalRowCount.get(csvFilePath) - 1);
        } catch (FileNotFoundException e) {
            log.error(e, "File {0} does not exist!", config.getCsvFile().toString());
            throw new ConnectorIOException("File " + config.getCsvFile().toString() + " does not exist", e);
        } catch (IOException e) {
            log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
            throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file reader");
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file writer");
                }
            }
            if (tmp != null && tmp.exists()) {
                try {
                    tmp.renameTo(config.getCsvFile().getAbsoluteFile());
                } catch (Exception e) {
                    log.error(e, "Error renaming file");
                }
            }
            lock.unlock();
        }
    }

    private Uid doUpdate(UpdateType type, Uid uid, Set<Attribute> attributes, OperationOptions options) {
        Uid updated = null;
        if (uid == null) {
            throw new IllegalArgumentException("Uid may not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("Attribute set may not be null");
        }

        final String[] header = getHeader();
        ICsvMapReader reader = null;
        ICsvMapWriter writer = null;
        File tmp = null;

        final WriteLock lock = fileNameToLockMap.get(csvFilePath).writeLock();
        lock.lock();
        try {
            reader = new CsvMapReader(new FileReader(config.getCsvFile()), csvPreference);
            tmp = File.createTempFile("csvfile", "tmp");
            writer = new CsvMapWriter(new FileWriter(tmp), csvPreference);

            writer.writeHeader(header);

            final CellProcessor[] processors = getProcessors(header);

            Map<String, Object> entry;
            reader.read(header, processors); // consume header
            while ((entry = reader.read(header, processors)) != null) {
                String sUid = (String) entry.get(config.getHeaderUid());
                if (uid.getUidValue().equals(sUid)) {
                    for (Attribute attr : attributes) {
                        if (type.equals(UpdateType.REMOVEVALUES)) {
                            entry.remove(getHeaderNameForAttrName(attr.getName()));
                        } else {
                            entry.put(getHeaderNameForAttrName(attr.getName()), getAttributeValue(attr));
                        }
                    }
                    updated = new Uid((String) entry.get(config.getHeaderUid()));
                }
                writer.write(entry, header, processors);
            }
            if (updated == null) {
                throw new UnknownUidException("Uid " + uid.getUidValue() + " does not exist");
            }
        } catch (FileNotFoundException e) {
            log.error(e, "File {0} does not exist!", config.getCsvFile().toString());
            throw new ConnectorIOException("File " + config.getCsvFile().toString() + " does not exist", e);
        } catch (IOException e) {
            log.error(e, "Error reading from {0}!", config.getCsvFile().toString());
            throw new ConnectorIOException("Error reading from file " + config.getCsvFile().toString(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file reader");
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    log.error(e, "Error closing file writer");
                }
            }
            if (tmp != null) {
                try {
                    tmp.renameTo(config.getCsvFile().getAbsoluteFile());
                } catch (Exception e) {
                    log.error(e, "Error renaming file");
                }
            }
            lock.unlock();
        }

        return updated;
    }

    private String getAttributeValue(Attribute attr) {
        return attr != null && attr.getValue() != null && attr.getValue().size() > 0
                ? (String) attr.getValue().get(0)
                : null;
    }

    private String getHeaderNameForAttrName(String attr) {
        if (attr.equals(Uid.NAME) || attr.equals(Name.NAME)) {
            return config.getHeaderUid();
        } else if (attr.equals("__PASSWORD__")) {
            return config.getHeaderPassword();
        }
        return attr;
    }

    private CellProcessor[] getProcessors(String[] header) {
        final CellProcessor[] processors = new CellProcessor[header.length];
        for (int i = 0; i < header.length; i++) {
            processors[i] = new OptionalTrim();
        }
        return processors;
    }

    private class OptionalTrim extends CellProcessorAdaptor implements BoolCellProcessor, DateCellProcessor, DoubleCellProcessor,
            LongCellProcessor, StringCellProcessor {

        /**
         * Constructs a new <tt>OptionalTrim</tt> processor, which trims a String to ensure it has no surrounding
         * whitespace.
         */
        public OptionalTrim() {
            super();
        }

        /**
         * Constructs a new <tt>OptionalTrim</tt> processor, which trims a String to ensure it has no surrounding
         * whitespace then calls the next processor in the chain.
         *
         * @param next
         *            the next processor in the chain
         * @throws NullPointerException
         *             if next is null
         */
        public OptionalTrim(final StringCellProcessor next) {
            super(next);
        }

        /**
         * {@inheritDoc}
         */
        public Object execute(final Object value, final CsvContext context) {
            Object result = value;
            if (value != null) {
                result = value.toString().trim();
            }
            return next.execute(result, context);
        }
    }
}