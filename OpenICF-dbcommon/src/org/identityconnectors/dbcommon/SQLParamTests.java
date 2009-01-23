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
package org.identityconnectors.dbcommon;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The SQL util tests
 * 
 * @version $Revision 1.0$
 * @since 1.0
 */
public class SQLParamTests {

    /**
     * Test method
     */
    @Test
    public void paramCreateValue() {
        SQLParam a = new SQLParam("A", 5);
        assertEquals("A", a.getParam());
        assertEquals(5, a.getSqlType());
    }
    
    /**
     * Test method
     */
    @Test
    public void paramCreateList() {
        Object[] a = {"a","b",5};
        List<SQLParam> list = SQLParam.asList(Arrays.asList(a));
        assertEquals(3, list.size());
        assertEquals("a", list.get(0).getParam());
        assertEquals("b", list.get(1).getParam());
        assertEquals(5, list.get(2).getParam());
        assertEquals(0, list.get(0).getSqlType());
    }
}

