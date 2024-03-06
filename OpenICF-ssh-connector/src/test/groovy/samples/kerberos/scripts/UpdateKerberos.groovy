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


package samples.kerberos.scripts

import org.forgerock.openicf.connectors.ssh.CommandLineBuilder
import org.forgerock.openicf.connectors.ssh.SSHConfiguration
import org.forgerock.openicf.connectors.ssh.SSHConnection
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.*

import static org.identityconnectors.common.security.SecurityUtil.decrypt

// SSH Connector specific bindings

//setTimeout <value> : defines global timeout (ms) on expect/send actions
//setTimeoutSec <value> : defines global timeout (sec) on expect/send actions
//send <command> : sends a String or GString of commands
//sendln <command> : sends a String or GString of commands + \r
//sendControlC: sends a Ctrl-C interrupt sequence
//sendControlD: sends a Ctrl-D sequence
//sudo <command>: mock the sudo command, using sudo cmd, sudo prompt and user password defined in the configuration
//promptReady <prompt> <retry>: force the connection to be in prompt ready mode. Returns true if success, false if failed
//expect <pattern>: expect a match pattern from the Read buffer
//expect <pattern>, <Closure>: expect a match pattern from the Read buffer and associate a simple Closure to be performed on pattern match.
//expect <List of matches>: expect a list of different match pattern
//match: defines a global match pattern and a Closure within a call to expect<List>
//regexp: defines a Perl5 style regular expression and a Closure within a call to expect<List>
//timeout: defines a local timeout and a Closure within a call to expect
// The following constants: TIMEOUT_FOREVER, TIMEOUT_NEVER, TIMEOUT_EXPIRED, EOF_FOUND

def operation = operation as OperationType
def attributes = attributes as Set<Attribute>
def configuration = configuration as SSHConfiguration
def connection = connection as SSHConnection
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def uid = uid.getUidValue() as String
def attrs = new AttributesAccessor(attributes)
def kadmin = configuration.getPropertyBag().get("kadmin") as ConfigObject

// We assume the operation is UPDATE
switch (operation) {
    case OperationType.UPDATE:
        break
    case OperationType.ADD_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                objectClass.objectClassValue + " is not supported.")
    case OperationType.REMOVE_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                objectClass.objectClassValue + " is not supported.")
    default:
        throw new ConnectorException("UpdateScript can not handle operation:" + operation.name())
}

log.info("Entering {0} script", operation);

// The prompt is the first thing we should expect from the connection
if (!promptReady(2)) {
    throw new ConnectorException("Can't get the session prompt")
}
log.info("Prompt ready...")

// Check if realm has been set... append the default one if not
if (!uid.contains('@')) {
    uid += "@" + kadmin.default_realm
}

// We may have to run 3 different commands for modify:
// - modify_principal (modprinc): to modify properties
// - rename_principal (renprinc): to rename the principal
// - change_password (cpw): to change the principal's password
//
// For now, we just handle change password and unlock a locked principal

// Example of change password:
// kadmin.local -p openidm/admin -q 'cpw j164884@EXAMPLE.COM'
// Authenticating as principal openidm/admin with password.
// Enter password for principal "j164884@EXAMPLE.COM":
// Re-enter password for principal "j164884@EXAMPLE.COM":
// Password for "j164884@EXAMPLE.COM" changed.

if (attrs.getPassword() != null) {
    log.info("Preparing to change password for {0}", uid)

    def command = new CommandLineBuilder(kadmin.cmd).p(kadmin.user).q("cpw $uid").build()
    log.info("Command is {0}", command)

    if (!sudo(command)) {
        throw new ConnectorException("Failed to run sudo $command")
    }

    if (kadmin.cmd.endsWith("kadmin")) {
        expect "\nPassword for $kadmin.user", { sendln kadmin.password }
    }
    expect "\nEnter password for principal \"$uid\":", { sendln decrypt(attrs.getPassword()) }
    expect "\nRe-enter password for principal \"$uid\":", { sendln decrypt(attrs.getPassword()) }
    expect(
            [
                    match("Password for \"$uid\" changed.") {
                        log.info("Principal {0} password changed", uid)
                    },
                    match("change_password: Principal does not exist while changing password") {
                        log.info("Principal {0} does not exist", uid)
                        throw new UnknownUidException(uid)
                    },
                    regexp("change_password:\\s([\\w\\s]+)\\swhile\\schanging\\spassword") {
                        log.info("{0} for {1}", it.getMatch(1), uid)
                        throw new InvalidAttributeValueException(it.getMatch(1))
                    },
                    timeout(500) {
                        throw new ConnectorException("Update of $uid password failed")
                    }
            ]
    )
}

// Example of unlock principal
// kadmin.local -p openidm/admin -q 'modprinc -unlock  j164884@EXAMPLE.COM'
// Authenticating as principal openidm/admin with password.
// Principal "j164884@EXAMPLE.COM" modified.

def locked = attrs.findBoolean(OperationalAttributes.LOCK_OUT_NAME)
if (locked != null && !locked) {
    log.info("Preparing to unlock {0}", uid)

    def command = new CommandLineBuilder(kadmin.cmd).p(kadmin.user).q("modprinc -unlock $uid").build()
    log.info("Command is {0}", command)

    if (!sudo(command)) {
        throw new ConnectorException("Failed to run sudo $command")
    }

    if (kadmin.cmd.endsWith("kadmin")) {
        expect "\nPassword for $kadmin.user", { sendln kadmin.password }
    }
    expect(
            [
                    match("Principal \"$uid\" modified.") {
                        log.info("Principal {0} unlocked", uid)
                    },
                    match("Principal does not exist while getting \"$uid\"") {
                        log.info("Principal {0} does not exist", uid)
                        throw new UnknownUidException(uid)
                    },
                    timeout(500) {
                        throw new ConnectorException("Unlock of {0} failed", uid)
                    }
            ]
    )
}

return uid