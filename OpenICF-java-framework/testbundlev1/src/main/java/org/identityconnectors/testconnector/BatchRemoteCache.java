/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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
 */
package org.identityconnectors.testconnector;

import org.identityconnectors.framework.api.operations.batch.BatchTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch operations are reformatted and sent off to remote resources for processing. Those
 * resources return a batch token and then process the batch tasks asynchronously to our
 * process.  To simulate these remote resources and their behavior this class provides a
 * common place to cache the results for later retrieval by a consumer who has only a
 * BatchToken to refer to them.
 *
 * This class also contains a copy of the batch task list just as the remote resource
 * would receive.  Tracking this data as a copy prevents delayed garbage collection and
 * connector release due to persistent references.
 */
public class BatchRemoteCache {
    public static class CachedBatchResult {
        public final Boolean complete;
        public final Boolean error;
        public final String resultId;
        public final Object result;

        public CachedBatchResult(Boolean complete, Boolean error, String resultId, Object result) {
            this.complete = complete;
            this.error = error;
            this.resultId = resultId;
            this.result = result;
        }
    }

    private static final BatchRemoteCache singleton = new BatchRemoteCache();
    private final Map<String,List<BatchTask>> tasks = new HashMap<String, List<BatchTask>>();
    private final Map<String,List<CachedBatchResult>> results = new HashMap<String, List<CachedBatchResult>>();
    private final Map<String, Boolean> complete = new HashMap<String, Boolean>();
    private final String resultLock = "resultLock";

    public static void addTasks(String token, List<BatchTask> tasklist) {
        singleton.tasks.put(token, new ArrayList<BatchTask>(tasklist));
    }

    public static List<BatchTask> getTasks(String token) {
        if (singleton.tasks.containsKey(token)) {
            return singleton.tasks.get(token);
        }
        return new ArrayList<BatchTask>();
    }

    public static void flushTasks(String token) {
        if (singleton.tasks.containsKey(token)) {
            singleton.tasks.remove(token);
        }
    }

    public static void addResult(String token, CachedBatchResult result) {
        synchronized (singleton.resultLock) {
            if (!singleton.results.containsKey(token)) {
                singleton.results.put(token, new ArrayList<CachedBatchResult>());
            }
            singleton.results.get(token).add(result);
        }
    }

    public static List<CachedBatchResult> getAndResetResults(String token) {
        synchronized (singleton.resultLock) {
            if (singleton.results.containsKey(token)) {
                List<CachedBatchResult> ret = Collections.unmodifiableList(
                        new ArrayList<CachedBatchResult>(singleton.results.get(token)));
                singleton.results.get(token).clear();
                return ret;
            }
            return new ArrayList<CachedBatchResult>();
        }
    }

    public static void flushResults(String token) {
        synchronized (singleton.resultLock) {
            if (singleton.results.containsKey(token)) {
                singleton.results.remove(token);
            }
        }
    }

    public static boolean isComplete(String token) {
        if (singleton.complete.containsKey(token)) {
            return singleton.complete.get(token);
        }
        return false;
    }

    public static void setComplete(String token) {
        singleton.complete.put(token, true);
    }
}