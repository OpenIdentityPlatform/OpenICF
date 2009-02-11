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
        return param.toString();
    }
}
