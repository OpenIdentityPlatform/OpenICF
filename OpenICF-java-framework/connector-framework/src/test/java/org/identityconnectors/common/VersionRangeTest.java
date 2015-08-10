/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
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

import org.identityconnectors.framework.api.ConnectorKey;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VersionRangeTest {

    @Test
    public void testIsInRange() throws Exception {
        Version reference0 = new Version(1, 1, 0, 0);
        Version reference1 = new Version(1, 1, 0, 1);
        Version reference2 = new Version(1, 1, 0, 2);
        Version reference3 = new Version(1, 1, 0, 3);
        Version reference4 = new Version(1, 1, 0, 4);
        VersionRange range = VersionRange.parse("[1.1.0.1,1.1.0.3)");

        Assert.assertFalse(range.isInRange(reference0));
        Assert.assertTrue(range.isInRange(reference1));
        Assert.assertTrue(range.isInRange(reference2));
        Assert.assertFalse(range.isInRange(reference3));
        Assert.assertFalse(range.isInRange(reference4));
    }

    @Test
    public void testIsExact() throws Exception {
        Assert.assertTrue(VersionRange.parse("1.1.0.0").isExact());
        Assert.assertTrue(VersionRange.parse("  [  1 , 1 ]  ").isExact());
        Assert.assertTrue(VersionRange.parse("[  1.1 , 1.1 ]").isExact());
        Assert.assertTrue(VersionRange.parse("  [1.1.1 , 1.1.1]  ").isExact());
        Assert.assertTrue(VersionRange.parse("[1.1.0.0,1.1.0.0]").isExact());
        Assert.assertTrue(VersionRange.parse("(1.1.0.0,1.1.0.2)").isExact());
    }

    @Test
    public void testIsEmpty() throws Exception {
        Assert.assertTrue(VersionRange.parse("(1.1.0.0,1.1.0.0)").isEmpty());
        Assert.assertTrue(VersionRange.parse("(1.2.0.0,1.1.0.0]").isEmpty());
    }

    @Test
    public void testValidSyntax() throws Exception {
        try {
            VersionRange.parse("(1.1.0.0)");
            Assert.fail("Invalid syntax not failed");
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            VersionRange.parse("1.1.0.0,1.1)]");
            Assert.fail("Invalid syntax not failed");
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            VersionRange.parse("(1.1.0.0-1.1)");
            Assert.fail("Invalid syntax not failed");
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            VersionRange.parse("1.1.0.0,1.1");
            Assert.fail("Invalid syntax not failed");
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            VersionRange.parse("( , 1.1)");
            Assert.fail("Invalid syntax not failed");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Test
    public void testIsEqual() throws Exception {
        VersionRange range1 = VersionRange.parse("[1.1.0.1,1.1.0.3)");
        VersionRange range2 = VersionRange.parse(range1.toString());
        Assert.assertTrue(range1.equals(range2));
    }

    @Test
    public void testConnectorKeysInRange() throws Exception {

        ConnectorKeyRange rS1 =
                ConnectorKeyRange.newBuilder().setBundleName("B").setConnectorName("C")
                        .setBundleVersion("1.1.0.0-SNAPSHOT").build();
        ConnectorKeyRange r1 =
                ConnectorKeyRange.newBuilder().setBundleName("B").setConnectorName("C")
                        .setBundleVersion("1.1.0.0-SNAPSHOT").build();

        ConnectorKeyRange rS2 =
                ConnectorKeyRange.newBuilder().setBundleName("B").setConnectorName("C")
                        .setBundleVersion("[1.1.0.0,1.2-SNAPSHOT]").build();
        ConnectorKeyRange r2 =
                ConnectorKeyRange.newBuilder().setBundleName("B").setConnectorName("C")
                        .setBundleVersion("[1.1.0.0,1.2]").build();

        ConnectorKey kS1 = new ConnectorKey("B","1.1.0.0-SNAPSHOT","C");
        ConnectorKey k1 = new ConnectorKey("B","1.1.0.0","C");
        ConnectorKey kS2 = new ConnectorKey("B","1.2.0.0-SNAPSHOT","C");
        ConnectorKey k2 = new ConnectorKey("B","1.2.0.0","C");

        
        Assert.assertTrue(rS1.getBundleVersionRange().isExact());
        Assert.assertTrue(r1.getBundleVersionRange().isExact());
        Assert.assertFalse(rS2.getBundleVersionRange().isExact());
        Assert.assertFalse(r2.getBundleVersionRange().isExact());
        
        
        Assert.assertTrue(rS1.isInRange(kS1));
        Assert.assertTrue(rS1.isInRange(k1));
        Assert.assertTrue(r1.isInRange(kS1));
        Assert.assertTrue(r1.isInRange(k1));
        
        //

        Assert.assertFalse(rS1.isInRange(kS2));
        Assert.assertFalse(rS1.isInRange(k2));
        Assert.assertFalse(r1.isInRange(kS2));
        Assert.assertFalse(r1.isInRange(k2));

        //

        Assert.assertTrue(rS2.isInRange(kS1));
        Assert.assertTrue(rS2.isInRange(k1));
        Assert.assertTrue(r2.isInRange(kS1));
        Assert.assertTrue(r2.isInRange(k1));

        Assert.assertTrue(rS2.isInRange(kS2));
        Assert.assertTrue(rS2.isInRange(k2));
        Assert.assertTrue(r2.isInRange(kS2));
        Assert.assertTrue(r2.isInRange(k2));
    }

}
