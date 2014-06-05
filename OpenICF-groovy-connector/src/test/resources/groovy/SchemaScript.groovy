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
import org.identityconnectors.common.security.GuardedByteArray
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos
import org.identityconnectors.framework.spi.operations.AuthenticateOp
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp
import org.identityconnectors.framework.spi.operations.SchemaOp
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp
import org.identityconnectors.framework.spi.operations.SearchOp
import org.identityconnectors.framework.spi.operations.SyncOp
import org.identityconnectors.framework.spi.operations.TestOp

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.MULTIVALUED
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_CREATABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_READABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_RETURNED_BY_DEFAULT
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_UPDATEABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.REQUIRED

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration
def log = log as Log

builder.schema({
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attribute OperationalAttributeInfos.PASSWORD
        attribute PredefinedAttributeInfos.DESCRIPTION
        attribute 'groups', String.class, EnumSet.of(MULTIVALUED)
        attributes {
            userName String.class, REQUIRED
            email REQUIRED, MULTIVALUED
            __ENABLE__ Boolean.class
            createDate  NOT_CREATABLE, NOT_UPDATEABLE
            lastModified Long.class, NOT_CREATABLE, NOT_UPDATEABLE, NOT_RETURNED_BY_DEFAULT
            passwordHistory String.class, MULTIVALUED, NOT_UPDATEABLE, NOT_READABLE, NOT_RETURNED_BY_DEFAULT
            firstName()
            sn()
        }

    }
    objectClass {
        type ObjectClass.GROUP_NAME
        attribute PredefinedAttributeInfos.DESCRIPTION
        attributes {
            cn REQUIRED
            member REQUIRED, MULTIVALUED
        }
        // ONLY CRUD
    }
    objectClass {
        type '__TEST__'
        container()
        attributes {

            // All possible attribute types

            attributeString String.class
            attributeStringMultivalue String.class, MULTIVALUED

            attributelongp Long.TYPE
            attributelongpMultivalue Long.TYPE, MULTIVALUED

            attributeLong Long.class
            attributeLongMultivalue Long.class, MULTIVALUED

            attributechar Character.TYPE
            attributecharMultivalue Character.TYPE, MULTIVALUED

            attributeCharacter Character.class
            attributeCharacterMultivalue Character.class, MULTIVALUED

            attributedoublep Double.TYPE
            attributedoublepMultivalue Double.TYPE, MULTIVALUED

            attributeDouble Double.class
            attributeDoubleMultivalue Double.class, MULTIVALUED

            attributefloatp Float.TYPE
            attributefloatpMultivalue Float.TYPE, MULTIVALUED

            attributeFloat Float.class
            attributeFloatMultivalue Float.class, MULTIVALUED

            attributeint Integer.TYPE
            attributeintMultivalue Integer.TYPE, MULTIVALUED

            attributeInteger Integer.class
            attributeIntegerMultivalue Integer.class, MULTIVALUED

            attributebooleanp Boolean.TYPE
            attributebooleanpMultivalue Boolean.TYPE, MULTIVALUED

            attributeBoolean Boolean.class
            attributeBooleanMultivalue Boolean.class, MULTIVALUED

            attributebytep Byte.TYPE
            attributebytepMultivalue Byte.TYPE, MULTIVALUED

            attributeByte Byte.class
            attributeByteMultivalued Byte.class, MULTIVALUED

            attributeByteArray byte[].class
            attributeByteArrayMultivalue byte[].class, MULTIVALUED

            attributeBigDecimal BigDecimal.class
            attributeBigDecimalMultivalue BigDecimal.class, MULTIVALUED

            attributeBigInteger BigInteger.class
            attributeBigIntegerMultivalue BigInteger.class, MULTIVALUED

            attributeGuardedByteArray GuardedByteArray.class
            attributeGuardedByteArrayMultivalue GuardedByteArray.class, MULTIVALUED

            attributeGuardedString GuardedString.class
            attributeGuardedStringMultivalue GuardedString.class, MULTIVALUED

            attributeMap Map.class
            attributeMapMultivalue Map.class, MULTIVALUED

        }
    }
    operationOption {
        name "notify"
        disable AuthenticateOp, ResolveUsernameOp, SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp, SyncOp, TestOp
    }
    operationOption {
        name "force"
        type Boolean
    }

    defineOperationOption OperationOptionInfoBuilder.buildPagedResultsCookie(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildPagedResultsOffset(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildPageSize(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildSortKeys(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildRunWithUser()
    defineOperationOption OperationOptionInfoBuilder.buildRunWithPassword()
}
)