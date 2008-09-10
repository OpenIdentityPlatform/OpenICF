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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.Pair;
import org.identityconnectors.common.StringUtil;


/**
 * The Database Query builder creates the database query.
 * <p>The main functionality of this helper class is create SQL query statement
 * with bundled object references</p>
 * @version $Revision 1.0$
 * @since 1.0
 */
public class DatabaseQueryBuilder {
    private String select = null; //Mandatory select clause
    private String tableName = null; //Mandatory select clause
    private FilterWhereBuilder whereBuilder = null;
    private Set<String> columnNamesToGet=new HashSet<String>();
    private List<Pair<String, Boolean>> columnNamesToOrderBy = null;
    /**
     * DatabaseQuery Constructor, construct select  from table name, columnNamesToGet and where clause
     * @param tableName The name of the database table to select from
     * @param columnNamesToGet the names of the column to be in the result
     * @param whereBuilder whereBuilder the whereBuilder, part of the query
     * @param columnNamesToOrderBy The list of order by column names with ascendent / descendent boolean flag
     *  
     */
    public DatabaseQueryBuilder(String tableName, Set<String> columnNamesToGet, FilterWhereBuilder whereBuilder,
            List<Pair<String, Boolean>> columnNamesToOrderBy) {
        if(StringUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("the tableName must not be null or empty");
        }
        if(columnNamesToGet == null || columnNamesToGet.size() == 0) {
            throw new IllegalArgumentException("the columnNamesToGet must not be null or empty");            
        }  
        this.tableName = tableName;
        this.columnNamesToGet=columnNamesToGet;
        this.whereBuilder = whereBuilder;  
        this.columnNamesToOrderBy = columnNamesToOrderBy;
    }     

    
    /**
     * DatabaseQuery Constructor, construct select  from table name, columnNamesToGet and where clause
     * @param tableName
     * @param columnNamesToGet
     * @param whereBuilder
     */
    public DatabaseQueryBuilder(final String tableName, final Set<String> columnNamesToGet, final FilterWhereBuilder whereBuilder) {
        this(tableName,columnNamesToGet, whereBuilder, null);
    }
    
    /**
     * DatabaseQuery Constructor which takes advantage of prepared select SQL clause
     * @param select mandatory select clause
     * @param whereBuilder the whereBuilder, part of the query
     */
    public DatabaseQueryBuilder(final String select, final FilterWhereBuilder whereBuilder) {
        if(StringUtil.isBlank(select)) {
            throw new IllegalArgumentException("the select clause must not be empty");
        }
        if(!select.toUpperCase().contains("SELECT")) {
            throw new IllegalArgumentException("the select clause is missing");            
        }
        if(!select.toUpperCase().contains("FROM")) {
            throw new IllegalArgumentException("the from clause is missing");            
        }
        this.select = select;
        this.whereBuilder = whereBuilder;       
    }     


    /**
     * Return full sql statement string
     * @return Sql query statement to execute
     */
    public String getSQL() {
       
        if (StringUtil.isBlank(select)) {
            select = createSelect(columnNamesToGet);
        }       
        
        String ret = select;
        if(whereBuilder != null) {
            final String where = whereBuilder.getWhereClause();
            if(!StringUtil.isBlank(where)) {
                ret += (select.indexOf("WHERE")==-1) ? " WHERE "+where:" AND ( "+where+" )";
            }        
        }
        if(this.columnNamesToOrderBy != null) {
            StringBuilder obld = new StringBuilder(" ORDER BY ");
            boolean first=true;
            for (Pair<String, Boolean> orderBy : columnNamesToOrderBy) {
                if( !first) {
                    obld.append(", ");
                }
                first = false;
                obld.append(orderBy.first);
                obld.append(orderBy.second ? " ASC" : " DESC");
            }
            if(obld.length() != 0) {
                ret += obld.toString();
            }        
        }
        return ret;
    }

    /**
     * @param columnNamesToGet
     * @param columnQuote
     * @return the select statement
     */
    private String createSelect(final Set<String> columnNamesToGet) {
        if (columnNamesToGet.size() == 0) {
            throw new IllegalStateException("No coulmn names to get in query");
        }
        StringBuilder ret = new StringBuilder("SELECT ");
        boolean first=true;
        for(String name: columnNamesToGet){
            if( !first) {
                ret.append(", ");
            }
            ret.append(name);
            ret.append(" ");
            first = false;
        }
        ret.append("FROM ");
        ret.append(tableName);
        return ret.toString();
    }

    /**
     * Values in wrapped object
     * @return
     */
    public List<Object> getParams() {
        if(whereBuilder==null) {
            return new ArrayList<Object>();
        }
        return whereBuilder.getParams();
    }
}
