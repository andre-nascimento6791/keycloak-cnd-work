/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.services.cors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

import org.jboss.logging.Logger;
import org.keycloak.http.HttpRequest;
import org.keycloak.common.util.CollectionUtil;
import org.keycloak.http.HttpResponse;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.utils.WebOriginsUtils;
import org.keycloak.representations.AccessToken;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class DefaultCors implements Cors {

    private static final Logger logger = Logger.getLogger(DefaultCors.class);

    private HttpRequest request;
    private ResponseBuilder builder;
    private Set<String> allowedOrigins;
    private Set<String> allowedMethods;
    private Set<String> exposedHeaders;

    private boolean preflight;
    private boolean auth;

    DefaultCors(HttpRequest request) {
        this.request = request;
    }

    @Override
    public Cors request(HttpRequest request) {
        this.request = request;
        return this;
    }

    @Override
    public Cors builder(ResponseBuilder builder) {
        this.builder = builder;
        return this;
    }

    @Override
    public Cors preflight() {
        preflight = true;
        return this;
    }

    @Override
    public Cors auth() {
        auth = true;
        return this;
    }

    @Override
    public Cors allowAllOrigins() {
        allowedOrigins = Collections.singleton(ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD);
        return this;
    }

    @Override
    public Cors allowedOrigins(KeycloakSession session, ClientModel client) {
        if (client != null) {
            allowedOrigins = WebOriginsUtils.resolveValidWebOrigins(session, client);
        }
        return this;
    }

    @Override
    public Cors allowedOrigins(AccessToken token) {
        if (token != null) {
            allowedOrigins = token.getAllowedOrigins();
        }
        return this;
    }

    @Override
    public Cors allowedOrigins(String... allowedOrigins) {
        if (allowedOrigins != null && allowedOrigins.length > 0) {
            this.allowedOrigins = new HashSet<>(Arrays.asList(allowedOrigins));
        }
        return this;
    }

    @Override
    public Cors allowedMethods(String... allowedMethods) {
        this.allowedMethods = new HashSet<>(Arrays.asList(allowedMethods));
        return this;
    }

    @Override
    public Cors exposedHeaders(String... exposedHeaders) {
        this.exposedHeaders = new HashSet<>(Arrays.asList(exposedHeaders));
        return this;
    }

    @Override
    public Response build() {
        if (builder == null) {
            throw new IllegalStateException("builder is not set");
        }

        if (build(builder::header)) {
            logger.debug("Added CORS headers to response");
        }
        return builder.build();
    }

    @Override
    public boolean build(HttpResponse response) {
        if (build(response::addHeader)) {
            logger.debug("Added CORS headers to response");
            return true;
        }
        return false;
    }

    @Override
    public boolean build(BiConsumer<String, String> addHeader) {
        if (request == null) {
            throw new IllegalStateException("request is not set");
        }

        String origin = request.getHttpHeaders().getRequestHeaders().getFirst(ORIGIN_HEADER);
        if (origin == null) {
            logger.trace("No Origin header, ignoring");
            return false;
        }

        if (!preflight && (allowedOrigins == null || (!allowedOrigins.contains(origin) && !allowedOrigins.contains(ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD)))) {
            if (logger.isDebugEnabled()) {
                logger.debugv("Invalid CORS request: origin {0} not in allowed origins {1}", origin, allowedOrigins);
            }
            return false;
        }

        addHeader.accept(ACCESS_CONTROL_ALLOW_ORIGIN, origin);

        if (preflight) {
            if (allowedMethods != null) {
                addHeader.accept(ACCESS_CONTROL_ALLOW_METHODS, CollectionUtil.join(allowedMethods));
            } else {
                addHeader.accept(ACCESS_CONTROL_ALLOW_METHODS, DEFAULT_ALLOW_METHODS);
            }
        }

        if (!preflight && exposedHeaders != null) {
            addHeader.accept(ACCESS_CONTROL_EXPOSE_HEADERS, CollectionUtil.join(exposedHeaders));
        }

        addHeader.accept(ACCESS_CONTROL_ALLOW_CREDENTIALS, Boolean.toString(auth));

        if (preflight) {
            if (auth) {
                addHeader.accept(ACCESS_CONTROL_ALLOW_HEADERS, String.format("%s, %s", DEFAULT_ALLOW_HEADERS, AUTHORIZATION_HEADER));
            } else {
                addHeader.accept(ACCESS_CONTROL_ALLOW_HEADERS, DEFAULT_ALLOW_HEADERS);
            }
        }

        if (preflight) {
            addHeader.accept(ACCESS_CONTROL_MAX_AGE, String.valueOf(DEFAULT_MAX_AGE));
        }

        return true;
    }

    @Override
    public void close() {
    }

}
