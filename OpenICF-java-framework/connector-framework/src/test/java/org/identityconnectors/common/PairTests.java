/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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
package org.identityconnectors.common;

import org.testng.annotations.Test;
import org.testng.AssertJUnit;
import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.Pair;


/**
 * Tests the pair object.
 */
public class PairTests {

    @Test
    public void equals() {
        Pair<String, String> a = new Pair<String, String>("a", "b");
        Pair<String, String> b = new Pair<String, String>("a", "b");
        AssertJUnit.assertTrue(a.equals(b));
        AssertJUnit.assertFalse(a.equals(null));
        AssertJUnit.assertFalse(b.equals(null));
        AssertJUnit.assertFalse(a.equals("f"));
    }

    @Test
    public void hash() {
        Set<Pair<Integer, Integer>> set = new HashSet<Pair<Integer,Integer>>();
        for (int i=0; i<20; i++) {
            Pair<Integer, Integer> pair = new Pair<Integer, Integer>(i, i+1);
            Pair<Integer, Integer> tst = new Pair<Integer, Integer>(i, i+1);
            set.add(pair);
            AssertJUnit.assertTrue(set.contains(tst));
        }
        // check that each pair is unique..
        AssertJUnit.assertEquals(20, set.size());
    }
}
