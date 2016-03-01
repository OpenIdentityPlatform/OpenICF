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

//setTimeout <value> : defines global timeout (ms) on expect/send actions
//setTimeoutSec <value> : defines global timeout (sec) on expect/send actions
//send <command> : sends a String or GString of commands
//sendln <command> : sends a String or GString of commands + \r
//sudo <command>: mock the sudo command, using sudo cmd, sudo prompt and user password defined in the configuration
//sendControlC: sends a Ctrl-C interrupt sequence
//sendControlD: sends a Ctrl-D sequence
//promptReady <prompt> <retry>: force the connection to be in prompt ready mode. Returns true if success, false if failed
//expect <pattern>: expect a match pattern from the Read buffer
//expect <pattern>, <Closure>: expect a match pattern from the Read buffer and associate a simple Closure to be performed on pattern match.
//expect <List of matches>: expect a list of different match pattern
//match: defines a global match pattern and a Closure within a call to expect<List>
//regexp: defines a Perl5 style regular expression and a Closure within a call to expect<List>
//timeout: defines a local timeout and a Closure within a call to expect
// The following constants: TIMEOUT_FOREVER, TIMEOUT_NEVER, TIMEOUT_EXPIRED, EOF_FOUND


log.info("Entering {0} script", operation);
assert operation == OperationType.TEST, 'Operation must be a TEST'

// Initial Expect read buffer should be filled with welcome message and prompt
// Something like:
// Welcome to Ubuntu 14.04.4 LTS (GNU/Linux 3.13.0-46-generic i686)\r\n\r\n
// * Documentation:  https://help.ubuntu.com/\r\n\r\n
// Last login: Wed Feb 24 11:11:26 2016 from localhost\r\r\n
// user@host:~$

// "user@host:~$ " is the prompt and it has been defined in the connector configuration
def prompt = configuration.getPrompt()

// Let's set global timeout to 2 seconds - default is 10 sec
// Special values:
// Never timeout, wait forever: TIMEOUT_FOREVER
// No timeout, If nothing is found right away, don't wait: TIMEOUT_NEVER

setTimeoutSec 2

// The prompt is the first thing we should expect from the connection
// before sending any commands
// The most simple way is to just expect it without doing nothing.
// The problem is that a timeout is not handled...
// If we do not have the prompt after the timeout, program will just continue

expect prompt

// The initial buffer content was ending with the prompt
// Now the buffer is empty since the previous call to 'expect prompt'
// We can try to expect prompt again, but that will timeout.
// A call to expect returns an integer values. If negative, it is an error code.
// 2 special values are injected to the script: TIMEOUT_EXPIRED, EOF_FOUND

if (TIMEOUT_EXPIRED == expect(prompt)) {
    log.info("TIMEOUT EXPIRED")
}
// just send carriage return to fill the read buffer with the prompt
sendln ""

// Same but with a closure to do something if we have the prompt
expect prompt, { log.info("Prompt ready!") }

// One way to make sure we are prompt ready
// is to set a boolean in a closure when the prompt is ready
// If not, send Ctrl+C and try again...
// If still no prompt, just throw and leave

// send sleep 4 since our global timeout is 2 sec
sendln "sleep 4"

def ready = false
def maxTry = 0
// Should timeout
expect prompt, { ready = true }
while (!ready) {
    log.info("Trying to get the prompt...")
    // That will kill the "sleep 4"
    sendControlC()
    expect prompt, { ready = true }
    maxTry++
    if (maxTry > 5) {
        throw new ConnectorException("Can't get the session prompt with Ctrl+C")
    }
}
log.info("Prompt ready after Ctrl+C...")

// This is actually what the promptReady() command is meant for.
// It accepts the prompt string and an amount of retries with Ctrl+C
// Return true if connection is in "Prompt Ready" mode, false otherwise

if (!promptReady(2)) {
    throw new ConnectorException("promptReady can't get the session prompt")
} else {
    log.info("Prompt ready after calling promptReady()...")
}

// Another common use case is that you may run a command that you don't know
// how long it will take to return... 1 sec? 20 sec?
// In that case you create a local infinite timeout
sendln "sleep 5; echo TEST"
expect(
        [
                match("TEST") { log.info("Caught the TEST string after infinite timeout...") },
                timeout(TIMEOUT_FOREVER) {}
        ]
)

log.info("Passed the TIMEOUT_FOREVER test!...")

//The opposite: you don't want to wait if the pattern is not there
// The Read buffer should be empty at this stage

expect(
        [
                match(prompt) {},
                timeout(TIMEOUT_NEVER) { log.info("Don't want to wait for prompt ready...") }
        ]
)
log.info("Passed the TIMEOUT_NEVER prompt ready tests!...")

// Simple send/expect
log.info("Simple echo TEST...")
send "echo TEST\n"
// Read Buffer should now contain:
// >>TEST\r\nuser@host:~$ <<
expect "TEST", { log.info("TEST read ok") }

// Read Buffer should now contain:
// >>\r\nuser@host:~$ <<
// After this next call to expect, read buffer should be empty..
expect prompt

// Generate a random number between 10-99
// And then use expect with a list of pattern matches
// Also sets a local timeout match if random number is > 69
log.info("Random number test...")
sendln "echo \$RANDOM |head -c 2; echo"
expect(
        [
                match("1?") { log.info "Got 10-ish" },
                match("2?") { log.info "Got 20 some" },
                match("3?") { log.info "Got 30 some" },
                regexp("^[4|5|6]") { log.info "Got something between 40 and 69" },
                timeout(500) { log.info "No luck!" }
        ]
)

expect prompt

// Play the same game but this time ask the pattern matchers to carry on
// looping on found, Optionally you can reset the timeout to make sure it will not get expired

log.info("Random numbers loop test...")
sendln "echo \$RANDOM |head -c 2; echo"
sendln "echo \$RANDOM |head -c 2; echo"
sendln "echo \$RANDOM |head -c 2; echo"
sendln "echo \$RANDOM |head -c 2; echo"
expect(
        [
                match("1?") {
                    log.info "Got 10-ish"
                    it.exp_continue_reset_timer()
                },
                match("2?") {
                    log.info "Got 20 some"
                    it.exp_continue_reset_timer()

                },
                match("3?") {
                    log.info "Got 30 some"
                    it.exp_continue_reset_timer()
                },
                regexp("^[4|5|6|7|8|9]") {
                    log.info "Got something between 40 and 99"
                    it.exp_continue_reset_timer()
                },
                match(prompt) {
                    log.info "Got the prompt"
                    it.exp_continue()
                },
                timeout(1000) { log.info "Loop completed..." }
        ]
)
// Prompt should be the last match

// Perl regexp
sendln("echo abcdef1234567890")
expect(regexp("[a-f]+([0-9]+)") { log.info("Found: " + it.getMatch(0)) })

// Create some text file, leveraging Ctrl+D
expect prompt

def text = "This is a short story\nto illustrate text file creation\n"

sendln "cat > /tmp/text.txt"
send text
sendControlD()

// Should be im prompt ready mode...