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

import org.forgerock.json.fluent.JsonValue
import org.forgerock.json.resource.Connection
import org.forgerock.json.resource.CreateRequest
import org.forgerock.json.resource.QueryResult
import org.forgerock.json.resource.Requests
import org.forgerock.json.resource.Resource
import org.forgerock.json.resource.RootContext
import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.Base64
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedByteArray
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.common.security.SecurityUtil
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeBuilder
import org.identityconnectors.framework.common.objects.AttributeInfo
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.AttributesAccessor
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.ObjectClassInfo
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.OperationalAttributes
import org.identityconnectors.framework.common.objects.Schema
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.FilterBuilder

def action = action as OperationType
def createAttributes = attributes as Set<Attribute>
def attributeAccessor = new AttributesAccessor(createAttributes);
def configuration = configuration as ScriptedCRESTConfiguration
def connection = connection as Connection
def name = id as String
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def schema = schema as Schema


log.info("Entering " + action + " Script");

ObjectClassInfo ocInfo = schema.findObjectClassInfo(objectClass.objectClassValue)
if (null != ocInfo) {

    def converter = { v ->
        if (v instanceof GuardedString) {
            return SecurityUtil.decrypt(v as GuardedString);
        } else if (v instanceof GuardedByteArray) {
            return Base64.encode(SecurityUtil.decrypt(v as GuardedByteArray));
        } else if (v instanceof byte[]) {
            return Base64.encode(v as byte[]);
        } else {
            return v
        }
    }
    def user = [:]
    for (AttributeInfo attributeInfo : ocInfo.attributeInfo) {
        if (attributeInfo.is(Name.NAME)){
            continue
        }
        Attribute attribute = attributeAccessor.find(attributeInfo.name);
        if (attributeInfo.required && (null == attribute || null == attribute.value || attribute.value.size() == 0)) {
            throw new InvalidAttributeValueException("Missing required attribute:" + attributeInfo.name);
        }
        if (!attributeInfo.multiValued && null != attribute && attribute?.value?.size() > 1) {
            throw new InvalidAttributeValueException("Non multivalued attribute has multiple values:" + attributeInfo.name);
        }

        def value = null;
        if (null != attribute) {
            if (attributeInfo.multiValued) {
                value = attribute.value.each converter
            } else {
                value = converter attribute.value.first()
            }
        }

        user[attributeInfo.name] = value
    }

    CreateRequest request = Requests.newCreateRequest("users", name, new JsonValue(user))
    Resource resource = connection.create(new RootContext(), request)
    return new Uid(resource.getId(), resource.getRevision())

} else {
    throw UnsupportedOperationException("Create operation of type:" + objectClass.objectClassValue)
}

//switch (objectClass) {
//    case ObjectClass.ACCOUNT:
//        CRESTBuilder.create("users") { reg ->
//            newResourceId = "dsfd"
//            content {
//                userName '12'
//                givenName 'John'
//                sn 'Doe'
//                mail 'jdoe@example.com'
//                telephoneNumber '1-555-555-1212'
//            }
//        }.onSuccess{ response ->
//            new Uid(response.id, response.revision)
//        }.onFailure{ ex ->
//            throw ex;
//        }.execute(connection, null)
//
//
//
//        def filter = FilterBuilder.equalTo(AttributeBuilder.build(Name.name, "123"))
//
//        CRESTBuilder.query("users") {
//            queryFilter = filter
//        }.onSuccess{ QueryResult response ->
//            while (response.pagedResultsCookie != null){
//                request.pagedResultsCookie = response.pagedResultsCookie
//                execute()
//            }
//        }.onFailure{ ex ->
//            throw ex;
//        }.execute(connection, new RootContext())

//        def queryResult = builder.query("users"){queryRequest ->
//
//        }.execute(connection, context){ res ->
//            handler{
//                uid res.id, res.revision
//                res.content.each {
//
//                }
//            }
//        }
//
//        break
//    default:
//        throw UnsupportedOperationException("Create operation of type:" + objectClass)
//}
