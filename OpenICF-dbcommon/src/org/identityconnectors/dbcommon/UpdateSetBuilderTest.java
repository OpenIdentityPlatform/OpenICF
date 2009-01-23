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

import java.sql.Types;

import org.junit.Test;


/**
 * Tests
 * @version $Revision 1.0$
 * @since 1.0
 */
public class UpdateSetBuilderTest {

    private static final String MYSQL_USER_COLUMN  = "User";
    private static final SQLParam VALUE = new SQLParam("name", Types.VARCHAR);

   
    /**
     * Test method for {@link org.identityconnectors.dbcommon.UpdateSetBuilder#UpdateSetBuilder()}.
     */
    @Test
    public void testUpdateSetBuilder() {
        UpdateSetBuilder actual = new UpdateSetBuilder();
        assertNotNull(actual);
        assertNotNull(actual.getParams());
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.UpdateSetBuilder#addBind(String, String, Object)}.
     */
    @Test
    public void testAddBindExpression() {
        UpdateSetBuilder actual = new UpdateSetBuilder();
        assertNotNull(actual);
        
        // do the update
        actual.addBind("test1","password(?)",new SQLParam("val1"));
        actual.addBind("test2","max(?)",new SQLParam("val2"));
        
        assertNotNull(actual.getSQL());
        assertEquals("The update string","test1 = password(?) , test2 = max(?)",actual.getSQL());
        
        assertNotNull(actual.getParams());
        assertEquals("The count",2,actual.getParams().size());                
    }
    
    
    /**
     * Test method for {@link org.identityconnectors.dbcommon.UpdateSetBuilder#getParams()}.
     */
    @Test
    public void testGetValues() {
        UpdateSetBuilder actual = new UpdateSetBuilder();
        assertNotNull(actual);

        // do the update
        actual.addBind(MYSQL_USER_COLUMN, VALUE);
        
        assertNotNull(actual.getSQL());
        assertEquals("The update string","User = ?",actual.getSQL());
        
        assertNotNull(actual.getParams());   
        assertNotNull(actual.getParams().get(0));
        assertEquals("The values",VALUE,actual.getParams().get(0));
        assertEquals("The values",Types.VARCHAR,actual.getParams().get(0).getSqlType());
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.UpdateSetBuilder#addBind(String, Object)}
     */
    @Test
    public void testAddBind() {
        UpdateSetBuilder actual = new UpdateSetBuilder();
        assertNotNull(actual);

        // do the update
        actual.addBind(MYSQL_USER_COLUMN, VALUE);

        
        assertNotNull(actual.getParams());
        assertEquals("The count",1,actual.getParams().size());        
        assertNotNull(actual.getParams().get(0));
        assertEquals("The values",VALUE,actual.getParams().get(0));
        assertEquals("The update string",MYSQL_USER_COLUMN+" = ?",actual.getSQL());
        }
}
