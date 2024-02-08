/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.openicf.framework.remote;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationRequest;
import org.forgerock.openicf.common.protobuf.OperationMessages.OperationResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages.CancelOpRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages.ControlRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages.ControlResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages.ExceptionMessage;
import org.forgerock.openicf.common.protobuf.RPCMessages.HandshakeMessage;
import org.forgerock.openicf.common.protobuf.RPCMessages.RPCRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages.RPCResponse;
import org.forgerock.openicf.common.protobuf.RPCMessages.RemoteMessage;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.async.AsyncConnectorInfoManager;
import org.forgerock.openicf.framework.async.impl.AuthenticationAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.BatchApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ConnectorEventSubscriptionApiOpImpl;
import org.forgerock.openicf.framework.async.impl.CreateAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.DeleteAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.GetAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ResolveUsernameAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SchemaAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ScriptOnConnectorAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ScriptOnResourceAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SearchAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SyncAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.SyncEventSubscriptionApiOpImpl;
import org.forgerock.openicf.framework.async.impl.TestAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.UpdateAsyncApiOpImpl;
import org.forgerock.openicf.framework.async.impl.ValidateAsyncApiOpImpl;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.openicf.framework.remote.security.ECIESEncryptor;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.Encryptor;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConfigurationPropertyChangeListener;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.impl.api.AbstractConnectorInfo;
import org.identityconnectors.framework.impl.api.remote.RemoteConnectorInfoImpl;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * OpenICFServerAdapter process all messages.
 */
public class OpenICFServerAdapter implements OperationMessageListener {

    private static final Log logger = Log.getLog(OpenICFServerAdapter.class);

    private final KeyPair keyPair = SecurityUtil.generateKeyPair();

    private final Boolean client;
    private final ConnectorFramework connectorFramework;
    private final HandshakeMessage.Builder handshakeMessage;
    private final AsyncConnectorInfoManager connectorInfoManager;

    public OpenICFServerAdapter(final ConnectorFramework framework,
            final AsyncConnectorInfoManager defaultConnectorInfoManager, final boolean isClient) {
        if (isClient) {
            client = Boolean.TRUE;
        } else {
            client = Boolean.FALSE;
        }
        connectorFramework = Assertions.nullChecked(framework, "connectorFramework");
        connectorInfoManager =
                Assertions.nullChecked(defaultConnectorInfoManager, "connectorInfoManager");
        handshakeMessage = MessagesUtil.createHandshakeMessage(keyPair.getPublic());
    }

    public void onClose(final WebSocketConnectionHolder socket, int code, String reason) {
        logger.info("{0} onClose({1},{2}) ", loggerName(), String.valueOf(code), String
                .valueOf(reason));
    }

    public void onConnect(final WebSocketConnectionHolder socket) {
        if (isClient()) {
            logger.info("Client onConnect() - send Handshake '({0})'", handshakeMessage.build().getSessionId());
            RemoteMessage.Builder requestBuilder =
                    MessagesUtil.createRequest(0, RPCRequest.newBuilder().setHandshakeMessage(
                            handshakeMessage));

            socket.sendBytes(requestBuilder.build().toByteArray());
        } else {
            logger.info("Server onConnect()");
        }
    }

    public void onError(Throwable t) {
        logger.ok(t, "Socket error");
    }

    public void onMessage(WebSocketConnectionHolder socket, String data) {
        logger.warn("String message is ignored: {0}", data);
    }

    public void onMessage(final WebSocketConnectionHolder socket, final byte[] bytes) {
        logger.ok("{0} onMessage({1}:bytes)", loggerName(), bytes.length);

        connectorFramework.executeMessage(new Runnable() {
            public void run() {
                processMessage(socket, bytes);
            }
        });
    }

