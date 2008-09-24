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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.exceptions.ContractException;
import org.junit.Assert;

/**
 * This macro takes at least one parameter - class name of type of the array, and then array values.
 * 
 * @author David Adam
 * @author Zdenek Louzensky
 * 
 */
public class ArrayMacro implements Macro {

    private static final Log LOG = Log.getLog(ArrayMacro.class);
    // number of mandatory parameters
    private static final int NR_OF_MANDATORY_PARAMS = 2;

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "ARRAY";
    }

    /**
     * {@inheritDoc}
     */
    public Object resolve(Object[] parameters) {
        LOG.ok("enter");

        // number of parameters should be at least 2
        Assert.assertTrue(NR_OF_MANDATORY_PARAMS <= parameters.length);

        // first parameter (mandatory) is macro name
        Assert.assertEquals(parameters[0], getName());

        // second parameter (mandatory) is the array type name
        Assert.assertTrue(parameters[1] instanceof String);
        String inClazz = (String) parameters[1];

        try {
            Class<?> clazz = Class.forName(inClazz);
            Constructor<?> c = clazz.getConstructor(String.class);
            // create array instance
            Object array = Array.newInstance(clazz, parameters.length - NR_OF_MANDATORY_PARAMS);
            // set values in the array
            for (int i = NR_OF_MANDATORY_PARAMS; i < parameters.length; i++) {
                Array.set(array, i - NR_OF_MANDATORY_PARAMS, c.newInstance(parameters[i]));
            }

            return array;

        } catch (Exception ex) {
            LOG.error(ex,
                    "Unable to process the Array macro with parameters: ''{0}''",
                    Arrays.asList(parameters));
            throw ContractException.wrap(ex);
        }
    }

}
