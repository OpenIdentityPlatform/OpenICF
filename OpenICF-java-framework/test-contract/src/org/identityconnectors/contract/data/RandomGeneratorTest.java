/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.contract.data;

import junit.framework.Assert;

import org.junit.Test;

/**
 * JUnit test class for RandomGenerator
 * 
 * @author David Adam
 * 
 */
public class RandomGeneratorTest {

    @Test
    public void testRandomLongGenerator() {
        {
            Object o = RandomGenerator.generate("#####", Long.class);
            Assert.assertNotNull(o);
            Assert.assertTrue(o instanceof Long);
            System.out.println(o.toString());
        }

    }

    @Test
    public void testRgen2() {
        {
            Object o = RandomGenerator.generate("###X##");
            Assert.assertNotNull(o);
            Assert.assertTrue(o.toString().contains("X"));
            System.out.println(o.toString());
        }
    }

    @Test
    public void testRgen3() {
        {
            Object o = RandomGenerator.generate("###\\.##", Float.class); // this means
                                                                // ###\.##
            Assert.assertNotNull(o);
            Assert.assertTrue(o instanceof Float);
            Assert.assertTrue(o.toString().contains("."));
            System.out.println(o.toString());
        }
    }
}
