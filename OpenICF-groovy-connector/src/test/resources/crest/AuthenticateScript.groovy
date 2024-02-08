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

import org.forgerock.json.resource.ActionRequest
import org.forgerock.json.resource.Connection
import org.forgerock.json.resource.ForbiddenException
import org.forgerock.json.resource.InternalServerErrorException
import org.forgerock.json.resource.NotFoundException
import org.forgerock.json.resource.PermanentException
import org.forgerock.json.resource.Requests
import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.forgerock.services.context.RootContext
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

def operation = operation as OperationType
def configuration = configuration as ScriptedCRESTConfiguration
def connection = connection as Connection
def username = username as String
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def password = password as GuardedString;

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        ActionRequest request = Requests.newActionRequest("system/ldap/account", "authenticate")
        request.setAdditionalParameter("username", username)
        request.setAdditionalParameter("password", SecurityUtil.decrypt(password))
        try {
            def response = connection.action(new RootContext(), request);
            return new Uid(response._id, response._rev)
        } catch (ForbiddenException e) {
            if (true) {
                throw new PasswordExpiredException(e.getMessage(), e)
            } else {
                throw new PermissionDeniedException(e.getMessage(), e)
            }
        } catch (NotFoundException e) {
            throw new UnknownUidException(e.getMessage(), e)
        } catch (InternalServerErrorException e) {
            throw new ConnectorSecurityException(e.getMessage(), e)
        }
        catch (PermanentException e) {
            if (e.getCode() == 401) {
                throw new InvalidPasswordException(e.getMessage(), e);
            }
            throw new InvalidCredentialException(e.getMessage(), e);
        }
        break
    default:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                objectClass.objectClassValue + " is not supported.")
}
