/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012. ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 */

/* +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */

//-DtestConfig=default -DconnectorName=org.forgerock.openicf.connectors.basic.BasicConnector -DbundleJar=target/basic-connector-1.1.0.0-SNAPSHOT.jar -DbundleName=org.forgerock.openicf.connectors.basic-connector -DbundleVersion=1.1.0.0-SNAPSHOT

import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.common.security.GuardedString

// Connector WRONG configuration for ValidateApiOpTests
connector.i1.wrong.host = ""
connector.i2.wrong.login = ""
connector.i3.wrong.password = new GuardedString("".toCharArray())


configuration {
    ssl = false
    host = "__configureme__"
    remoteUser = "__configureme__"
    password = new GuardedString("__configureme__".toCharArray())
    baseContext = "dc=example,dc=com"
}

testsuite {
    // path to bundle jar - property is set by ant - leave it as it is
    bundleJar = System.getProperty("bundleJar")
    bundleName = System.getProperty("bundleName")
    bundleVersion = System.getProperty("bundleVersion")
    connectorName = System.getProperty("connectorName")

    // AttributeTests:
    Attribute {

    }

    // AuthenticationApiOpTests:
    Authentication {
        __ACCOUNT__ {
            __NAME__ = 'uid=Bugs Bunny,' + Lazy.get('configuration.baseContext')
            __PASSWORD__ = new GuardedString('password'.toCharArray())
            username = 'Bugs Bunny'
            modified.__PASSWORD__ = new GuardedString('newpassword'.toCharArray())
            wrong.password = new GuardedString('bogus'.toCharArray())
        }
    }

    // AuthenticationApiOpTests:
    //Authentication.__ACCOUNT__.username=Lazy.get("i0.Authentication.__ACCOUNT__.__NAME__")
    //Authentication.__ACCOUNT__.wrong.password=new GuardedString("bogus".toCharArray())

    // SearchApiOpTests:
    Search.disable.caseinsensitive = true

    // SchemaApiOpTests:
    Schema {
        oclasses = ["__ACCOUNT__", "__GROUP__", "organization"]
        strictCheck = true
        operations = [
                GetApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                SchemaApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                ValidateApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                TestApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                CreateApiOp: ["__ACCOUNT__"],
                SearchApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                DeleteApiOp: ["__ACCOUNT__"],
                ScriptOnConnectorApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                ScriptOnResourceApiOp: ["__ACCOUNT__", "__GROUP__", "organization"],
                UpdateApiOp: ["__ACCOUNT__"],
                AuthenticationApiOp: ["__ACCOUNT__"],
                ResolveUsernameApiOp: ["__ACCOUNT__"],
                SyncApiOp: ["__ACCOUNT__"]
        ]
        attributes {
            __GROUP__ {
                oclasses = [
                        "__NAME__",
                        "__DESCRIPTION__"
                        ]
            }
            organization {
                oclasses = [
                        "__NAME__",
                        "__DESCRIPTION__",
                        "members"
                        ]
            }
            __ACCOUNT__ {
                oclasses = [
                        '__DESCRIPTION__',
                        '__CURRENT_PASSWORD__',
                        '_Attribute-Double',
                        '_Attribute-primitive-double',
                        '__ENABLE__',
                        '_Attribute-BigInteger',
                        '__DISABLE_DATE__',
                        '_Attribute-Long',
                        '_Attribute-primitive-long',
                        '__GROUPS__',
                        '_Attribute-primitive-char',
                        '_Attribute-Integer',
                        '_Attribute-byte[]',
                        '__PASSWORD__',
                        '__SHORT_NAME__',
                        '_Attribute-Boolean',
                        '_Attribute-primitive-boolean',
                        '_Attribute-BigDecimal',
                        '_Attribute-Character',
                        '_Attribute-GuardedString',
                        '__PASSWORD_CHANGE_INTERVAL__',
                        '__ENABLE_DATE__',
                        '__PASSWORD_EXPIRED__',
                        '__LOCK_OUT__',
                        '_Attribute-primitive-float',
                        '_Attribute-Float',
                        '_Attribute-String',
                        '__PASSWORD_EXPIRATION_DATE__',
                        '_Attribute-GuardedByteArray',
                        '__LAST_PASSWORD_CHANGE_DATE__',
                        '__NAME__',
                        '__LAST_LOGIN_DATE__',
                        '_Attribute-primitive-int'
                ]
            }
        }

        // many attributes have similar values
        common.attribute = [
                type: java.lang.String.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]

        __NAME__.attribute.definition = [
                type: java.lang.String.class,
                readable: true,
                createable: true,
                updateable: true,
                required: true,
                multiValue: false,
                returnedByDefault: true
        ]

        __NAME__.attribute.__ACCOUNT__.oclasses = testsuite.Schema.__NAME__.attribute.definition
        __NAME__.attribute.__GROUP__.oclasses = testsuite.Schema.__NAME__.attribute.definition
        __NAME__.attribute.organization.oclasses = testsuite.Schema.__NAME__.attribute.definition



        __DESCRIPTION__.attribute.__ACCOUNT__.oclasses = testsuite.Schema.common.attribute
        __DESCRIPTION__.attribute.__GROUP__.oclasses = testsuite.Schema.common.attribute
        __DESCRIPTION__.attribute.organization.oclasses = testsuite.Schema.common.attribute


        __CURRENT_PASSWORD__.attribute.__ACCOUNT__.oclasses = [
                type: org.identityconnectors.common.security.GuardedString.class,
                readable: false,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: false
        ]


        __ENABLE__.attribute.__ACCOUNT__.oclasses = [
                type: boolean.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]

        __DISABLE_DATE__.attribute.__ACCOUNT__.oclasses = [
                type: long.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]

        __GROUPS__.attribute.__ACCOUNT__.oclasses = [
                type: java.lang.String.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: true,
                returnedByDefault: false
        ]
        __PASSWORD__.attribute.__ACCOUNT__.oclasses = [
                type: org.identityconnectors.common.security.GuardedString.class,
                readable: false,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: false
        ]
        __SHORT_NAME__.attribute.__ACCOUNT__.oclasses = [
                type: java.lang.String.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __PASSWORD_CHANGE_INTERVAL__.attribute.__ACCOUNT__.oclasses = [
                type: long.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __ENABLE_DATE__.attribute.__ACCOUNT__.oclasses = [
                type: long.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __PASSWORD_EXPIRED__.attribute.__ACCOUNT__.oclasses = [
                type: boolean.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __LOCK_OUT__.attribute.__ACCOUNT__.oclasses = [
                type: boolean.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __PASSWORD_EXPIRATION_DATE__.attribute.__ACCOUNT__.oclasses = [
                type: long.class,
                readable: true,
                createable: true,
                updateable: true,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __LAST_PASSWORD_CHANGE_DATE__.attribute.__ACCOUNT__.oclasses = [
                type: long.class,
                readable: true,
                createable: false,
                updateable: false,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]
        __LAST_LOGIN_DATE__.attribute.__ACCOUNT__.oclasses = [
                type: long.class,
                readable: true,
                createable: false,
                updateable: false,
                required: false,
                multiValue: false,
                returnedByDefault: true
        ]



        members.attribute.organization.oclasses = [
                type: java.lang.String.class,
                readable: true,
                createable: true,
                updateable: false,
                required: false,
                multiValue: true,
                returnedByDefault: true
        ]

    }
    Schema['_Attribute-Double'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.Double.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-primitive-double'].attribute.__ACCOUNT__.oclasses = [
            type: double.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-BigInteger'].attribute.__ACCOUNT__.oclasses = [
            type: java.math.BigInteger.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-Long'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.Long.class,
            readable: true,
            createable: false,
            updateable: false,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-primitive-long'].attribute.__ACCOUNT__.oclasses = [
            type: long.class,
            readable: false,
            createable: true,
            updateable: false,
            required: false,
            multiValue: false,
            returnedByDefault: false
    ]
    Schema['_Attribute-primitive-char'].attribute.__ACCOUNT__.oclasses = [
            type: char.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-Integer'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.Integer.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-byte[]'].attribute.__ACCOUNT__.oclasses = [
            type: byte[].class,
            readable: true,
            createable: true,
            updateable: false,
            required: false,
            multiValue: false,
            returnedByDefault: false
    ]
    Schema['_Attribute-Boolean'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.Boolean.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-primitive-boolean'].attribute.__ACCOUNT__.oclasses = [
            type: boolean.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-BigDecimal'].attribute.__ACCOUNT__.oclasses = [
            type: java.math.BigDecimal.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-Character'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.Character.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-GuardedString'].attribute.__ACCOUNT__.oclasses = [
            type: org.identityconnectors.common.security.GuardedString.class,
            readable: true,
            createable: true,
            updateable: false,
            required: true,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-primitive-float'].attribute.__ACCOUNT__.oclasses = [
            type: float.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-Float'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.Float.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-String'].attribute.__ACCOUNT__.oclasses = [
            type: java.lang.String.class,
            readable: true,
            createable: true,
            updateable: false,
            required: false,
            multiValue: true,
            returnedByDefault: true
    ]
    Schema['_Attribute-GuardedByteArray'].attribute.__ACCOUNT__.oclasses = [
            type: org.identityconnectors.common.security.GuardedByteArray.class,
            readable: true,
            createable: true,
            updateable: false,
            required: true,
            multiValue: false,
            returnedByDefault: true
    ]
    Schema['_Attribute-primitive-int'].attribute.__ACCOUNT__.oclasses = [
            type: int.class,
            readable: true,
            createable: true,
            updateable: true,
            required: false,
            multiValue: false,
            returnedByDefault: true
    ]

    // ValidateApiOpTests:
    Validate {
        invalidConfig = [
                [0: "foo", 1: "bar", 2: "wooow!"],
                [0: "foo", 1: "bar", 2: "wooow!"]
        ]
    }

    // ValidateApiOpTests:
    Test.invalidConfig = [
            [ password : "NonExistingPassword_foo_bar_boo" ]
    ]

    // SyncApiOpTests:
    Sync{
        disable.create=true
        disable.update=true
        disable.delete=true
    }

} // testsuite

HOST="0.0.0.0"
