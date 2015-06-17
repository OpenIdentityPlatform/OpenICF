/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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

package org.identityconnectors.framework.api;

/**
 * Provides a mechanism for receiving push-based notifications.
 * <p>
 * After an Observer calls
 * {@link org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp#subscribe
 * subscribe} or
 * {@link org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp#subscribe
 * subscribe} method, the {@code Connector} calls the Observer's {@link #onNext}
 * method to provide notifications. The {@code Connector} should call an
 * Observer's {@link #onCompleted} or {@link #onError} method exactly once.
 *
 * @param <T>
 *            the type of item the Observer expects to observe
 */
public interface Observer<T> {

    /**
     * Notifies the Observer that the {@code Connector} has finished sending
     * push-based notifications.
     * <p>
     * The {@code Connector} will not call this method if it calls
     * {@link #onError}.
     */
    void onCompleted();

    /**
     * Notifies the Observer that the {@code Connector} has experienced an error
     * condition.
     * <p>
     * If the {@code Connector} calls this method, it will not thereafter call
     * {@link #onNext} or {@link #onCompleted}.
     *
     * @param e
     *            the exception encountered by the {@code Connector}
     */
    void onError(Throwable e);

    /**
     * Provides the Observer with a new item to observe.
     * <p>
     * The {@code Connector} may call this method 0 or more times.
     * <p>
     * The {@code Connector} will not call this method again after it calls
     * either {@link #onCompleted} or {@link #onError}.
     *
     * @param t
     *            the item emitted by the {@code Connector}
     */
    void onNext(T t);

}
