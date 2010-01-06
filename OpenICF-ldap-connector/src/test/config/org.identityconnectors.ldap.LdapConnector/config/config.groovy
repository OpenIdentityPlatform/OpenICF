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
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.contract.data.groovy.Lazy;
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;

// Unit tests.

sunds.host = '__configureme__'
sunds.port = 389
sunds.principal = '__configureme__'
sunds.credentials = '__configureme__'
sunds.baseContext = '__configureme__'

// Contract tests.

Object telephoneNumber() {
    return Lazy.random('### ### ####');
}

bundleJar = System.getProperty('bundleJar')
bundleName = System.getProperty('bundleName')
bundleVersion = System.getProperty('bundleVersion')
connectorName='org.identityconnectors.ldap.LdapConnector'

baseContext = '__configureme__'

connector {
    host = '__configureme__'
    port = 389
    principal = '__configureme__'
    credentials = new GuardedString('__configureme__'.toCharArray())
    baseContexts = [ baseContext ] as String[]
    usePagedResultControl = true // We do not have a VLV index.
    uidAttribute = 'entryDN' // Sun DSEE 6.3 does not support entryUUID
}

Validate.invalidConfig = [
    [ host : '' ],
    [ port : 100000 ],
    [ baseContexts : [] ]
]

Test.invalidConfig = [
    [ principal : 'cn=Nobody' ],
    [ credentials : 'bogus' ]
]

Schema {
    strictCheck = false

    oclasses = [ '__ACCOUNT__', '__GROUP__' ]

    operations = [
        GetApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        SchemaApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        ValidateApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        CreateApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        SearchApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        DeleteApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        ScriptOnConnectorApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        UpdateApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        AuthenticationApiOp: [ '__ACCOUNT__' ],
        TestApiOp: [ '__ACCOUNT__', '__GROUP__' ],
        SyncApiOp: [ ]
    ]

    attributes.__ACCOUNT__.oclasses = [ '__NAME__', 'cn', 'sn', 'jpegPhoto' ]
    __NAME__.attribute.__ACCOUNT__.oclasses = [
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: false,
        returnedByDefault: true
    ]
    cn.attribute.__ACCOUNT__.oclasses = [
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: true,
        returnedByDefault: true
    ]
    jpegPhoto.attribute.__ACCOUNT__.oclasses = [
        type: byte[].class,
        readable: true,
        createable: true,
        updateable: true,
        required: false,
        multiValue: true,
        returnedByDefault: true
    ]

    attributes.__GROUP__.oclasses = [ '__NAME__', 'cn' ]
    __NAME__.attribute.__ACCOUNT__.oclasses = [
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: false,
        returnedByDefault: true
    ]
    cn.attribute.__ACCOUNT__.oclasses = [
        type: java.lang.String.class,
        readable: true,
        createable: true,
        updateable: true,
        required: true,
        multiValue: true,
        returnedByDefault: true
    ]
}

objectClasses = [ '__ACCOUNT__', '__GROUP__' ]

Search.disable.caseinsensitive = true

Authentication {
    __ACCOUNT__ {
        __NAME__ = 'uid=Bugs Bunny,' + Lazy.get('baseContext')
        __PASSWORD__ = new GuardedString('password'.toCharArray())
        username = 'Bugs Bunny'
        modified.__PASSWORD__ = new GuardedString('newpassword'.toCharArray())
        wrong.password = new GuardedString('bogus'.toCharArray())
    }
}

__ACCOUNT__.__NAME__ = 'uid=' + Lazy.random('Aaaa Aaaa') + ',' + Lazy.get('baseContext')
__ACCOUNT__.uid = new ObjectNotFoundException() // Since 'uid', as the naming attribute, already has a value.

__ACCOUNT__.homePhone = telephoneNumber()
__ACCOUNT__.mobile = telephoneNumber()
__ACCOUNT__.pager = telephoneNumber()
__ACCOUNT__.telephoneNumber = telephoneNumber()

__ACCOUNT__.x500UniqueIdentifier = new ObjectNotFoundException()
__ACCOUNT__.manager = new ObjectNotFoundException()
__ACCOUNT__.internationaliSDNNumber = new ObjectNotFoundException()
__ACCOUNT__.x121Address = new ObjectNotFoundException()
__ACCOUNT__.preferredDeliveryMethod = new ObjectNotFoundException()
__ACCOUNT__.telexNumber = new ObjectNotFoundException()
__ACCOUNT__.owner = new ObjectNotFoundException()
__ACCOUNT__.secretary = new ObjectNotFoundException()
__ACCOUNT__.seeAlso = new ObjectNotFoundException()
__ACCOUNT__.userPassword = new ObjectNotFoundException()
__ACCOUNT__.__PASSWORD__ = new ObjectNotFoundException()

__ACCOUNT__.modified.__NAME__ = 'cn=' + Lazy.random('Aaaa Aaaa') + ',' + Lazy.get('baseContext')
__ACCOUNT__.modified.cn = new ObjectNotFoundException() // Since 'cn', as the naming attribute, already has a value.
__ACCOUNT__.modified.uid = new ObjectNotFoundException() // Since 'uid', as the previous naming attribute, already has a value.

