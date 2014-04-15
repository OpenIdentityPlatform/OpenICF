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

import org.forgerock.openicf.misc.scriptedcommon.ScriptedConnectorBase
import org.identityconnectors.common.security.GuardedString


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
    GROOVY {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/groovy/").file, "UTF-8")]
            scriptExtensions = ['groovy', 'java'] as String[]
        }
    }
    CREST {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/crest/").file, "UTF-8")]
            serviceAddress = new URI("http://localhost:8080/crest/")
            login = "admin"
            password = new GuardedString("Passw0rd".toCharArray())
        }
    }
    REST {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/rest/").file, "UTF-8")]
            serviceAddress = new URI("http://localhost:8080/rest/")
            username = "admin"
            password = new GuardedString("Passw0rd".toCharArray())
        }
    }
    SQL {
        configuration {
            classpath = [URLDecoder.decode(ScriptedConnectorBase.class.getResource("/sql/").file, "UTF-8")]
            driverClassName = "org.h2.Driver"
            validationQuery = "select 1"
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = "sa"
        }
    }
}

