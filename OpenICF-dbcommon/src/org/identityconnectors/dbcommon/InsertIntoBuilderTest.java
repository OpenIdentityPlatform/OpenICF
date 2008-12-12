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
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Tests
 * 
 * @version $Revision 1.0$
 * @since 1.0
 */
public class InsertIntoBuilderTest {
    /**
     * Test method for {@link org.identityconnectors.dbcommon.InsertIntoBuilder#InsertIntoBuilder()}.
     */
    @Test
    public void testInsertIntoBuilder() {
        InsertIntoBuilder actual = new InsertIntoBuilder();
        assertNotNull(actual);
        assertNotNull(actual.getParams());
        assertNotNull(actual.getValues());
        assertNotNull(actual.getInto());
        assertEquals("", actual.getValues());
        assertEquals("", actual.getInto());
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.InsertIntoBuilder#addBind(String, Object)}.
     */
    @Test
    public void testAddBindExpression() {
        InsertIntoBuilder actual = new InsertIntoBuilder();
        assertNotNull(actual);

        // do the update
        actual.addBind("test1", "val1");

        assertNotNull(actual.getInto());
        assertEquals("The update string", "test1", actual.getInto());

        assertNotNull(actual.getValues());
        assertEquals("The update string", "?", actual.getValues());

        assertNotNull(actual.getParams());
        assertEquals("The count", 1, actual.getParams().size());
        assertEquals("The val", "val1", actual.getParams().get(0));
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.InsertIntoBuilder#addBind(String, Object)}.
     */
    @Test
    public void testAddSecondBindExpression() {
        InsertIntoBuilder actual = new InsertIntoBuilder();
        assertNotNull(actual);

        // do the update
        actual.addBind("test1", "val1");
        actual.addBind("test2", "val2");

        assertNotNull(actual.getInto());
        assertEquals("The update string", "test1, test2", actual.getInto());

        assertNotNull(actual.getValues());
        assertEquals("The update string", "?, ?", actual.getValues());

        assertNotNull(actual.getParams());
        assertEquals("The count", 2, actual.getParams().size());
        assertEquals("The val", "val1", actual.getParams().get(0));
        assertEquals("The val", "val2", actual.getParams().get(1));
    }
}
