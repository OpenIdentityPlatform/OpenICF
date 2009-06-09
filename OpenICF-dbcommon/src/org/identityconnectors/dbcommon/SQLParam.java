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
package org.identityconnectors.dbcommon;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.identityconnectors.common.Pair;


/**
 * The SQL parameter / util class
 * 
 * @version $Revision 1.0$
 * @since 1.0
 */
public final class SQLParam {

    private Pair<Object, Integer> param = new Pair<Object, Integer>();

    /**
     * The Sql param is a pair of value and its sqlType
     * 
     * @param value
     * @param sqlType
     */
    public SQLParam(Object value, int sqlType) {
        param.first = value;
        param.second = sqlType;
    }

    /**
     * The Sql param is a pair of value and its sqlType
     * 
     * @param value
     */
    public SQLParam(Object value) {
        param.first = value;
        param.second = Types.NULL;
    }
    /**
     * The list convertor util method
     * @param values
     * @return the list of SQLParam values
     */
    public static List<SQLParam> asList(List<Object> values){
        List<SQLParam>  ret = new ArrayList<SQLParam>();
        for (Object value : values) {
            ret.add(new SQLParam(value));
        }
        return ret;
    }
    
    /**
     * The param value
     * 
     * @return a value
     */
    public Object getValue() {
        return param.first;
    }

    /**
     * Sql Type
     * 
     * @return a type
     */
    public int getSqlType() {
        return param.second;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SQLParam) {
            return param.equals(((SQLParam) obj).param);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return param.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("\""+getValue()+"\":[");
        switch (getSqlType()) {
        case Types.ARRAY: ret.append("ARRAY"); break;
        case Types.BIGINT: ret.append("BIGINT"); break;
        case Types.BINARY: ret.append("BINARY"); break;
        case Types.BIT: ret.append("BIT"); break;
        case Types.BLOB: ret.append("BLOB"); break;
        case Types.BOOLEAN: ret.append("BOOLEAN"); break;
        case Types.CHAR: ret.append("CHAR"); break;
        case Types.CLOB: ret.append("CLOB"); break;
        case Types.DATALINK: ret.append("DATALINK"); break;
        case Types.DATE: ret.append("DATE"); break;
        case Types.DECIMAL: ret.append("DECIMAL"); break;
        case Types.DISTINCT: ret.append("DISTINCT"); break;
        case Types.DOUBLE: ret.append("DOUBLE"); break;
        case Types.FLOAT: ret.append("FLOAT"); break;
        case Types.INTEGER: ret.append("INTEGER"); break;
        case Types.JAVA_OBJECT: ret.append("JAVA_OBJECT"); break;
        case Types.LONGVARBINARY: ret.append("LONGVARBINARY"); break;
        case Types.LONGVARCHAR: ret.append("LONGVARCHAR"); break;
        case Types.NULL: ret.append("NULL"); break;
        case Types.NUMERIC: ret.append("NUMERIC"); break;
        case Types.OTHER: ret.append("OTHER"); break;
        case Types.REAL: ret.append("REAL"); break;
        case Types.REF: ret.append("REF"); break;
        case Types.SMALLINT: ret.append("SMALLINT"); break;
        case Types.STRUCT: ret.append("STRUCT"); break;
        case Types.TIME: ret.append("TIME"); break;
        case Types.TIMESTAMP: ret.append("TIMESTAMP"); break;
        case Types.TINYINT: ret.append("TINYINT"); break;
        case Types.VARBINARY: ret.append("VARBINARY"); break;
        case Types.VARCHAR: ret.append("VARCHAR"); break;
        default:
            ret.append("SQL Type"+getSqlType());
        }
        ret.append("]");
        return ret.toString();
        
    }
}
