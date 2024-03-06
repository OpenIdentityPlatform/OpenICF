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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A round robin load balancing algorithm distributes
 * {@link org.forgerock.openicf.common.rpc.RemoteRequest}s across a list of
 * {@link RequestDistributor}s one at a time. When the end of the list is
 * reached, the algorithm starts again from the beginning.
 * <p>
 * This algorithm is typically used for load-balancing <i>within</i> data
 * centers, where load must be distributed equally across multiple servers. This
 * algorithm contrasts with the {@link FailoverLoadBalancingAlgorithm} which is
 * used for load-balancing <i>between</i> data centers.
 * <p>
 * If a problem occurs that temporarily prevents connections from being obtained
 * for one of the {@link RequestDistributor}s, then this algorithm automatically
 * "fails over" to the next operational {@link RequestDistributor} in the list.
 * If none of the {@link RequestDistributor} are operational then a {@code null}
 * is returned to the client.
 * <p>
 *
 * @see FailoverLoadBalancingAlgorithm
 */
public class RoundRobinLoadBalancingAlgorithm<G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>>
        extends AbstractLoadBalancingAlgorithm<G, H, P> {

    private final AtomicInteger counter = new AtomicInteger(0);

    public RoundRobinLoadBalancingAlgorithm(
            final List<RequestDistributor<G, H, P>> requestDistributors) {
        super(requestDistributors);
    }

    protected int getInitialConnectionFactoryIndex() {
        // A round robin pool of one connection factories is unlikely in
        // practice and requires special treatment.
        int maxSize = requestDistributors.size();
        if (maxSize == 1) {
            return 0;
        }
        return (counter.getAndIncrement() & 0x7fffffff) % maxSize;
    }
}
