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

import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.common.security.SecurityUtil
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

def action = action as OperationType
def configuration = configuration as ScriptedConfiguration
def username = username as String
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def password = password as GuardedString;

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        throw UnsupportedOperationException(action.name() + " operation of type:" + objectClass)
        break
    case ObjectClass.GROUP:
        throw UnsupportedOperationException(action.name() + " operation of type:" + objectClass)
        break
    case ObjectClass.ALL:
        log.error("ICF Framework MUST reject this")
        break
    case TestHelper.TEST:
        if (username.equals("TEST1")) {
            throw new ConnectorSecurityException();
        } else if (username.equals("TEST2")) {
            throw new InvalidCredentialException();
        } else if (username.equals("TEST3")) {
            throw new InvalidPasswordException();
        } else if (username.equals("TEST4")) {
            throw new PermissionDeniedException();
        } else if (username.equals("TEST5")) {
            throw new PasswordExpiredException();
        } else if (username.equals("TESTOK1")) {
            def clearPassword = SecurityUtil.decrypt(password)
            if ("Passw0rd".equals(clearPassword)) {
                return new Uid(username);
            }
            throw new InvalidPasswordException();
        } else if (username.equals("TESTOK2")) {
            def clearPassword = SecurityUtil.decrypt(password)
            if ("".equals(clearPassword)) {
                return new Uid(username);
            }
        }
        throw new UnknownUidException();
    case TestHelper.SAMPLE:
        throw UnsupportedOperationException(action.name() + " operation of type:" + objectClass)
        break
    default:
        throw UnsupportedOperationException(action.name() + " operation of type:" + objectClass)
}

