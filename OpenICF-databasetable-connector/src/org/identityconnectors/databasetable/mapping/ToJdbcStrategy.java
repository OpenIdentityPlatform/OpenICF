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
package org.identityconnectors.databasetable.mapping;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;


/**
 * The SQL get/set strategy class implementation convert attributes back to database types
 * @version $Revision 1.0$
 * @since 1.0
 */
public class ToJdbcStrategy implements MappingStrategy {

    MappingStrategy delegate;
    
    /**
     * Final sql mapping
     * @param delegate
     */
    public ToJdbcStrategy(MappingStrategy delegate) {
        Assertions.nullCheck(delegate, "MappingStrategy delegate");
        this.delegate = delegate;
    }
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.MappingStrategy#getSQLParam(java.sql.ResultSet, int, int)
     */
    public SQLParam getSQLParam(ResultSet resultSet, int i, final int sqlType) throws SQLException {
        //Default processing otherwise
        return delegate.getSQLParam(resultSet, i, sqlType);
    } 
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.MappingStrategy#getSQLAttributeType(int)
     */
    public Class<?> getSQLAttributeType(int sqlType) {
        return delegate.getSQLAttributeType(sqlType);
    }
    
    /* (non-Javadoc)
     * @see org.identityconnectors.databasetable.MappingStrategy#setSQLParam(java.sql.PreparedStatement, int, org.identityconnectors.dbcommon.SQLParam)
     */
    public void setSQLParam(final PreparedStatement stmt, final int idx, SQLParam parm) throws SQLException {
        final Object val = SQLUtil.attribute2jdbcValue(parm.getValue(), parm.getSqlType());
        delegate.setSQLParam(stmt, idx, new SQLParam(val, parm.getSqlType()));
    }    
}

