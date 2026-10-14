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
package org.forgerock.openicf.misc.crest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.Future;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestType;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourcePath;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.ServiceUnavailableException;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.query.QueryFilter;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AbstractRemoteConnectionTest {

    private static class StubConnection extends AbstractRemoteConnection {

        StubConnection() {
            super(ResourcePath.valueOf("api"), new HttpHost("localhost", 8080));
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public <T> Future<T> execute(Context context, HttpUriRequest request,
                HttpAsyncResponseConsumer<T> responseConsumer, FutureCallback<T> callback) {
            throw new AssertionError("no HTTP request expected, got " + request);
        }

        @Override
        protected JsonValue parseJsonBody(HttpEntity entity, boolean allowEmpty) {
            throw new AssertionError();
        }

        @Override
        protected QueryResponse parseQueryResponse(HttpResponse response,
                QueryResourceHandler handler) {
            throw new AssertionError();
        }
    }

    private static QueryRequest usersQuery() {
        return Requests.newQueryRequest("users").setQueryFilter(QueryFilter.<JsonPointer>alwaysTrue());
    }

    private static final QueryResourceHandler NO_RESOURCES_EXPECTED = new QueryResourceHandler() {
        @Override
        public boolean handleResource(ResourceResponse resource) {
            throw new AssertionError("no resource expected, got " + resource);
        }
    };

    /** Pins kept behaviour: a Throwable from execute() still becomes an InternalServerErrorException. */
    @Test
    public void testQueryWrapsAnErrorFromExecuteInInternalServerError() {
        try {
            new StubConnection().query(new RootContext(), usersQuery(), NO_RESOURCES_EXPECTED);
            Assert.fail("InternalServerErrorException expected");
        } catch (ResourceException e) {
            Assert.assertTrue(e instanceof InternalServerErrorException, String.valueOf(e));
            Assert.assertTrue(e.getCause() instanceof AssertionError, String.valueOf(e.getCause()));
        }
    }

    /** Pins kept behaviour: an interrupted caller gets ServiceUnavailableException. */
    @Test(timeOut = 10000)
    public void testInterruptedQueryFailsWithServiceUnavailable() {
        final StubConnection pending = new StubConnection() {
            @Override
            public <T> Future<T> execute(Context context, HttpUriRequest request,
                    HttpAsyncResponseConsumer<T> responseConsumer, FutureCallback<T> callback) {
                return null; // the response never arrives
            }
        };
        Thread.currentThread().interrupt();
        try {
            pending.query(new RootContext(), usersQuery(), NO_RESOURCES_EXPECTED);
            Assert.fail("ServiceUnavailableException expected");
        } catch (ResourceException e) {
            Assert.assertTrue(e instanceof ServiceUnavailableException, String.valueOf(e));
        } finally {
            Thread.interrupted();
        }
    }

    /** Pins kept behaviour: a ResourceException from the transport reaches the caller unwrapped. */
    @Test
    public void testQueryPassesATransportResourceExceptionThrough() {
        final StubConnection cancelling = new StubConnection() {
            @Override
            public <T> Future<T> execute(Context context, HttpUriRequest request,
                    HttpAsyncResponseConsumer<T> responseConsumer, FutureCallback<T> callback) {
                callback.cancelled(); // InternalFutureCallback fails the promise with ServiceUnavailableException
                return null;
            }
        };
        try {
            cancelling.query(new RootContext(), usersQuery(), NO_RESOURCES_EXPECTED);
            Assert.fail("ServiceUnavailableException expected");
        } catch (ResourceException e) {
            Assert.assertTrue(e instanceof ServiceUnavailableException, String.valueOf(e));
        }
    }

    /** A read request that reports a type no {@code Connection} method produces. */
    private static ReadRequest apiTypedRequest() {
        final ReadRequest real = Requests.newReadRequest("users/1");
        return (ReadRequest) Proxy.newProxyInstance(ReadRequest.class.getClassLoader(),
                new Class<?>[] { ReadRequest.class }, (proxy, method, args) -> {
                    if ("getRequestType".equals(method.getName())) {
                        return RequestType.API;
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Test
    public void testUnsupportedRequestTypeFailsThePromise() throws InterruptedException {
        try {
            new StubConnection().readAsync(new RootContext(), apiTypedRequest()).getOrThrow();
            Assert.fail("NotSupportedException expected");
        } catch (ResourceException e) {
            Assert.assertTrue(e instanceof NotSupportedException, String.valueOf(e));
        }
    }
}
