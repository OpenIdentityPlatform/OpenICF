/*  +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */

import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.common.security.GuardedString

// Connector configuration
connector {
      jdbcDriver="com.ibm.db2.jcc.DB2Driver"
      keyColumn="ACCOUNTID"
      passwordColumn="PASSWORD"
      table="ACCOUNTS"
      user="__configureme__"
      password="__configureme__"
      jdbcUrlTemplate="jdbc:db2://%h:%p/%d"
      host="__configureme__"
      port="50000"
      database="SAMPLECT"      
}
