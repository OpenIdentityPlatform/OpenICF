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

package org.identityconnectors.common;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 *
 * @since 1.5
 */
public abstract class AbstractAsyncResultsHandler<V, E extends RuntimeException> implements
        SuccessHandler<V>, FailureHandler<E> {

    private static interface StateListener<V, E extends RuntimeException> {
        void handleStateChange(int newState, V result, E error);
    }

    /**
     * State value indicating that this handler has not completed.
     */
    private static final int PENDING = 0;

    /**
     * State value indicating that this handler has completed successfully
     * (result set).
     */
    private static final int SUCCEEDED = 1;

    /**
     * State value indicating that this handler has failed (error set).
     */
    private static final int FAILED = 2;

    /**
     * State value indicating that this handler has been cancelled (error set).
     */
    private static final int CANCELLED = 3;

    private volatile int state = PENDING;
    private V result = null;
    private E error = null;

    private final Queue<StateListener<V, E>> listeners =
            new ConcurrentLinkedQueue<StateListener<V, E>>();

    /**
     * Signals that the asynchronous task represented by this handler has
     * succeeded. If the task has already completed (i.e.
     * {@code isDone() == true}) then calling this method has no effect and the
     * provided result will be discarded.
     *
     * @param result
     *            The result of the asynchronous task (may be {@code null}).
     * @see #tryHandleResult(Object)
     */
    public void handleResult(V result) {
        tryHandleResult(result);
    }

    /**
     * Signals that the asynchronous task represented by this handler has
     * failed. If the task has already completed (i.e. {@code isDone() == true})
     * then calling this method has no effect and the provided result will be
     * discarded.
     *
     * @param error
     *            The exception indicating why the task failed.
     * @see #tryHandleError(E)
     */
    public void handleError(E error) {
        tryHandleError(error);
    }

    /**
     * Attempts to signal that the asynchronous task represented by this handler
     * has succeeded. If the task has already completed (i.e.
     * {@code isDone() == true}) then calling this method has no effect and
     * {@code false} is returned.
     * <p>
     * This method should be used in cases where multiple threads may
     * concurrently attempt to complete a handler and need to release resources
     * if the completion attempt fails. For example, an asynchronous TCP connect
     * attempt may complete after a timeout has expired. In this case the
     * connection should be immediately closed because it is never going to be
     * used.
     *
     * @param result
     *            The result of the asynchronous task (may be {@code null}).
     * @return {@code false} if this handler has already been completed, either
     *         due to normal termination, an exception, or cancellation (i.e.
     *         {@code isDone() == true}).
     * @see #handleResult(Object)
     */
    public final boolean tryHandleResult(final V result) {
        return setState(SUCCEEDED, result, null);
    }

    /**
     * Attempts to signal that the asynchronous task represented by this handler
     * has failed. If the task has already completed (i.e.
     * {@code isDone() == true}) then calling this method has no effect and
     * {@code false} is returned.
     * <p>
     * This method should be used in cases where multiple threads may
     * concurrently attempt to complete a handler and need to release resources
     * if the completion attempt fails. For example, an asynchronous TCP connect
     * attempt may complete after a timeout has expired. In this case the
     * connection should be immediately closed because it is never going to be
     * used.
     *
     * @param error
     *            The exception indicating why the task failed.
     * @return {@code false} if this handler has already been completed, either
     *         due to normal termination, an exception, or cancellation (i.e.
     *         {@code isDone() == true}).
     * @see #handleError(E)
     */
    public final boolean tryHandleError(final E error) {
        return setState(FAILED, null, error);
    }

    public final boolean isCancelled() {
        return state == CANCELLED;
    }

    public final boolean isDone() {
        return state != PENDING;
    }

    protected final void onCancel(final SuccessHandler<Boolean> handler) {
        addOrFireListener(new StateListener<V, E>() {

            public void handleStateChange(final int newState, final V result, final E error) {
                if (newState == CANCELLED) {
                    handler.handleResult(Boolean.TRUE);
                } else {
                    handler.handleResult(Boolean.FALSE);
                }
            }
        });
    }

    protected final void onSuccess(final SuccessHandler<? super V> onSuccess) {
        addOrFireListener(new StateListener<V, E>() {
            public void handleStateChange(final int newState, final V result, final E error) {
                if (newState == SUCCEEDED) {
                    onSuccess.handleResult(result);
                }
            }
        });
    }

    protected final void onFailure(final FailureHandler<? super E> onFail) {
        addOrFireListener(new StateListener<V, E>() {
            public void handleStateChange(final int newState, final V result, final E error) {
                if (newState != SUCCEEDED) {
                    onFail.handleError(error);
                }
            }
        });
    }

    private boolean setState(final int newState, final V result, final E error) {
        synchronized (this) {
            if (state != PENDING) {
                // Already completed.
                return false;
            }
            this.result = result;
            this.error = error;
            state = newState;
        }
        StateListener<V, E> listener;
        while ((listener = listeners.poll()) != null) {
            listener.handleStateChange(newState, result, error);
        }
        return true;
    }

    private void addOrFireListener(final StateListener<V, E> listener) {
        final int stateBefore = state;
        if (stateBefore != PENDING) {
            listener.handleStateChange(stateBefore, result, error);
        } else {
            listeners.add(listener);
            final int stateAfter = state;
            if (stateAfter != PENDING && listeners.remove(listener)) {
                listener.handleStateChange(stateAfter, result, error);
            }
        }
    }
}
