/*  +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */

import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.identityconnectors.common.security.GuardedString

// Connector configuration
connector {
    jdbcDriver="com.microsoft.sqlserver.jdbc.SQLServerDriver"
    keyColumn="ACCOUNTID"
    passwordColumn="PASSWORD"
    table="ACCOUNTS"
    jdbcUrlTemplate="jdbc:sqlserver://%h;databaseName=%d;"
    host="__configureme__"
    database="test"
    user="__configureme__"
    password="__configureme__"
    changeLogColumn="CHANGELOG" 
}

testsuite.Schema.operations.SyncApiOp = ['__ACCOUNT__'] // sync is available
