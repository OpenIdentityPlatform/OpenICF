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

package org.forgerock.openicf.common.rpc;

/**
 * A RemoteRequestFactory creates a new
 * {@link org.forgerock.openicf.common.rpc.RemoteConnectionContext} aware
 * {@link org.forgerock.openicf.common.rpc.RemoteRequest} before sending in
 * {@link org.forgerock.openicf.common.rpc.RemoteConnectionGroup}.
 */
public interface RemoteRequestFactory<M, R extends RemoteRequest<M, V, E, G, H, P>, V, E extends Exception, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>> {

    public interface CompletionCallback<M, V, E extends Exception, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>> {

        void complete(RemoteRequest<M, V, E, G, H, P> request);

    }

    public R createRemoteRequest(final P context, final long requestId,
            final CompletionCallback<M, V, E, G, H, P> completionCallback);

}
