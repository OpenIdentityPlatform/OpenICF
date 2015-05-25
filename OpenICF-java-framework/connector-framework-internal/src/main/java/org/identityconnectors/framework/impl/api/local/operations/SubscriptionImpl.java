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

import java.util.concurrent.atomic.AtomicBoolean;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp;
import org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl.ReferenceCounter;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.ConnectorEventSubscriptionOp;
import org.identityconnectors.framework.spi.operations.SyncEventSubscriptionOp;

public class SubscriptionImpl extends ConnectorAPIOperationRunner {

    private static final Log logger = Log.getLog(SubscriptionImpl.class);

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

    public Subscription subscribe(final ObjectClass objectClass, final Filter eventFilter,
            final Observer<ConnectorObject> handler, final OperationOptions operationOptions) {
        final ConnectorEventSubscriptionOp operation =
                ((ConnectorEventSubscriptionOp) getConnector());
        final InternalObserver<ConnectorObject> observer =
                new InternalObserver<ConnectorObject>(handler);
        try {
            referenceCounter.acquire();
            final Subscription subscription =
                    operation.subscribe(objectClass, eventFilter, observer, operationOptions);

            return new Subscription() {
                public void unsubscribe() {
                    if (observer.doRelease()) {
                        subscription.unsubscribe();
                    }
                }

                public boolean isUnsubscribed() {
                    return subscription.isUnsubscribed();
                }
            };
        } catch (Throwable t) {
            observer.onError(t);
            throw ConnectorException.wrap(t);
        }
    }

    public Subscription subscribe(final ObjectClass objectClass, final SyncToken token,
            final Observer<SyncDelta> handler, final OperationOptions operationOptions) {
        final SyncEventSubscriptionOp operation = ((SyncEventSubscriptionOp) getConnector());
        final InternalObserver<SyncDelta> observer =
                new InternalObserver<SyncDelta>(handler);
        try {
            referenceCounter.acquire();
            final Subscription subscription =
                    operation.subscribe(objectClass, token, observer, operationOptions);

            return new Subscription() {
                public void unsubscribe() {
                    if (observer.doRelease()) {
                        subscription.unsubscribe();
                    }
                }

                public boolean isUnsubscribed() {
                    return observer.isUnsubscribed() && subscription.isUnsubscribed();
                }
            };
        } catch (Throwable t) {
            observer.onError(t);
            throw ConnectorException.wrap(t);
        }
    }

    private class InternalObserver<T> implements Observer<T> {
        private final Observer<T> delegate;
        private final AtomicBoolean subscribed = new AtomicBoolean(Boolean.TRUE);

        public InternalObserver(Observer<T> delegate) {
            this.delegate = delegate;
        }

        public void onCompleted() {
            if (doRelease()) {
                delegate.onCompleted();
            }
        }

        public void onError(Throwable e) {
            if (doRelease()) {
                delegate.onError(e);
            }
        }

        public void onNext(final T connectorObject) {
            try {
                if (subscribed.get()) {
                    delegate.onNext(connectorObject);
                }
            } catch (Throwable t) {
                onError(t);
            }
        }

        private boolean doRelease() {
            if (subscribed.compareAndSet(Boolean.TRUE, Boolean.FALSE)) {
                referenceCounter.release();
                return true;
            }
            return false;
        }

        private boolean isUnsubscribed() {
            return !subscribed.get();
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
