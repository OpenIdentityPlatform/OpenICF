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
 * Portions Copyrighted 2010-2013 ForgeRock AS.
 */
package org.identityconnectors.testconnector;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchEmptyResult;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
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
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.identityconnectors.framework.spi.operations.BatchOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.testcommon.TstCommon;

@ConnectorClass(
    displayNameKey="TestConnector",
    categoryKey="TestConnector.category",
    configurationClass=TstConnectorConfig.class)
public class TstConnector implements CreateOp, PoolableConnector, SchemaOp, SearchOp<String>, SyncOp, BatchOp {

    private static int _connectionCount = 0;
    private MyTstConnection _myConnection;
    private TstConnectorConfig _config;

    public static void checkClassLoader() {
        if (Thread.currentThread().getContextClassLoader() !=
            TstConnector.class.getClassLoader()) {
            throw new IllegalStateException("Unexpected classloader");
        }
    }

    public TstConnector() {
        checkClassLoader();
    }

    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        checkClassLoader();
        Integer delay = (Integer)options.getOptions().get("delay");
        if ( delay != null ) {
            try { Thread.sleep(delay.intValue()); } catch (Exception e) {}
        }
        if ( options.getOptions().get("testPooling") != null) {
            return new Uid(String.valueOf(_myConnection.getConnectionNumber()));
        }
        else {
            String version = TstCommon.getVersion();
            return new Uid(version);
        }
    }

    public void init(Configuration cfg) {
        checkClassLoader();
        _config = (TstConnectorConfig)cfg;
        if (_config.getResetConnectionCount()) {
            _connectionCount = 0;
        }
        _myConnection = new MyTstConnection(_connectionCount++);
    }

    public Configuration getConfiguration() {
        return _config;
    }

    public void dispose() {
        checkClassLoader();
        if (_myConnection != null) {
            _myConnection.dispose();
            _myConnection = null;
        }
    }

    public void checkAlive() {
        checkClassLoader();
        _myConnection.test();
    }

    /**
     * Used by the script tests
     */
    public void update() {
        _config.updateTest();
    }
    
    /**
     * Used by the script tests
     */
    public String concat(String s1, String s2) {
        checkClassLoader();
        return s1+s2;
    }

    public FilterTranslator<String> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
         checkClassLoader();
         //no translation - ok since this is just for tests
         return new AbstractFilterTranslator<String>(){};
    }

    public void executeQuery(ObjectClass objectClass, String query, ResultsHandler handler, OperationOptions options) {
        checkClassLoader();
        int remaining = _config.getNumResults();
        for (int i = 0; i < _config.getNumResults(); i++ ) {
            Integer delay = (Integer)options.getOptions().get("delay");
            if ( delay != null ) {
                try { Thread.sleep(delay.intValue()); } catch (Exception e) {}
            }
            ConnectorObjectBuilder builder =
                new ConnectorObjectBuilder();
            builder.setUid(Integer.toString(i));
            builder.setName(Integer.toString(i));
            builder.setObjectClass(objectClass);
            for ( int j = 0; j < 50; j++ ) {
                builder.addAttribute("myattribute"+j,"myvaluevaluevalue"+j);
            }

            ConnectorObject rv = builder.build();
            if (handler.handle(rv)) {
                remaining--;
            } else {
                break;
            }
        }

        if (handler instanceof SearchResultsHandler) {
            ((SearchResultsHandler) handler).handleResult(new SearchResult("",remaining));
        }
    }

    public void sync(ObjectClass objectClass, SyncToken token,
                     SyncResultsHandler handler,
                     OperationOptions options) {
        checkClassLoader();
        int remaining = _config.getNumResults();
        for (int i = 0; i < _config.getNumResults(); i++ ) {
            ConnectorObjectBuilder obuilder =
                new ConnectorObjectBuilder();
            obuilder.setUid(Integer.toString(i));
            obuilder.setName(Integer.toString(i));
            obuilder.setObjectClass(objectClass);

            SyncDeltaBuilder builder =
                new SyncDeltaBuilder();
            builder.setObject(obuilder.build());
            builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
            builder.setToken(new SyncToken("mytoken"));

            SyncDelta rv = builder.build();
            if (!handler.handle(rv)) {
                break;
            }
            remaining--;
        }
        if (handler instanceof SyncTokenResultsHandler) {
            ((SyncTokenResultsHandler) handler).handleResult(new SyncToken(remaining));
        }
    }

    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        checkClassLoader();
        return new SyncToken("mylatest");
    }

    public Schema schema() {
        checkClassLoader();
        SchemaBuilder builder = new SchemaBuilder(TstConnector.class);
        for ( int i = 0 ; i < 2; i++ ) {
            ObjectClassInfoBuilder classBuilder = new ObjectClassInfoBuilder();
            classBuilder.setType("class"+i);
            for ( int j = 0; j < 200; j++) {
                classBuilder.addAttributeInfo(AttributeInfoBuilder.build("attributename"+j, String.class));
            }
            builder.defineObjectClass(classBuilder.build());
        }
        return builder.build();
    }

    BatchUseCase3Processor processorUseCase3 = null;

    public Subscription executeBatch(final List<BatchTask> tasks, final Observer<BatchResult> observer,
                                   final OperationOptions options) {
        checkClassLoader();

        if (options.getOptions().containsKey("TEST_USECASE2")) {
            final BatchToken token = new BatchUseCase2Processor().executeBatch(tasks, options);

            return new Subscription() {
                public void close() {
                    assert _myConnection != null;
                }

                public boolean isUnsubscribed() {
                    assert _myConnection != null;
                    return true;
                }

                public Object getReturnValue() {
                    assert _myConnection != null;
                    return token;
                }
            };
        } else if (options.getOptions().containsKey("TEST_USECASE3")) {
            processorUseCase3 = new BatchUseCase3Processor();
            final BatchToken token = processorUseCase3.executeBatch(tasks, options, observer);

            return new Subscription() {
                public void close() {
                    assert _myConnection != null;
                }

                public boolean isUnsubscribed() {
                    assert _myConnection != null;
                    return true;
                }

                public Object getReturnValue() {
                    assert _myConnection != null;
                    return token;
                }
            };
        } else /* Use Case 1 */ {
            boolean complete = false;
            for (int i = 0; i < tasks.size() && !complete; i++) {
                BatchTask task = tasks.get(i);
                try {
                    complete = (i == tasks.size() - 1);
                    try {
                        Object result;
                        if (task instanceof DeleteBatchTask) {
                            result = new BatchEmptyResult(task.getClass().toString() + " successful");
                        } else {
                            result = new Uid(String.valueOf(i));
                        }
                        observer.onNext(new BatchResult(result, null, String.valueOf(i), complete, false));
                    } catch (RuntimeException e) {
                        observer.onNext(new BatchResult(e, null, String.valueOf(i), complete, true));
                    }
                } catch (Exception e) {
                    observer.onError(e);
                }
            }
            observer.onCompleted();
            return new Subscription() {
                public void close() {
                    assert _myConnection != null;
                }

                public boolean isUnsubscribed() {
                    assert _myConnection != null;
                    return true;
                }

                public Object getReturnValue() {
                    assert _myConnection != null;
                    return null;
                }
            };
        }
    }

    public Subscription queryBatch(final BatchToken batchToken, final Observer<BatchResult> observer,
                                 final OperationOptions options) {
        checkClassLoader();

        final AtomicBoolean opComplete = new AtomicBoolean(false);

        Subscription ret = new Subscription() {
            public void close() {}

            public boolean isUnsubscribed() {
                return true;
            }

            public Object getReturnValue() {
                return opComplete.get() ? null : batchToken;
            }
        };

        if (options.getOptions().containsKey("TEST_USECASE0")
                || options.getOptions().containsKey("TEST_USECASE1")) {
            observer.onCompleted();
        } else if (options.getOptions().containsKey("TEST_USECASE3")) {
            boolean allComplete = true;
            for (String token : batchToken.getTokens()) {
                allComplete &= BatchRemoteCache.isComplete(token);
            }
            opComplete.set(opComplete.get() | allComplete);
            if (opComplete.get()) {
                return null;
            }
        } else {
            opComplete.set(batchToken.getTokens().size() == 0);
            for (String token : batchToken.getTokens()) {
                List<BatchRemoteCache.CachedBatchResult> results = BatchRemoteCache.getAndResetResults(token);
                boolean tokenComplete = results.size() <= 0;
                for (BatchRemoteCache.CachedBatchResult result : results) {
                    observer.onNext(new BatchResult(result.result, batchToken, result.resultId,
                            result.complete, result.error));
                    tokenComplete |= result.complete;
                    if (result.error && options.getFailOnError()) {
                        return null;
                    }
                }
                if (tokenComplete) {
                    BatchRemoteCache.flushResults(token);
                    batchToken.removeToken(token);
                    observer.onCompleted();
                    return null;
                }
            }
        }

        return ret;
    }

    private class BatchUseCase2Processor extends Thread {
        private OperationOptions options;
        private String token;

        public BatchToken executeBatch(List<BatchTask> tasks, OperationOptions options) {
            this.token = UUID.randomUUID().toString();
            this.options = new OperationOptions(options.getOptions());
            BatchRemoteCache.addTasks(token, tasks);
            start();
            return new BatchToken(token);
        }

        public void run() {
            List<BatchTask> tasks = BatchRemoteCache.getTasks(token);
            int failId = options.getOptions().containsKey("FAIL_TEST_ITERATION")
                    ? (Integer) options.getOptions().get("FAIL_TEST_ITERATION")
                    : -1;

            for (int i = 0; i < tasks.size(); i++) {
                BatchTask task = tasks.get(i);
                try {
                    sleep(1);
                    Object result;
                    try {
                        if (i == failId) {
                            throw new ConnectorException(task.getClass().toString() + " failed");
                        }
                        if (task instanceof DeleteBatchTask) {
                            result = new BatchEmptyResult("Delete successful");
                        } else {
                            result = new Uid(String.valueOf(i));
                        }
                        BatchRemoteCache.addResult(token, new BatchRemoteCache.CachedBatchResult(
                                i == tasks.size() - 1, false, String.valueOf(i), result));
                    } catch (Exception e) {
                        BatchRemoteCache.addResult(token, new BatchRemoteCache.CachedBatchResult(
                                i == tasks.size() - 1, true, String.valueOf(i), e));
                        if (options.getFailOnError()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // interrupted
                }
            }
            BatchRemoteCache.flushTasks(token);
        }
    }

    private class BatchUseCase3Processor extends Thread {
        private OperationOptions options;
        private Observer<BatchResult> observer;
        private String token;

        public BatchToken executeBatch(List<BatchTask> tasks, OperationOptions options,
                                       Observer<BatchResult> observer) {
            this.token = UUID.randomUUID().toString();
            this.options = new OperationOptions(options.getOptions());
            this.observer = observer;
            BatchRemoteCache.addTasks(token, tasks);
            this.start();
            return new BatchToken(token);
        }

        public void run() {
            List<BatchTask> tasks = BatchRemoteCache.getTasks(token);
            int failId = options.getOptions().containsKey("FAIL_TEST_ITERATION")
                    ? (Integer) options.getOptions().get("FAIL_TEST_ITERATION")
                    : -1;

            for (int i = 0; i < tasks.size(); i++) {
                BatchTask task = tasks.get(i);
                try {
                    sleep(1);
                    Object result;
                    boolean complete = (i == tasks.size() - 1);
                    try {
                        if (i == failId) {
                            throw new ConnectorException(task.getClass().toString() + " failed");
                        }
                        if (task instanceof DeleteBatchTask) {
                            result = new BatchEmptyResult("Delete successful");
                        } else {
                            result = new Uid(String.valueOf(i));
                        }
                        observer.onNext(new BatchResult(result, new BatchToken(token), String.valueOf(i),
                                complete, false));
                    } catch (RuntimeException e) {
                        observer.onNext(new BatchResult(e, new BatchToken(token), String.valueOf(i),
                                complete, true));
                        if (options.getFailOnError()) {
                            break;
                        }
                    }
                    if (complete) {
                        observer.onCompleted();
                        BatchRemoteCache.setComplete(token);
                    }
                } catch (Exception e) {
                    // interrupted
                    observer.onError(e);
                    break;
                }
            }
            BatchRemoteCache.flushTasks(token);
            processorUseCase3 = null;
        }
    }
}
