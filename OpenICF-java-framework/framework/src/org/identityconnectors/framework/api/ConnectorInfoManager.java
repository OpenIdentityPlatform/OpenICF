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
package org.identityconnectors.framework.api;

import java.util.List;

/**
 * Maintains a list of <code>ConnectorInfo</code> instances,
 * each of which describes a connector that is available.
 * 
 * @see ConnectorInfoManagerFactory
 */
public interface ConnectorInfoManager {
    /**
     * Returns the list of <code>ConnectorInfo</code> instances.
     * @return the list of <code>ConnectorInfo</code> instances.
     */
    public List<ConnectorInfo> getConnectorInfos();
    
    /**
     * Returns the <code>ConnectorInfo</code> that is 
     * associated with the specified <code>ConnectorKey</code>.
     * @param key The key of a connector.
     * @return The <code>ConnectorInfo</code> 
     *  or <code>null</code> if none was associated with the specified key.
     */
    public ConnectorInfo findConnectorInfo(ConnectorKey key);
}
