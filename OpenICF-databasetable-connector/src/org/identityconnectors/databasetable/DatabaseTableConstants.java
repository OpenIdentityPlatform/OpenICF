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
package org.identityconnectors.databasetable;

/**
 * The database table constants 
 * @author Petr Jung
 * @since 1.0
 */
public class DatabaseTableConstants {
    private DatabaseTableConstants() {
        throw new AssertionError();
    }
    /**  The default connect URL template.  */
    public static final String DEFAULT_TEMPLATE = "jdbc:oracle:thin:@%h:%p:%d";
    
    /**
     * The default value for the RA_DRIVER resource attribute.
     */
    public static final String DEFAULT_DRIVER = "oracle.jdbc.driver.OracleDriver";    

    /** The null column default value */
    public static final String EMPTY_STR = "";    

    
    // Validate messages constants
    static final String MSG_NAME_BLANK = "name.blank";
    static final String MSG_PWD_BLANK = "pwd.blank";
    static final String MSG_TABLE_BLANK = "table.blank";    
    static final String MSG_KEY_COLUMN_BLANK="key.column.blank";
    static final String MSG_PWD_COLUMN_BLANK="pwd.column.blank";
    static final String MSG_CHANGELOG_COLUMN_BLANK="changelog.column.blank";
    static final String MSG_USER_BLANK = "admin.user.blank";
    static final String MSG_PASSWORD_BLANK = "admin.password.blank";
    static final String MSG_HOST_BLANK = "host.blank";
    static final String MSG_PORT_BLANK = "port.blank";
    static final String MSG_DATABASE_BLANK = "database.blank";
    static final String MSG_JDBC_TEMPLATE_BLANK = "jdbc.template.blank";
    static final String MSG_JDBC_DRIVER_BLANK = "jdbc.driver.blank";
    static final String MSG_JDBC_DRIVER_NOT_FOUND  = "jdbc.driver.not.found";
    static final String MSG_INVALID_QUOTING = "invalid.quoting";
    static final String MSG_ACCOUNT_OBJECT_CLASS_REQUIRED = "acount.object.class.required";
    static final String MSG_AUTHENTICATE_OP_NOT_SUPPORTED = "auth.op.not.supported";
    static final String MSG_AUTH_FAILED="auth.op.failed";
    static final String MSG_INVALID_ATTRIBUTE_SET="invalid.attribute.set";
    static final String MSG_UID_BLANK="uid.blank";
    static final String MSG_RESULT_HANDLER_NULL="result.handler.null";
}
