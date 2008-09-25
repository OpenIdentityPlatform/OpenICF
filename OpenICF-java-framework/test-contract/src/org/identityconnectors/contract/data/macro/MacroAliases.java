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
package org.identityconnectors.contract.data.macro;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro aliases definitions.
 * 
 * @author Zdenek Louzensky
 *
 */
public class MacroAliases {
    
    /**
     * Key = alias, Value[0] = aliased macro, Value[1..] = parameters added to alias parameters.
     * Example: ${INTEGER, value} is alias for ${OBJECT, java.lang.Integer, value}  
     */
    public static final Map<String,Object[]> ALIASES = new HashMap<String, Object[]>();
    
    static {
        ALIASES.put("INTEGER", new Object[] {"OBJECT", "java.lang.Integer"});
        ALIASES.put("LONG", new Object[] {"OBJECT", "java.lang.Long"});
        ALIASES.put("FLOAT", new Object[] {"OBJECT", "java.lang.Float"});
        ALIASES.put("DOUBLE", new Object[] {"OBJECT", "java.lang.Double"});
        ALIASES.put("BIGDECIMAL", new Object[] {"OBJECT", "java.math.BigDecimal"});
        ALIASES.put("BIGINTEGER", new Object[] {"OBJECT", "java.math.BigInteger"});
        ALIASES.put("BOOLEAN", new Object[] {"OBJECT", "java.lang.Boolean"});
        ALIASES.put("FILE", new Object[] {"OBJECT", "java.io.File"});
        ALIASES.put("URI", new Object[] {"OBJECT", "java.net.URI"});
        
        // arrays
        ALIASES.put("STRINGARRAY", new Object[] {"ARRAY", "java.lang.String"});
        ALIASES.put("LONGARRAY", new Object[] {"ARRAY", "java.lang.Long"});
        ALIASES.put("INTEGERARRAY", new Object[] {"ARRAY", "java.lang.Integer"});
        ALIASES.put("DOUBLEARRAY", new Object[] {"ARRAY", "java.lang.Double"});
        ALIASES.put("FLOATARRAY", new Object[] {"ARRAY", "java.lang.Float"});
        ALIASES.put("BOOLEANARRAY", new Object[] {"ARRAY", "java.lang.Boolean"});
        ALIASES.put("URIARRAY", new Object[] {"ARRAY", "java.net.URI"});
        ALIASES.put("FILEARRAY", new Object[] {"ARRAY", "java.io.File"});
    }
}
