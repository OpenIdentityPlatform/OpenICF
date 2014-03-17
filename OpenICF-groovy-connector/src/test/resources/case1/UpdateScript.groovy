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

import org.forgerock.openicf.misc.scriptedcommon.Logger
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase.Action
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.exceptions.PreconditionFailedException
import org.identityconnectors.framework.common.exceptions.PreconditionRequiredException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

def action = action as Action
def attributes = attributes as Map<String, Attribute>
def configuration = configuration as ScriptedConfiguration
def id = id as Attribute
def log = log as Logger
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def uid = uid as Uid


log.debugLocale("scriptingLanguage.help", "Default Message")

switch (action) {
    case Action.UPDATE:
        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            if ("TEST1".equals(uid.uidValue)) {
                throw new UnknownUidException(uid, objectClass);
            } else if ("TEST2".equals(uid.uidValue)) {
                throw new InvalidAttributeValueException();
            } else if ("TEST3".equals(uid.uidValue)) {
                throw new PreconditionFailedException();
            } else if ("TEST4".equals(uid.uidValue)) {
                throw new PreconditionRequiredException();
            }
            return uid
        } else {
            throw new UnsupportedOperationException("Unsupported Update -> " + objectClass)
        }
        break
    case Action.ADD_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException()
    case Action.REMOVE_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException()
    default:
        throw new ConnectorException("UpdateScript can not handle action:" + action.name())
}






