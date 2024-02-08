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

package org.forgerock.openicf.connectors.ssh

import static org.identityconnectors.common.StringUtil.isBlank
import static org.identityconnectors.common.StringUtil.isEmpty

/**
 * CommandLineBuilder is a simple groovy class that leverages methodMissing() to
 * dynamically build a typical shell command line with options and switches.
 * <p>
 * For instance, to build the following string:
 * <code>/usr/sbin/useradd -c 'this is the description' -d /home/user -g group -s /bin/bash  login</code>
 * the following code could be used:
 * <code>def cmd = new CommandLineBuilder("/usr/sbin/useradd").c("this is the description").d("home/user").g("group").s("/bin/bash").append("login").build()</code>
 * The alternate "double dash" can be used as well. The property 'dash' can be set globally or the double dash can be passed on each call.
 * <code>def cmd = new CommandLineBuilder("/usr/sbin/useradd").comment("this is the description", "--").d("home/user").g("group").s("/bin/bash").append("login").build()</code>
 * <p>
 * A set of properties helps defining the behavior for trimming and quoting,
 *
 */
class CommandLineBuilder {
    def BLANK = " "

    /**
     * Defines the default
     */
    def dash = "-"
    def trim = true
    def quote = true
    def allowBlank = false
    def quoteSymbol = "'"

    def command = new StringBuilder()

    def CommandLineBuilder(String cmd) {
        command.append(cmd)
    }

    def build = {
        command.toString()
    }

    def append(String value) {
        command.append(BLANK).append(value)
        return this
    }

    def methodMissing(String name, args) {
        if (args.length == 0) {
            // pure switch
            command.append(BLANK).append(dash).append(name)
        } else {
            def value = args[0]

            if (isEmpty(value)) {
                return this
            }

            if (!allowBlank && isBlank(value)) {
                return this
            }

            if (args.length == 2) {
                dash = args[1]
            }

            if (trim && !allowBlank) {
                value = value.trim()
            }

            if (quote) {
                def wspace = false
                value.toCharArray().each { char c ->
                    if (Character.isWhitespace(c)) {
                        wspace = true
                    }
                }
                if (wspace) {
                    value = quoteSymbol + value + quoteSymbol
                }
            }
            command.append(BLANK).append(dash).append(name).append(BLANK).append(value)
        }
        return this
    }
}