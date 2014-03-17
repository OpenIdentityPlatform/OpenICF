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
import org.forgerock.openicf.misc.scriptedcommon.Logger
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase.Action
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.SyncToken

def action = action as Action
def configuration = configuration as ScriptedConfiguration
def log = log as Logger
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def token = token as SyncToken


switch (action) {
    case Action.SYNC:
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
                                "address": "foo@example.com",
                                "type": "home",
                                "customType": "",
                                "primary": true]
                ]))
            }
        })

        return new SyncToken('13')
        break
    case Action.GET_LATEST_SYNC_TOKEN:
        throw new UnsupportedOperationException()
    default:
        throw new ConnectorException("SyncScript can not handle action:" + action.name())
}
