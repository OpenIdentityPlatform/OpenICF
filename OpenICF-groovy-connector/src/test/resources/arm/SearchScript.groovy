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
 * Portions Copyrighted 2024 3A Systems, LLC
 */

@Grapes([
        @Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1'),
        @Grab(group = 'commons-io', module = 'commons-io', version = '2.16.1')]
)
import groovyx.net.http.RESTClient
import org.apache.http.client.HttpClient
import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.PredefinedAttributes
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.Filter

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.GET

def operation = operation as OperationType
def configuration = configuration as ScriptedRESTConfiguration
def httpClient = connection as HttpClient
def connection = customizedConnection as RESTClient
def filter = filter as Filter
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions


if (filter instanceof EqualsFilter && ((EqualsFilter) filter).getAttribute().is(Uid.NAME)) {
    //This is a Read

    def objectID = AttributeUtil.getStringValue(((EqualsFilter) filter).getAttribute());

    if (objectClass.equals(ObjectClass.GROUP)) {
        connection.request(GET) { req ->
            uri.path = '/domain/groups/' + objectID
            contentType:
            JSON

            response.success = { resp, groupJson ->
                handler {
                    uid groupJson.name
                    id groupJson.name
                    attribute PredefinedAttributes.DESCRIPTION, groupJson?.description
                    attribute 'endpoints', groupJson.endpoints
                    attribute 'subGroups', groupJson.subGroups
                }
            }

            response."401" = {
                throw new InvalidCredentialException("Problem accessing /domain/endpoints. Reason: Unauthorized")
            }
            response."404" = { /* Not Exists, do nothing*/ }
            response."408" = { throw new ConnectException('Endpoint did not respond, time-out (only when sync=true)') }
            response."409" = {
                throw new ConnectException('Conflict. Endpoint is in queue mode and synchronous request cannot be made (only when sync=true), or if noResp=true, then means that the request is not supported.')
            }
            response."410" = { throw new ConnectException('Gone. Endpoint not found.') }
            response."429" = {
                throw new ConnectException('Cannot make a request at the moment, already ongoing other request for this endpoint')
            }

            response.failure = { resp, json ->
                throw new ConnectorException(resp as String)
            }
        }
    } else {
        connection.request(GET) { req ->
            uri.path = '/domain/endpoints'
            uri.query = [name: objectID]
            contentType:
            JSON

            response.success = { resp, json ->
                println(json)
                json.each { item ->
                    handler {
                        uid item.name
                        id item.name
                        attribute 'type', item.type
                        attribute 'status', item.status
                    }
                }
            }

            response."401" = {
                throw new InvalidCredentialException("Problem accessing /domain/endpoints. Reason: Unauthorized")
            }
            response."404" = { throw new ConnectException('Requested endpoint\'s resource is not found') }
            response."408" = { throw new ConnectException('Endpoint did not respond, time-out (only when sync=true)') }
            response."409" = {
                throw new ConnectException('Conflict. Endpoint is in queue mode and synchronous request cannot be made (only when sync=true), or if noResp=true, then means that the request is not supported.')
            }
            response."410" = { throw new ConnectException('Gone. Endpoint not found.') }
            response."429" = {
                throw new ConnectException('Cannot make a request at the moment, already ongoing other request for this endpoint')
            }

            response.failure = { resp, json ->
                throw new ConnectorException(resp as String)
            }
        }
    }

    return

} else if (filter != null) {
    //This is a Search

}


connection.request(GET) { req ->
    if (objectClass.equals(ObjectClass.GROUP)) {
        uri.path = '/domain/groups/'
    } else {
        uri.path = '/domain/endpoints/'
    }
    contentType:
    JSON

    response.success = { resp, json ->

        if (objectClass.equals(ObjectClass.GROUP)) {
            for (item in json) {

                connection.request(GET) { subReq ->

                    uri.path = '/domain/groups/' + item

                    contentType:
                    JSON

                    response.success = { subResp, groupJson ->
                        handler {
                            uid groupJson.name
                            id groupJson.name
                            attribute PredefinedAttributes.DESCRIPTION, groupJson?.description
                            attribute 'endpoints', groupJson.endpoints
                            attribute 'subGroups', groupJson.subGroups
                        }
                    }
                }
            }
        } else {
            for (item in json) {
                handler {
                    uid item.name
                    id item.name
                    attribute 'type', item.type
                    attribute 'status', item.status
                }
            }
        }
    }

    response."401" = {
        throw new InvalidCredentialException("Problem accessing /domain/endpoints. Reason: Unauthorized")
    }
    response."404" = { throw new ConnectException('Requested endpoint\'s resource is not found') }
    response."408" = { throw new ConnectException('Endpoint did not respond, time-out (only when sync=true)') }
    response."409" = {
        throw new ConnectException('Conflict. Endpoint is in queue mode and synchronous request cannot be made (only when sync=true), or if noResp=true, then means that the request is not supported.')
    }
    response."410" = { throw new ConnectException('Gone. Endpoint not found.') }
    response."429" = {
        throw new ConnectException('Cannot make a request at the moment, already ongoing other request for this endpoint')
    }

    response.failure = { resp, json ->
        throw new ConnectorException(resp as String)
    }
}