    public void processMessage(final WebSocketConnectionHolder socket, byte[] bytes){
        try {
            RemoteMessage message = RemoteMessage.parseFrom(bytes);

            if (logger.isOk()) {
                logger.ok("{0} onMessage({1})", loggerName(), message.toString());
            }
            if (message.hasRequest()) {
                if (message.getRequest().hasHandshakeMessage()) {
                    if (isClient()) {
                        logger.ok("Error = The client must send the Handshake first");
                    } else {
                        processHandshakeRequest(socket, message.getMessageId(), message
                                .getRequest().getHandshakeMessage());
                    }
                } else if (socket.isHandHooked()) {
                    if (message.getRequest().hasOperationRequest()) {
                        processOperationRequest(socket, message.getMessageId(), message
                                .getRequest().getOperationRequest());

                    } else if (message.getRequest().hasCancelOpRequest()) {
                        processCancelOpRequest(socket, message.getMessageId(), message.getRequest()
                                .getCancelOpRequest());
                    } else if (message.getRequest().hasControlRequest()) {
                        processControlRequest(socket, message.getMessageId(), message.getRequest()
                                .getControlRequest());
                    } else {
                        handleRequestMessage(socket, message.getMessageId(), message.getRequest());
                    }
                } else {
                    handleRequestMessage(socket, message.getMessageId(), message.getRequest());
                }
            } else if (message.hasResponse()) {
                if (message.getResponse().hasHandshakeMessage()) {
                    if (isClient()) {
                        processHandshakeResponse(socket, message.getMessageId(), message
                                .getResponse().getHandshakeMessage());
                    } else {
                        logger.ok("Error = The server must send the Handshake response");
                    }
                } else if (message.getResponse().hasError()) {
                    processExceptionMessage(socket, message.getMessageId(), message.getResponse()
                            .getError());
                } else if (socket.isHandHooked()) {
                    if (message.getResponse().hasOperationResponse()) {
                        processOperationResponse(socket, message.getMessageId(), message
                                .getResponse().getOperationResponse());
                    } else if (message.getResponse().hasControlResponse()) {
                        processControlResponse(socket, message.getMessageId(), message
                                .getResponse().getControlResponse());
                    } else {
                        handleResponseMessage(socket, message.getMessageId(), message.getResponse());
                    }
                } else {
                    handleResponseMessage(socket, message.getMessageId(), message.getResponse());
                }
            } else {
                handleRemoteMessage(socket, message);
            }
        } catch (InvalidProtocolBufferException e) {
            logger.warn(e, "{0} failed parse message", loggerName());
        } catch (Throwable t) {
            logger.info(t, "{0} Unhandled exception", loggerName());
        }
    }

