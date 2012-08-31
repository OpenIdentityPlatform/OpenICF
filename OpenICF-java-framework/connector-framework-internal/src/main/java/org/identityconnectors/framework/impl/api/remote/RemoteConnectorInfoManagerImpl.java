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
 *
 * Portions Copyrighted 2012 ForgeRock Inc.
 */
package org.identityconnectors.framework.impl.api.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.event.ConnectorEventHandler;
import org.identityconnectors.common.event.ConnectorEventPublisher;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;
import org.identityconnectors.common.event.ConnectorEvent;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.identityconnectors.framework.impl.api.remote.messages.HelloRequest;
import org.identityconnectors.framework.impl.api.remote.messages.HelloResponse;


public class RemoteConnectorInfoManagerImpl implements ConnectorInfoManager, ConnectorEventPublisher, Runnable {

    /**
     * Logger.
     */
    private static final Log logger = Log.getLog(RemoteConnectorInfoManagerImpl.class);

    private final RemoteFrameworkConnectionInfo _remoteConnectionInfo;
    private List<ConnectorInfo> _connectorInfoList;
    private Long serverStartTime = null;

    private final Vector<ConnectorEventHandler> eventHandlers = new Vector<ConnectorEventHandler>();

    private RemoteConnectorInfoManagerImpl() {
        _remoteConnectionInfo = null;
    }

    public RemoteConnectorInfoManagerImpl(RemoteFrameworkConnectionInfo info) throws RuntimeException {
        this(info, true);
    }

    public RemoteConnectorInfoManagerImpl(RemoteFrameworkConnectionInfo info, boolean loadConnectorInfo)
            throws RuntimeException {
        _remoteConnectionInfo = info;
        if (loadConnectorInfo) {
            init();
        } else {
            _connectorInfoList = null;
        }
    }

    private void init() {
        RemoteFrameworkConnection connection = new RemoteFrameworkConnection(_remoteConnectionInfo);
        HelloResponse response = null;
        try {
            connection.writeObject(CurrentLocale.get());
            connection.writeObject(_remoteConnectionInfo.getKey());
            connection.writeObject(new HelloRequest(HelloRequest.CONNECTOR_INFO));
            response = (HelloResponse) connection.readObject();
        } finally {
            connection.close();
        }
        if (null == response) {
            logger.error("HelloResponse is null from {0}", _remoteConnectionInfo);
            throw new ConnectorIOException("HelloResponse is null from " + _remoteConnectionInfo.toString());
        }
        if (response.getException() != null) {
            throw ConnectorException.wrap(response.getException());
        }

        List<RemoteConnectorInfoImpl> remoteInfos = response.getConnectorInfos();
        //populate transient fields not serialized
        for (RemoteConnectorInfoImpl remoteInfo : remoteInfos) {
            remoteInfo.setRemoteConnectionInfo(_remoteConnectionInfo);
        }

        List<ConnectorInfo> connectorInfoBefore = _connectorInfoList;
        _connectorInfoList = CollectionUtil.<ConnectorInfo>newReadOnlyList(remoteInfos);
        Object o = response.getServerInfo().get(HelloResponse.SERVER_START_TIME);
        if (o instanceof Long) {
            serverStartTime = (Long) o;
        } else {
            serverStartTime = System.currentTimeMillis();
        }

        //Notify all the listeners
        List<ConnectorInfo> unchanged = new ArrayList<ConnectorInfo>(_connectorInfoList.size());
        if (null != connectorInfoBefore) {
            for (ConnectorInfo oldCi : connectorInfoBefore) {
                boolean unregistered = true;
                for (ConnectorInfo newCi : _connectorInfoList) {
                    if (oldCi.getConnectorKey().equals(newCi.getConnectorKey())) {
                        unregistered = false;
                        unchanged.add(newCi);
                        break;
                    }
                }
                if (unregistered) {
                    notifyListeners(new ConnectorEvent(ConnectorEvent.CONNECTOR_UNREGISTERING,
                            oldCi.getConnectorKey()));
                }
            }
        }
        for (ConnectorInfo newCi : _connectorInfoList) {
            if (!unchanged.contains(newCi)) {
                notifyListeners(new ConnectorEvent(ConnectorEvent.CONNECTOR_REGISTERED, newCi.getConnectorKey()));
            }
        }
    }

