/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.dbcommon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.identityconnectors.dbcommon.DatabaseQueryBuilder.OrderBy;
import org.junit.Test;

/**
 * DatabaseQueryBuilder test Class
 * @version $Revision 1.0$
 * @since 1.0
 */
public class DatabaseQueryBuilderTest {

    private static final String SELECT = "SELECT * FROM Users";
    private static final String SELECT_WITH_WHERE = "SELECT * FROM Users WHERE test = 1";
    private static final String VALUE = "value";
    private static final String NAME = "name";
    private static final String OPERATOR = "=";


    /**
     * Test method for {@link DatabaseQueryBuilder#DatabaseQueryBuilder(String, Set)}.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFilterQueryBuilderTableMissing() {
        new DatabaseQueryBuilder("", null).getSQL();
    }
    
    /**
     * Test method for {@link DatabaseQueryBuilder#DatabaseQueryBuilder(String, Set)}.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFilterQueryBuilderColumnMissing() {
        new DatabaseQueryBuilder("table", null).getSQL();
    }
    
    /**
     * Test method for {@link DatabaseQueryBuilder#DatabaseQueryBuilder(String, Set)}.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFilterQueryBuilderColumnEmpty() {
        new DatabaseQueryBuilder("table", new HashSet<String>()).getSQL();
    }    
    /**
     * Test method for {@link DatabaseQueryBuilder#DatabaseQueryBuilder(String)}.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFilterQueryBuilderSelectMissing() {
        new DatabaseQueryBuilder("").getSQL();
    }
    
    
    /**
     * Test method for {@link DatabaseQueryBuilder#DatabaseQueryBuilder(String)}.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFilterQueryBuilderWhereMissing() {
        new DatabaseQueryBuilder(SELECT.substring(0, 7), null).getSQL();
    }
    
    /**
     * Test method for {@link DatabaseQueryBuilder#DatabaseQueryBuilder(String)}.
     */
    @Test
    public void testFilterQueryBuilder() {
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder(SELECT);
        assertNotNull(actual);
        assertNotNull(actual.getSQL());
        assertEquals(SELECT, actual.getSQL());
    }

    /**
     * Test method for {@link DatabaseQueryBuilder#getSQL()}.
     */
    @Test
    public void testGetSql() {
        FilterWhereBuilder where = new FilterWhereBuilder();
        where.addBind(NAME, OPERATOR, VALUE);
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder(SELECT);
        actual.setWhere(where);
        assertNotNull(actual);
        assertEquals(SELECT + " WHERE name = ?", actual.getSQL());
        assertEquals("not one value for binding", 1, actual.getParams().size());
        assertEquals("value for binding", VALUE, actual.getParams().get(0));
    }

    /**
     * Test method for {@link DatabaseQueryBuilder#getSQL()}.
     */
    @Test
    public void testGetSqlWithWhere() {
        FilterWhereBuilder where = new FilterWhereBuilder();
        where.addBind(NAME, OPERATOR, VALUE);
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder(SELECT_WITH_WHERE);
        actual.setWhere(where);        
        assertNotNull(actual);
        assertEquals(SELECT_WITH_WHERE + " AND ( name = ? )", actual.getSQL());
        assertEquals("not one value for binding", 1, actual.getParams().size());
    }
    
    /**
     * Test method for {@link DatabaseQueryBuilder#getSQL()}.
     */
    @Test
    public void testGetSqlWithEmptyWhere() {
        FilterWhereBuilder where = new FilterWhereBuilder();
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder(SELECT_WITH_WHERE);
        actual.setWhere(where);
        assertNotNull(actual);
        assertEquals(SELECT_WITH_WHERE, actual.getSQL());
    }    
    
    /**
     * Test method for {@link DatabaseQueryBuilder#getSQL()}.
     */
    @Test
    public void testGetSqlWithAttributesToGet() {
        Set<String> attributesToGet=new LinkedHashSet<String>();
        attributesToGet.add("test1");
        attributesToGet.add("test2");        
        FilterWhereBuilder where = new FilterWhereBuilder();
        where.addBind(NAME, OPERATOR, VALUE);
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder("table" , attributesToGet);
        actual.setWhere(where);
        assertEquals("SELECT test1 , test2 FROM table WHERE name = ?", actual.getSQL());
        assertEquals("not one value for binding", 1, actual.getParams().size());
        assertEquals("value for binding", VALUE, actual.getParams().get(0));
    }    
    
    /**
     * Test method for {@link DatabaseQueryBuilder#getSQL()}.
     */
    @Test
    public void testGetSqlWithAttributesToGetDifferentQuoting() {
        Set<String> attributesToGet=new LinkedHashSet<String>();
        attributesToGet.add("test1");
        attributesToGet.add("test2");        
        FilterWhereBuilder where = new FilterWhereBuilder();
        where.addBind(NAME, OPERATOR, VALUE);
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder("table" , attributesToGet);
        actual.setWhere(where);
        actual.setTableName("table");
        
        assertNotNull(actual);
        assertEquals("SELECT test1 , test2 FROM table WHERE name = ?", actual.getSQL());
        assertEquals("not one value for binding", 1, actual.getParams().size());
        assertEquals("value for binding", VALUE, actual.getParams().get(0));
    }    
    
    /**
     * Test method for {@link DatabaseQueryBuilder#getSQL()}.
     */
    @Test
    public void testGetSqlWithAttributesToGetAndOrderBy() {
        Set<String> attributesToGet=new LinkedHashSet<String>();
        ArrayList<OrderBy> orderBy = new ArrayList<OrderBy>();
        attributesToGet.add("test1");
        attributesToGet.add("test2");        
        orderBy.add(new OrderBy("test1", true));
        orderBy.add(new OrderBy("test2", false));
        FilterWhereBuilder where = new FilterWhereBuilder();
        where.addBind(NAME, OPERATOR, VALUE);
        DatabaseQueryBuilder actual = new DatabaseQueryBuilder("table" , attributesToGet);
        actual.setWhere(where);
        actual.setOrderBy(orderBy);
        assertNotNull(actual);
        assertEquals("SELECT test1 , test2 FROM table WHERE name = ? ORDER BY test1 ASC, test2 DESC", actual.getSQL());
        assertEquals("not one value for binding", 1, actual.getParams().size());
        assertEquals("value for binding", VALUE, actual.getParams().get(0));
    }    
        
}
