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

import org.identityconnectors.common.logging.Log;

import junit.framework.Assert;

/**
 * {@link Macro} implementation which resolves parameter as String (keeps the
 * original string), usage example: ${FLOAT, ${RANDOM, #####}${LITERAL, .}${RANDOM, ##}}
 * 
 * @author Dan Vernon
 */
public class LiteralMacro implements Macro {
    
    private static final Log LOG = Log.getLog(LiteralMacro.class);

    /**
     * {@inheritDoc}     
     */
    public String getName() {
        return "LITERAL";
    }

    /**
     * {@inheritDoc}     
     */
    public Object resolve(Object[] parameters) {
        LOG.ok("enter");
        
        // should be two parameters
        Assert.assertEquals(2, parameters.length);
        
        // first parameter is macro name
        Assert.assertEquals(parameters[0], getName());
        
        // and the second must be a string 
        // this could be expanded in the future to just concatenate
        // all parameters (assuming they are strings), but this should
        // be ok for now.
        Assert.assertTrue(parameters[1] instanceof String);
        String value = (String)parameters[1];

        // trim and get
        value = value.trim();
        
        LOG.ok("''{0}'' macro with parameter ''{1}'' resolves to (''{2}'',''{3}'')", getName(), value, value.getClass().getName(), value.toString());        
        return value;
    }

}
