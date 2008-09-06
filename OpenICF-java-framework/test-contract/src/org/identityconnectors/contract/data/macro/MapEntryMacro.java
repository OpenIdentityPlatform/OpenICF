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

import org.identityconnectors.common.logging.Log;

import junit.framework.Assert;


/**
 * {@link Macro} implementation which resolves parameters to {@link Map.Entry}
 * 
 * @author Zdenek Louzensky
 */
public class MapEntryMacro implements Macro {

    private static final Log LOG = Log.getLog(MapEntryMacro.class);

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "MAPENTRY";
    }

    /**
     * {@inheritDoc}
     */
    public Object resolve(Object[] parameters) {
        LOG.ok("enter");

        // should be 3 parameters
        Assert.assertTrue(parameters.length == 3);

        // first parameter is macro name
        Assert.assertEquals(parameters[0], getName());

        // will use map to get Entry implementation
        Map<Object, Object> objectMap = new HashMap<Object, Object>();
        objectMap.put(parameters[1], parameters[2]);

        LOG.ok("''{0}'' macro with parameters ''{1}'', ''{2}'' resolves to entry (''{3}'' (''{4}'') => ''{5}'' (''{6}''))",
                         getName(), parameters[1], parameters[2],
                         parameters[1],parameters[1].getClass().getName(), 
                         parameters[2], parameters[2].getClass().getName());

        // return the one object which is in Map as Map.Entry implementation
        return objectMap.entrySet().toArray()[0];
    }
}
