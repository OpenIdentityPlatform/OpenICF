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

import java.util.List;

/**
 * A AbstractLoadBalancingAlgorithm is a base class to implementing different
 * LoadBalancingAlgorithm for multiple {@link RequestDistributor}.
 *
 */
public abstract class AbstractLoadBalancingAlgorithm<G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>>
        implements RequestDistributor<G, H, P> {

    protected final List<RequestDistributor<G, H, P>> requestDistributors;

    protected AbstractLoadBalancingAlgorithm(
            final List<RequestDistributor<G, H, P>> requestDistributors) {
        this.requestDistributors = requestDistributors;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isOperational() {
        for (RequestDistributor<G, H, P> e : requestDistributors)
            if (e.isOperational())
                return true;
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public <R extends RemoteRequest<V, E, G, H, P>, V, E extends Exception> R trySubmitRequest(
            RemoteRequestFactory<R, V, E, G, H, P> requestFactory) {
        return trySubmitRequest(getInitialConnectionFactoryIndex(), requestFactory);
    }

    protected <R extends RemoteRequest<V, E, G, H, P>, V, E extends Exception> R trySubmitRequest(
            int initialIndex, RemoteRequestFactory<R, V, E, G, H, P> requestFactory) {
        int index = initialIndex;
        final int maxIndex = requestDistributors.size();
        do {
            final RequestDistributor<G, H, P> factory = requestDistributors.get(index);
            R result = factory.trySubmitRequest(requestFactory);
            if (null != result) {
                return result;
            }
            index = (index + 1) % maxIndex;
        } while (index != initialIndex);

        /*
         * All factories are offline so give up.
         */
        return null;
    }

    protected abstract int getInitialConnectionFactoryIndex();

}
