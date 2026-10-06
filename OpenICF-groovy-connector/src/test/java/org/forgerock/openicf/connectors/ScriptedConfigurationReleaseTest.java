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

package org.forgerock.openicf.connectors;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConfiguration;
import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConfiguration;
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration;
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import groovy.lang.Closure;

/**
 * Tests that the REST, CREST and SQL configurations close their HTTP client or connection pool
 * on {@code release()} even when the release closure throws.
 */
public class ScriptedConfigurationReleaseTest {

    /** A stand-in for the released resource that counts its {@code close()} calls. */
    private interface CloseCounter {
        int closes();
    }

    private static final class CountingHttpClient extends CloseableHttpClient implements CloseCounter {

        private final RuntimeException closeFailure;

        private int closes;

        CountingHttpClient(RuntimeException closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() {
            closes++;
            if (null != closeFailure) {
                throw closeFailure;
            }
        }

        @Override
        public int closes() {
            return closes;
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request,
                HttpContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("deprecation")
        public HttpParams getParams() {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("deprecation")
        public ClientConnectionManager getConnectionManager() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CountingHttpAsyncClient extends CloseableHttpAsyncClient
            implements CloseCounter {

        private final RuntimeException closeFailure;

        private int closes;

        CountingHttpAsyncClient(RuntimeException closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() {
            closes++;
            if (null != closeFailure) {
                throw closeFailure;
            }
        }

        @Override
        public int closes() {
            return closes;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void start() {
        }

        @Override
        public <T> Future<T> execute(HttpAsyncRequestProducer requestProducer,
                HttpAsyncResponseConsumer<T> responseConsumer, HttpContext context,
                FutureCallback<T> callback) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CountingDataSource extends DataSource implements CloseCounter {

        private final RuntimeException closeFailure;

        private int closes;

        CountingDataSource(RuntimeException closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() {
            closes++;
            if (null != closeFailure) {
                throw closeFailure;
            }
        }

        @Override
        public int closes() {
            return closes;
        }
    }

    /** One configuration whose {@code release()} override closes a resource. */
    private static final class Connector {

        private final String name;

        private final Supplier<ScriptedConfiguration> configuration;

        private final String field;

        private final Function<RuntimeException, CloseCounter> resource;

        Connector(String name, Supplier<ScriptedConfiguration> configuration, String field,
                Function<RuntimeException, CloseCounter> resource) {
            this.name = name;
            this.configuration = configuration;
            this.field = field;
            this.resource = resource;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @DataProvider
    public Object[][] connectors() {
        return new Object[][] {
            { new Connector("REST", ScriptedRESTConfiguration::new, "httpClient",
                    CountingHttpClient::new) },
            { new Connector("CREST", ScriptedCRESTConfiguration::new, "httpClient",
                    CountingHttpAsyncClient::new) },
            { new Connector("SQL", ScriptedSQLConfiguration::new, "dataSource",
                    CountingDataSource::new) },
        };
    }

    @Test(dataProvider = "connectors")
    public void testThrowingReleaseClosureStillClosesTheResource(Connector connector)
            throws Exception {
        final ScriptedConfiguration configuration = connector.configuration.get();
        final CloseCounter resource = connector.resource.apply(null);
        setResource(configuration, connector, resource);
        final RuntimeException closureFailure = new IllegalStateException("release failed");
        configuration.setReleaseClosure(throwing(closureFailure));

        try {
            configuration.release();
            fail("The release closure failure must reach the caller");
        } catch (RuntimeException e) {
            assertThat(e).isSameAs(closureFailure);
        }
        assertThat(resource.closes()).isEqualTo(1);
        assertThat(getResource(configuration, connector)).isNull();
    }

    @Test(dataProvider = "connectors")
    public void testCloseFailureDoesNotHideTheReleaseClosureFailure(Connector connector)
            throws Exception {
        final ScriptedConfiguration configuration = connector.configuration.get();
        final CloseCounter resource =
                connector.resource.apply(new IllegalStateException("close failed"));
        setResource(configuration, connector, resource);
        final RuntimeException closureFailure = new IllegalStateException("release failed");
        configuration.setReleaseClosure(throwing(closureFailure));

        try {
            configuration.release();
            fail("The release closure failure must reach the caller");
        } catch (RuntimeException e) {
            assertThat(e).isSameAs(closureFailure);
        }
        assertThat(resource.closes()).isEqualTo(1);
        assertThat(getResource(configuration, connector)).isNull();
    }

    @Test(dataProvider = "connectors")
    public void testCloseFailureStillClearsTheResource(Connector connector) throws Exception {
        final ScriptedConfiguration configuration = connector.configuration.get();
        final CloseCounter resource =
                connector.resource.apply(new IllegalStateException("close failed"));
        setResource(configuration, connector, resource);

        configuration.release();

        assertThat(resource.closes()).isEqualTo(1);
        assertThat(getResource(configuration, connector)).isNull();
    }

    private static Closure<Void> throwing(final RuntimeException failure) {
        return new Closure<Void>(null) {
            public Void doCall() {
                throw failure;
            }
        };
    }

    private static void setResource(ScriptedConfiguration configuration, Connector connector,
            CloseCounter resource) throws Exception {
        field(configuration, connector).set(configuration, resource);
    }

    private static Object getResource(ScriptedConfiguration configuration, Connector connector)
            throws Exception {
        return field(configuration, connector).get(configuration);
    }

    private static Field field(ScriptedConfiguration configuration, Connector connector)
            throws Exception {
        final Field field = configuration.getClass().getDeclaredField(connector.field);
        field.setAccessible(true);
        return field;
    }
}
