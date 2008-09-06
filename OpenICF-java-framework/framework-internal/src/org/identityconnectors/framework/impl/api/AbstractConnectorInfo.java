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
package org.identityconnectors.framework.impl.api;

import java.util.Locale;

import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.serializer.SerializerUtil;


/**
 * Common base class shared between local and remote implementations
 */
abstract public class AbstractConnectorInfo implements ConnectorInfo { 

    private String _connectorDisplayNameKey;
    private ConnectorKey _connectorKey;
    private ConnectorMessagesImpl _messages;  
    
    private APIConfigurationImpl _defaultAPIConfiguration;

    
    protected AbstractConnectorInfo() {

    }
    
    public final ConnectorMessagesImpl getMessages() {
        return _messages;
    }
    
    public final void setMessages(ConnectorMessagesImpl messages) {
        _messages = messages;
    }

    public final String getConnectorDisplayName(Locale locale) {
        return _messages.format(locale,_connectorDisplayNameKey, _connectorKey.getConnectorName());
    }
    
    public final String getConnectorDisplayNameKey() {
        return _connectorDisplayNameKey;
    }

    public final void setConnectorDisplayNameKey(String name) {
        _connectorDisplayNameKey = name;
    }

    public final ConnectorKey getConnectorKey() {
        return _connectorKey;
    }
    
    public final void setConnectorKey(ConnectorKey key) {
        _connectorKey = key;
    }
    
    public final APIConfiguration createDefaultAPIConfiguration() {        
        APIConfigurationImpl rv = 
            (APIConfigurationImpl)
            SerializerUtil.cloneObject(_defaultAPIConfiguration);
        rv.setConnectorInfo(this);
        return rv;
    }
    
    public final APIConfigurationImpl getDefaultAPIConfiguration() {
        return _defaultAPIConfiguration;
    }
    
    public final void setDefaultAPIConfiguration(APIConfigurationImpl api) {
        if ( api != null ) {
            api.setConnectorInfo(this);
        }
        _defaultAPIConfiguration = api;
    }
}
