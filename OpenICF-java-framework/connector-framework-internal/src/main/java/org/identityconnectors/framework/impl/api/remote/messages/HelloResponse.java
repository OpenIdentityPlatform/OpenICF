/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
/*
 * Portions Copyrighted  2012 ForgeRock Inc.
 */
package org.identityconnectors.framework.impl.api.remote.messages;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl;


/**
 * Sent in response to a {@link HelloRequest}.
 */
public class HelloResponse implements Message {

    public static final String SERVER_START_TIME = "SERVER_START_TIME";
    /**
     * The exception
     */
    private Throwable _exception;

    private Map<String,Object> _serverInfo;

    /**
     * List of connector infos, containing infos for all the connectors
     * on the server.
     */
    private List<RemoteConnectorInfoImpl> _connectorInfos;

    /**
     * List of connector keys, containing the keys of all the connectors
     * on the server.
     */
    private List<ConnectorKey> _connectorKeys;

    public HelloResponse(Throwable exception,
                         Map<String, Object> serverInfo,
                         List<ConnectorKey> connectorKeys,
                         List<RemoteConnectorInfoImpl> connectorInfos) {
        _exception = exception;
        _serverInfo = CollectionUtil.asReadOnlyMap(serverInfo);
        _connectorKeys = CollectionUtil.newReadOnlyList(connectorKeys);
        _connectorInfos = CollectionUtil.newReadOnlyList(connectorInfos);
    }

    public Throwable getException() {
        return _exception;
    }

    public List<RemoteConnectorInfoImpl> getConnectorInfos() {
        return _connectorInfos;
    }

    public List<ConnectorKey> getConnectorKeys() {
        return _connectorKeys;
    }

    public Map<String, Object> getServerInfo() {
        return _serverInfo;
    }

    public Date getStartTime(){
        Object time = getServerInfo().get(SERVER_START_TIME);
        if (time instanceof Long) {
           return new Date((Long) time);
        }
        return null;
    }
}
