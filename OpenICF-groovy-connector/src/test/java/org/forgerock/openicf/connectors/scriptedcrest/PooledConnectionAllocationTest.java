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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.connectors.scriptedcrest;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.conn.ManagedNHttpClientConnection;
import org.apache.http.nio.conn.NHttpClientConnectionManager;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Characterises the HTTP client that the connector is built against (issue #207).
 * <p>
 * The pool hands a free keep-alive connection to the request on the caller's thread, while the
 * I/O dispatcher runs the exchange. httpasyncclient 4.0.2 asks the dispatcher to write the request
 * first and checks {@code isOpen()} after that. If the dispatcher completes the exchange and
 * releases the connection in between, {@code isOpen()} is false and the caller fails a request
 * that the server has already answered with {@code ConnectionClosedException}. httpasyncclient 4.1
 * checks the connection first, and its dispatcher cannot start the exchange until that check
 * has returned. The test holds the caller after {@code requestOutput()} to make the interleaving
 * certain.
 */
public class PooledConnectionAllocationTest {

    /**
     * How long the caller is held at most. With httpasyncclient 4.1 the exchange cannot run during
     * the hold, so the hold always lasts this long.
     */
    private static final long HOLD_MILLIS = 1000;

    private final AtomicInteger served = new AtomicInteger();

    private Server server;

    private HttpHost host;

    @BeforeClass
    public void startServer() throws Exception {
        server = new Server(new InetSocketAddress("127.0.0.1", 0));
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) throws IOException {
                served.incrementAndGet();
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLength(0);
                baseRequest.setHandled(true);
            }
        });
        server.start();
        host = new HttpHost("127.0.0.1", ((ServerConnector) server.getConnectors()[0]).getLocalPort(),
                "http");
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() throws Exception {
        server.stop();
    }

    @Test
    public void testAnsweredRequestOnAPooledConnectionCompletes() throws Exception {
        final Hold hold = new Hold();
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(hold.wrap(
                        new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor())))
                .setKeepAliveStrategy(IdleKeepAliveStrategy.INSTANCE)
                .build();
        client.start();
        try {
            // Opens the connection; the pool calls back on the dispatcher for a new connection.
            Assert.assertEquals(get(client, hold).getStatusLine().getStatusCode(), 200);

            hold.caller = Thread.currentThread();
            final HttpResponse response = get(client, hold);

            Assert.assertTrue(hold.heldTheCaller,
                    "the pooled connection was not allocated on the caller's thread");
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
            Assert.assertEquals(served.get(), 2);
        } finally {
            client.close();
        }
    }

    private HttpResponse get(final CloseableHttpAsyncClient client, final Hold hold) throws Exception {
        // One per request: a late callback of the previous request must not count for this one.
        final CountDownLatch reported = new CountDownLatch(1);
        hold.outcomeReported = reported;
        final Future<HttpResponse> future = client.execute(HttpAsyncMethods.createGet(host.toURI() + "/"),
                new BasicAsyncResponseConsumer(), new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(HttpResponse result) {
                        reported.countDown();
                    }

                    @Override
                    public void failed(Exception ex) {
                        reported.countDown();
                    }

                    @Override
                    public void cancelled() {
                        reported.countDown();
                    }
                });
        return future.get(10, TimeUnit.SECONDS);
    }

    /**
     * Wraps the pool's connection manager. The connections it hands out hold the {@link #caller}
     * after {@code requestOutput()} until the exchange has released the connection. The pool casts
     * a connection back to its own type, so every argument is unwrapped on the way in.
     */
    private static final class Hold {

        volatile Thread caller;

        volatile boolean holding;

        volatile boolean heldTheCaller;

        final CountDownLatch released = new CountDownLatch(1);

        volatile CountDownLatch outcomeReported;

        NHttpClientConnectionManager wrap(final NHttpClientConnectionManager manager) {
            return (NHttpClientConnectionManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { NHttpClientConnectionManager.class }, new InvocationHandler() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            for (int i = 0; args != null && i < args.length; i++) {
                                args[i] = unwrap(args[i]);
                            }
                            if (method.getName().equals("requestConnection")) {
                                args[5] = wrap((FutureCallback<NHttpClientConnection>) args[5]);
                            }
                            final boolean releasedWhileHolding =
                                    method.getName().equals("releaseConnection") && holding;
                            final Object result = call(method, manager, args);
                            if (releasedWhileHolding) {
                                released.countDown();
                                // Lets the caller report its outcome first, as in the failed CI runs.
                                outcomeReported.await(HOLD_MILLIS, TimeUnit.MILLISECONDS);
                            }
                            return result;
                        }
                    });
        }

        FutureCallback<NHttpClientConnection> wrap(final FutureCallback<NHttpClientConnection> callback) {
            return new FutureCallback<NHttpClientConnection>() {
                @Override
                public void completed(NHttpClientConnection connection) {
                    callback.completed(wrap(connection));
                }

                @Override
                public void failed(Exception ex) {
                    callback.failed(ex);
                }

                @Override
                public void cancelled() {
                    callback.cancelled();
                }
            };
        }

        NHttpClientConnection wrap(final NHttpClientConnection connection) {
            return (NHttpClientConnection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { ManagedNHttpClientConnection.class }, new ConnectionHandler(connection));
        }

        final class ConnectionHandler implements InvocationHandler {

            final NHttpClientConnection connection;

            ConnectionHandler(NHttpClientConnection connection) {
                this.connection = connection;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                final Object result = call(method, connection, args);
                if (method.getName().equals("requestOutput") && Thread.currentThread() == caller) {
                    caller = null;
                    heldTheCaller = true;
                    holding = true;
                    try {
                        released.await(HOLD_MILLIS, TimeUnit.MILLISECONDS);
                    } finally {
                        holding = false;
                    }
                }
                return result;
            }
        }

        private static Object unwrap(final Object argument) {
            if (argument != null && Proxy.isProxyClass(argument.getClass())
                    && Proxy.getInvocationHandler(argument) instanceof ConnectionHandler) {
                return ((ConnectionHandler) Proxy.getInvocationHandler(argument)).connection;
            }
            return argument;
        }

        private static Object call(final Method method, final Object target, final Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
