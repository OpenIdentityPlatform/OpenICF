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

package org.identityconnectors.framework.impl.api.local.operations;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.identityconnectors.common.FailureHandler;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.SubscriptionHandler;
import org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp;
import org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl.ReferenceCounter;
import org.identityconnectors.framework.spi.AsyncCallbackHandler;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.ConnectorEventSubscriptionOp;
import org.identityconnectors.framework.spi.operations.SyncEventSubscriptionOp;

public class SubscriptionImpl extends ConnectorAPIOperationRunner {

    private static final Log logger = Log.getLog(SubscriptionImpl.class);

    private static final ExecutorService executorService = Executors
            .newCachedThreadPool(new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger();

                public Thread newThread(final Runnable r) {
                    final String name =
                            String.format("OpenICF Subscription Thread {%d}", count
                                    .getAndIncrement());
                    final Thread t = new Thread(null, r, name);
                    t.setDaemon(true);
                    return t;
                }
            });

    private final ReferenceCounter referenceCounter;

    /**
     * Creates the API operation so it can called multiple times.
     *
     * @param context
     * @param connector
     */
    protected SubscriptionImpl(final ConnectorOperationalContext context,
            final Connector connector, final ReferenceCounter referenceCounter) {
        super(context, connector);
        this.referenceCounter = referenceCounter;
    }

    public SubscriptionHandler subscribe(final ObjectClass objectClass, final Filter eventFilter,
            final ResultsHandler handler, final OperationOptions operationOptions) {
        final ConnectorEventSubscriptionOp operation =
                ((ConnectorEventSubscriptionOp) getConnector());

        final AsyncSubscriptionCallbackHandler callbackHandler =
                new AsyncSubscriptionCallbackHandler();

        executorService.submit(new Runnable() {
            public void run() {
                try {
                    referenceCounter.acquire();
                    operation.subscribe(objectClass, eventFilter, callbackHandler, handler,
                            operationOptions);
                } catch (Error error) {
                    callbackHandler.handleError(new ConnectorException(error.getMessage(), error));
                } catch (RuntimeException e) {
                    callbackHandler.handleError(e);
                } catch (Exception e) {
                    callbackHandler.handleError(new ConnectorException(e.getMessage(), e));
                }
            }
        });

        return callbackHandler.adapt();
    }

    public SubscriptionHandler subscribe(final ObjectClass objectClass, final SyncToken token,
            final SyncResultsHandler handler, final OperationOptions operationOptions) {
        final SyncEventSubscriptionOp operation = ((SyncEventSubscriptionOp) getConnector());

        final AsyncSubscriptionCallbackHandler callbackHandler =
                new AsyncSubscriptionCallbackHandler();

        executorService.submit(new Runnable() {
            public void run() {
                try {
                    referenceCounter.acquire();
                    operation.subscribe(objectClass, token, callbackHandler, handler,
                            operationOptions);
                } catch (Error error) {
                    callbackHandler.handleError(new ConnectorException(error.getMessage(), error));
                } catch (RuntimeException e) {
                    callbackHandler.handleError(e);
                } catch (Exception e) {
                    callbackHandler.handleError(new ConnectorException(e.getMessage(), e));
                }
            }
        });

        return callbackHandler.adapt();
    }

    interface AsyncOperationResultHandler extends SubscriptionHandler {

        void onComplete(final Runnable cancelHandler);

    }
    
    private interface StateListener {
        void handleStateChange(int newState, RuntimeException error);
    }

    private class AsyncSubscriptionCallbackHandler implements AsyncCallbackHandler {

        /**
         * State value indicating that this handler has not completed.
         */
        private static final int PENDING = 0;

        /**
         * State value indicating that this handler has failed (error set).
         */
        private static final int FAILED = 1;

        /**
         * State value indicating that this handler has been cancelled (error
         * set).
         */
        private static final int CANCELLED = 2;

        private volatile AtomicInteger state = new AtomicInteger(PENDING);
        private RuntimeException error = null;

        private final Queue<StateListener> listeners = new ConcurrentLinkedQueue<StateListener>();

        // --- Implement AsyncCallbackHandler interface

        /**
         * Signals that the asynchronous task represented by this handler has
         * failed. If the task has already completed then calling this method
         * has no effect and the provided result will be discarded.
         *
         * @param error
         *            The exception indicating why the task failed.
         */
        public void handleError(final RuntimeException error) {
            setState(FAILED, error);
        }

        public void onCancel(final Runnable cancelHandler) {
            addOrFireListener(new StateListener() {
                public void handleStateChange(final int newState, final RuntimeException error) {
                    if (newState == CANCELLED) {
                        cancelHandler.run();
                    }
                }
            });
        }

        // --- Implement SubscriptionHandler

        protected SubscriptionHandler adapt() {
            return new AsyncOperationResultHandler() {
                public void unsubscribe() {
                    setState(CANCELLED, null);
                }

                public void onFailure(final FailureHandler<RuntimeException> onFailure) {
                    AsyncSubscriptionCallbackHandler.this.onFailure(onFailure);
                }

                public void onComplete(final Runnable completeHandler) {
                    AsyncSubscriptionCallbackHandler.this.onComplete(completeHandler);
                }
            };
        }

        private void onFailure(final FailureHandler<RuntimeException> onFail) {
            addOrFireListener(new StateListener() {
                public void handleStateChange(final int newState, final RuntimeException error) {
                    if (newState == FAILED) {
                        onFail.handleError(error);
                    }
                }
            });
        }

        public void onComplete(final Runnable cancelHandler) {
            addOrFireListener(new StateListener() {
                public void handleStateChange(final int newState, final RuntimeException error) {
                    cancelHandler.run();
                }
            });
        }

        private void setState(final int newState, final RuntimeException error) {
            if (state.compareAndSet(PENDING, newState)) {
                this.error = error;
                referenceCounter.release();
                StateListener listener;
                while ((listener = listeners.poll()) != null) {
                    try {
                        listener.handleStateChange(newState, error);
                    } catch (Throwable ignore) {
                        logger.ok(ignore, "Failed to call StateListener");
                    }
                }
            }
        }

        private void addOrFireListener(final StateListener listener) {
            final int stateBefore = state.get();
            if (stateBefore != PENDING) {
                listener.handleStateChange(stateBefore, error);
            } else {
                listeners.add(listener);
                final int stateAfter = state.get();
                if (stateAfter != PENDING && listeners.remove(listener)) {
                    listener.handleStateChange(stateAfter, error);
                }
            }
        }
    }

    public static class ConnectorEventSubscriptionApiOpImp extends SubscriptionImpl implements
            ConnectorEventSubscriptionApiOp {

        /**
         * Creates the API operation so it can called multiple times.
         *
         * @param context
         * @param connector
         * @param referenceCounter
         */
        public ConnectorEventSubscriptionApiOpImp(ConnectorOperationalContext context,
                Connector connector, ReferenceCounter referenceCounter) {
            super(context, connector, referenceCounter);
        }
    }

    public static class SyncEventSubscriptionApiOpImpl extends SubscriptionImpl implements
            SyncEventSubscriptionApiOp {

        /**
         * Creates the API operation so it can called multiple times.
         *
         * @param context
         * @param connector
         * @param referenceCounter
         */
        public SyncEventSubscriptionApiOpImpl(ConnectorOperationalContext context,
                Connector connector, ReferenceCounter referenceCounter) {
            super(context, connector, referenceCounter);
        }
    }
}
