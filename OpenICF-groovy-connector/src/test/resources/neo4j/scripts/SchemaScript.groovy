/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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


import groovyx.net.http.RESTClient
import org.apache.http.client.HttpClient
import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.MULTIVALUED
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_CREATABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_UPDATEABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.REQUIRED

def operation = operation as OperationType
def configuration = configuration as ScriptedRESTConfiguration
def httpClient = connection as HttpClient
def connection = customizedConnection as RESTClient
def log = log as Log



return builder.schema({
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attribute Neo4JUtils.LABELS, String.class, EnumSet.of(MULTIVALUED)
    }
    objectClass {
        type Neo4JUtils.RELATION
        attribute Neo4JUtils.LABELS, String.class, EnumSet.of(REQUIRED)
        attribute Neo4JUtils.FROM, String.class, EnumSet.of(REQUIRED)
        attribute Neo4JUtils.FROM_LABEL, String.class, EnumSet.of(NOT_UPDATEABLE, NOT_CREATABLE, MULTIVALUED)
        attribute Neo4JUtils.TO, String.class, EnumSet.of(REQUIRED)
        attribute Neo4JUtils.TO_LABEL, String.class, EnumSet.of(NOT_UPDATEABLE, NOT_CREATABLE, MULTIVALUED)

    }
})