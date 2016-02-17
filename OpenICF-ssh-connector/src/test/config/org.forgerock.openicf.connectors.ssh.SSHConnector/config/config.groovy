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
import org.identityconnectors.common.security.GuardedString

configuration {
    authenticateScriptFileName = "AuthenticateScript.groovy"
    createScriptFileName = "CreateScript.groovy"
    deleteScriptFileName = "DeleteScript.groovy"
    resolveUsernameScriptFileName = "ResolveUsernameScript.groovy"
    schemaScriptFileName = "SchemaScript.groovy"
    scriptOnResourceScriptFileName = "ScriptOnResourceScript.groovy"
    searchScriptFileName = "SearchScript.groovy"
    syncScriptFileName = "SyncScript.groovy"
    testScriptFileName = "TestScript.groovy"
    updateScriptFileName = "UpdateScript.groovy"
}

environments {
    linux {
        configuration {
            //classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/samples/linux/shared").file, "UTF-8")]
            //scriptRoots = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/samples/linux/scripts").file, "UTF-8")]
            debug = true

            host = "localhost"
            user = "__configureme__"
            password = new GuardedString("__configureme__".toCharArray())
            prompt = "root@localhost:~\$ "
            echoOff = true
        }
    }
}
