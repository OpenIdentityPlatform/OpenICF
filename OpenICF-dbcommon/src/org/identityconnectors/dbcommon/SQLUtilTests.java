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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.junit.Assert;
import org.junit.Test;


/**
 * The SQL util tests
 * @version $Revision 1.0$
 * @since 1.0
 */
public class SQLUtilTests {
 
    /**
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#closeQuietly(Connection)}.
     * @throws Exception
     */
    @Test
    public void quietConnectionClose() throws Exception {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("isClosed",Boolean.FALSE); 
        tp.expectAndThrow("close",new SQLException("expected")); 
        Connection c = tp.getProxy(Connection.class);
        SQLUtil.closeQuietly(c);
        Assert.assertTrue(tp.isDone());
        
        tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("isClosed",Boolean.TRUE); 
        c = tp.getProxy(Connection.class);
        SQLUtil.closeQuietly(c);
        Assert.assertTrue(tp.isDone());
    }
    
    /**
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#rollbackQuietly(Connection)}.
     * @throws Exception
     */
    @Test
    public void quietConnectionRolback() throws Exception {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("isClosed",Boolean.FALSE); 
        tp.expectAndThrow("rollback",new SQLException("expected")); 
        Connection s = tp.getProxy(Connection.class);
        SQLUtil.rollbackQuietly(s);
        Assert.assertTrue(tp.isDone());
        
        tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("isClosed",Boolean.TRUE); 
        s = tp.getProxy(Connection.class);
        SQLUtil.rollbackQuietly(s);
        Assert.assertTrue(tp.isDone());
        
    }
    

    /**
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#closeQuietly(Statement)}.
     */
    @Test
    public void quietStatementClose() {
        ExpectProxy<Statement> tp = new ExpectProxy<Statement>().expectAndThrow("close",new SQLException("expected")); 
        Statement s = tp.getProxy(Statement.class);
        SQLUtil.closeQuietly(s);
        Assert.assertTrue(tp.isDone());
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#closeQuietly(ResultSet)}.
     */
    @Test
    public void quietResultSetClose() {
        ExpectProxy<ResultSet> tp = new ExpectProxy<ResultSet>().expectAndThrow("close",new SQLException("expected")); 
        ResultSet c = tp.getProxy(ResultSet.class);
        SQLUtil.closeQuietly(c);
        Assert.assertTrue(tp.isDone());
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#closeQuietly(Connection)}.
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#closeQuietly(ResultSet)}.
     * Test method for {@link org.identityconnectors.dbcommon.SQLUtil#closeQuietly(Statement)}.
     */
    @Test
    public void withNull() {
        // attempt to close on a null..
        SQLUtil.closeQuietly((Connection)null);
        SQLUtil.closeQuietly((ResultSet)null);
        SQLUtil.closeQuietly((Statement)null);
    }
    
    /**
     * Test method
     * @throws SQLException 
     */
    @Test(expected=NullPointerException.class)
    public void testBuildConnectorObjectBuilderNull() throws SQLException {
        SQLUtil.getAttributeSet(null);
    }
    
    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertBlobToAttribute() throws SQLException {
        ExpectProxy<Blob> tb = new ExpectProxy<Blob>();
        byte[] expected = new byte[] {'a','h','o','j'};
        final ByteArrayInputStream is = new ByteArrayInputStream(expected);
        tb.expectAndReturn("getBinaryStream", is);
        Blob blob = tb.getProxy(Blob.class);
        Attribute actual = SQLUtil.convertToAttribute("test", blob);
        final Object object = AttributeUtil.getSingleValue(actual);
        assertEquals(expected[0], ((byte[]) object)[0]);
        assertEquals(expected[3], ((byte[]) object)[3]);
        assertTrue(tb.isDone());
    }

    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertSringToAttribute() throws SQLException {
        String expected = "test";
        Attribute actual = SQLUtil.convertToAttribute(expected, expected);
        final Object object = AttributeUtil.getSingleValue(actual);
        assertEquals(expected, object);
    }

    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertDateToAttribute() throws SQLException {
        Timestamp src = new Timestamp(System.currentTimeMillis());
        long expected = src.getTime();
        Attribute actual = SQLUtil.convertToAttribute("test", src);
        final Object object = AttributeUtil.getSingleValue(actual);
        assertEquals(expected, object);
    }    
    

    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertTimestampToJDBC() {
        Timestamp expected = new Timestamp(System.currentTimeMillis());
        long src = expected.getTime();
        Object actual = SQLUtil.convertToJDBC(src, expected.getClass().getName());
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected, actual);
    }    
    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertDateToJDBC() {
        Date expected = new Date(System.currentTimeMillis());
        long src = expected.getTime();
        Object actual = SQLUtil.convertToJDBC(src, expected.getClass().getName());
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected, actual);
    }    
    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertSqlDateToJDBC() {
        java.sql.Date expected = new java.sql.Date(System.currentTimeMillis());
        long src = expected.getTime();
        Object actual = SQLUtil.convertToJDBC(src, expected.getClass().getName());
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected, actual);
    }  
   
        
    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertStringToJDBC() {
        String expected = "test";
        Object actual = SQLUtil.convertToJDBC(expected, expected.getClass().getName());
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected, actual);
    }  

    /**
     * Test method
     * @throws SQLException 
     */
    @Test
    public void testConvertLongToJDBC() {
        Long expected = 10l;
        Object actual = SQLUtil.convertToJDBC(expected, expected.getClass().getName());
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected, actual);
    }     
    
    
    /**
     * GetAttributeSet test method
     * @throws SQLException 
     */
    @Test
    public void testGetAttributeSet() throws SQLException {
        final String TEST1 = "test1";
        final String TEST_VAL1 = "testValue1";
        final String TEST2 = "test2";
        final String TEST_VAL2 = "testValue2";

        //Resultset
        final ExpectProxy<ResultSet> trs = new ExpectProxy<ResultSet>();
        ResultSet resultSetProxy = trs.getProxy(ResultSet.class);
        
        //Metadata
        final ExpectProxy<ResultSetMetaData> trsmd = new ExpectProxy<ResultSetMetaData>();
        ResultSetMetaData metaDataProxy = trsmd.getProxy(ResultSetMetaData.class);

        trs.expectAndReturn("getMetaData",metaDataProxy);
        trsmd.expectAndReturn("getColumnCount", 2);
        trsmd.expectAndReturn("getColumnName", TEST1);
        trs.expectAndReturn("getObject", TEST_VAL1);
        trsmd.expectAndReturn("getColumnName", TEST2);        
        trs.expectAndReturn("getObject", TEST_VAL2);
        
        final Set<Attribute> actual = SQLUtil.getAttributeSet(resultSetProxy);
        assertTrue(trs.isDone());
        assertTrue(trsmd.isDone());
        assertEquals(2, actual.size());
        assertNotNull(AttributeUtil.find(TEST1, actual));
        assertNotNull(AttributeUtil.find(TEST2, actual));
        assertEquals(TEST_VAL1,AttributeUtil.find(TEST1, actual).getValue().get(0));
        assertEquals(TEST_VAL2,AttributeUtil.find(TEST2, actual).getValue().get(0));
     }     
}
