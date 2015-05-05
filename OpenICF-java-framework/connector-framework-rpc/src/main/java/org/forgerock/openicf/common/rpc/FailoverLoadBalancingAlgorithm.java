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
 * A fail-over load balancing algorithm provides fault tolerance across multiple
 * underlying {@link RequestDistributor}s.
 * <p>
 * This algorithm is typically used for load-balancing <i>between</i> data
 * centers, where there is preference to always forward connection requests to
 * the <i>closest available</i> data center. This algorithm contrasts with the
 * {@link RoundRobinLoadBalancingAlgorithm} which is used for load-balancing
 * <i>within</i> a data center.
 * <p>
 * This algorithm selects {@link RequestDistributor}s based on the order in
 * which they were provided during construction. More specifically, an attempt
 * to obtain a {@link RequestDistributor} will always return the <i>first
 * operational</i> {@link RequestDistributor} in the list. Applications should,
 * therefore, organize the connection factories such that the <i>preferred</i>
 * (usually the closest) {@link RequestDistributor} appear before those which
 * are less preferred.
 * <p>
 * If a problem occurs that temporarily prevents connections from being obtained
 * for one of the {@link RequestDistributor}, then this algorithm automatically
 * "fails over" to the next operational {@link RequestDistributor} in the list.
 * If none of the {@link RequestDistributor} are operational then a {@code null}
 * is returned to the client.
 * <p>
 *
 * @see RoundRobinLoadBalancingAlgorithm
 */
public class FailoverLoadBalancingAlgorithm<M, G extends RemoteConnectionGroup<M, G, H, P>, H extends RemoteConnectionHolder<M, G, H, P>, P extends RemoteConnectionContext<M, G, H, P>>
        extends AbstractLoadBalancingAlgorithm<M, G, H, P> {

    public FailoverLoadBalancingAlgorithm(
            final List<RequestDistributor<M, G, H, P>> requestDistributors) {
        super(requestDistributors);
    }

    protected int getInitialConnectionFactoryIndex() {
        return 0;
    }
}
