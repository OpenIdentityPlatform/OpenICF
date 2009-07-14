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


import java.util.ArrayList;
import java.util.List;

import org.identityconnectors.common.CollectionUtil;


/**
 * The update set builder create the database update statement.
 * <p>The main functionality is create set part of update statement from Attribute set</p>
 *
 * @version $Revision 1.0$
 * @since 1.0
 */
public class UpdateSetBuilder {
    private List<SQLParam> params = new ArrayList<SQLParam>();
    private StringBuilder set = new StringBuilder();
    
    /**
     * Add column name and value pair
     * The names are quoted using the {@link #columnQuote} value
     * 
     * @param name name
     * @param param value
     * @param sqlType
     * @return self
     */
    public UpdateSetBuilder addBind(SQLParam param) {
        return addBind(param, "?");
    }

    /**
     * Add column name and expression value pair
     * The names are quoted using the {@link #columnQuote} value
     * @param param the value to bind
     * @param expression the expression
     * @return self
     */
    public UpdateSetBuilder addBind(SQLParam param, String expression) {
        if(set.length()>0) {
            set.append(" , ");
        }
        set.append(param.getName()).append(" = ").append(expression);
        params.add(param);
        return this;
    }    
    
    /**
     * Build the set SQL 
     * @return The update set clause 
     */
    public String getSQL() {
        return set.toString();
    }

    /**
     * Add the update value
     * @param param 
     */
    public void addValue(SQLParam param) {
        params.add(param);
    }

    
    /**
     * @return the param values
     */
    public List<SQLParam> getParams() {
        return CollectionUtil.newReadOnlyList(params);
    }

}