    protected void handleRemoteMessage(final WebSocketConnectionHolder socket,
            final RemoteMessage message) {
        logger.warn("{0} received unknown message('{1}') via socket:{2} ", loggerName(), message.getMessageId(),
                socket.hashCode());
            socket.getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                    MessagesUtil.createErrorResponse(message.getMessageId(),
                            new ConnectorException("Unknown RemoteMessage")).build());
    }

    protected void handleRequestMessage(final WebSocketConnectionHolder socket, long messageId,
            final RPCRequest message) {
        if (socket.isHandHooked()) {
            logger.warn("{0} received unknown request('{1}') via socket:{2} ", loggerName(), messageId,
                    socket.hashCode());
            socket.getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                    MessagesUtil.createErrorResponse(messageId,
                            new ConnectorException("Unknown Request message")).build());
        } else {
            logger.warn("{0} received request('{1}') before handshake via socket:{2} ", loggerName(), messageId,
                    socket.hashCode());
            socket.sendBytes(
                    MessagesUtil.createErrorResponse(messageId,
                            new ConnectorException("Connection received request before handshake"))
                            .build().toByteArray());
        }
    }

    protected void handleResponseMessage(final WebSocketConnectionHolder socket, long messageId,
            final RPCResponse message) {
        if (socket.isHandHooked()) {
            logger.info("{0} received unknown response('{1}') via socket:{2} ", loggerName(), messageId,
                    socket.hashCode());
            socket.getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                    MessagesUtil.createErrorResponse(messageId,
                            new ConnectorException("Unknown Request message")).build());
        } else {
            logger.info("{0} received response('{1}') before handshake via socket:{2} ", loggerName(), messageId,
                    socket.hashCode());
            socket.sendBytes(
                    MessagesUtil.createErrorResponse(messageId,
                            new ConnectorException("Connection received response before handshake"))
                            .build().toByteArray());
        }
    }

    public void onPing(WebSocketConnectionHolder socket, byte[] bytes) {
        // Nothing to do, pong response has been sent
        logger.info("{0} onPing()", loggerName());
        try {
            RPCMessages.PingMessage message = RPCMessages.PingMessage.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            logger.warn(e, "{0} failed parse message", loggerName());
        }
        // socket.touch();
    }

    public void onPong(WebSocketConnectionHolder socket, byte[] bytes) {
        // Confirm ping response!
        logger.info("{0} onPong()", loggerName());
        try {
            RPCMessages.PingMessage message = RPCMessages.PingMessage.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            logger.warn(e, "{0} failed parse message", loggerName());
        }
        // socket.touch();
    }

    protected boolean isClient() {
        return client;
    }

    // Handshake Operations

    public void processHandshakeRequest(final WebSocketConnectionHolder socket, long messageId,
            HandshakeMessage message) {
        RemoteMessage.Builder responseBuilder =
                MessagesUtil.createResponse(messageId, RPCResponse.newBuilder()
                        .setHandshakeMessage(handshakeMessage));
        socket.sendBytes(responseBuilder.build().toByteArray());
        logger.info("{0} respond Handshake ({1}:{2})", loggerName(), handshakeMessage.build().getSessionId(),
                socket.hashCode());
        // Set Operational
        socket.receiveHandshake(message);
        logger.info("{0} accept Handshake ({1}:{2})", loggerName(), message.getSessionId(), socket.hashCode());
    }

    public void processHandshakeResponse(final WebSocketConnectionHolder socket, long messageId,
            HandshakeMessage message) {
        // Set Operational
        socket.receiveHandshake(message);

        logger.info("{0} accept Handshake ({1}:{2})", loggerName(), message.getSessionId(), socket.hashCode());
    }

    // Control Operations

    public void processControlRequest(final WebSocketConnectionHolder socket, long messageId,
            ControlRequest message) {
        final ControlResponse.Builder builder = ControlResponse.newBuilder();
        if (message.getInfoLevelList().contains(ControlRequest.InfoLevel.CONNECTOR_INFO)) {
            List<RemoteConnectorInfoImpl> connectorInfos = new ArrayList<RemoteConnectorInfoImpl>();
            for (ConnectorInfo ci : connectorInfoManager.getConnectorInfos()) {
                connectorInfos.add(toRemote((AbstractConnectorInfo) ci));
            }

            ByteString response = MessagesUtil.serializeLegacy(connectorInfos);
            builder.setConnectorInfos(response);
        }
        socket.getRemoteConnectionContext().getRemoteConnectionGroup().processControlRequest(
                message);

        RemoteMessage.Builder responseBuilder =
                MessagesUtil.createResponse(messageId, RPCResponse.newBuilder().setControlResponse(
                        builder));

        socket.sendBytes(responseBuilder.build().toByteArray());
        logger.info("{0} accept ControlRequest ({1})", loggerName(), message);
    }

    private RemoteConnectorInfoImpl toRemote(AbstractConnectorInfo source) {
        RemoteConnectorInfoImpl rv = new RemoteConnectorInfoImpl();
        rv.setConnectorDisplayNameKey(source.getConnectorDisplayNameKey());
        rv.setConnectorKey(source.getConnectorKey());
        rv.setDefaultAPIConfiguration(source.getDefaultAPIConfiguration());
        rv.setMessages(source.getMessages());
        return rv;
    }

    public void processControlResponse(final WebSocketConnectionHolder socket, long messageId,
            ControlResponse message) {

        socket.getRemoteConnectionContext().getRemoteConnectionGroup().receiveRequestResponse(
                socket, messageId, message);

        logger.info("{0} accept ControlResponse", loggerName());
    }

    public void processOperationRequest(final WebSocketConnectionHolder socket,
            final long messageId, final OperationRequest message) {
        logger.ok("IN Request({0}:{1})", messageId, socket.getRemoteConnectionContext()
                .getRemotePrincipal().getName());

        final String connectorFacadeKey = message.getConnectorFacadeKey().toStringUtf8();

        if (message.hasConfigurationChangeEvent()) {
            List<ConfigurationProperty> changes =
                    MessagesUtil.deserializeLegacy(message.getConfigurationChangeEvent()
                            .getConfigurationPropertyChange());

            socket.getRemoteConnectionContext().getRemoteConnectionGroup()
                    .notifyConfigurationChangeListener(connectorFacadeKey, changes);
        } else {

            final CommonObjectMessages.ConnectorKey connectorKey = message.getConnectorKey();

            ConnectorInfo info = findConnectorInfo(connectorKey);
            if (info == null) {
                RemoteMessage.Builder response =
                        MessagesUtil.createErrorResponse(messageId, new ConnectorException(
                                "Connector not found: " + connectorKey + " "));
                socket.getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                        response.build());
            } else {

                try {
                    try {
                        if (message.hasLocale()) {
                            Locale local =
                                    MessagesUtil.deserializeMessage(message.getLocale(),
                                            Locale.class);
                            CurrentLocale.set(local);
                        }
                    } catch (Throwable e) {
                        logger.ok(e, "Failed to set request Locale");
                    }

                    ConnectorFacade connectorFacade = newInstance(socket, info, connectorFacadeKey);

                    if (message.hasBatchOpRequest()) {
                        BatchApiOpImpl.createProcessor(messageId, socket,
                                message.getBatchOpRequest()).execute(connectorFacade);
                    } else if (message.hasAuthenticateOpRequest()) {
                        AuthenticationAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getAuthenticateOpRequest()).execute(connectorFacade);
                    } else if (message.hasCreateOpRequest()) {
                        CreateAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getCreateOpRequest()).execute(connectorFacade);
                    } else if (message.hasConnectorEventSubscriptionOpRequest()) {
                        ConnectorEventSubscriptionApiOpImpl.createProcessor(messageId, socket,
                                message.getConnectorEventSubscriptionOpRequest()).execute(
                                connectorFacade);
                    } else if (message.hasDeleteOpRequest()) {
                        DeleteAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getDeleteOpRequest()).execute(connectorFacade);
                    } else if (message.hasGetOpRequest()) {
                        GetAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getGetOpRequest()).execute(connectorFacade);
                    } else if (message.hasResolveUsernameOpRequest()) {
                        ResolveUsernameAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getResolveUsernameOpRequest()).execute(connectorFacade);
                    } else if (message.hasSchemaOpRequest()) {
                        SchemaAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getSchemaOpRequest()).execute(connectorFacade);
                    } else if (message.hasScriptOnConnectorOpRequest()) {
                        ScriptOnConnectorAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getScriptOnConnectorOpRequest()).execute(connectorFacade);
                    } else if (message.hasScriptOnResourceOpRequest()) {
                        ScriptOnResourceAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getScriptOnResourceOpRequest()).execute(connectorFacade);
                    } else if (message.hasSearchOpRequest()) {
                        SearchAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getSearchOpRequest()).execute(connectorFacade);
                    } else if (message.hasSyncOpRequest()) {
                        SyncAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getSyncOpRequest()).execute(connectorFacade);
                    } else if (message.hasSyncEventSubscriptionOpRequest()) {
                        SyncEventSubscriptionApiOpImpl.createProcessor(messageId, socket,
                                message.getSyncEventSubscriptionOpRequest()).execute(
                                connectorFacade);
                    } else if (message.hasTestOpRequest()) {
                        TestAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getTestOpRequest()).execute(connectorFacade);
                    } else if (message.hasUpdateOpRequest()) {
                        UpdateAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getUpdateOpRequest()).execute(connectorFacade);
                    } else if (message.hasValidateOpRequest()) {
                        ValidateAsyncApiOpImpl.createProcessor(messageId, socket,
                                message.getValidateOpRequest()).execute(connectorFacade);
                    } else {
                        socket.getRemoteConnectionContext().getRemoteConnectionGroup()
                                .trySendMessage(
                                        MessagesUtil.createErrorResponse(messageId,
                                                new ConnectorException("Unknown OperationRequest"))
                                                .build());
                    }
                } catch (Throwable t) {
                    logger.ok(t, "Failed handle OperationRequest {0}", messageId);
                    socket.getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                            MessagesUtil.createErrorResponse(messageId, t).build());

                } finally {
                    CurrentLocale.clear();
                }
            }
        }
    }

    public void processOperationResponse(final WebSocketConnectionHolder socket, long messageId,
            final OperationResponse message) {
        logger.ok("IN Response({0}:{1})", messageId, socket.getRemoteConnectionContext()
                .getRemotePrincipal().getName());
        socket.getRemoteConnectionContext().getRemoteConnectionGroup().receiveRequestResponse(
                socket, messageId, message);
    }

    public void processExceptionMessage(final WebSocketConnectionHolder socket, long messageId,
            final ExceptionMessage message) {
        socket.getRemoteConnectionContext().getRemoteConnectionGroup().receiveRequestResponse(
                socket, messageId, message);
    }

    public void processCancelOpRequest(final WebSocketConnectionHolder socket, long messageId,
            final CancelOpRequest message) {
        socket.getRemoteConnectionContext().getRemoteConnectionGroup().receiveRequestCancel(
                messageId);
    }

    protected Encryptor initialiseEncryptor() {
        HandshakeMessage message = null;
        // Create Encryptor
        if (!message.getPublicKey().isEmpty()) {
            PublicKey publicKey =
                    SecurityUtil.createPublicKey(message.getPublicKey().toByteArray());
            Encryptor encryptor = new ECIESEncryptor(keyPair, publicKey);
        }
        return null;
    }

    protected String loggerName() {
        return isClient() ? "Client" : "Server";
    }

    public ConnectorFacade newInstance(final WebSocketConnectionHolder socket,
            final ConnectorInfo connectorInfo, final String config) {
        return connectorFramework.newManagedInstance(connectorInfo, config,
                new RemoteConfigurationChangeListener(socket, connectorInfo, config));
    }

    public ConnectorInfo findConnectorInfo(CommonObjectMessages.ConnectorKey key) {
        return connectorInfoManager.findConnectorInfo(new ConnectorKey(key.getBundleName(), key
                .getBundleVersion(), key.getConnectorName()));
    }

    private static class RemoteConfigurationChangeListener implements
            ConfigurationPropertyChangeListener {
        private final WebSocketConnectionHolder socket;
        private final ConnectorInfo connectorInfo;
        private final String config;

        public RemoteConfigurationChangeListener(WebSocketConnectionHolder socket,
                ConnectorInfo connectorInfo, String config) {
            this.socket = socket;
            this.connectorInfo = connectorInfo;
            this.config = config;
        }

        public void configurationPropertyChange(List<ConfigurationProperty> changes) {
            try {
                RemoteMessage.Builder request =
                        MessagesUtil
                                .createRequest(
                                        0,
                                        RPCMessages.RPCRequest
                                                .newBuilder()
                                                .setOperationRequest(
                                                        OperationRequest
                                                                .newBuilder()
                                                                .setConnectorFacadeKey(
                                                                        ByteString
                                                                                .copyFromUtf8(config))
                                                                .setConfigurationChangeEvent(
                                                                        OperationMessages.ConfigurationChangeEvent
                                                                                .newBuilder()
                                                                                .setConfigurationPropertyChange(
                                                                                        MessagesUtil
                                                                                                .serializeLegacy(changes)))));

                socket.getRemoteConnectionContext().getRemoteConnectionGroup().trySendMessage(
                        request.build());
            } catch (Throwable t) {
                logger.info(logger.isOk() ? t : null,
                        "Failed to send ConfigurationChangeEvent event: {0}", connectorInfo);

            }
        }
    }
}
