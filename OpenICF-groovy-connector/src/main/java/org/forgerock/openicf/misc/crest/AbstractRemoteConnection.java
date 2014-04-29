/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.misc.crest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.http.ContentTooLongException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;
import org.apache.http.util.EntityUtils;
import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.Context;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.FutureResult;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceName;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServiceUnavailableException;
import org.forgerock.json.resource.SortKey;
import org.forgerock.json.resource.UpdateRequest;

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
public abstract class AbstractRemoteConnection implements Connection {

    static final String ETAG_ANY = "*";
    static final String PARAM_ACTION = param(ActionRequest.FIELD_ACTION);
    static final String PARAM_FIELDS = param(Request.FIELD_FIELDS);
    static final String PARAM_PAGE_SIZE = param(QueryRequest.FIELD_PAGE_SIZE);
    static final String PARAM_PAGED_RESULTS_COOKIE = param(QueryRequest.FIELD_PAGED_RESULTS_COOKIE);
    static final String PARAM_PAGED_RESULTS_OFFSET = param(QueryRequest.FIELD_PAGED_RESULTS_OFFSET);
    static final String PARAM_QUERY_EXPRESSION = param(QueryRequest.FIELD_QUERY_EXPRESSION);
    static final String PARAM_QUERY_FILTER = param(QueryRequest.FIELD_QUERY_FILTER);
    static final String PARAM_QUERY_ID = param(QueryRequest.FIELD_QUERY_ID);
    static final String PARAM_SORT_KEYS = param(QueryRequest.FIELD_SORT_KEYS);

    static final Pattern CONTENT_TYPE_REGEX = Pattern.compile(
            "^application/json([ ]*;[ ]*charset=utf-8)?$", Pattern.CASE_INSENSITIVE);

    private final ResourceName resourceName;

    private final HttpHost httpHost;

    protected AbstractRemoteConnection(ResourceName resourceName, HttpHost httpHost) {
        this.resourceName = resourceName;
        this.httpHost = httpHost;
    }

    public abstract <T> Future<T> execute(final HttpUriRequest request,
            final HttpAsyncResponseConsumer<T> responseConsumer, final FutureCallback<T> callback);

    protected abstract JsonValue parseJsonBody(final HttpEntity entity, final boolean allowEmpty)
            throws ResourceException;

    protected abstract QueryResult parseQueryResponse(final HttpResponse response,
            final QueryResultHandler handler) throws ResourceException;

    public ResourceName getResourceName() {
        return resourceName;
    }

    public HttpHost getHttpHost() {
        return httpHost;
    }

    @Override
    public void close() {
        // Do Nothing
    }

    @Override
    public boolean isValid() {
        return !isClosed();
    }

