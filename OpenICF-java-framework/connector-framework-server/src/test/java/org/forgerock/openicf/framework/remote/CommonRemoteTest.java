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

package org.forgerock.openicf.framework.remote;

import org.forgerock.openicf.framework.client.ClientRemoteConnectorInfoManager.Statistic;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CommonRemoteTest {

    @Test
    public void testStatistic() throws Exception {
        Statistic testable = new Statistic();
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(10);
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(15);
        Assert.assertEquals(testable.touch(), Statistic.Action.INCREMENT);
        testable.measure(20);
        Assert.assertEquals(testable.touch(), Statistic.Action.INCREMENT);
        testable.measure(20);
        Assert.assertEquals(testable.touch(), Statistic.Action.INCREMENT);
        testable.measure(30);
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(30);
        Assert.assertEquals(testable.touch(), Statistic.Action.INCREMENT);
        testable.measure(30);
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(20);
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(30);
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(25);
        Assert.assertEquals(testable.touch(), Statistic.Action.NEUTRAL);
        testable.measure(20);
        Assert.assertEquals(testable.touch(), Statistic.Action.DECREMENT);

        testable.measure(Long.MAX_VALUE);
        testable.measure(1L);
        testable.measure(2L);
    }

}
