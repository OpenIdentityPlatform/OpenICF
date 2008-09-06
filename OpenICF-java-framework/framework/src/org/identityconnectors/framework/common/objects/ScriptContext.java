/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.common.objects;

import java.util.LinkedHashMap;
import java.util.Map;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.serializer.ObjectSerializerFactory;
import org.identityconnectors.framework.common.serializer.SerializerUtil;


/**
 * Encapsulates a script and all of its parameters.
 * @see org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp
 * @see org.identityconnectors.framework.api.operations.ScriptOnConnectorApiOp
 */
public final class ScriptContext {
    
    private final String _scriptLanguage;
    private final String _scriptText;
    private final Map<String,Object> _scriptArguments;
    
    /**
     * Public only for serialization; please use {@link ScriptContextBuilder}.
     * @param scriptLanguage The script language. Must not be null.
     * @param scriptText The script text. Must not be null.
     * @param scriptArguments The script arguments. May be null.
     */
    public ScriptContext(String scriptLanguage,
            String scriptText,
            Map<String,Object> scriptArguments) {
        
        if (scriptLanguage == null) {
            throw new IllegalArgumentException("Argument 'scriptLanguage' must be specified");
        }
        if (scriptText == null) {
            throw new IllegalArgumentException("Argument 'scriptText' must be specified");
        }
        //clone script arguments and options - this serves two purposes
        //1)makes sure everthing is serializable
        //2)does a deep copy
        @SuppressWarnings("unchecked")
        Map<String,Object> scriptArgumentsClone = (Map<String,Object>)SerializerUtil.cloneObject(scriptArguments);
        _scriptLanguage = scriptLanguage;
        _scriptText = scriptText;
        _scriptArguments = CollectionUtil.asReadOnlyMap(scriptArgumentsClone);
    }
    
    /**
     * Identifies the language in which the script is written 
     * (e.g., <code>bash</code>, <code>csh</code>, 
     * <code>Perl4</code> or <code>Python</code>).
     * @return The script language.
     */
    public String getScriptLanguage() {
        return _scriptLanguage;
    }
    
    /**
     * Returns the text (i.e., actual characters) of the script.
     * @return The text of the script.
     */
    public String getScriptText() {
        return _scriptText;
    }
    
    /**
     * Returns a map of arguments to be passed to the script.
     * Values must be types that the framework can serialize.
     * See {@link ObjectSerializerFactory} for a list of supported types.
     * @return A map of arguments to be passed to the script.
     */
    public Map<String,Object> getScriptArguments() {
        return _scriptArguments;
    }
    
    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder();
        bld.append("ScriptContext: ");
        // poor mans to string method..
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("Languge", getScriptLanguage());
        map.put("Text", getScriptText());
        map.put("Arguments", getScriptArguments());
        bld.append(map.toString());
        return bld.toString();
    }
    
}