    /**
     * Derives another RemoteConnectorInfoManagerImpl with
     * a different RemoteFrameworkConnectionInfo but with the
     * same metadata
     * @param info
     * @return
     */
    public RemoteConnectorInfoManagerImpl derive(RemoteFrameworkConnectionInfo info) {
        RemoteConnectorInfoManagerImpl rv = new RemoteConnectorInfoManagerImpl();
        if (null == _connectorInfoList || _connectorInfoList.isEmpty()) {
            rv._connectorInfoList = Collections.emptyList();
        } else {
            @SuppressWarnings("unchecked")
            List<RemoteConnectorInfoImpl> remoteInfos = (List<RemoteConnectorInfoImpl>) SerializerUtil
                    .cloneObject(_connectorInfoList);
            for (RemoteConnectorInfoImpl remoteInfo : remoteInfos) {
                remoteInfo.setRemoteConnectionInfo(info);
            }
            rv._connectorInfoList = CollectionUtil.<ConnectorInfo>newReadOnlyList(remoteInfos);
        }
        return rv;
    }
    
    public ConnectorInfo findConnectorInfo(ConnectorKey key) {
        for (ConnectorInfo info : getConnectorInfos()) {
            if ( info.getConnectorKey().equals(key) ) {
                return info;
            }
        }
        return null;
    }

    public List<ConnectorInfo> getConnectorInfos() {
        List<ConnectorInfo> result = _connectorInfoList;
        if (null == result ) {
            result = Collections.emptyList();
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void run() {
        try {
            Map<String, Object> serverInfo = getServerInfo();
            Object o = serverInfo.get(HelloResponse.SERVER_START_TIME);
            if (o instanceof Long) {
                if (null == serverStartTime || ((Long) o) > serverStartTime) {
                    if (logger.isOk()) {
                        if (null != serverStartTime) {
                            logger.ok("Connector server has been restarted since {0}, new start time: {1}",
                                    new Date(serverStartTime),
                                    new Date((Long) o));
                        } else {
                            logger.ok("First connection to connector server has been established, new start time: {0}",
                                    new Date((Long) o));
                        }
                    }
                    init();
                }
            }
        } catch (ConnectorIOException e) {
            // Server is unreachable and we notify all listeners
            if (null != _connectorInfoList) {
                for (ConnectorInfo connectorInfo : _connectorInfoList) {
                    notifyListeners(new ConnectorEvent(ConnectorEvent.CONNECTOR_UNREGISTERING,
                            connectorInfo.getConnectorKey()));
                }
            }
            _connectorInfoList = null;
            logger.error("Failed to connect to remote connector server {0}", _remoteConnectionInfo);
        } catch (Exception e) {
            logger.error(e, "Failed to update the ConnectorInfo from remote connector server");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addConnectorEventHandler(ConnectorEventHandler hook) {
        if (hook == null)
            throw new NullPointerException();
        if (!eventHandlers.contains(hook)) {
            if (null != _connectorInfoList) {
                for (ConnectorInfo connectorInfo : _connectorInfoList) {
                    hook.handleEvent(
                            new ConnectorEvent(ConnectorEvent.CONNECTOR_REGISTERED, connectorInfo.getConnectorKey()));
                }
            }
            eventHandlers.addElement(hook);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void deleteConnectorEventHandler(ConnectorEventHandler hook) {
        eventHandlers.removeElement(hook);
    }

    public Map<String, Object> getServerInfo() throws RuntimeException {
        RemoteFrameworkConnection connection = new RemoteFrameworkConnection(_remoteConnectionInfo);
        try {
            connection.writeObject(CurrentLocale.get());
            connection.writeObject(_remoteConnectionInfo.getKey());
            connection.writeObject(new HelloRequest(HelloRequest.SERVER_INFO));
            HelloResponse response = (HelloResponse) connection.readObject();
            if (response.getException() instanceof ConnectorException) {
                throw (ConnectorException) response.getException();
            } else if (response.getException() != null) {
                throw ConnectorException.wrap(response.getException());
            }
            return response.getServerInfo();
        } finally {
            connection.close();
        }
    }

    public List<ConnectorKey> getConnectorKeys() throws RuntimeException {
        RemoteFrameworkConnection connection = new RemoteFrameworkConnection(_remoteConnectionInfo);
        try {
            connection.writeObject(CurrentLocale.get());
            connection.writeObject(_remoteConnectionInfo.getKey());
            connection.writeObject(new HelloRequest(HelloRequest.CONNECTOR_KEY_LIST));
            HelloResponse response = (HelloResponse) connection.readObject();
            if (response.getException() instanceof ConnectorException) {
                throw (ConnectorException) response.getException();
            } else if (response.getException() != null) {
                throw ConnectorException.wrap(response.getException());
            }
            return response.getConnectorKeys();
        } finally {
            connection.close();
        }
    }

    private void notifyListeners(ConnectorEvent event) {
        /*
         * a temporary array buffer, used as a snapshot of the state of current
         * Handlers.
         */
        Object[] arrLocal = eventHandlers.toArray();

        for (int i = arrLocal.length - 1; i >= 0; i--) {
            try {
                ((ConnectorEventHandler) arrLocal[i]).handleEvent(new ConnectorEvent(event));
            } catch (Throwable t) {
                /* ignore */
            }
        }
    }
}
