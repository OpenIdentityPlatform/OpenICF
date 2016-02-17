/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

import org.forgerock.openicf.connectors.ssh.SSHConfiguration
import org.forgerock.openicf.connectors.ssh.SSHConnection
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException

def operation = operation as OperationType
def configuration = configuration as SSHConfiguration
def connection = connection as SSHConnection
def log = log as Log

// SSH Connector specific bindings

//setTimeout <value> : defines global timeout on expect/send actions
//send <command> : sends a String or GString of commands
//sendln <command> : sends a String or GString of commands + \r
//sendControlC: sends a Ctrl-C interrupt sequence
//sendControlD: sends a Ctrl-D sequence
//expect <pattern>: expect a match pattern from the Read buffer
//expect <pattern>, <Closure>: expect a match pattern from the Read buffer and associate a simple Closure to be performed on pattern match.
//expect <List of matches>: expect a list of different match pattern
//global: defines a global match pattern and a Closure within a call to expect
//regexp: defines a Perl5 style regular expression and a Closure within a call to expect
//timeout: defines a local timeout and a Closure within a call to expect

log.info("Entering {0} script", operation);
// We assume the operation is TEST
assert operation == OperationType.TEST, 'Operation must be a TEST'

def prompt = configuration.getPrompt()
def ready = false
def message = ""

setTimeout 2

// The prompt is the first thing we should expect from the connection
expect prompt, { ready = true }
while (!ready) {
    log.info("Trying to get the prompt...")
    sendControlC()
    expect prompt, { ready = true }
}
log.info("Prompt ready...")

sendln "/bin/uname"
expect prompt, {
    message = it.getBuffer().substring(0, it.getMatchedWhere()).trim()
}

log.info(message)
if (!message.contains("Linux")) {
    throw new ConnectorException("Test failed!")
}

// Put the connection in prompt ready mode for next usage
sendln ""