/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package samples.kerberos.scripts

import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationalAttributes
import org.identityconnectors.framework.spi.operations.AuthenticateOp
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp
import org.identityconnectors.framework.spi.operations.SyncOp

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_CREATABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_UPDATEABLE

def operation = operation as OperationType
def log = log as Log

log.info("Entering {0} script", operation);
assert operation == OperationType.SCHEMA, 'Operation must be a SCHEMA'

// The __NAME__ and __UID__ are the principal name

builder.schema({
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attribute OperationalAttributes.LOCK_OUT_NAME
        attribute OperationalAttributes.PASSWORD_NAME, GuardedString.class
        attributes {
            // The password policy used by this principal.
            "policy" String.class
            // The expiration date of the principal
            "expirationDate" String.class
            // The password expiration date.
            "passwordExpiration" String.class
            // The maximum ticket life for the principal
            "maximumTicketLife" String.class
            // The maximum renewable life of tickets for the principal.
            "maximumRenewableLife" String.class
            // Read-only informative fields
            "lastPasswordChange" String.class, NOT_CREATABLE, NOT_UPDATEABLE
            "lastModified" String.class, NOT_CREATABLE, NOT_UPDATEABLE
            "lastSuccessfulAuthentication" String.class, NOT_CREATABLE, NOT_UPDATEABLE
            "lastFailedAuthentication" String.class, NOT_CREATABLE, NOT_UPDATEABLE
            "failedPasswordAttempts" String.class, NOT_CREATABLE, NOT_UPDATEABLE
        }
        disable SyncOp.class, AuthenticateOp.class, ResolveUsernameOp.class
    }
})