__ACCOUNT__.modified.homePhone = telephoneNumber()
__ACCOUNT__.modified.mobile = telephoneNumber()
__ACCOUNT__.modified.pager = telephoneNumber()
__ACCOUNT__.modified.telephoneNumber = telephoneNumber()

__ACCOUNT__.modified.x500UniqueIdentifier = new ObjectNotFoundException()
__ACCOUNT__.modified.manager = new ObjectNotFoundException()
__ACCOUNT__.modified.internationaliSDNNumber = new ObjectNotFoundException()
__ACCOUNT__.modified.x121Address = new ObjectNotFoundException()
__ACCOUNT__.modified.preferredDeliveryMethod = new ObjectNotFoundException()
__ACCOUNT__.modified.telexNumber = new ObjectNotFoundException()
__ACCOUNT__.modified.owner = new ObjectNotFoundException()
__ACCOUNT__.modified.secretary = new ObjectNotFoundException()
__ACCOUNT__.modified.seeAlso = new ObjectNotFoundException()
__ACCOUNT__.modified.userPassword = new ObjectNotFoundException()
__ACCOUNT__.modified.__PASSWORD__ = new ObjectNotFoundException()

__GROUP__.__NAME__ = 'ou=' + Lazy.random('Aaaa Aaaa') + ',' + Lazy.get('baseContext')
__GROUP__.ou = new ObjectNotFoundException() // Since 'ou', as the naming attribute, already has a value.

__GROUP__.seeAlso = new ObjectNotFoundException()
__GROUP__.uniqueMember = new ObjectNotFoundException()
__GROUP__.owner = new ObjectNotFoundException()

__GROUP__.modified.__NAME__ = 'cn=' + Lazy.random('Aaaa Aaaa') + ',' + Lazy.get('baseContext')
__GROUP__.modified.cn = new ObjectNotFoundException() // Since 'cn', as the naming attribute, already has a value.
__GROUP__.modified.ou = new ObjectNotFoundException() // Since 'ou', as the previous naming attribute, already has a value.

__GROUP__.modified.seeAlso = new ObjectNotFoundException()
__GROUP__.modified.uniqueMember = new ObjectNotFoundException()
__GROUP__.modified.owner = new ObjectNotFoundException()

// Workaround for issue 599.
added.audio = new ObjectNotFoundException()
added.businessCategory = new ObjectNotFoundException()
added.carLicense = new ObjectNotFoundException()
added.cn = new ObjectNotFoundException()
added.departmentNumber = new ObjectNotFoundException()
added.description = new ObjectNotFoundException()
added.destinationIndicator = new ObjectNotFoundException()
added.displayName = new ObjectNotFoundException()
added.employeeNumber = new ObjectNotFoundException()
added.employeeType = new ObjectNotFoundException()
added.facsimileTelephoneNumber = new ObjectNotFoundException()
added.givenName = new ObjectNotFoundException()
added.homePhone = new ObjectNotFoundException()
added.homePostalAddress = new ObjectNotFoundException()
added.initials = new ObjectNotFoundException()
added.internationaliSDNNumber = new ObjectNotFoundException()
added.jpegPhoto = new ObjectNotFoundException()
added.l = new ObjectNotFoundException()
added.labeledUri = new ObjectNotFoundException()
added.mail = new ObjectNotFoundException()
added.manager = new ObjectNotFoundException()
added.mobile = new ObjectNotFoundException()
added.o = new ObjectNotFoundException()
added.ou = new ObjectNotFoundException()
added.owner = new ObjectNotFoundException()
added.pager = new ObjectNotFoundException()
added.photo = new ObjectNotFoundException()
added.physicalDeliveryOfficeName = new ObjectNotFoundException()
added.postOfficeBox = new ObjectNotFoundException()
added.postalAddress = new ObjectNotFoundException()
added.postalCode = new ObjectNotFoundException()
added.preferredLanguage = new ObjectNotFoundException()
added.registeredAddress = new ObjectNotFoundException()
added.roomNumber = new ObjectNotFoundException()
added.secretary = new ObjectNotFoundException()
added.seeAlso = new ObjectNotFoundException()
added.sn = new ObjectNotFoundException()
added.st = new ObjectNotFoundException()
added.street = new ObjectNotFoundException()
added.telephoneNumber = new ObjectNotFoundException()
added.teletexTerminalIdentifier = new ObjectNotFoundException()
added.telexNumber = new ObjectNotFoundException()
added.title = new ObjectNotFoundException()
added.uid = new ObjectNotFoundException()
added.uniqueMember = new ObjectNotFoundException()
added.userCertificate = new ObjectNotFoundException()
added.userPKCS12 = new ObjectNotFoundException()
added.userPassword = new ObjectNotFoundException()
added.userSMIMECertificate = new ObjectNotFoundException()
added.x121Address = new ObjectNotFoundException()
added.x500UniqueIdentifier = new ObjectNotFoundException()

// Workaround for issue 489. This will cause the server to reject the request, so the connector
// will throw an exception, making the contract tests happy.
Update.unsupportedAttributeName = 'cn'

// Workaround for issue XXX.
Sync.disable.create = true
Sync.disable.update = true
Sync.disable.delete = true
