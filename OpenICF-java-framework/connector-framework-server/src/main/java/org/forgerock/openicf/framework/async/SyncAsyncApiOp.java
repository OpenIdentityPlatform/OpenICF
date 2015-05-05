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

package org.forgerock.openicf.framework.async;

import org.forgerock.util.promise.Promise;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;

/**
 * {@inheritDoc}
 *
 * @since 1.5
 */
public interface SyncAsyncApiOp extends SyncApiOp {

    /**
     * Request synchronization events--i.e., native changes to target objects.
     * <p>
     * This method will call the specified
     * {@linkplain org.identityconnectors.framework.common.objects.SyncResultsHandler#handle
     * handler} once to pass back each matching
     * {@linkplain org.identityconnectors.framework.common.objects.SyncDelta
     * synchronization event}. Once this method returns, this method will no
     * longer invoke the specified handler.
     * <p>
     * Each
     * {@linkplain org.identityconnectors.framework.common.objects.SyncDelta#getToken()
     * synchronization event contains a token} that can be used to resume
     * reading events <i>starting from that point in the event stream</i>. In
     * typical usage, a client will save the token from the final
     * synchronization event that was received from one invocation of this
     * {@code sync()} method and then pass that token into that client's next
     * call to this {@code sync()} method. This allows a client to
     * "pick up where he left off" in receiving synchronization events. However,
     * a client can pass the token from <i>any</i> synchronization event into a
     * subsequent invocation of this {@code sync()} method. This will return
     * synchronization events (that represent native changes that occurred)
     * immediately subsequent to the event from which the client obtained the
     * token.
     * <p>
     * A client that wants to read synchronization events "starting now" can
     * call {@link #getLatestSyncToken} and then pass that token into this
     * {@code sync()} method.
     *
     * @param objectClass
     *            The class of object for which to return synchronization
     *            events. Must not be null.
     * @param token
     *            The token representing the last token from the previous sync.
     *            The {@code SyncResultsHandler} will return any number of
     *            {@linkplain org.identityconnectors.framework.common.objects.SyncDelta}
     *            objects, each of which contains a token. Should be
     *            {@code null} if this is the client's first call to the
     *            {@code sync()} method for this connector.
     * @param handler
     *            The result handler. Must not be null.
     * @param options
     *            Options that affect the way this operation is run. May be
     *            null.
     * @return The sync token or {@code null}.
     * @throws IllegalArgumentException
     *             if {@code objectClass} or {@code handler} is null or if any
     *             argument is invalid.
     */
    public Promise<SyncToken, RuntimeException> syncAsync(ObjectClass objectClass, SyncToken token,
            SyncResultsHandler handler, OperationOptions options);

    /**
     * Returns the token corresponding to the most recent synchronization event
     * for any instance of the specified object class.
     * <p>
     * An application that wants to receive synchronization events
     * "starting now" --i.e., wants to receive only native changes that occur
     * after this method is called-- should call this method and then pass the
     * resulting token into {@linkplain #sync the sync() method}.
     *
     * @param objectClass
     *            the class of object for which to find the most recent
     *            synchronization event (if any).
     * @return A token if synchronization events exist; otherwise {@code null}.
     */
    public Promise<SyncToken, RuntimeException> getLatestSyncTokenAsync(ObjectClass objectClass);
}
