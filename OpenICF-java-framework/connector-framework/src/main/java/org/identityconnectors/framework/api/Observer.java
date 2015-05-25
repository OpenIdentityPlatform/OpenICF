package org.identityconnectors.framework.api;

import org.identityconnectors.common.FailureHandler;
import org.identityconnectors.common.SuccessHandler;

/**
 * Provides a mechanism for receiving push-based notifications.
 * <p>
 * After an Observer calls {@link org.identityconnectors.framework.api.operations.SyncEventSubscriptionApiOp#subscribe subscribe} or {@link org.identityconnectors.framework.api.operations.ConnectorEventSubscriptionApiOp#subscribe subscribe} method, the
 * {@code Observable} calls the Observer's {@link #onNext} method to provide notifications. A well-behaved
 * {@code Observable} will call an Observer's {@link #onCompleted} method exactly once or the Observer's
 * {@link #onError} method exactly once.
 *
 * @see <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation: Observable</a>
 * @param <T>
 *          the type of item the Observer expects to observe
 */
public interface Observer<T> /*extends SuccessHandler<T>, FailureHandler<RuntimeException>*/ {

    /**
     * Notifies the Observer that the Observable has finished sending push-based notifications.
     * <p>
     * The Observable will not call this method if it calls {@link #onError}.
     */
    void onCompleted();

    /**
     * Notifies the Observer that the Observable has experienced an error condition.
     * <p>
     * If the Observable calls this method, it will not thereafter call {@link #onNext} or
     * {@link #onCompleted}.
     *
     * @param e
     *          the exception encountered by the Observable
     */
    void onError(Throwable e);

    /**
     * Provides the Observer with a new item to observe.
     * <p>
     * The Observable may call this method 0 or more times.
     * <p>
     * The {@code Observable} will not call this method again after it calls either {@link #onCompleted} or
     * {@link #onError}.
     *
     * @param t
     *          the item emitted by the Observable
     */
    void onNext(T t);

}