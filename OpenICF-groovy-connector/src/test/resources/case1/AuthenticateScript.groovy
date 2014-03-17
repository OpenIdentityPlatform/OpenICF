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
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

def action = action as Action
def configuration = configuration as ScriptedConfiguration
def username = username as String
def log = log as Logger
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def password = password as Object;

if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
    if (username.equals("TEST1")) {
        throw new ConnectorSecurityException();
    } else if (username.equals("TEST2")) {
        throw new InvalidCredentialException();
    } else if (username.equals("TEST3")) {
        throw new InvalidPasswordException();
    } else if (username.equals("TEST4")) {
        throw new PermissionDeniedException();
    } else if (username.equals("TEST5")) {
        def clearPassword
        if (password instanceof Closure) {
            clearPassword = password()
        } else {
            clearPassword = password
        }

        if ("Passw0rd".equals(clearPassword)) {
            return new Uid(username);
        }
        throw new InvalidPasswordException();
    } else if (username.equals("TEST6")) {
        def clearPassword
        if (password instanceof Closure) {
            password({ out ->
                clearPassword = out
            })
        } else {
            clearPassword = password
        }

        if ("Passw0rd".equals(clearPassword)) {
            return new Uid(username);
        }
        throw new InvalidPasswordException();
    }
    throw new UnknownUidException();
} else {
    throw UnsupportedOperationException("Authentication is not supported on " + objectClass);
}
