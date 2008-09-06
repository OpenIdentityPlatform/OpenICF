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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * DatabaseConnection test class
 * @version $Revision 1.0$
 * @since 1.0
 */
public class DatabaseConnectionTest {

    private static final String LOGIN = "";
    private static final String TEST_SQL_STATEMENT = "SELECT * FROM dummy";
    
    private List<Object> values;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        values = new ArrayList<Object>();
        values.add(LOGIN); 
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        // not used yet 
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.DatabaseConnection#DatabaseConnection(java.lang.String, java.lang.String, java.lang.String, java.lang.String)}.
     */
    @Test
    public void testDatabaseConnection() {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        DatabaseConnection dbc = new DatabaseConnection(tp.getProxy(Connection.class));
        assertNotNull(dbc);         
        assertNotNull(dbc.getConnection());          
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.DatabaseConnection#dispose()}.
     */
    @Test
    public void testDispose() {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("isClosed", Boolean.FALSE);
        tp.expect("close");
        DatabaseConnection dbc = new DatabaseConnection(tp.getProxy(Connection.class));
        dbc.dispose();
        assertTrue("close called", tp.isDone());
        
        tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("isClosed", Boolean.TRUE);
        dbc = new DatabaseConnection(tp.getProxy(Connection.class));
        dbc.dispose();
        assertTrue("close called", tp.isDone());         
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.DatabaseConnection#test()}.
     */
    @Test
    public void testTest() {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        tp.expectAndReturn("getAutoCommit", Boolean.FALSE);
        tp.expect("setAutoCommit");
        tp.expectAndReturn("getAutoCommit", Boolean.TRUE);
        tp.expect("setAutoCommit");
        DatabaseConnection dbc = new DatabaseConnection(tp.getProxy(Connection.class));
        dbc.test();
        assertTrue("test called", tp.isDone());
        
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.DatabaseConnection#getConnection()}.
     */
    @Test
    public void testGetSetConnection() {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        final Connection testc = tp.getProxy(Connection.class);
        DatabaseConnection dbc = new DatabaseConnection(testc);
        dbc.getConnection();
        assertTrue("close called", tp.isDone());
        assertNotNull(dbc.getConnection());
        assertSame("connection", testc, dbc.getConnection());
        assertTrue("test called", tp.isDone());
    }

    /**
     * Test method for {@link org.identityconnectors.dbcommon.DatabaseConnection#prepareStatement(java.lang.String, java.util.List)}.
     * @throws Exception 
     */
    @Test
    public void testPrepareStatement() throws Exception{
        ExpectProxy<Connection> tpc = new ExpectProxy<Connection>();
        ExpectProxy<PreparedStatement> tps = new ExpectProxy<PreparedStatement>();
        tpc.expectAndReturn("prepareStatement", tps.getProxy(PreparedStatement.class));
        tps.expectAndReturn("setObject", "test");

        DatabaseConnection dbc = new DatabaseConnection(tpc.getProxy(Connection.class));
        dbc.prepareStatement(TEST_SQL_STATEMENT, values);
       
        assertTrue("statement created", tpc.isDone());
        assertTrue("value binded", tps.isDone());
    }    
    
    /**
     * Test method for {@link org.identityconnectors.dbcommon.DatabaseConnection#commit(org.identityconnectors.common.logging.Log)}.
     */
    @Test
    public void testCommit() {
        ExpectProxy<Connection> tp = new ExpectProxy<Connection>();
        tp.expect("commit");
        DatabaseConnection dbc = new DatabaseConnection(tp.getProxy(Connection.class));
        dbc.commit();
        assertTrue("commit called", tp.isDone());
    }
}
