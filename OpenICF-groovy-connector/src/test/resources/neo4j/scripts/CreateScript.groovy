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

import groovy.json.JsonBuilder
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import org.apache.http.client.HttpClient
import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributesAccessor
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST

def operation = operation as OperationType
def createAttributes = new AttributesAccessor(attributes as Set<Attribute>)
def configuration = configuration as ScriptedRESTConfiguration
def httpClient = connection as HttpClient
def connection = customizedConnection as RESTClient
def name = id as String
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions

def node = Neo4JUtils.createNode(attributes as Set<Attribute>)

def json
if (objectClass.is(Neo4JUtils.RELATION)) {

    String type = createAttributes.findString(Neo4JUtils.LABELS)
    String from = createAttributes.findString(Neo4JUtils.FROM)
    String to = createAttributes.findString(Neo4JUtils.TO)
    if (StringUtil.isBlank(type)) {
        throw new InvalidAttributeValueException("Required attribute: __LABEL__: A single relationship type must be specified for CREATE")
    }
    if (StringUtil.isBlank(from)) {
        throw new InvalidAttributeValueException("Required attribute: __FROM__")
    }
    if (StringUtil.isBlank(to)) {
        throw new InvalidAttributeValueException("Required attribute: __TO__")
    }
    json = new JsonBuilder([
            statements: [
                    [
                            statement : "START a=node(${from}), b=node(${to}) CREATE (a)-[r:`${type}` { props }]->(b) RETURN id(r)",
                            parameters: [
                                    props: node
                            ]
                    ]
            ]
    ])

} else {
    def labels = Neo4JUtils.fetchLabels(objectClass, createAttributes.find(Neo4JUtils.LABELS))
    json = new JsonBuilder([
            statements: [
                    [
                            statement : "CREATE (n${labels} { props }) RETURN id(n)",
                            parameters: [
                                    props: node
                            ]
                    ]
            ]
    ])

}

if (log.ok) {
    log.ok("Transactional Cypher request {0}", json.toPrettyString())
}

def uid = connection.request(POST, JSON) { req ->
    uri.path = "/db/data/transaction/commit"
    body = json.toString()

    response.success = { HttpResponseDecorator resp ->
        return Neo4JUtils.parserResponse(resp, {
            def id = it.row?.find { true }
            if (null != id) {
                return new Uid(id as String)
            }
            throw new ConnectorException("The ID is missing from response")
        })
    }

    response.failure = { HttpResponseDecorator resp ->
        throw new ConnectorException("REST POST failed with code:${resp.statusLine.statusCode} - ${resp.statusLine.reasonPhrase}")
    }
}

if (null != uid) {
    return uid
}
throw new ConnectorException("Response is not parsed")

