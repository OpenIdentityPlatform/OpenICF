/* +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */

import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.common.security.GuardedString

configurations {
    oracle {
        jdbcDriver="oracle.jdbc.driver.OracleDriver"
        keyColumn="ACCOUNTID"
        passwordColumn= "PASSWORD"
        table="ACCOUNTS"
        user="__configureme__"
        jdbcUrlTemplate="jdbc:oracle:thin:@%h:%p:%d"
        password="__configureme__"
        host="__configureme__"
        port="1521"
        database="XE"
        nativeTimestamps=true
    }
}


// Connector WRONG configuration for ValidateApiOpTests
connector.i1.wrong.host=""
connector.i2.wrong.login=""
connector.i3.wrong.password=""

testsuite {
    // path to bundle jar - property is set by ant - leave it as it is
    bundleJar=System.getProperty("bundleJar")
    bundleName=System.getProperty("bundleName")
    bundleVersion=System.getProperty("bundleVersion")
    connectorName="org.identityconnectors.databasetable.DatabaseTableConnector"    

    Search.disable.caseinsensitive=true // skip insensitive test

    // ValidateApiOpTests:
    Validate.iterations="3"

    // AuthenticationApiOpTests:
    Authentication.__ACCOUNT__.username=Lazy.get("i0.Authentication.__ACCOUNT__.__NAME__")
    Authentication.__ACCOUNT__.wrong.password="bogus"

    // SyncApiOpTests:
    Sync.disable.delete=true
    
    // SchemaApiOpTests:
    Schema.oclasses=['__ACCOUNT__']
    Schema.attributes.__ACCOUNT__.oclasses=['__NAME__', '__PASSWORD__', 'MANAGER', 'MIDDLENAME', 'FIRSTNAME', 'LASTNAME', 'EMAIL', 'DEPARTMENT', 'TITLE', 'AGE', 'SALARY', 'JPEGPHOTO']

    Schema.__NAME__.attribute.__ACCOUNT__.oclasses=[
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: false,
        returnedByDefault: true 
   ]

    Schema.__PASSWORD__.attribute.__ACCOUNT__.oclasses=[
        type: org.identityconnectors.common.security.GuardedString.class, 
        readable: false,
        createable: true,
        updateable: true, 
        required: false,
        multiValue: false,
        returnedByDefault: false
     ]                                                        
    // many attributes have similar values                                                
    Schema.common.attribute=[
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: false,
        multiValue: false,
        returnedByDefault: true
    ]                                        
    Schema.MIDDLENAME.attribute.__ACCOUNT__.oclasses=testsuite.Schema.common.attribute                                                        
    Schema.MANAGER.attribute.__ACCOUNT__.oclasses=testsuite.Schema.common.attribute                                                        
    Schema.EMAIL.attribute.__ACCOUNT__.oclasses=testsuite.Schema.common.attribute                                                                                                                                                                                                                                                                                                                                                         
    Schema.DEPARTMENT.attribute.__ACCOUNT__.oclasses=testsuite.Schema.common.attribute
    Schema.TITLE.attribute.__ACCOUNT__.oclasses=testsuite.Schema.common.attribute                                                                                                                                                                       

    Schema.LASTNAME.attribute.__ACCOUNT__.oclasses=[
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: false,
        returnedByDefault: true
    ]                                                              
    Schema.FIRSTNAME.attribute.__ACCOUNT__.oclasses=[
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: false,
        returnedByDefault: true
    ]                          

    Schema.AGE.attribute.__ACCOUNT__.oclasses=[
        type: java.lang.Integer.class,
        readable: true,
        createable: true,
        updateable: true,
        required: false,
        multiValue: false,
        returnedByDefault: true
    ]
    
    Schema.ACCESSED.attribute.account.oclasses=[
       type: java.lang.Integer.class,
       readable: true,
       createable: true,
       updateable: true,
       required: false,
       multiValue: false,
       returnedByDefault: true
    ]        
                                                                                                    
    Schema.SALARY.attribute.__ACCOUNT__.oclasses=[
        type: java.math.BigDecimal.class,
        readable: true,
        createable: true,
        updateable: true,
        required: false,
        multiValue: false,
        returnedByDefault: true
   ]                                         
                                                                                                                                                                                                                                                                                                                                                                        
    Schema.JPEGPHOTO.attribute.__ACCOUNT__.oclasses=[
        type: byte[].class,//"[B",
        readable: true,
        createable: true,
        updateable: true,
        required: false,
        multiValue: false,
        returnedByDefault: false
    ]
    

    Schema.LAST_MODIFY.attribute.__ACCOUNT__.oclasses=[
      type: java.lang.String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: false,
      returnedByDefault: true
    ]
    Schema.CHANGED.attribute.account.oclasses=[
      type: java.lang.String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: true,
      multiValue: false,
      returnedByDefault: true
    ]    

    Schema.LAST_DATE.attribute.__ACCOUNT__.oclasses=[
       type: java.lang.String.class,
       readable: true,
       createable: true,
       updateable: true,
       required: false,
       multiValue: false,
       returnedByDefault: true    
    ]    

    // object classes supported by operations                                                                                                              
    Schema.operations=[
                                    GetApiOp: ['__ACCOUNT__'],
                                    SchemaApiOp: ['__ACCOUNT__'],
                                    ValidateApiOp: ['__ACCOUNT__'],
                                    CreateApiOp: ['__ACCOUNT__'],
                                    SearchApiOp: ['__ACCOUNT__'],
                                    DeleteApiOp: ['__ACCOUNT__'],
                                    ScriptOnConnectorApiOp: ['__ACCOUNT__'],
                                    UpdateApiOp: ['__ACCOUNT__'],
                                    AuthenticationApiOp: ['__ACCOUNT__'],
                                    TestApiOp: ['__ACCOUNT__'],
                                    SyncApiOp: [] // sync column is missing in the tables.
                ]
  
} // testsuite


// ATTRIBUTES' VALUES:
// longer bytearray value
JPEGPHOTO=Lazy.random("????????????????????????????????????????", byte[].class)
