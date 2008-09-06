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
package org.identityconnectors.framework.impl.api.remote;

import java.lang.reflect.InvocationHandler;
import java.util.HashMap;

import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.AbstractConnectorFacade;


/**
 * Implements all the methods of the facade 
 */
public class RemoteConnectorFacadeImpl extends AbstractConnectorFacade {

    final APIConfigurationImpl _remoteConfiguration;
    
    /**
     * Builds up the maps of supported operations and calls.
     */
    public RemoteConnectorFacadeImpl(final APIConfigurationImpl configuration)  {
        super(configuration);
        //clone since we're going to modify it
        _remoteConfiguration = (APIConfigurationImpl)SerializerUtil.cloneObject(configuration);
        //parent ref not included in the clone
        _remoteConfiguration.setConnectorInfo(configuration.getConnectorInfo());
        //disable buffering and timeout on the remote end since we do it locally
        _remoteConfiguration.setProducerBufferSize(0);
        _remoteConfiguration.setTimeoutMap(new HashMap<Class<? extends APIOperation>,Integer>());
    }

    @Override
    protected APIOperation getOperationImplementation(final Class<? extends APIOperation> api) {
        // add remote proxy
        InvocationHandler handler = new RemoteOperationInvocationHandler(
                _remoteConfiguration,
                api);
        APIOperation proxy = newAPIOperationProxy(api, handler);
        //now wrap the proxy in the appropriate timeout proxy
        proxy = createTimeoutProxy(api, proxy);
        // add logging proxy
        proxy = createLoggingProxy(api, proxy);
        
        
        return proxy;                
    }
}
