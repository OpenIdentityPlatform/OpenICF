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
 * {@link Macro} implementation which resolves MAPENTRY parameters to {@link Map}
 * 
 * @author Zdenek Louzensky
 */
public class MapMacro implements Macro {
    
    private static final Log LOG = Log.getLog(MapMacro.class);

    /**
     * {@inheritDoc}     
     */
    public String getName() {
        return "MAP";
    }

    /**
     * {@inheritDoc}     
     */
    public Object resolve(Object[] parameters) {
        LOG.ok("enter");

        // should be at least one parameter (the macro name)
        // it's ok if there are no other parameters, we will
        // just create an empty map.
        Assert.assertTrue(parameters.length > 0);

        // first parameter is macro name
        Assert.assertEquals(parameters[0], getName());

        LOG.ok("''{0}'' macro: <start map>", getName());

        // iterate through parameters (starting after the
        // macroname parameter), and add check that are of type Map.Entry and add them to Map
        Map<Object, Object> objectMap = new HashMap<Object, Object>();
        for (int i = 1; i < parameters.length; i++) {
            // parameter must be of type Map.Entry
            Assert.assertTrue("MAP macro must contain MAPENTRY parameters.", parameters[i] instanceof Map.Entry);
            
            Map.Entry entry = (Map.Entry) parameters[i];
            objectMap.put(entry.getKey(), entry.getValue());

            LOG.ok("  MapMember->''{0}'' (''{1}'') => ''{2}'' (''{3}'')", entry.getKey(), entry
                    .getKey().getClass().getName(), entry.getValue(), entry.getValue().getClass().getName());
        }
        LOG.ok("''{0}'' macro: <end of map>", getName());

        return objectMap;
    }

}
