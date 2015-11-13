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

import static org.forgerock.json.resource.Responses.newActionResponse;
import static org.forgerock.json.resource.Responses.newQueryResponse;
import static org.forgerock.json.resource.Responses.newResourceResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
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
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ResourcePath;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.services.context.Context;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ServiceUnavailableException;
import org.forgerock.json.resource.SortKey;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;

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

    private final ResourcePath resourcePath;

    private final HttpHost httpHost;

    protected AbstractRemoteConnection(ResourcePath resourcePath, HttpHost httpHost) {
        this.resourcePath = resourcePath;
        this.httpHost = httpHost;
    }

    public abstract <T> Future<T> execute(Context context,  HttpUriRequest request,
            final HttpAsyncResponseConsumer<T> responseConsumer, FutureCallback<T> callback);


    /*
    public abstract FutureResult<JsonValue> execute(Context context, HttpUriRequest request,
            HttpAsyncResponseConsumer<JsonValue> responseConsumer,
            FutureCallback<JsonValue> callback);

    public abstract FutureResult<Resource> execute(Context context, HttpUriRequest request,
            HttpAsyncResponseConsumer<Resource> responseConsumer, FutureCallback<Resource> callback);

    public abstract FutureResult<QueryResult> execute(Context context, HttpUriRequest request,
            QueryResultHandler handler, HttpAsyncResponseConsumer<QueryResult> responseConsumer,
            FutureCallback<QueryResult> callback);
     */

    protected abstract JsonValue parseJsonBody(final HttpEntity entity, final boolean allowEmpty)
            throws ResourceException;

    protected abstract QueryResponse parseQueryResponse(final HttpResponse response,
            final QueryResourceHandler handler) throws ResourceException;

    public ResourcePath getResourcePath() {
        return resourcePath;
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
    public ActionResponse action(final Context context, final ActionRequest request)
            throws ResourceException {
        final Promise<ActionResponse, ResourceException> promise = actionAsync(context, request);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionAsync(final Context context, final ActionRequest request) {
        try {
            final PromiseImpl<ActionResponse, ResourceException> promise = PromiseImpl.create();
            execute(context, convert(request), new JsonValueResponseHandler(),
                    new FutureCallback<JsonValue>() {
                        @Override
                        public void completed(JsonValue jsonValue) {
                            promise.handleResult(newActionResponse(jsonValue));
                        }

                        @Override
                        public void failed(Exception e) {
                            promise.handleException(adapt(e));
                        }

                        @Override
                        public void cancelled() {
                            promise.handleException(new ServiceUnavailableException("Client thread interrupted"));
                        }
                    });
            return promise;
        } catch (final Throwable t) {
            return adapt(t).asPromise();
        }
    }

    @Override
    public QueryResponse query(final Context context, final QueryRequest request, final QueryResourceHandler handler)
            throws ResourceException {
        final Promise<QueryResponse, ResourceException> promise = queryAsync(context, request, handler);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public QueryResponse query(final Context context, final QueryRequest request,
            final Collection<? super ResourceResponse> results) throws ResourceException {
        final QueryResourceHandler handler = new QueryResourceHandler() {
            @Override
            public boolean handleResource(final ResourceResponse resource) {
                results.add(resource);
                return true;
            }
        };
        return query(context, request, handler);
    }

    @Override
    public Promise<QueryResponse, ResourceException> queryAsync(Context context, QueryRequest request,
            final QueryResourceHandler handler) {
        try {
            final PromiseImpl<QueryResponse, ResourceException> promise = PromiseImpl.create();
            execute(context, convert(request), new QueryResultResponseHandler(handler),
                    new FutureCallback<QueryResponse>() {
                        @Override
                        public void completed(QueryResponse response) {
                            promise.handleResult(response);
                        }

                        @Override
                        public void failed(Exception e) {
                            promise.handleException(adapt(e));
                        }

                        @Override
                        public void cancelled() {
                            promise.handleException(new ServiceUnavailableException("Client thread interrupted"));
                        }
                    });
            return promise;
        } catch (final Throwable t) {
            //noinspection ThrowableResultOfMethodCallIgnored
            return adapt(t).asPromise();
        }
    }

    @Override
    public ResourceResponse create(final Context context, final CreateRequest request)
            throws ResourceException {
        final Promise<ResourceResponse, ResourceException> promise = createAsync(context, request);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> createAsync(Context context, CreateRequest request) {
        return handleRequestAsync(context, request);
    }

    @Override
    public ResourceResponse delete(final Context context, final DeleteRequest request)
            throws ResourceException {
        final Promise<ResourceResponse, ResourceException> promise = deleteAsync(context, request);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> deleteAsync(Context context, DeleteRequest request) {
        return handleRequestAsync(context, request);
    }

    @Override
    public ResourceResponse patch(final Context context, final PatchRequest request)
            throws ResourceException {
        final Promise<ResourceResponse, ResourceException> promise = patchAsync(context, request);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchAsync(Context context, PatchRequest request) {
        return handleRequestAsync(context, request);
    }

    @Override
    public ResourceResponse read(final Context context, final ReadRequest request) throws ResourceException {
        final Promise<ResourceResponse, ResourceException> promise = readAsync(context, request);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readAsync(Context context, ReadRequest request) {
        return handleRequestAsync(context, request);
    }

    @Override
    public ResourceResponse update(final Context context, final UpdateRequest request)
            throws ResourceException {
        final Promise<ResourceResponse, ResourceException> promise = updateAsync(context, request);
        try {
            return promise.getOrThrow();
        } catch (final InterruptedException e) {
            throw interrupted(e);
        } catch (Exception e) {
            throw adapt(e);
        } finally {
            // Cancel the request if it hasn't completed.
            promise.cancel(false);
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateAsync(Context context, UpdateRequest request) {
        return handleRequestAsync(context, request);
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
        rq.setHeader(HttpHeaders.CONNECTION, "close, TE");
        return rq;
    }

    private URIBuilder getUriBuilder(Request request) {
        ResourcePath resourcePath = getResourcePath().concat(request.getResourcePathObject());

        if (request instanceof CreateRequest
                && null != ((CreateRequest) request).getNewResourceId()) {
            resourcePath = resourcePath.concat(((CreateRequest) request).getNewResourceId());
        }

        URIBuilder builder =
                new URIBuilder().setScheme(getHttpHost().getSchemeName()).setHost(
                        getHttpHost().getHostName()).setPort(getHttpHost().getPort()).setPath(
                        "/" + resourcePath.toString());

        for (JsonPointer field : request.getFields()) {
            builder.addParameter(PARAM_FIELDS, field.toString());
        }
        return builder;
    }

    private Promise<ResourceResponse, ResourceException> handleRequestAsync(final Context context,
            final Request request) {
        try {
            final PromiseImpl<ResourceResponse, ResourceException> promise = PromiseImpl.create();
            execute(context, convert(request), new ResourceResponseHandler(),
                    new FutureCallback<ResourceResponse>() {
                        @Override
                        public void completed(ResourceResponse response) {
                            promise.handleResult(response);
                        }

                        @Override
                        public void failed(Exception e) {
                            promise.handleException(adapt(e));
                        }

                        @Override
                        public void cancelled() {
                            promise.handleException(new ServiceUnavailableException("Client thread interrupted"));
                        }
                    });
            return promise;
        } catch (final Throwable t) {
            //noinspection ThrowableResultOfMethodCallIgnored
            return adapt(t).asPromise();
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
    public class QueryResultResponseHandler extends AbstractJsonValueResponseHandler<QueryResponse> {

        private final QueryResourceHandler handler;

        public QueryResultResponseHandler(final QueryResourceHandler handler) {
            this.handler = handler;
        }

        /**
         * Returns the response body as a String if the response was successful
         * (a 2xx status code). If no response body exists, this returns null.
         * If the response was unsuccessful (>= 300 status code), throws an
         * {@link org.apache.http.client.HttpResponseException}.
         */
        public QueryResponse buildResult(HttpContext context) throws Exception {

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
    public class ResourceResponseHandler extends AbstractJsonValueResponseHandler<ResourceResponse> {

        /**
         * Returns the response body as a String if the response was successful
         * (a 2xx status code). If no response body exists, this returns null.
         * If the response was unsuccessful (>= 300 status code), throws an
         * {@link org.apache.http.client.HttpResponseException}.
         */
        public ResourceResponse buildResult(HttpContext context) throws Exception {

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

    //
    // Protected static methods
    //

    protected static ResourceResponse getAsResource(final JsonValue value) throws ResourceException {
        try {
            return newResourceResponse(value.get(ResourceResponse.FIELD_CONTENT_ID).asString(), value
                    .get(ResourceResponse.FIELD_CONTENT_REVISION).asString(), value);
        } catch (JsonValueException e) {
            // What shall we do if the response does not contain the _id
            throw new InternalServerErrorException(e);
        }
    }

    protected static ResourceException getAsResourceException(JsonValue resourceException) {
        ResourceException exception = null;
        if (resourceException.isMap()) {
            JsonValue code = resourceException.get(ResourceException.FIELD_CODE);
            if (code.isNumber()) {
                String message = resourceException.get(ResourceException.FIELD_MESSAGE).asString();

                exception = ResourceException.newResourceException(code.asInteger(), message);

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
                        ResourceException.newResourceException(statusLine.getStatusCode(), statusLine
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
