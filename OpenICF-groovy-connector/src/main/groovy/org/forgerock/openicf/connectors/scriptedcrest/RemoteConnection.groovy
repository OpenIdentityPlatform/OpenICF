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

package org.forgerock.openicf.connectors.scriptedcrest

import groovy.json.JsonException
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.StatusLine
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.concurrent.FutureCallback
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer
import org.forgerock.json.fluent.JsonValue
import org.forgerock.json.resource.BadRequestException
import org.forgerock.json.resource.InternalServerErrorException
import org.forgerock.json.resource.QueryResult
import org.forgerock.json.resource.QueryResultHandler
import org.forgerock.json.resource.ResourceException
import org.forgerock.openicf.misc.crest.AbstractRemoteConnection
import org.forgerock.openicf.misc.crest.AbstractRemoteConnection.HttpResponseResourceException
//Add import org.forgerock.openicf.misc.crest.AbstractRemoteConnection.HttpResponseResourceException

import java.util.concurrent.Future

/**
 *
 *
 * @author Laszlo Hordos
 */
class RemoteConnection extends AbstractRemoteConnection {

    private final ScriptedCRESTConfiguration configuration;

    RemoteConnection(final ScriptedCRESTConfiguration configuration) {
        super(configuration.getResourceName(), configuration.getHttpHost())
        this.configuration = configuration
    }

    @Override
    public boolean isClosed() {
        return configuration.isClosed();
    }

    def <T> Future<T> execute(HttpUriRequest request, HttpAsyncResponseConsumer<T> responseConsumer, FutureCallback<T> callback) {
        return configuration.execute(request, responseConsumer, callback)
    }


    protected JsonValue parseJsonBody(final HttpEntity entity, final boolean allowEmpty)
            throws ResourceException {
        StreamingJsonSlurper parser = new StreamingJsonSlurper();
        try {

            // Ensure that there is no trailing data following the JSON
            // resource.
            boolean hasTrailingGarbage;
            def content = parser.parse(entity, null, (Closure) { lexer, object ->
                try {
                    hasTrailingGarbage = lexer.nextToken() != null;
                } catch (JsonException e) {
                    hasTrailingGarbage = true;
                }
            })


            if (hasTrailingGarbage) {
                throw new BadRequestException(
                        "The request could not be processed because there is "
                                + "trailing data after the JSON content");
            }

            return new JsonValue(content);
        } catch (final JsonException e) {
            throw new BadRequestException(
                    "The request could not be processed because the provided "
                            + "content is not valid JSON", e).setDetail(new JsonValue(e
                    .getMessage()));
        } catch (final EOFException e) {
            if (allowEmpty) {
                return null;
            } else {
                throw new BadRequestException("The request could not be processed "
                        + "because it did not contain any JSON content", e);
            }
        } catch (final IOException e) {
            throw adapt(e);
        }
    }

    protected QueryResult parseQueryResponse(final HttpResponse response,
                                             final QueryResultHandler handler) throws ResourceException {
        final StatusLine statusLine = response.getStatusLine();
        final HttpEntity entity = response.getEntity();

        ResourceException exception = isOK(statusLine, entity);

        if (null != exception) {
            throw exception;
        }

        StreamingJsonSlurper parser = new StreamingJsonSlurper();
        try {

            // Ensure that there is no trailing data following the
            // JSON
            // resource.
            boolean hasTrailingGarbage;

            def content = parser.parse(entity, {
                handler.handleResource(getAsResource(it));
            }, { lexer, object ->
                try {
                    hasTrailingGarbage = lexer.nextToken() != null;
                } catch (JsonException e) {
                    hasTrailingGarbage = true;
                }
            })

            if (content."${QueryResult.FIELD_ERROR}" != null) {
                exception =
                        getAsResourceException(new JsonValue(content."${QueryResult.FIELD_ERROR}"));
                throw new HttpResponseResourceException(exception);
            }

            String pagedResultsCookie = content."${QueryResult.FIELD_PAGED_RESULTS_COOKIE}";
            int remainingPagedResults = content."${QueryResult.FIELD_REMAINING_PAGED_RESULTS}";


            if (hasTrailingGarbage) {
                // throw new BadRequestException(
                // "The request could not be processed because there is "
                // + "trailing data after the JSON content");
            }
            return new QueryResult(pagedResultsCookie, remainingPagedResults);

        } catch (final JsonException e) {
            // throw new BadRequestException(
            // "The request could not be processed because the provided "
            // + "content is not valid JSON", e).setDetail(new
            // JsonValue(e
            // .getMessage()));
            throw new InternalServerErrorException(e);
        } catch (final EOFException e) {
            // if (allowEmpty) {
            // return null;
            // } else {
            // throw new
            // BadRequestException("The request could not be processed "
            // + "because it did not contain any JSON content");
            // }
            throw new InternalServerErrorException(e);
        } catch (final IOException e) {
            // throw adapt(e);
            throw new InternalServerErrorException(e);
        }
    }
}
