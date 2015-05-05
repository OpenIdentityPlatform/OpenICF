/*
 * Copyright (c) 2012-2015 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 * ====================
 * Portions Copyrighted 2015 ForgeRock AS.
 */

/**
 * @see https://github.com/AsyncHttpClient/async-http-client/blob/1.8.x/src/main/java/com/ning/http/client/providers/grizzly/GrizzlyAsyncHttpProvider.java
 */
package org.forgerock.openicf.framework.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.openicf.framework.remote.rpc.OperationMessageListener;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.util.Utils;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketClientFilter;
import org.glassfish.grizzly.websockets.WebSocketHolder;
import org.identityconnectors.common.Base64;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

public class ConnectionManager extends RemoteConnectionInfoManagerFactory {

    private static final Log logger = Log.getLog(ConnectionManager.class);

    private static final Attribute<RemoteConnectionContext> REQUEST_STATE_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(RemoteConnectionContext.class
                    .getName());

    public static final SSLContextConfigurator DEFAULT_CONFIG = new SSLContextConfigurator();
    public static final String USER_AGENT = "OpenICF-Client-" + FrameworkUtil.getFrameworkVersion();

    /**
     * The default scheduler which should be used when the application does not
     * provide one.
     */
    public final ReferenceCountedObject<ScheduledExecutorService> DEFAULT_SCHEDULER =
            new ReferenceCountedObject<ScheduledExecutorService>() {

                @Override
                protected ScheduledExecutorService newInstance() {
                    final ThreadFactory factory =
                            Utils.newThreadFactory(null,
                                    "OpenICF Client ConnectionManager Scheduler %d", true);
                    return Executors.newScheduledThreadPool(getManagerConfig()
                            .getScheduledThreadPoolSize(), factory);
                }

                @Override
                protected void destroyInstance(ScheduledExecutorService instance) {
                    instance.shutdown();
                    try {
                        instance.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };

    private TCPNIOTransport clientTransport = null;
    private DelayedExecutor.Resolver<Connection> resolver;
    private DelayedExecutor timeoutExecutor;
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduledExecutorService;

    protected final ConcurrentMap<String, WebSocketConnectionGroup> connectionGroups =
            new ConcurrentHashMap<String, WebSocketConnectionGroup>();
    private final Map<RemoteWSFrameworkConnectionInfo, ClientRemoteConnectorInfoManager> registry =
            new ConcurrentHashMap<RemoteWSFrameworkConnectionInfo, ClientRemoteConnectorInfoManager>();
    private final ScheduledFuture<?> groupCheckFuture;

    private final Runnable groupChecker = new Runnable() {
        public void run() {
            if (isRunning()) {
                for (WebSocketConnectionGroup group : connectionGroups.values()) {
                    logger.ok("Check ConnectionGroup:{0} - operational={1}", group
                            .getRemoteSessionId(), group.isOperational());
                }
            }
        }
    };

    public ConnectionManager(final OperationMessageListener messageListener,
            final ConnectionManagerConfig config) throws Exception {
        super(messageListener, config);
        this.scheduledExecutorService = DEFAULT_SCHEDULER.acquire();
        init();
        groupCheckFuture =
                scheduledExecutorService.get().scheduleAtFixedRate(groupChecker, 30, 60,
                        TimeUnit.SECONDS);
    }

    protected void init() throws Exception {
        clientTransport = initializeNIOTransport(getManagerConfig());
        clientTransport.setProcessor(initializeFilterChain(getManagerConfig()));
        clientTransport.start();
    }

    protected void doClose() {
        try {
            groupCheckFuture.cancel(false);
            for (ClientRemoteConnectorInfoManager manager : registry.values()) {
                manager.close();
            }
            registry.clear();

            for (WebSocketConnectionGroup group : connectionGroups.values()) {
                // group.close();
            }
            connectionGroups.clear();

            clientTransport.shutdownNow();

            if (timeoutExecutor != null) {
                timeoutExecutor.stop();
                timeoutExecutor.getThreadPool().shutdownNow();
            }
        } catch (IOException ignored) {
            logger.ok(ignored, "Failed closed with exception");
        }
        scheduledExecutorService.release();
    }

    public ClientRemoteConnectorInfoManager connect(final RemoteWSFrameworkConnectionInfo info) {
        if (isRunning()) {
            ClientRemoteConnectorInfoManager manager = registry.get(info);
            if (null == manager) {
                synchronized (this) {
                    manager = registry.get(info);
                    if (null == manager) {
                        manager =
                                new ClientRemoteConnectorInfoManager(clientTransport, info,
                                        getMessageListener(), scheduledExecutorService,
                                        connectionGroups);

                        if (!manager.isSelfManaged()) {
                            throw new IllegalStateException(
                                    "Failed to start keepConnected Scheduler");
                        }

                        manager.addCloseListener(new org.forgerock.openicf.framework.CloseListener<ClientRemoteConnectorInfoManager>() {
                            public void onClosed(ClientRemoteConnectorInfoManager source) {
                                registry.remove(info);
                            }
                        });
                        registry.put(info, manager);
                        if (!isRunning() && registry.remove(info) != null) {
                            manager.close();
                            throw new IllegalStateException(
                                    "RemoteConnectionInfoManagerFactory is shut down");
                        }
                    }
                }
            }
            return manager;
        }
        throw new IllegalStateException("RemoteConnectionInfoManagerFactory is shut down");
    }

    protected TCPNIOTransport initializeNIOTransport(
            final ConnectionManagerConfig connectionManagerConfig) {
        final TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();

        builder.setOptimizedForMultiplexing(true).setName("OpenICF Grizzly NIO");

        builder.setIOStrategy(WorkerThreadIOStrategy.getInstance());

        // Calculate thread counts.
        final int cpus = Runtime.getRuntime().availableProcessors();

        // Calculate the number of selector threads.
        final int selectorThreadCount =
                connectionManagerConfig.getIoSelectorThreadCount() > 0 ? connectionManagerConfig
                        .getIoSelectorThreadCount() : Math.max(2, cpus / 4);

        builder.setSelectorThreadPoolConfig(ThreadPoolConfig.defaultConfig().setCorePoolSize(
                selectorThreadCount).setMaxPoolSize(selectorThreadCount).setPoolName(
                "OpenICF Client Grizzly selector thread"));

        // Calculate the number of worker threads.
        final int workerThreadCount =
                Math.max(5, (cpus * Math.max(1, connectionManagerConfig
                        .getIoWorkerThreadMultiplier())));

        builder.setWorkerThreadPoolConfig(ThreadPoolConfig.defaultConfig().setCorePoolSize(
                workerThreadCount).setMaxPoolSize(workerThreadCount).setPoolName(
                "OpenICF Client Grizzly worker thread"));

        final TCPNIOTransport transport = builder.build();

        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(
                AsyncQueueWriter.AUTO_SIZE);

        // FIXME: raise bug in Grizzly. We should not need to do this, but
        // failure to do so causes many deadlocks.
        transport.setSelectorRunnersCount(selectorThreadCount);

        return transport;
    }

    protected FilterChain initializeFilterChain(final ConnectionManagerConfig clientConfig) {

        final FilterChainBuilder fcb = FilterChainBuilder.stateless();
        fcb.add(new TransportFilter());

        final int timeout = clientConfig.getRequestTimeoutInMs();
        if (timeout > 0) {
            int delay = 500;
            if (timeout < delay) {
                delay = timeout - 10;
            }
            timeoutExecutor =
                    IdleTimeoutFilter
                            .createDefaultIdleDelayedExecutor(delay, TimeUnit.MILLISECONDS);
            timeoutExecutor.start();
            final IdleTimeoutFilter.TimeoutResolver timeoutResolver =
                    new IdleTimeoutFilter.TimeoutResolver() {
                        @Override
                        public long getTimeout(FilterChainContext ctx) {
                            return clientConfig.getWebSocketIdleTimeoutInMs();
                        }
                    };
            final IdleTimeoutFilter timeoutFilter =
                    new IdleTimeoutFilter(timeoutExecutor, timeoutResolver,
                            new IdleTimeoutFilter.TimeoutHandler() {
                                public void onTimeout(Connection connection) {
                                    WebSocketHolder.get(connection).webSocket.close(
                                            WebSocket.NORMAL_CLOSURE, "Idle timeout occurred");
                                }
                            });
            fcb.add(timeoutFilter);
            resolver = timeoutFilter.getResolver();
        }

        SSLContextConfigurator contextConfigurator = createSSLContextConfigurator(clientConfig);
        SSLContext context =
                null != contextConfigurator ? contextConfigurator.createSSLContext() : null;
        boolean defaultSecState = (context != null);
        if (context == null) {
            try {
                context = DEFAULT_CONFIG.createSSLContext();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        final SSLEngineConfigurator configurator =
                new SSLEngineConfigurator(context, true, false, false);
        final SwitchingSSLFilter filter = new SwitchingSSLFilter(configurator, defaultSecState);
        fcb.add(filter);

        final AsyncHttpClientEventFilter eventFilter =
                new AsyncHttpClientEventFilter(HttpCodecFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE);

        fcb.add(eventFilter);

        fcb.add(new IC());

        return fcb.build();
    }

    protected SSLContextConfigurator createSSLContextConfigurator(
            final ConnectionManagerConfig clientConfig) {
        final AtomicReference<SSLContextConfigurator> configurator =
                new AtomicReference<SSLContextConfigurator>();

        String trustStoreProvider = clientConfig.getTrustStoreProvider();
        if (StringUtil.isNotBlank(trustStoreProvider)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setTrustStoreProvider(trustStoreProvider);
        }
        String keyStoreProvider = clientConfig.getKeyStoreProvider();
        if (StringUtil.isNotBlank(keyStoreProvider)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setKeyStoreProvider(keyStoreProvider);
        }

        String trustStoreType = clientConfig.getTrustStoreType();
        if (StringUtil.isNotBlank(trustStoreType)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setTrustStoreType(trustStoreType);
        }
        String keyStoreType = clientConfig.getKeyStoreType();
        if (StringUtil.isNotBlank(keyStoreType)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setKeyStoreType(keyStoreType);
        }

        if (null != clientConfig.getTrustStorePass()) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            clientConfig.getTrustStorePass().access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    configurator.get().setTrustStorePass(new String(clearChars));
                }
            });

        }
        if (null != clientConfig.getKeyStorePass()) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            clientConfig.getKeyStorePass().access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    configurator.get().setKeyStorePass(clearChars);
                }
            });
        }
        if (null != clientConfig.getKeyPass()) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            clientConfig.getKeyPass().access(new GuardedString.Accessor() {
                public void access(char[] clearChars) {
                    configurator.get().setKeyPass(clearChars);
                }
            });
        }

        String trustStoreFile = clientConfig.getTrustStoreFile();
        if (StringUtil.isNotBlank(trustStoreFile)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setTrustStoreFile(trustStoreFile);
        }
        String keyStoreFile = clientConfig.getKeyStoreFile();
        if (StringUtil.isNotBlank(keyStoreFile)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setKeyStoreFile(keyStoreFile);
        }

        byte[] trustStoreBytes = clientConfig.getTrustStoreBytes();
        if (null != trustStoreBytes) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setKeyStoreBytes(trustStoreBytes);
        }
        byte[] keyStoreBytes = clientConfig.getKeyStoreBytes();
        if (null != keyStoreBytes) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setKeyStoreBytes(keyStoreBytes);
        }

        String trustManagerFactoryAlgorithm = clientConfig.getTrustManagerFactoryAlgorithm();
        if (StringUtil.isNotBlank(trustManagerFactoryAlgorithm)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setTrustManagerFactoryAlgorithm(trustManagerFactoryAlgorithm);
        }
        String keyManagerFactoryAlgorithm = clientConfig.getKeyManagerFactoryAlgorithm();
        if (StringUtil.isNotBlank(keyManagerFactoryAlgorithm)) {
            if (configurator.get() == null) {
                configurator.set(new SSLContextConfigurator(false));
            }
            configurator.get().setKeyManagerFactoryAlgorithm(keyManagerFactoryAlgorithm);
        }

        return configurator.get();
    }

    // ----------------------------------------------------------- Inner Classes

    public static class RemoteConnectionContext {

        private final RemoteWSFrameworkConnectionInfo connectionInfo;

        boolean establishingTunnel;

        private HttpResponsePacket responsePacket;

        public RemoteConnectionContext(final Connection c,
                final RemoteWSFrameworkConnectionInfo connectionInfo) {
            this.connectionInfo = connectionInfo;
            set(c, this);
        }

        private final CloseListener listener = new CloseListener<Closeable, CloseType>() {
            @Override
            public void onClosed(Closeable closeable, CloseType type) throws IOException {
                if (responsePacket != null && isGracefullyFinishResponseOnClose()) {
                    // Connection was closed.
                    // This event is fired only for responses, which don't have
                    // associated transfer-encoding or content-length.
                    // We have to complete such a request-response processing
                    // gracefully.
                    final Connection c = responsePacket.getRequest().getConnection();
                    final FilterChain fc = (FilterChain) c.getProcessor();

                    fc.fireEventUpstream(c, new GracefulCloseEvent(responsePacket), null);
                    logger.ok("DEBUG - Connection Locally closed");
                } else if (CloseType.REMOTELY.equals(type)) {
                    logger.ok("Remotely Closed Connection");
                }
            }
        };

        public String getAuthority() {
            return connectionInfo.getRemoteURI().getAuthority();
        }

        boolean isGracefullyFinishResponseOnClose() {
            final HttpResponsePacket response = responsePacket;
            return !response.getProcessingState().isKeepAlive() && !response.isChunked()
                    && response.getContentLength() == -1;
        }

        // -------------------------------------------------------- Static
        // methods

        static void set(final Connection c, final RemoteConnectionContext httpTxContext) {
            c.addCloseListener(httpTxContext.listener);
            REQUEST_STATE_ATTR.set(c, httpTxContext);
        }

        static RemoteConnectionContext remove(final Connection c) {
            final RemoteConnectionContext httpTxContext = REQUEST_STATE_ATTR.remove(c);
            c.removeCloseListener(httpTxContext.listener);
            return httpTxContext;

        }

        static RemoteConnectionContext get(final Connection c) {
            return REQUEST_STATE_ATTR.get(c);
        }

        boolean isTunnelEstablished(final Connection c) {
            return c.getAttributes().getAttribute("tunnel-established") != null;
        }

        void tunnelEstablished(final Connection c) {
            c.getAttributes().setAttribute("tunnel-established", Boolean.TRUE);
        }

    }

    public static String computeBasicAuthentication(String principal, GuardedString password)
            throws UnsupportedEncodingException {
        String s = principal + ":" + SecurityUtil.decrypt(password);
        return "Basic " + Base64.encode(s.getBytes("ISO-8859-1"));
    }

    private static final class IC extends WebSocketClientFilter {

        private static final Logger logger = Grizzly.logger(WebSocketClientFilter.class);

        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            // Get connection
            final Connection connection = ctx.getConnection();

            // Get the parsed HttpContent (we assume prev. filter was HTTP)
            final HttpContent message = ctx.getMessage();
            // Get the HTTP header
            final HttpResponsePacket httpHeader = (HttpResponsePacket) message.getHttpHeader();

            final RemoteConnectionContext context =
                    RemoteConnectionContext.get(ctx.getConnection());
            if (context.establishingTunnel
                    && HttpStatus.OK_200.statusMatches(httpHeader.getStatus())) {
                context.establishingTunnel = false;

                context.tunnelEstablished(connection);
                try {
                    ConnectionManager.logger.ok("Tunnel is established: {0}", connection
                            .getPeerAddress());

                    HttpRequestPacket requestPacket =
                            (HttpRequestPacket) WebSocketHolder.get(connection).handshake
                                    .composeHeaders().getHttpHeader();

                    boolean secure = context.connectionInfo.isSecure();
                    requestPacket.setSecure(secure);
                    ctx.notifyDownstream(new SwitchingSSLFilter.SSLSwitchingEvent(secure,
                            connection));

                    addServiceHeaders(requestPacket);
                    addAuthorizationHeader(context.connectionInfo.getPrincipal(),
                            context.connectionInfo.getPassword(), requestPacket);

                    addHostHeaderIfNeeded(context.getAuthority(), requestPacket);

                    if (ConnectionManager.logger.isOk()) {
                        ConnectionManager.logger.ok("REQUEST: " + requestPacket.toString());
                    }
                    ctx.write(requestPacket);

                } catch (IOException e) {
                    ClientRemoteConnectorInfoManager.CONNECT_PROMISE.get(connection).handleError(
                            new ConnectorIOException(e.getMessage(), e));
                    if (ConnectionManager.logger.isWarning()) {
                        ConnectionManager.logger.warn(e.toString(), e);
                    }
                    throw e;
                }
                return ctx.getStopAction();
            } else {
                return super.handleRead(ctx);
            }
        }

        protected void onHandshakeFailure(final Connection connection, final HandshakeException e) {
            super.onHandshakeFailure(connection, e);
            ClientRemoteConnectorInfoManager.CONNECT_PROMISE.get(connection).handleError(
                    new ConnectorIOException(e.getMessage(), e));
        }

        @Override
        public NextAction handleConnect(FilterChainContext ctx) throws IOException {
            // Get connection
            final Connection connection = ctx.getConnection();
            // check if it's websocket connection
            if (!webSocketInProgress(connection)) {
                // if not - pass processing to a next filter
                return ctx.getInvokeAction();
            }

            final RemoteConnectionContext context = RemoteConnectionContext.get(connection);
            final boolean useProxy = context.connectionInfo.isUseProxy();

            final boolean isEstablishingConnectTunnel =
                    useProxy && !context.isTunnelEstablished(connection);

            if (isEstablishingConnectTunnel) {
                // once the tunnel is established, handleConnect will
                // be called again and we'll finally send the request over the
                // tunnel
                establishConnectTunnel(context, ctx);
            } else {
                boolean secure = context.connectionInfo.isSecure();

                WebSocketHolder holder = WebSocketHolder.get(connection);

                HttpRequestPacket requestPacket =
                        (HttpRequestPacket) holder.handshake.composeHeaders().getHttpHeader();

                // @formatter:off
                    /* This may be a bug if the Proxy requires rawURI
                    if (secure && config.isUseRelativeURIsWithConnectProxies()) {
                        // Sending message over established CONNECT tunnel
                        builder.uri(uri.getRawPath());
                        builder.query(uri.getRawQuery());
                    }
                    */
                // @formatter:on

                requestPacket.setSecure(secure);
                ctx.notifyDownstream(new SwitchingSSLFilter.SSLSwitchingEvent(secure, connection));

                addServiceHeaders(requestPacket);
                addAuthorizationHeader(context.connectionInfo.getPrincipal(),
                        context.connectionInfo.getPassword(), requestPacket);

                addHostHeaderIfNeeded(context.getAuthority(), requestPacket);
                if (useProxy) {
                    addProxyHeaders(context, requestPacket);
                }

                if (ConnectionManager.logger.isOk()) {
                    ConnectionManager.logger.ok("REQUEST: " + requestPacket.toString());
                }
                ctx.write(requestPacket);
            }
            // call the next filter in the chain
            return ctx.getInvokeAction();
        }

        private void establishConnectTunnel(final RemoteConnectionContext httpCtx,
                final FilterChainContext ctx) throws IOException {
            final Connection connection = ctx.getConnection();

            final HttpRequestPacket requestPacket =
                    HttpRequestPacket.builder().protocol(Protocol.HTTP_1_0).method(Method.CONNECT)
                            .uri(httpCtx.getAuthority()).build();

            httpCtx.establishingTunnel = true;

            // turn off SSL, because CONNECT will be sent in plain mode
            ctx.notifyDownstream(new SwitchingSSLFilter.SSLSwitchingEvent(false, connection));

            addServiceHeaders(requestPacket);
            addHostHeaderIfNeeded(httpCtx.getAuthority(), requestPacket);

            addProxyHeaders(httpCtx, requestPacket);

            ctx.write(requestPacket);
        }

        private void addAuthorizationHeader(final String principal, final GuardedString password,
                final HttpRequestPacket requestPacket) throws UnsupportedEncodingException {
            if (principal != null && password != null) {
                final String authHeaderValue = computeBasicAuthentication(principal, password);
                if (authHeaderValue != null) {
                    requestPacket.addHeader(Header.Authorization, authHeaderValue);
                }
            }
        }

        private void addProxyAuthorizationHeader(final String principal,
                final GuardedString password, final HttpRequestPacket requestPacket)
                throws UnsupportedEncodingException {
            if (principal != null && password != null) {
                final String authHeaderValue = computeBasicAuthentication(principal, password);
                if (authHeaderValue != null) {
                    requestPacket.addHeader(Header.ProxyAuthorization, authHeaderValue);
                }
            }
        }

        private void addProxyHeaders(final RemoteConnectionContext proxy,
                final HttpRequestPacket requestPacket) throws UnsupportedEncodingException {

            if (!requestPacket.getHeaders().contains(Header.ProxyConnection)) {
                requestPacket.setHeader(Header.ProxyConnection, "keep-alive");
            }

            if (StringUtil.isNotBlank(proxy.connectionInfo.getProxyPrincipal())) {
                addProxyAuthorizationHeader(proxy.connectionInfo.getProxyPrincipal(),
                        proxy.connectionInfo.getProxyPassword(), requestPacket);
            }
        }

        private void addHostHeaderIfNeeded(final String host, final HttpRequestPacket requestPacket) {
            if (!requestPacket.containsHeader(Header.Host)) {
                requestPacket.addHeader(Header.Host, host);
            }
        }

        private void addServiceHeaders(final HttpRequestPacket requestPacket) {
            final MimeHeaders headers = requestPacket.getHeaders();
            if (!headers.contains(Header.Connection)) {
                requestPacket.addHeader(Header.Connection, "keep-alive");
            }

            if (!headers.contains(Header.Accept)) {
                requestPacket.addHeader(Header.Accept, "*/*");
            }

            if (!headers.contains(Header.UserAgent)) {
                requestPacket.addHeader(Header.UserAgent, USER_AGENT);
            }
        }

    } // END AsyncHttpClientFiler

    private static final class AsyncHttpClientEventFilter extends HttpClientFilter {

        // -------------------------------------------------------- Constructors

        AsyncHttpClientEventFilter(int maxHeadersSize) {
            super(maxHeadersSize);
        }

        // --------------------------------------- Methods from HttpClientFilter

        @Override
        public NextAction handleEvent(final FilterChainContext ctx, final FilterChainEvent event)
                throws IOException {
            if (event.type() == GracefulCloseEvent.class) {
                // Connection was closed.
                // This event is fired only for responses, which don't have
                // associated transfer-encoding or content-length.
                // We have to complete such a request-response processing
                // gracefully.
                final GracefulCloseEvent closeEvent = (GracefulCloseEvent) event;
                final HttpResponsePacket response = closeEvent.getResponsePacket();
                response.getProcessingState().getHttpContext().attach(ctx);
                onHttpPacketParsed(response, ctx);

                return ctx.getStopAction();
            }

            return ctx.getInvokeAction();
        }

        @Override
        public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
            logger.ok(error, "DEBUG - onHttpHeaderError:{0}", ctx);
            ClientRemoteConnectorInfoManager.CONNECT_PROMISE.get(ctx.getConnection()).handleError(
                    new ConnectorIOException(error.getMessage(), error));
        }

        @Override
        protected void onHttpHeaderError(final HttpHeader httpHeader, final FilterChainContext ctx,
                final Throwable error) throws IOException {
            logger.ok(error, "DEBUG - onHttpHeaderError:{0}", httpHeader);
            httpHeader.setSkipRemainder(true);
            ClientRemoteConnectorInfoManager.CONNECT_PROMISE.get(ctx.getConnection()).handleError(
                    new ConnectorIOException(error.getMessage(), error));
        }

        @Override
        protected void onHttpContentError(final HttpHeader httpHeader,
                final FilterChainContext ctx, final Throwable error) throws IOException {
            logger.ok(error, "DEBUG - onHttpContentError:{0}", httpHeader);
            httpHeader.setSkipRemainder(true);
            ClientRemoteConnectorInfoManager.CONNECT_PROMISE.get(ctx.getConnection()).handleError(
                    new ConnectorIOException(error.getMessage(), error));
        }

        @Override
        protected void onInitialLineParsed(HttpHeader httpHeader, FilterChainContext ctx) {

            super.onInitialLineParsed(httpHeader, ctx);
            if (httpHeader.isSkipRemainder()) {
                return;
            }
            final RemoteConnectionContext context =
                    RemoteConnectionContext.get(ctx.getConnection());
            final int status = ((HttpResponsePacket) httpHeader).getStatus();
            if (context.establishingTunnel && HttpStatus.OK_200.statusMatches(status)) {
                return;
            }

            context.responsePacket = ((HttpResponsePacket) httpHeader);
            if (101 != status) {
                httpHeader.setSkipRemainder(true);
                // new ConnectorIOException("Unexpected result:" + status));
            }

        }

        @SuppressWarnings({ "unchecked" })
        @Override
        protected void onHttpHeadersParsed(HttpHeader httpHeader, MimeHeaders headers,
                FilterChainContext ctx) {
            super.onHttpHeadersParsed(httpHeader, headers, ctx);
            logger.ok("RESPONSE: {0}", httpHeader);
            if (httpHeader.containsHeader(Header.Connection)) {
                if ("close".equals(httpHeader.getHeader(Header.Connection))) {
                    WebSocketHolder.get(ctx.getConnection()).handler.doClose();
                }
            }
        }

        @Override
        protected boolean onHttpHeaderParsed(final HttpHeader httpHeader, final Buffer buffer,
                final FilterChainContext ctx) {
            super.onHttpHeaderParsed(httpHeader, buffer, ctx);

            final HttpRequestPacket request = ((HttpResponsePacket) httpHeader).getRequest();
            if (Method.CONNECT.equals(request.getMethod())) {
                // finish request/response processing, because Grizzly itself
                // treats CONNECT traffic as part of request-response processing
                // and we don't want it be treated like that
                httpHeader.setExpectContent(false);
            }
            return false;
        }

    } // END AsyncHttpClientEventFilter

    /**
     * {@link FilterChainEvent} to gracefully complete the request-response
     * processing when {@link Connection} is getting closed by the remote host.
     *
     * @since 1.8.7
     * @author The Grizzly Team
     */
    public static class GracefulCloseEvent implements FilterChainEvent {

        private final HttpResponsePacket responsePacket;

        public GracefulCloseEvent(HttpResponsePacket responsePacket) {
            this.responsePacket = responsePacket;
        }

        public HttpResponsePacket getResponsePacket() {
            return responsePacket;
        }

        @Override
        public Object type() {
            return GracefulCloseEvent.class;
        }
    } // END GracefulCloseEvent

    static final class SwitchingSSLFilter extends SSLFilter {

        private final boolean secureByDefault;
        final Attribute<Boolean> CONNECTION_IS_SECURE = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
                .createAttribute(SwitchingSSLFilter.class.getName());

        // -------------------------------------------------------- Constructors

        SwitchingSSLFilter(final SSLEngineConfigurator clientConfig, final boolean secureByDefault) {

            super(null, clientConfig);
            this.secureByDefault = secureByDefault;

        }

        // ---------------------------------------------- Methods from SSLFilter

        @Override
        public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event)
                throws IOException {

            if (event.type() == SSLSwitchingEvent.class) {
                final SSLSwitchingEvent se = (SSLSwitchingEvent) event;
                CONNECTION_IS_SECURE.set(se.connection, se.secure);
                return ctx.getStopAction();
            }
            return ctx.getInvokeAction();

        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {

            if (isSecure(ctx.getConnection())) {
                return super.handleRead(ctx);
            }
            return ctx.getInvokeAction();

        }

        @Override
        public NextAction handleWrite(FilterChainContext ctx) throws IOException {

            if (isSecure(ctx.getConnection())) {
                return super.handleWrite(ctx);
            }
            return ctx.getInvokeAction();

        }

        @Override
        public void onFilterChainChanged(FilterChain filterChain) {
            // no-op
        }

        // ----------------------------------------------------- Private Methods

        private boolean isSecure(final Connection c) {

            Boolean secStatus = CONNECTION_IS_SECURE.get(c);
            if (secStatus == null) {
                secStatus = secureByDefault;
            }
            return secStatus;

        }

        // ------------------------------------------------------ Nested Classes

        static final class SSLSwitchingEvent implements FilterChainEvent {

            final boolean secure;
            final Connection connection;

            // ---------------------------------------------------- Constructors

            SSLSwitchingEvent(final boolean secure, final Connection c) {

                this.secure = secure;
                connection = c;

            }

            // ----------------------------------- Methods from FilterChainEvent

            @Override
            public Object type() {
                return SSLSwitchingEvent.class;
            }

        } // END SSLSwitchingEvent

    } // END SwitchingSSLFilter

}
