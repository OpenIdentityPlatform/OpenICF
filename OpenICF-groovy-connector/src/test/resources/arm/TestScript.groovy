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

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.GET

def operation = operation as OperationType
def configuration = configuration as ScriptedRESTConfiguration
def httpClient = connection as HttpClient
def connection = customizedConnection as RESTClient
def log = log as Log

connection.handler.failure = { resp ->
    log.ok "Unexpected failure: ${resp.statusLine}"
}

connection.request(GET) { req ->
    uri.path = '/domain/endpoints'
    uri.query = [type: 'UNKNOWN']
    contentType: JSON

    response.success = { resp, json ->
        
    }

    response."401" = { throw new InvalidCredentialException("Problem accessing /domain/endpoints. Reason: Unauthorized")}
    response."404" = { throw new ConnectException('Requested endpoint\'s resource is not found')}
    response."408" = { throw new ConnectException('Endpoint did not respond, time-out (only when sync=true)')}
    response."409" = { throw new ConnectException('Conflict. Endpoint is in queue mode and synchronous request cannot be made (only when sync=true), or if noResp=true, then means that the request is not supported.')}
    response."410" = { throw new ConnectException('Gone. Endpoint not found.')}
    response."429" = { throw new ConnectException('Cannot make a request at the moment, already ongoing other request for this endpoint')}
    
    response.failure = { resp, json ->
        throw new ConnectorException(resp as String)
    }
}