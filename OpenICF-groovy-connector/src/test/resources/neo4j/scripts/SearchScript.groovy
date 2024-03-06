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
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.Filter

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST

def operation = operation as OperationType
def configuration = configuration as ScriptedRESTConfiguration
def httpClient = connection as HttpClient
def connection = customizedConnection as RESTClient
def filter = filter as Filter
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions


if (!objectClass.is(Neo4JUtils.RELATION) && filter instanceof EqualsFilter && ((EqualsFilter) filter).attribute.is(Uid.NAME)) {
    //This is a Read Request
    def uid = AttributeUtil.getStringValue(((EqualsFilter) filter).attribute)
    connection.request(GET, JSON) { req ->
        uri.path = "/db/data/node/${uid}"

        response.success = { resp, json ->

            //Relation use type
            def labels = json.metadata?.labels
            def data = json.data as Map

            handler {
                //Required
                delegate.uid json.metadata?.id as String
                delegate.id json.metadata?.id as String
                delegate.objectClass objectClass

                //Optional
                attribute Neo4JUtils.LABELS, labels

                data.each { key, value ->
                    attribute key, value
                }
            }

        }

        response."404" = { HttpResponseDecorator resp ->
            //Node does not exists, return null
        }
        
        response.failure = { HttpResponseDecorator resp ->
            throw new ConnectorException("GET failed with code:${resp.statusLine.statusCode} - ${resp.statusLine.reasonPhrase}")
        }
    }
} else {

    def where = null;
    if (null != filter) {
        //Todo support __LABELS__
        where = filter.accept(new CypherFilterVisitor(objectClass), null)
    }

    def json
    if (objectClass.is(Neo4JUtils.RELATION)) {
        //Todo create multiple statements if there are multiple labels
        json = new JsonBuilder([
                statements: [
                        [
                                //Fetch types [n:`ACTED IN`|:`DIRECTED`]
                                statement: "MATCH (a)-[n]->(b) ${StringUtil.isNotBlank(where) ? "WHERE " + where : ""} RETURN ID(n), n, TYPE(n), ID(a), LABELS(a), ID(b), LABELS(b)"
                        ]
                ]
        ])
    } else {
        //Todo support __ALL__
        def labels = Neo4JUtils.fetchLabels(objectClass, null)
        json = new JsonBuilder([
                statements: [
                        [
                                statement: "MATCH (n${labels}) ${StringUtil.isNotBlank(where) ? "WHERE " + where : ""}  RETURN ID(n), n, LABELS(n)"
                        ]
                ]
        ])
    }

    //Todo Support Paging
    if (null != options.pageSize) {

        "MATCH (n) RETURN n ORDER BY ID(n) SKIP ${options.pagedResultsOffset} LIMIT ${options.pageSize}"
        options.pageSize
        if (null != options.pagedResultsCookie) {
            options.pagedResultsCookie
        }
        if (null != options.pagedResultsOffset) {
            options.pagedResultsOffset
        }
    }

    if (log.ok) {
        log.ok("Transactional Cypher request {0}", json.toPrettyString())
    }

    connection.request(POST, JSON) { req ->
        uri.path = "/db/data/transaction/commit"
        headers["X-Stream"] = "true"
        body = json.toString()

        response.success = { HttpResponseDecorator resp ->

            if (objectClass.is(Neo4JUtils.RELATION)) {
                Neo4JUtils.parserResponse(resp, {
                    def id = it.row[0] as String
                    def relation = it.row[1] as Map
                    def relationType = it.row[2] as String
                    def fromID = it.row[3] as String
                    def fromLabels = it.row[4] as List
                    def toID = it.row[5] as String
                    def toLabels = it.row[6] as List

                    handler {
                        //Required
                        delegate.uid id
                        delegate.id id
                        delegate.objectClass objectClass

                        attribute Neo4JUtils.FROM, fromID
                        attribute Neo4JUtils.FROM_LABEL, fromLabels
                        attribute Neo4JUtils.TO, toID
                        attribute Neo4JUtils.TO_LABEL, toLabels

                        //Optional
                        attribute Neo4JUtils.LABELS, relationType

                        relation.each { key, value ->
                            attribute key, value
                        }
                    }
                })
            } else {
                Neo4JUtils.parserResponse(resp, {
                    def id = it.row[0] as String
                    def node = it.row[1] as Map
                    def nodeLabels = it.row[2] as List

                    handler {
                        //Required
                        delegate.uid id
                        delegate.id id
                        delegate.objectClass objectClass

                        //Optional
                        attribute Neo4JUtils.LABELS, nodeLabels

                        node.each { key, value ->
                            attribute key, value
                        }
                    }
                })
            }


        }

        response.failure = { HttpResponseDecorator resp ->
            throw new ConnectorException("REST POST failed with code:${resp.statusLine.statusCode} - ${resp.statusLine.reasonPhrase}")
        }
    }

}