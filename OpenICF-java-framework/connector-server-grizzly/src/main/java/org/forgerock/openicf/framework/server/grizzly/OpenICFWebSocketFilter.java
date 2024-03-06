/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.server.grizzly;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.security.SharedSecretPrincipal;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.ssl.SSLSupportImpl;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.WebSocketFilter;
import org.identityconnectors.common.Base64;
import org.identityconnectors.common.security.GuardedString;

public class OpenICFWebSocketFilter extends WebSocketFilter {

    private static final Logger logger = Grizzly.logger(OpenICFWebSocketFilter.class);

    protected boolean supportMutualAuth = false;

    public OpenICFWebSocketFilter(long wsTimeoutInSeconds) {
        super(wsTimeoutInSeconds);
    }

    protected NextAction handleHandshake(final FilterChainContext ctx, final HttpContent content)
            throws IOException {
        // get HTTP request headers
        final HttpRequestPacket request = (HttpRequestPacket) content.getHttpHeader();
       
        WebSocketApplication application = null;
        try{
            application =
                    WebSocketEngine.getEngine().getApplication(request);
        } catch (NullPointerException e){
            // Catch GRIZZLY-1764 NPE Bug upgrade to 2.3.21
            logger.warning("GRIZZLY-1764 Bug " + request.getRequestURI());
            e.printStackTrace();
        }

        if (application instanceof OpenICFWebSocketApplication) {

            OpenICFWebSocketApplication app = OpenICFWebSocketApplication.class.cast(application);

            Principal principal = null;
            if (supportMutualAuth && request.isSecure()) {
                SSLSupportImpl sslSupport = new SSLSupportImpl(request.getConnection());

                try {
                    X509Certificate[] certs =
                            (X509Certificate[]) sslSupport.getPeerCertificateChain();
                    if (certs != null && certs.length > 0 && certs[0] != null) {
                        X509Certificate clientCertificate = certs[0];
                        principal = SharedSecretPrincipal.createMutual(clientCertificate);
                    }
                } catch (Exception e) {
                    HandshakeException error = new HandshakeException(e.getMessage());
                    error.initCause(e);
                    throw error;
                }
            }
            if (null == principal) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null) {
                    StringTokenizer st = new StringTokenizer(authHeader);
                    if (st.hasMoreTokens()) {
                        String basic = st.nextToken();

                        if (basic.equalsIgnoreCase("Basic")) {
                            try {
                                String credentials =
                                        new String(Base64.decode(st.nextToken()),
                                                "ISO-8859-1");
                                logger.finer("Credentials: " + credentials);
                                int p = credentials.indexOf(":");
                                if (p != -1) {
                                    String username = credentials.substring(0, p).trim();
                                    String password = credentials.substring(p + 1).trim();

                                    principal =
                                            SharedSecretPrincipal.createBasic(username,
                                                    new GuardedString(password.toCharArray()));
                                } else {
                                    logger.finer("Invalid authentication token: " + credentials);
                                }
                            } catch (UnsupportedEncodingException e) {
                                throw new Error("Couldn't retrieve authentication", e);
                            }
                        }
                    }
                }
            }

            try {
                final HttpResponsePacket response = request.getResponse();
                if (null != principal) {
                    ConnectionPrincipal<?> connectionManager = app.authenticate(principal);
                    if (null != connectionManager) {
                        request.setAttribute(ConnectionPrincipal.class.getName(), connectionManager);
                        if (doServerUpgrade(ctx, content)) {
                            setIdleTimeout(ctx);
                        } else {
                            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                            response.setReasonPhrase("Server failed during ServerUpgrade");
                            ctx.write(response);
                        }
                    } else {
                        response.setStatus(HttpStatus.FORBIDDEN_403);
                        response.setReasonPhrase("Failed To connect");
                        ctx.write(response);
                    }
                } else {

                    response.setStatus(HttpStatus.UNAUTHORIZED_401);
                    response.setReasonPhrase("Unauthorized. ");
                    response.getHttpHeader().addHeader(Header.WWWAuthenticate,
                            "Basic realm=\"openicf.forgerock.org\"");

                    ctx.write(response);
                }

            } catch (HandshakeException e) {
                ctx.write(composeError(request, HttpStatus.getHttpStatus(e.getCode()), e
                        .getMessage()));
            }

            ctx.flush(null);
            content.recycle();
            return ctx.getStopAction();
        } else {
            return super.handleHandshake(ctx, content);
        }
    }

    protected boolean doServerUpgrade(final FilterChainContext ctx, final HttpContent requestContent)
            throws IOException {
        return WebSocketEngine.getEngine().upgrade(ctx, requestContent);
    }

    protected static HttpResponsePacket composeError(final HttpRequestPacket request,
            final HttpStatus status, final String reasonPhrase) {
        final HttpResponsePacket response = request.getResponse();
        response.setStatus(status);
        if (null != reasonPhrase) {
            response.setReasonPhrase(reasonPhrase);
        }
        return response;
    }
}