    @Override
    public JsonValue action(final Context context, final ActionRequest request)
            throws ResourceException {
        final FutureResult<JsonValue> future = actionAsync(context, request, null);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public FutureResult<JsonValue> actionAsync(final Context context, final ActionRequest request,
            final ResultHandler<? super JsonValue> handler) {
        try {

            final Future<JsonValue> result =
                    execute(convert(request), new JsonValueResponseHandler(),
                            new InternalFutureCallback<JsonValue>(
                                    (ResultHandler<JsonValue>) handler));

            return new InternalFutureResult<JsonValue>(result);
        } catch (final Throwable t) {
            final ResourceException exception = adapt(t);
            if (null != handler) {
                handler.handleError(exception);
            }
            return new FailedFutureResult<JsonValue>(exception);
        }
    }

    @Override
    public QueryResult query(final Context context, final QueryRequest request,
            final QueryResultHandler handler) throws ResourceException {
        final FutureResult<QueryResult> future = queryAsync(context, request, handler);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public QueryResult query(final Context context, final QueryRequest request,
            final Collection<? super Resource> results) throws ResourceException {
        final QueryResultHandler handler = new QueryResultHandler() {

            @Override
            public void handleError(final ResourceException error) {
                // Ignore - handled by future.
            }

            @Override
            public boolean handleResource(final Resource resource) {
                results.add(resource);
                return true;
            }

            @Override
            public void handleResult(final QueryResult result) {
                // Ignore - handled by future.
            }
        };
        return query(context, request, handler);
    }

    @Override
    public FutureResult<QueryResult> queryAsync(Context context, QueryRequest request,
            final QueryResultHandler handler) {
        try {

            final Future<QueryResult> result =
                    execute(convert(request), new QueryResultResponseHandler(handler),
                            new InternalFutureCallback<QueryResult>(handler));

            return new InternalFutureResult<QueryResult>(result);
        } catch (final Throwable t) {
            final ResourceException exception = adapt(t);
            if (null != handler) {
                handler.handleError(exception);
            }
            return new FailedFutureResult<QueryResult>(exception);
        }
    }

    @Override
    public Resource create(final Context context, final CreateRequest request)
            throws ResourceException {
        final FutureResult<Resource> future = createAsync(context, request, null);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public FutureResult<Resource> createAsync(Context context, CreateRequest request,
            ResultHandler<? super Resource> handler) {
        return handleRequestAsync(request, handler);
    }

    @Override
    public Resource delete(final Context context, final DeleteRequest request)
            throws ResourceException {
        final FutureResult<Resource> future = deleteAsync(context, request, null);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public FutureResult<Resource> deleteAsync(Context context, DeleteRequest request,
            ResultHandler<? super Resource> handler) {
        return handleRequestAsync(request, handler);
    }

    @Override
    public Resource patch(final Context context, final PatchRequest request)
            throws ResourceException {
        final FutureResult<Resource> future = patchAsync(context, request, null);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public FutureResult<Resource> patchAsync(Context context, PatchRequest request,
            ResultHandler<? super Resource> handler) {
        return handleRequestAsync(request, handler);
    }

    @Override
    public Resource read(final Context context, final ReadRequest request) throws ResourceException {
        final FutureResult<Resource> future = readAsync(context, request, null);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public FutureResult<Resource> readAsync(Context context, ReadRequest request,
            ResultHandler<? super Resource> handler) {
        return handleRequestAsync(request, handler);
    }

    @Override
    public Resource update(final Context context, final UpdateRequest request)
            throws ResourceException {
        final FutureResult<Resource> future = updateAsync(context, request, null);
        try {
            return future.get();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    @Override
    public FutureResult<Resource> updateAsync(Context context, UpdateRequest request,
            ResultHandler<? super Resource> handler) {
        return handleRequestAsync(request, handler);
    }

    private HttpUriRequest convert(Request request) throws ResourceException {
        URIBuilder builder = getUriBuilder(request);
        HttpUriRequest rq = null;
        try {
            switch (request.getRequestType()) {
            case ACTION: {
                ActionRequest actionRequest = (ActionRequest) request;

                builder.setParameter(PARAM_ACTION, actionRequest.getAction());
                for (Map.Entry<String, String> entry : actionRequest.getAdditionalParameters()
                        .entrySet()) {
                    builder.setParameter(entry.getKey(), entry.getValue());
                }

                HttpPost httpRequest = new HttpPost(builder.build());
                httpRequest.setEntity(new JsonEntity(actionRequest.getContent()));

                rq = httpRequest;
                rq.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
                rq.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
                break;
            }
            case CREATE: {
                CreateRequest createRequest = (CreateRequest) request;

                if (null == createRequest.getNewResourceId()) {
                    builder.setParameter(PARAM_ACTION, ActionRequest.ACTION_ID_CREATE);
                    HttpPost httpRequest = new HttpPost(builder.build());
                    httpRequest.setEntity(new JsonEntity(createRequest.getContent()));
                    rq = httpRequest;
                } else {
                    HttpPut httpRequest = new HttpPut(builder.build());
                    httpRequest.setEntity(new JsonEntity(createRequest.getContent()));
                    httpRequest.setHeader(HttpHeaders.IF_NONE_MATCH, ETAG_ANY);
                    rq = httpRequest;
                }
                rq.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
                rq.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
                break;
            }
            case UPDATE: {
                UpdateRequest updateRequest = (UpdateRequest) request;

                HttpPut httpRequest = new HttpPut(builder.build());
                httpRequest.setEntity(new JsonEntity(updateRequest.getContent()));

                if (null != updateRequest.getRevision()) {
                    httpRequest.setHeader(HttpHeaders.ETAG, updateRequest.getRevision());
                }

                rq = httpRequest;
                rq.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
                rq.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
                break;
            }
            case PATCH: {
                PatchRequest patchRequest = (PatchRequest) request;

                List<Map<String, Object>> patch =
                        new ArrayList<Map<String, Object>>(patchRequest.getPatchOperations().size());
                for (PatchOperation operation : patchRequest.getPatchOperations()) {
                    patch.add(operation.toJsonValue().asMap());
                }

                HttpPatch httpRequest = new HttpPatch(builder.build());
                httpRequest.setEntity(new JsonEntity(patch));

                if (null != patchRequest.getRevision()) {
                    httpRequest.setHeader(HttpHeaders.ETAG, patchRequest.getRevision());
                }

                rq = httpRequest;
                rq.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
                rq.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
                break;
            }
            case DELETE: {
                DeleteRequest deleteRequest = (DeleteRequest) request;

                rq = new HttpDelete(builder.build());
                if (null != deleteRequest.getRevision()) {
                    rq.setHeader(HttpHeaders.ETAG, deleteRequest.getRevision());
                }
                break;
            }
            case READ: {
                rq = new HttpGet(builder.build());
                break;
            }
            case QUERY: {
                QueryRequest queryRequest = (QueryRequest) request;

                for (Map.Entry<String, String> entry : queryRequest.getAdditionalParameters()
                        .entrySet()) {
                    builder.setParameter(entry.getKey(), entry.getValue());
                }

                if (null != queryRequest.getQueryId()) {
                    builder.setParameter(PARAM_QUERY_ID, queryRequest.getQueryId());
                } else if (null != queryRequest.getQueryFilter()) {
                    builder.setParameter(PARAM_QUERY_FILTER, queryRequest.getQueryFilter()
                            .toString());
                } else if (null != queryRequest.getQueryExpression()) {
                    builder.setParameter(PARAM_QUERY_EXPRESSION, queryRequest.getQueryExpression());
                }

                if (0 < queryRequest.getPageSize()) {
                    builder.setParameter(PARAM_PAGE_SIZE, Integer.toString(queryRequest
                            .getPageSize()));

                    if (null != queryRequest.getPagedResultsCookie()) {
                        builder.setParameter(PARAM_PAGED_RESULTS_COOKIE, queryRequest
                                .getPagedResultsCookie());
                    }
                    builder.setParameter(PARAM_PAGED_RESULTS_OFFSET, Integer.toString(queryRequest
                            .getPagedResultsOffset()));
                }

                for (SortKey key : queryRequest.getSortKeys()) {
                    builder.setParameter(PARAM_SORT_KEYS, key.toString());
                }
                rq = new HttpGet(builder.build());
                break;
            }
            }
        } catch (URISyntaxException e) {
            throw new InternalServerErrorException(e);
        }
        return rq;
    }

    private URIBuilder getUriBuilder(Request request) {
        ResourceName resourceName = getResourceName().concat(request.getResourceNameObject());

        if (request instanceof CreateRequest
                && null != ((CreateRequest) request).getNewResourceId()) {
            resourceName = resourceName.concat(((CreateRequest) request).getNewResourceId());
        }

        URIBuilder builder =
                new URIBuilder().setScheme(getHttpHost().getSchemeName()).setHost(
                        getHttpHost().getHostName()).setPort(getHttpHost().getPort()).setPath(
                        "/" + resourceName.toString());

        for (JsonPointer field : request.getFields()) {
            builder.addParameter(PARAM_FIELDS, field.toString());
        }
        return builder;
    }

    private FutureResult<Resource> handleRequestAsync(Request request,
            ResultHandler<? super Resource> handler) {
        try {

            final Future<Resource> result =
                    execute(convert(request), new ResourceResponseHandler(),
                            new InternalFutureCallback<Resource>((ResultHandler<Resource>) handler));

            return new InternalFutureResult<Resource>(result);
        } catch (final Throwable t) {
            final ResourceException exception = adapt(t);
            if (null != handler) {
                handler.handleError(exception);
            }
            return new FailedFutureResult<Resource>(exception);
        }
    }

    // Handle thread interruption.
    private ResourceException interrupted(final InterruptedException e) {
        return new ServiceUnavailableException("Client thread interrupted", e);
    }

    // Internal Class definitions

    @Immutable
    static abstract public class AbstractJsonValueResponseHandler<T> extends
            AbstractAsyncResponseConsumer<T> {

        protected volatile HttpResponse response;
        private volatile SimpleInputBuffer buf;

        @Override
        protected void onResponseReceived(final HttpResponse response) throws IOException {
            this.response = response;
        }

        @Override
        protected void onEntityEnclosed(final HttpEntity entity, final ContentType contentType)
                throws IOException {
            long len = entity.getContentLength();
            if (len > Integer.MAX_VALUE) {
                throw new ContentTooLongException("Entity content is too long: " + len);
            }
            if (len < 0) {
                len = 4096;
            }
            this.buf = new SimpleInputBuffer((int) len, new HeapByteBufferAllocator());
            this.response.setEntity(new ContentBufferEntity(entity, this.buf));
        }

        @Override
        protected void onContentReceived(final ContentDecoder decoder, final IOControl ioctrl)
                throws IOException {
            Asserts.notNull(this.buf, "Content buffer");
            this.buf.consumeContent(decoder);
        }

        @Override
        protected void releaseResources() {
            this.response = null;
            this.buf = null;
        }

    }

    /**
     * A {@link org.apache.http.client.ResponseHandler} that returns the
     * response body as a JsonValue for successful (2xx) responses. If the
     * response code was >= 300, the response body is consumed and an
     * {@link HttpResponseResourceException} is thrown.
     * <p/>
     * If this is used with
     * {@link org.apache.http.client.HttpClient#execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler)}
     * , HttpClient may handle redirects (3xx responses) internally.
     */
    @Immutable
    public class JsonValueResponseHandler extends AbstractJsonValueResponseHandler<JsonValue> {

        /**
         * Returns the response body as a String if the response was successful
         * (a 2xx status code). If no response body exists, this returns null.
         * If the response was unsuccessful (>= 300 status code), throws an
         * {@link org.apache.http.client.HttpResponseException}.
         */
        @Override
        protected JsonValue buildResult(HttpContext context) throws Exception {
            return parseResponse(response);
        }

    }

    /**
     * A {@link org.apache.http.client.ResponseHandler} that returns the
     * response body as a String for successful (2xx) responses. If the response
     * code was >= 300, the response body is consumed and an
     * {@link org.apache.http.client.HttpResponseException} is thrown.
     * <p/>
     * If this is used with
     * {@link org.apache.http.client.HttpClient#execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler)}
     * , HttpClient may handle redirects (3xx responses) internally.
     */
    @Immutable
    public class QueryResultResponseHandler extends AbstractJsonValueResponseHandler<QueryResult> {

        private final QueryResultHandler handler;

        public QueryResultResponseHandler(final QueryResultHandler handler) {
            this.handler = handler;
        }

        /**
         * Returns the response body as a String if the response was successful
         * (a 2xx status code). If no response body exists, this returns null.
         * If the response was unsuccessful (>= 300 status code), throws an
         * {@link org.apache.http.client.HttpResponseException}.
         */
        public QueryResult buildResult(HttpContext context) throws Exception {

            return parseQueryResponse(response, handler);

        }

    }

    /**
     * A {@link org.apache.http.client.ResponseHandler} that returns the
     * response body as a Resource for successful (2xx) responses. If the
     * response code was >= 300, the response body is consumed and an
     * {@link HttpResponseResourceException} is thrown.
     * <p/>
     * If this is used with
     * {@link org.apache.http.client.HttpClient#execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler)}
     * , HttpClient may handle redirects (3xx responses) internally.
     */
    @Immutable
    public class ResourceResponseHandler extends AbstractJsonValueResponseHandler<Resource> {

        /**
         * Returns the response body as a String if the response was successful
         * (a 2xx status code). If no response body exists, this returns null.
         * If the response was unsuccessful (>= 300 status code), throws an
         * {@link org.apache.http.client.HttpResponseException}.
         */
        public Resource buildResult(HttpContext context) throws Exception {

            return getAsResource(parseResponse(response));

        }

    }

    /**
     * The HttpResponseResourceException wraps the {@link ResourceException}
     * into {@link org.apache.http.client.HttpResponseException}.
     * <p/>
     * This exception is used inside
     * {@link org.apache.http.client.ResponseHandler#handleResponse(org.apache.http.HttpResponse)}.
     */
    public static class HttpResponseResourceException extends HttpResponseException {

        private static final long serialVersionUID = 1L;

        private final ResourceException cause;

        public HttpResponseResourceException(ResourceException wrapped) {
            super(wrapped.getCode(), wrapped.getReason());
            cause = wrapped;
        }

        @Override
        public ResourceException getCause() {
            return cause;
        }
    }

    static private class InternalFutureCallback<R> implements FutureCallback<R> {

        private final ResultHandler<R> handler;

        protected InternalFutureCallback(final ResultHandler<R> handler) {
            this.handler = handler;
        }

        protected ResultHandler<R> getResultHandler() {
            return handler;
        }

        protected R adapt(R source) throws ResourceException {
            return source;
        }

        @Override
        public void completed(final R s) {
            final ResultHandler<R> handler = getResultHandler();
            if (null != handler) {
                try {
                    handler.handleResult(adapt(s));
                } catch (final ResourceException e) {
                    handler.handleError(e);
                }
            }
        }

        @Override
        public void failed(final Exception e) {
            final ResultHandler<R> handler = getResultHandler();
            if (null != handler) {
                if (e instanceof HttpResponseResourceException) {
                    handler.handleError(((HttpResponseResourceException) e).getCause());
                } else {
                    handler.handleError(new InternalServerErrorException(e));
                }
            }
        }

        @Override
        public void cancelled() {
            final ResultHandler<R> handler = getResultHandler();
            if (null != handler) {
                handler.handleError(new ServiceUnavailableException("Client thread interrupted"));
            }
        }
    }

    /**
     * @param < T >
     */
    private static class InternalFutureResult<T> implements FutureResult<T> {

        private final Future<T> futureTask;

        protected InternalFutureResult(final Future<T> futureTask) {
            this.futureTask = futureTask;
        }

        protected Future<T> getResult() {
            return futureTask;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return getResult().cancel(mayInterruptIfRunning);
        }

        @Override
        public T get() throws ResourceException, InterruptedException {
            try {
                return getResult().get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof ResourceException) {
                    throw (ResourceException) cause;
                }
                throw new InternalServerErrorException(e);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws ResourceException, TimeoutException,
                InterruptedException {
            try {
                return getResult().get(timeout, unit);
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof ResourceException) {
                    throw (ResourceException) cause;
                }
                throw new InternalServerErrorException(e);
            }
        }

        @Override
        public boolean isCancelled() {
            return getResult().isCancelled();
        }

        @Override
        public boolean isDone() {
            return getResult().isDone();
        }
    }

    static class FailedFutureResult<V> implements FutureResult<V> {
        private ResourceException exception;

        FailedFutureResult(final ResourceException result) {
            this.exception = result;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false; // Cannot cancel.
        }

        @Override
        public V get() throws ResourceException, InterruptedException {
            throw exception;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws ResourceException, TimeoutException,
                InterruptedException {
            throw exception;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

    }

    //
    // Protected static methods
    //

    protected static Resource getAsResource(final JsonValue value) throws ResourceException {
        try {
            return new Resource(value.get(Resource.FIELD_CONTENT_ID).required().asString(), value
                    .get(Resource.FIELD_CONTENT_REVISION).asString(), value);
        } catch (JsonValueException e) {
            // What shell we do if the response does not contains the _id
            throw new InternalServerErrorException(e);
        }
    }

    protected static ResourceException getAsResourceException(JsonValue resourceException) {
        ResourceException exception = null;
        if (resourceException.isMap()) {
            JsonValue code = resourceException.get(ResourceException.FIELD_CODE);
            if (code.isNumber()) {
                String message = resourceException.get(ResourceException.FIELD_MESSAGE).asString();

                exception = ResourceException.getException(code.asInteger(), message);

                String reason = resourceException.get(ResourceException.FIELD_REASON).asString();
                if (null != reason) {
                    exception.setReason(reason);
                }

                JsonValue detail = resourceException.get(ResourceException.FIELD_DETAIL);
                if (!detail.isNull()) {
                    exception.setDetail(detail);
                }
            }
        }
        return exception;
    }

    protected ResourceException isOK(StatusLine statusLine, HttpEntity entity) {
        ResourceException exception = null;
        if (statusLine.getStatusCode() >= 300) {

            String contentType = entity.getContentType().getValue();
            if (contentType != null && CONTENT_TYPE_REGEX.matcher(contentType).matches()) {
                try {
                    JsonValue resourceException = parseJsonBody(entity, true);

                    exception = getAsResourceException(resourceException);

                } catch (ResourceException e) {
                    /* ignore */
                }
            } else {
                try {
                    EntityUtils.consume(entity);
                } catch (IOException e) {
                    /* ignore */
                }
            }
            if (null == exception) {
                exception =
                        ResourceException.getException(statusLine.getStatusCode(), statusLine
                                .getReasonPhrase());
            }
        }
        return exception;
    }

    protected JsonValue parseResponse(final HttpResponse response) throws ResourceException {
        final StatusLine statusLine = response.getStatusLine();
        final HttpEntity entity = response.getEntity();
        ResourceException exception = isOK(statusLine, entity);
        if (null != exception) {
            throw exception;
        }
        return parseJsonBody(entity, true);
    }

    protected static String param(final String field) {
        return "_" + field;
    }

    /**
     * Adapts an {@code Exception} to a {@code ResourceException}.
     *
     * @param t
     *            The exception which caused the request to fail.
     * @return The equivalent resource exception.
     */
    protected static ResourceException adapt(final Throwable t) {
        if (t instanceof ResourceException) {
            return (ResourceException) t;
        } else {
            return new InternalServerErrorException(t);
        }
    }
}
