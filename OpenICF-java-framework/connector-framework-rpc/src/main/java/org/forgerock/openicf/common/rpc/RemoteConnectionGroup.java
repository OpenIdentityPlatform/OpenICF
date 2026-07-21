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
 *
 * Portions Copyrighted 2026 3A Systems, LLC
 */

package org.forgerock.openicf.common.rpc;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.util.Pair;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * A RemoteConnectionGroups represent a remote pair of another instance of
 * RemoteConnectionGroups.
 * <p>
 * RemoteConnectionGroups holds the
 * {@link org.forgerock.openicf.common.rpc.RemoteConnectionHolder} which are
 * connected to the remotely paired RemoteConnectionGroups.
 * <p>
 * The local instance of {@link RemoteConnectionGroup#remoteRequests} paired
 * with the remote instance of {@link RemoteConnectionGroup#localRequests}.
 *
 */
public abstract class RemoteConnectionGroup<G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>>
        implements RequestDistributor<G, H, P> {

    protected final String remoteSessionId;

    protected final ConcurrentNavigableMap<Long, RemoteRequest<?, ?, G, H, P>> remoteRequests =
            new ConcurrentSkipListMap<Long, RemoteRequest<?, ?, G, H, P>>();

    protected final ConcurrentNavigableMap<Long, LocalRequest<?, ?, G, H, P>> localRequests =
            new ConcurrentSkipListMap<Long, LocalRequest<?, ?, G, H, P>>();

    /**
     * Cancel messages that arrived before the addressed operation registered
     * itself in {@link #localRequests} are parked here (request id to expiry
     * timestamp) and applied when {@link #receiveRequest} registers the
     * request. Request ids are never reused within a group, so a parked
     * cancel can only ever match the operation it was sent for; entries whose
     * operation never arrives are dropped after {@link #PENDING_CANCEL_TTL_MS}.
     * The park in {@link #receiveRequestCancel} is unconditional, so a cancel
     * that loses the race with completion (the operation already produced its
     * result and unregistered) also leaves an entry behind for the full TTL;
     * such entries are harmless and are removed by the next purge.
     */
    private final ConcurrentMap<Long, Long> pendingCancels =
            new ConcurrentHashMap<Long, Long>();

    private static final long PENDING_CANCEL_TTL_MS = 60L * 1000L;

    protected final CopyOnWriteArrayList<Pair<String, H>> webSockets =
            new CopyOnWriteArrayList<Pair<String, H>>();

    private final AtomicLong messageId = new AtomicLong(0);

    private final AtomicInteger nextIndex = new AtomicInteger(-1);

    public RemoteConnectionGroup(final String remoteSessionId) {
        this.remoteSessionId = remoteSessionId;
    }

    public String getRemoteSessionId() {
        return remoteSessionId;
    }

    protected abstract P getRemoteConnectionContext();

    protected int getInitialConnectionFactoryIndex() {
        if (webSockets.size() == 1) {
            return 0;
        }
        int oldNextIndex;
        int newNextIndex;
        do {
            oldNextIndex = nextIndex.get();
            newNextIndex = oldNextIndex + 1;
            if (newNextIndex >= webSockets.size()) {
                newNextIndex = 0;
            }
        } while (!nextIndex.compareAndSet(oldNextIndex, newNextIndex));
        return newNextIndex;
    }

    public <V> V trySendMessage(Function<H, V, Exception> function) {
        H connectionInfo;
        ArrayList<H> failed = null;
        int p = getInitialConnectionFactoryIndex();
        int index = p;

        V result = null;
        do {
            if (!webSockets.isEmpty()) {
                // Reset the loop if we reached the end
                if (index >= webSockets.size()) {
                    index = 0;
                }
                try {
                    connectionInfo = webSockets.get(index).getSecond();
                    if (null != failed) {
                        if (failed.size() >= webSockets.size()) {
                            // Assume we have tried all
                            break;
                        } else if (failed.contains(connectionInfo)) {
                            // Try next
                            index++;
                            continue;
                        }
                    }

                    try {
                        result = function.apply(connectionInfo);
                        // If successful break the loop and return
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) {
                            return null;
                        }
                        if (null == failed) {
                            failed = new ArrayList<H>(webSockets.size());
                        }
                        failed.add(connectionInfo);
                    }

                } catch (IndexOutOfBoundsException e) {
                    // We have looped and reached the end of array
                    if (index < p || p == 0) {
                        break;
                    }
                    index = 0;
                }
                index++;
            } else {
                break;
            }
        } while (null == result);

        return result;
    }

    protected long getNextRequestId() {
        long next = messageId.incrementAndGet();
        while (next == 0) {
            next = messageId.incrementAndGet();
        }
        return next;
    }

    protected <R extends RemoteRequest<V, E, G, H, P>, V, E extends Exception> R allocateRequest(
            RemoteRequestFactory<R, V, E, G, H, P> requestFactory) {
        long messageId;
        R request;
        do {
            messageId = getNextRequestId();
            request =
                    requestFactory.createRemoteRequest(getRemoteConnectionContext(), messageId,
                            new RemoteRequestFactory.CompletionCallback<V, E, G, H, P>() {
                                public void complete(RemoteRequest<V, E, G, H, P> request) {
                                    remoteRequests.remove(request.getRequestId());
                                }
                            });
            if (null == request) {
                break;
            }
        } while (null != remoteRequests.putIfAbsent(messageId, request));
        return request;
    }

    // -- Pair of methods to Submit and Receive the new Request Start --

    public <R extends RemoteRequest<V, E, G, H, P>, V, E extends Exception> R trySubmitRequest(
            RemoteRequestFactory<R, V, E, G, H, P> requestFactory) {

        final R remoteRequest = allocateRequest(requestFactory);
        if (null != remoteRequest) {
            Promise<V, E> result = trySendMessage(remoteRequest.getSendFunction());
            if (null == result) {
                remoteRequests.remove(remoteRequest.getRequestId());
                return null;
            }
        }
        return remoteRequest;
    }

    /**
     * Registers the given request so responses and cancel messages can be
     * dispatched to it.
     *
     * @return the registered request, or {@code null} when a cancel for its
     *         request id arrived before registration - the request is
     *         cancelled and must not be executed.
     */
    public <V, E extends Exception, R extends LocalRequest<V, E, G, H, P>> R receiveRequest(
            final R localRequest) {
        purgeExpiredPendingCancels();
        LocalRequest<?, ?, G, H, P> tmp =
                localRequests.putIfAbsent(localRequest.getRequestId(), localRequest);
        if (null != tmp && !tmp.equals(localRequest)) {
            throw new IllegalStateException("Request has been registered with id: "
                    + localRequest.getRequestId());
        }
        // Registration and receiveRequestCancel write the two maps in
        // opposite order, so whichever call runs second is guaranteed to see
        // the other's entry and deliver the cancel. The verdict must come
        // from the request's own state rather than from winning the park
        // removal: receiveRequestCancel may find the request in localRequests
        // right after the putIfAbsent above, consume its own park and cancel
        // the request directly - this thread then observes no parked entry.
        if (null != pendingCancels.remove(localRequest.getRequestId())) {
            localRequest.cancel();
        }
        if (localRequest.isCancelled()) {
            // cancel() removes the registration, but only when the cancelling
            // side found it - make sure a request reported dead is never left
            // registered.
            localRequests.remove(localRequest.getRequestId(), localRequest);
            return null;
        }
        return localRequest;
    }

    // -- Pair of methods to Submit and Receive the new Request End --

    // -- Pair of methods to Cancel pending Request Start --

    public RemoteRequest<?, ?, G, H, P> submitRequestCancel(final long messageId) {
        RemoteRequest<?, ?, G, H, P> tmp = remoteRequests.remove(messageId);
        if (null != tmp) {
            tmp.getPromise().cancel(true);
        }
        return tmp;
    }

    public LocalRequest<?, ?, G, H, P> receiveRequestCancel(long messageId) {
        purgeExpiredPendingCancels();
        // Park first, then look up: the operation may be racing through
        // registration on another thread, and this order guarantees that at
        // least one side observes the other (see receiveRequest).
        pendingCancels.put(messageId, System.currentTimeMillis() + PENDING_CANCEL_TTL_MS);
        LocalRequest<?, ?, G, H, P> tmp = localRequests.remove(messageId);
        if (null != tmp) {
            pendingCancels.remove(messageId);
            tmp.cancel();
        }
        return tmp;
    }

    private void purgeExpiredPendingCancels() {
        if (pendingCancels.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : pendingCancels.entrySet()) {
            if (entry.getValue() < now) {
                pendingCancels.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    // -- Pair of methods to Cancel pending Request End --

    // -- Pair of methods to Communicate pending Request Start --

    public RemoteRequest<?, ?, G, H, P> receiveRequestResponse(final H sourceConnection,
            final long messageId, Object message) {
        RemoteRequest<?, ?, G, H, P> tmp = remoteRequests.get(messageId);
        if (null != tmp) {
            tmp.handleIncomingMessage(sourceConnection, message);
        }
        return tmp;
    }

    public LocalRequest<?, ?, G, H, P> receiveRequestUpdate(final H sourceConnection,
            long messageId, Object message) {
        LocalRequest<?, ?, G, H, P> tmp = localRequests.get(messageId);
        if (null != tmp) {
            tmp.handleIncomingMessage(sourceConnection, message);
        }
        return tmp;
    }

    // -- Pair of methods to Communicate pending Request End --

    public LocalRequest<?, ?, G, H, P> removeRequest(long messageId) {
        return localRequests.remove(messageId);
    }

}
