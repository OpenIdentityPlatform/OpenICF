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
 * A RequestDistributor delivers the
 * {@link org.forgerock.openicf.common.rpc.RemoteRequest} to the connected
 * endpoint.
 * <p/>
 * The {@link org.forgerock.openicf.common.rpc.RemoteRequestFactory} is used to
 * create a {@link org.forgerock.openicf.common.rpc.RemoteConnectionContext}
 * aware {@link org.forgerock.openicf.common.rpc.RemoteRequest} which will be
 * delivered.
 * <p/>
 * The implementation may hold multiple transmission channels and try all to
 * deliver the message before if fails.
 * <p/>
 * The failed delivery signaled with null empty to avoid the expensive Throw and
 * Catch especially when many implementation are chained together.
 * 
 * @see org.forgerock.openicf.common.rpc.FailoverLoadBalancingAlgorithm
 * @see org.forgerock.openicf.common.rpc.RoundRobinLoadBalancingAlgorithm
 * @see org.forgerock.openicf.common.rpc.RemoteConnectionGroup
 */
public interface RequestDistributor<M, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>> {

    /**
     * 
     * @param requestFactory
     *            the factory to create the
     *            {@link org.forgerock.openicf.common.rpc.RemoteRequest}
     * @param <R>
     *            type of {@link org.forgerock.openicf.common.rpc.RemoteRequest}
     * @param <V>
     *            The type of the task's result, or {@link Void} if the task
     *            does not return anything (i.e. it only has side-effects).
     * @param <E>
     *            The type of the exception thrown by the task if it fails, or
     *            {@link org.forgerock.util.promise.NeverThrowsException} if the
     *            task cannot fail.
     * @return new promise if succeeded otherwise {@code null}.
     */
    <R extends RemoteRequest<M, V, E, G, H, P>, V, E extends Exception> R trySubmitRequest(
            RemoteRequestFactory<M, R, V, E, G, H, P> requestFactory);

    /**
     * Check if this implementation is operational.
     * 
     * @return {@code true} is operational and ready the submit
     *         {@link org.forgerock.openicf.common.rpc.RemoteRequestFactory}
     *         otherwise {@code false}.
     */
    boolean isOperational();

}
