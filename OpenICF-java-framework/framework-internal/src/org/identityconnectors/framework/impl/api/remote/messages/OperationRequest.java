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
package org.identityconnectors.framework.impl.api.remote.messages;

import java.util.List;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;


/**
 * Sent to request an operation. Response will consist of one-or-more
 * {@link OperationResponsePart}'s followed by an {@link OperationResponseEnd}.
 */
public class OperationRequest implements Message {
    /**
     * The key of the connector to operate on.
     */
    private final ConnectorKey _connectorKey;
    
    /**
     * The configuration information to use.
     */
    private final APIConfigurationImpl _configuration;
    
    /**
     * The operation to perform.
     */
    private final Class<? extends APIOperation> _operation;
    
    /**
     * The arguments to the operation. In general, these correspond
     * to the actual arguments of the method. The one exception is
     * search - in this case, the callback is not passed.
     */
    private final List<Object> _arguments;
    
    public OperationRequest(ConnectorKey key,
            APIConfigurationImpl apiConfiguration,
            Class<? extends APIOperation> operation,
            List<Object> arguments) {
        _connectorKey     = key;
        _configuration = apiConfiguration;
        _operation = operation;
        _arguments = CollectionUtil.newReadOnlyList(arguments);
    }
    
    public ConnectorKey getConnectorKey() {
        return _connectorKey;
    }
        
    public APIConfigurationImpl getConfiguration() {
        return _configuration;
    }
    
    public Class<? extends APIOperation> getOperation() {
        return _operation;
    }
    
    public List<Object> getArguments() {
        return _arguments;
    }
}
