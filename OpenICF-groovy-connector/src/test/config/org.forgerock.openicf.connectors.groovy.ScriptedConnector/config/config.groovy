/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014. ForgeRock AS. All rights reserved.
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
 *
 */

/* +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+
 */

import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase
import org.identityconnectors.common.security.GuardedString

// Connector WRONG configuration for ValidateApiOpTests
connector.i1.wrong.host = ""
connector.i2.wrong.login = ""
connector.i3.wrong.password = new GuardedString("".toCharArray())



configuration {
    clearTextPasswordToScript = false
    authenticateScriptFileName = "AuthenticateScript.groovy"
    createScriptFileName = "CreateScript.groovy"
    deleteScriptFileName = "DeleteScript.groovy"
    resolveUsernameScriptFileName = "ResolveUsernameScript.groovy"
    schemaScriptFileName = "SchemaScript.groovy"
    //scriptOnConnectorScriptFileName = "ScriptOnConnectorScript.groovy"
    scriptOnResourceScriptFileName = "ScriptOnResourceScript.groovy"
    searchScriptFileName = "SearchScript.groovy"
    syncScriptFileName = "SyncScript.groovy"
    testScriptFileName = "TestScript.groovy"
    updateScriptFileName = "UpdateScript.groovy"

    //warningLevel=WarningMessage.LIKELY_ERRORS
    //sourceEncoding="UTF-8"
    //targetDirectory=${project.build.directory}
    //classpath=[${project.build.outputDirectory}]
    //verbose=true
    //debug=true
    //tolerance=10
    //scriptBaseClass=""
    //recompileGroovySource=true
    //minimumRecompilationInterval=100
    //disabledGlobalASTTransformations=[]
}

environments {
    case1 {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/case1/").file, "UTF-8")]
        }
    }
    case2 {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/case2/").file, "UTF-8")]
        }
    }
    case2 {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/case3/").file, "UTF-8")]
        }
    }
}

