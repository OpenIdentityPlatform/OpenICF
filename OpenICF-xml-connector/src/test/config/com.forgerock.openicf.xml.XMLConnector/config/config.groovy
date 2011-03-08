/*  +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */
import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.contract.exceptions.ObjectNotFoundException
import org.identityconnectors.common.security.GuardedString

host="__CONFIGURE_ME__"
port= Integer.valueOf(22)

// single admin user = login user
user="__CONFIGURE_ME__" // change to root for admin user
pass=new org.identityconnectors.common.security.GuardedString("__CONFIGURE_ME__".toCharArray())
loginShellPrompt = "#"

// root user (if not defined, login user has administrator privileges)
rootUser = null
credentials = null
rootShellPrompt = null

connector {
  host = Lazy.get("host")
  loginUser = Lazy.get("user")
  rootUser = Lazy.get("rootUser")
  password = Lazy.get("pass")
  credentials = Lazy.get("credentials")
  loginShellPrompt = Lazy.get("loginShellPrompt")
  rootShellPrompt = Lazy.get("rootShellPrompt")
  connectionType = Lazy.get("connectionType")
  port = Lazy.get("port")
  sudoAuthorization = false
}


testsuite {
  // path to bundle jar - property is set by ant - leave it as it is
  bundleJar=System.getProperty("bundleJar")
  bundleName=System.getProperty("bundleName")
  bundleVersion=System.getProperty("bundleVersion")
  connectorName="com.forgerock.openicf.xml.XMLConnector"
  
  Authentication.__ACCOUNT__.username = Lazy.get("i0.Authentication.__ACCOUNT__.__NAME__")
  Authentication.__ACCOUNT__.wrong.password = new GuardedString("_nonExistingPassword_".toCharArray())
  
  Search.disable.caseinsensitive = true

  Validate.invalidConfig = [
    [port: null],
    [password: null],
    [loginUser: null],
    [loginUser: ""],
    [connectionType: 'boobar'],
    [sudoAuthorization: true, credentials: null]
  ]//Validate

  Test.invalidConfig = [
    [password: "nonsensePassword123456"], 
    [loginUser: "nonsenseUserName123456"]
  ]//Test

  Schema {
    oclasses = ['__ACCOUNT__', 'shell' , '__GROUP__']
    attributes {
      __GROUP__.oclasses = ['__NAME__' , 'gid', 'users']
      shell.oclasses = ['shell', '__NAME__']
      __ACCOUNT__.oclasses = ['__NAME__', 'dir', 'shell', 'group', 'secondary_group',
        'uid', 'expire', 'inactive', 'comment', 'time_last_login',
        'authorization', 'profile', 'role', 'max', 'min', 'warn', 'lock', 
        '__PASSWORD__' /* TODO extra attribute that wasn't in the schema of Adatper -- is it OK? */, 
         'force_change'
      ] //__ACCOUNT__.oclasses
    }//attributes

    attrTemplate = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: false,
      returnedByDefault: true
    ]// attrTemplate
    
    attrTemplateIntNRBD = [
      type: int.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: false,
      returnedByDefault: false
    ]// attrTemplate
    
    attrTemplateBooleanNRBD = [
      type: boolean.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: false,
      returnedByDefault: false
    ]// attrTemplate
  
    attrRequiredTemplate = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: true,
      multiValue: false,
      returnedByDefault: true
    ]

    attrPasswdTemplate = [
      type: org.identityconnectors.common.security.GuardedString.class,
      readable: false,
      createable: true,
      updateable: true,
      required: false,
      multiValue: false,
      returnedByDefault: false
    ]

    attrNotRequiredNotReturnedTemplate = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: false,
      returnedByDefault: false
    ]
    
    attrMultiValuedTemplate = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: true,
      returnedByDefault: false
    ]
    
    attrNotUpdateableTemplate = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: false,
      required: false,
      multiValue: false,
      returnedByDefault: true
    ]

  

    gid.attribute.__GROUP__.oclasses = Lazy.get("testsuite.Schema.testsuite.Schema.attrTemplateIntNRBD")
    __NAME__.attribute.__GROUP__.oclasses = Lazy.get("testsuite.Schema.attrRequiredTemplate")
    users.attribute.__GROUP__.oclasses = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: true,
      required: false,
      multiValue: true,
      returnedByDefault: true
    ]
    
    shell.attribute.shell.oclasses = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: false,
      required: false,
      multiValue: true,
      returnedByDefault: false
    ]
    __NAME__.attribute.shell.oclasses = Lazy.get("testsuite.Schema.attrRequiredTemplate")

    inactive.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateIntNRBD")
    min.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateIntNRBD")
    max.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateIntNRBD")
    secondary_group.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrMultiValuedTemplate")
    group.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplate")
    __NAME__.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrRequiredTemplate")
    expire.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplate")
    warn.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateIntNRBD")
    dir.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplate")
    comment.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplate")
    uid.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateIntNRBD")
    lock.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateBooleanNRBD")
    time_last_login.attribute.__ACCOUNT__.oclasses = [
      type: String.class,
      readable: true,
      createable: true,
      updateable: false,
      required: false,
      multiValue: false,
      returnedByDefault: false
    ]// time_last_login
    authorization.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrMultiValuedTemplate")
    profile.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrMultiValuedTemplate")
    __PASSWORD__.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrPasswdTemplate")
    role.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrMultiValuedTemplate")
    force_change.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplateBooleanNRBD")
    shell.attribute.__ACCOUNT__.oclasses = Lazy.get("testsuite.Schema.attrTemplate")

    operations = [
      AuthenticationApiOp: ['__ACCOUNT__'],
      GetApiOp: ['__ACCOUNT__', 'shell', '__GROUP__'],
      SchemaApiOp: ['__ACCOUNT__', '__GROUP__'],
      ValidateApiOp: ['__ACCOUNT__', 'shell', '__GROUP__'],
      CreateApiOp: ['__ACCOUNT__', '__GROUP__'],
      SearchApiOp: ['__ACCOUNT__', 'shell', '__GROUP__'],
      DeleteApiOp: ['__ACCOUNT__', '__GROUP__'],
      ScriptOnConnectorApiOp: ['__ACCOUNT__', 'shell', '__GROUP__'] /* connector doesn't support this, but framework needs it */,
      ScriptOnResourceApiOp: ['__ACCOUNT__', 'shell', '__GROUP__'],
      TestApiOp: ['__ACCOUNT__', 'shell', '__GROUP__'],
      UpdateApiOp: ['__ACCOUNT__', '__GROUP__'],
      ResolveUsernameApiOp: ['__ACCOUNT__']
    ]//operations
  } //Schema
  
  ScriptOnResource {
    language = "bash"
    script = "echo 'ahoj ship'"
    arguments = null
    result = "ahoj ship"
  } //ScriptOnResource

}// testsuite

gid = new ObjectNotFoundException()
modified.gid = new ObjectNotFoundException()
users = ["root"]
modified.users = [] // empty list

inactive = new ObjectNotFoundException()
shell = new ObjectNotFoundException()
role = new ObjectNotFoundException()
force_change = new ObjectNotFoundException()
authorization = new ObjectNotFoundException()
profile = new ObjectNotFoundException()
time_last_login = new ObjectNotFoundException()
lock = new ObjectNotFoundException()
comment = new ObjectNotFoundException()
min = new ObjectNotFoundException()
max = new ObjectNotFoundException()
group = new ObjectNotFoundException()
dir = new ObjectNotFoundException()
uid = new ObjectNotFoundException()
warn = new ObjectNotFoundException()
expire = new ObjectNotFoundException()
secondary_group = new ObjectNotFoundException()

Tstring = Lazy.random("aaaaa")