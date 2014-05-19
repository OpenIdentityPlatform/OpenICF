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
import org.identityconnectors.common.Pair
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

import java.text.DateFormat

def action = action as OperationType
def updateAttributes = attributes as Set<Attribute>
def configuration = configuration as ScriptedConfiguration
def id = id as Attribute
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def uid = uid as Uid


log.ok("Default Message")

switch (action) {
    case OperationType.UPDATE:
        switch (objectClass) {
            case ObjectClass.ACCOUNT:
                def ObjectCacheLibrary library = ObjectCacheLibrary.instance

                Pair<ConnectorObject, Date> current = library.getStore(objectClass).get(uid.uidValue)
                if (null != current) {
                    ConnectorObjectBuilder builder = new ConnectorObjectBuilder()
                    builder.setObjectClass(objectClass)
                    builder.addAttributes(AttributeUtil.filterUid(current.key.attributes))
                    builder.setUid(uid)

                    for (Attribute a : updateAttributes) {
                        if (a.is(Name.NAME)) {
                            if (a.value == null || a.value.size() == 0) {
                                throw new InvalidAttributeValueException("Expecting non empty value");
                            } else if (a.value.size() > 1) {
                                throw new InvalidAttributeValueException("Expecting single value");
                            } else if (AttributeUtil.getSingleValue(a) instanceof String) {
                                builder.setName(AttributeUtil.getStringValue(a))
                            } else {
                                throw new InvalidAttributeValueException("Expecting String value");
                            }
                        } else if (a.is("userName")) {
                            if (a.value == null || a.value.size() == 0) {
                                throw new InvalidAttributeValueException("Expecting non empty value");
                            } else if (a.value.size() > 1) {
                                throw new InvalidAttributeValueException("Expecting single value");
                            } else if (AttributeUtil.getSingleValue(a) instanceof String) {
                                builder.addAttribute("userName", AttributeUtil.getStringValue(a))
                            } else {
                                throw new InvalidAttributeValueException("Expecting String value");
                            }
                        } else if (a.is("email")) {
                            if (a.value == null || a.value.size() == 0) {
                                throw new InvalidAttributeValueException("Expecting non null value");
                            } else {
                                for (Object v : a.value) {
                                    if (!(v instanceof String)) {
                                        throw new InvalidAttributeValueException("Expecting String value");
                                    }
                                }
                                builder.addAttribute(a)
                            }
                        } else if (a.is("active")) {
                            if (a.value == null || a.value.size() == 0) {
                                throw new InvalidAttributeValueException("Expecting non empty value");
                            } else if (a.value.size() > 1) {
                                throw new InvalidAttributeValueException("Expecting single value");
                            } else if (AttributeUtil.getSingleValue(a) instanceof Boolean) {
                                builder.addAttribute("active", AttributeUtil.getBooleanValue(a))
                            } else {
                                throw new InvalidAttributeValueException("Expecting Boolean value");
                            }
                        } else if (a.is("createDate")) {
                            throw new InvalidAttributeValueException("Try update non modifiable attribute");
                        } else if (a.is("lastModified")) {
                            throw new InvalidAttributeValueException("Try update non modifiable attribute");
                        } else if (a.is("sureName")) {
                            if (a.value.size() > 1) {
                                throw new InvalidAttributeValueException("Expecting single value");
                            } else if (a.value == null || a.value.size() == 0) {
                                builder.addAttribute("sureName")
                            } else if (AttributeUtil.getSingleValue(a) instanceof String) {
                                builder.addAttribute("sureName", AttributeUtil.getStringValue(a))
                            } else {
                                throw new InvalidAttributeValueException("Expecting String value");
                            }
                        } else if (a.is("passwordHistory")) {
                            throw new InvalidAttributeValueException("Try update non modifiable attribute");
                        } else {
                            builder.addAttribute(a)
                        }
                    }
                    builder.addAttribute("lastModified", DateFormat.getDateTimeInstance().format(new Date()));
                    return ObjectCacheLibrary.instance.update(builder.build())
                } else {
                    throw new UnknownUidException(uid, objectClass)
                }
                break
            case ObjectClass.GROUP:
                return uid
                break
            case ObjectClass.ALL:
                log.error("ICF Framework MUST reject this")
                break
            case TestHelper.TEST:
                //Sample script for IDME-180:Support MVCC Revision attribute
                TestHelper.exceptionTest(action, objectClass, uid, options)
                break
            case TestHelper.SAMPLE:
                throw UnsupportedOperationException("Update operation of type:" + objectClass)
            default:
                throw UnsupportedOperationException("Update operation of type:" + objectClass)
        }
        break
    case OperationType.ADD_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException()
    case OperationType.REMOVE_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException()
    default:
        throw new ConnectorException("UpdateScript can not handle action:" + action.name())
}






