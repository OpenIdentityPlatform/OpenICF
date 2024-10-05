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

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST

def operation = operation as OperationType
def configuration = configuration as ScriptedRESTConfiguration
def httpClient = connection as HttpClient
def connection = customizedConnection as RESTClient
def log = log as Log

connection.handler.failure = { resp ->
    log.ok "Unexpected failure: ${resp.statusLine}"
}

def cleared = connection.post(
        path: "/rest/users",
        contentType: JSON,
        requestContentType: JSON,
        query: [
                _action: "clear"
        ],
        body: 'null'
)


cleared = connection.request(POST, JSON) { req ->
    uri.path = '/rest/users'
    uri.query = [_action: 'clear']
    send JSON, 'null'

    //requestContentType = JSON
    //body = 'null'

    response.success = { resp, json ->
        assert json.size() == 1
        println "Action response: "
        json.responseData.each() { key, value ->
            log.debug "  ${key} : ${value}"
        }
    }
}