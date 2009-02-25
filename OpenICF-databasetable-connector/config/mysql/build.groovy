/*  +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */

import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.identityconnectors.common.security.GuardedString

// Connector configuration
connector {
    jdbcDriver="com.mysql.jdbc.Driver"
    keyColumn="ACCOUNTID"
    passwordColumn="PASSWORD"
    table="ACCOUNTS"
    user="__configureme__"
    password="__configureme__"
    jdbcUrlTemplate="jdbc:mysql://%h/%d"
    host="__configureme__"
    database="idm_sync"
}
