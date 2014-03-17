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


import ObjectCacheLibrary
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder as ICF
import org.forgerock.openicf.misc.scriptedcommon.Logger
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions

def action = action as ScriptedConnectorBase.Action
def createAttributes = attributes as Map<String, Attribute>
def configuration = configuration as ScriptedConfiguration
def name = id as String
def log = log as Logger
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions


log.info("Entering " + action + " Script");


switch (objectClass) {
    case ObjectClass.ACCOUNT:
        return ObjectCacheLibrary.instance.create(ICF.co {
            uid UUID.randomUUID().toString()
            id name
            attributes(createAttributes.values())
        })
        break
    case ObjectClass.GROUP:
    default:
        throw UnsupportedOperationException("Create operation of type:" + objectClass)
}
