/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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
 */

import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder as ICF
import org.identityconnectors.common.logging.Log
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.SyncToken

def action = action as OperationType
def configuration = configuration as ScriptedConfiguration
def log = log as Log
def objectClass = objectClass as ObjectClass


switch (action) {
    case OperationType.SYNC:

        def options = options as OperationOptions
        def token = token as Object

        switch (objectClass) {
            case TestHelper.TEST:
            case ObjectClass.ACCOUNT:
                switch (token) {
                    case 0:
                        //CREATE See IDME-114
                        handler({
                            syncToken 1
                            CREATE()
                            object {
                                uid '001'
                                id 'foo'
                                delegate.objectClass(objectClass)
                                TestHelper.connectorObjectTemplate.each { key, value ->
                                    attribute key, value
                                }

                            }
                        })
                        return new SyncToken(1);
                        break;
                    case 1:
                        //UPDATE See IDME-114
                        handler({
                            syncToken 2
                            UPDATE()
                            object {
                                uid '001'
                                id 'foo'
                                delegate.objectClass(objectClass)
                                TestHelper.connectorObjectTemplate.each { key, value ->
                                    attribute key, value
                                }

                            }
                        })
                        return new SyncToken(2);
                        break;
                    case 2:
                        //UPDATE
                        handler({
                            syncToken 3
                            CREATE_OR_UPDATE()
                            object {
                                uid '001'
                                id 'foo'
                                delegate.objectClass(objectClass)
                                TestHelper.connectorObjectTemplate.each { key, value ->
                                    attribute key, value
                                }

                            }
                        })
                        return new SyncToken(3);
                        break;
                    case 3:
                        //RENAME
                        handler({
                            syncToken 4
                            UPDATE()
                            previousUid '001'
                            object {
                                uid '002'
                                id 'foo'
                                delegate.objectClass(objectClass)
                                TestHelper.connectorObjectTemplate.each { key, value ->
                                    attribute key, value
                                }

                            }
                        })
                        return new SyncToken(4);
                        break;
                    case 4:
                        //DELETE
                        handler({
                            syncToken 5
                            DELETE()
                            object {
                                uid '002'
                                id 'foo'
                                delegate.objectClass(objectClass)
                                TestHelper.connectorObjectTemplate.each { key, value ->
                                    attribute key, value
                                }

                            }
                        })
                        return new SyncToken(5);
                        break;
                    case 5..9:
                        //Empty change range: Sample script for IDME-179
                        log.ok("Sync empty change range")
                        return new SyncToken(10);
                        break;
                    case 10..16:
                        for (int i = 10; i <= 16; i = i + 2) {
                            handler({
                                syncToken i
                                CREATE_OR_UPDATE()
                                object {
                                    uid '002'
                                    id 'foo'
                                    delegate.objectClass(objectClass)
                                    TestHelper.connectorObjectTemplate.each { key, value ->
                                        attribute key, value
                                    }
                                }
                            })
                        }
                    default:
                        return new SyncToken(17);
                }
            case ObjectClass.GROUP:
                for (int i = 11; i <= 16; i = i + 2) {
                    handler({
                        syncToken i
                        CREATE_OR_UPDATE()
                        object {
                            uid 'group1'
                            id 'group1'
                            delegate.objectClass(ObjectClass.GROUP)
                            TestHelper.connectorObjectTemplate.each { key, value ->
                                attribute key, value
                            }

                        }
                    })
                }
                return new SyncToken(16);
            case ObjectClass.ALL:
                // Sample script for IDME-116
                for (int i = 10; i <= 16; i++) {
                    handler({
                        syncToken i
                        CREATE_OR_UPDATE()
                        object {
                            if (i % 2 == 0) {
                                uid '002'
                                id 'foo'
                                delegate.objectClass ObjectClass.ACCOUNT
                            } else {
                                uid 'group1'
                                id 'group1'
                                delegate.objectClass ObjectClass.GROUP
                            }
                            TestHelper.connectorObjectTemplate.each { key, value ->
                                attribute key, value
                            }

                        }
                    })
                }
                return new SyncToken(17);
            case TestHelper.SAMPLE:
                handler(
                        ICF.delta {
                            syncToken 12345
                            UPDATE()
                            previousUid '12'
                            object {
                                uid '13'
                                id '13'
                                attribute {
                                    name 'sureName'
                                    value 'Foo'
                                }
                                attribute {
                                    name 'lastName'
                                    value 'Bar'
                                }
                                attribute {
                                    name 'groups'
                                    values 'Group1', 'Group2'
                                }
                                attribute 'active', true
                                attribute 'NULL'
                            }
                        }
                )

                handler({
                    syncToken 12346
                    CREATE()
                    object {
                        uid '13'
                        id '13'
                        attribute {
                            name 'sureName'
                            value 'Foo'
                        }
                        attribute {
                            name 'lastName'
                            value 'Bar'
                        }
                        attribute {
                            name 'groups'
                            values 'Group1', 'Group2'
                        }
                        attribute 'active', true
                        attribute 'NULL'
                        attributes(new Attribute('emails', [
                                [
                                        "address"   : "foo@example.com",
                                        "type"      : "home",
                                        "customType": "",
                                        "primary"   : true]
                        ]))
                    }
                })
                return new SyncToken('SAMPLE')
                break
            default:
                throw UnsupportedOperationException("Sync operation of type:" + objectClass)
        }

    case OperationType.GET_LATEST_SYNC_TOKEN:
        switch (objectClass) {
            case ObjectClass.ACCOUNT:
                return new SyncToken(17);
            case ObjectClass.GROUP:
                return new SyncToken(16);
            case ObjectClass.ALL:
                return new SyncToken(17);
            case TestHelper.TEST:
                return new SyncToken(0);
            case TestHelper.SAMPLE:
                return new SyncToken("ANY OBJECT");
            default:
                throw UnsupportedOperationException("Sync operation of type:" + objectClass.objectClassValue)
        }
    default:
        throw new ConnectorException("SyncScript can not handle action:" + action.name())
}
