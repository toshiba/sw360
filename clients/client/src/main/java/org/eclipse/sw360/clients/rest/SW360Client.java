/*
 * Copyright (c) Bosch Software Innovations GmbH 2019.
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.clients.rest;

import org.eclipse.sw360.http.RequestBuilder;
import org.eclipse.sw360.http.ResponseProcessor;
import org.eclipse.sw360.http.utils.HttpConstants;
import org.eclipse.sw360.http.utils.HttpUtils;
import org.eclipse.sw360.clients.auth.AccessTokenProvider;
import org.eclipse.sw360.clients.config.SW360ClientConfig;
import org.eclipse.sw360.clients.utils.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.eclipse.sw360.http.utils.HttpConstants.URL_PATH_SEPARATOR;

/**
 * <p>
 * A base class for all classes that interact with SW360 REST endpoints.
 * </p>
 * <p>
 * This class provides a generic mechanism for the execution of HTTP requests
 * to an SW360 server. Requests are sent asynchronously, and authentication
 * information is added automatically. There is also a retry logic in place
 * that checks for expired access tokens.
 * </p>
 * <p>
 * The intended usage is that special client implementations providing CRUD
 * operations on specific SW360 REST resources extend this base class and make
 * use of the generic HTTP execution mechanism.
 * </p>
 */
public abstract class SW360Client {
    /**
     * Constant for an URI separator.
     */
    private static final String URI_SEPARATOR = "/";

    private static final Logger LOG = LoggerFactory.getLogger(SW360Client.class);

    /**
     * The configuration for this client.
     */
    private final SW360ClientConfig clientConfig;

    /**
     * The object to obtain access tokens.
     */
    private final AccessTokenProvider tokenProvider;

    /**
     * Creates a new instance of {@code SW360Client} with the given
     * dependencies.
     *
     * @param config        the configuration of this client
     * @param tokenProvider the provider for access tokens
     */
    protected SW360Client(SW360ClientConfig config, AccessTokenProvider tokenProvider) {
        this.clientConfig = config;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Returns the SW360 client configuration used by this client.
     *
     * @return the {@code SW360ClientConfig}
     */
    public SW360ClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * Returns the {@code AccessTokenProvider} used by this client.
     *
     * @return the {@code AccessTokenProvider}
     */
    public AccessTokenProvider getTokenProvider() {
        return tokenProvider;
    }

    /**
     * Executes a request to SW360 with authentication and retry logic. This
     * method wraps the {@code RequestProducer} to automatically add a current
     * access token. It also inspects the result and checks whether it has a
     * failed response status. In case of a 401 Unauthorized status, it is
     * assumed that the token became invalid, and the request is retried with a
     * fresh token. If the retried request fails again with 401, we give up and
     * report the failure.
     *
     * @param producer  the {@code RequestProducer}
     * @param processor the {@code ResponseProcessor} (does not need to handle
     *                  response status codes)
     * @param tag       a tag to identify the request
     * @param <T>       the type of the {@code ResponseProcessor}
     * @return a future with the result generated by the {@code ResponseProcessor}
     */
    protected <T> CompletableFuture<T> executeRequest(Consumer<? super RequestBuilder> producer,
                                                      ResponseProcessor<T> processor,
                                                      String tag) {
        return manageTokenAndExecute(producer, processor, tag, true);
    }

    /**
     * Executes a request to SW360 with authentication and retry logic that
     * expects a JSON response. This method performs the same checks as
     * {@code executeRequest()}, but it creates the {@code ResponseProcessor}
     * automatically that can convert the JSON response to the desired target
     * type.
     *
     * @param producer    the {@code RequestProducer}
     * @param resultClass the class to which the JSON payload is to be
     *                    converted
     * @param tag         a tag to identify the request
     * @param <T>         the type of the result
     * @return a future with the result of the request
     */
    protected <T> CompletableFuture<T> executeJsonRequest(Consumer<? super RequestBuilder> producer,
                                                          Class<T> resultClass, String tag) {
        return executeRequest(producer,
                HttpUtils.jsonResult(getClientConfig().getObjectMapper(), resultClass), tag);
    }

    /**
     * Executes a request to delete multiple entities of a given resource.
     * SW360 typically supports DELETE operations on resources that accept a
     * list of IDs. Result is a {@code MultiStatusResponse} with information
     * about the single operations and their outcome. This method handles the
     * details of constructing the correct REST URL for the delete operation
     * and parsing the response from the server.
     *
     * @param endpoint    the endpoint of the resource affected by the operation
     * @param idsToDelete a list with the IDs of the entities to be deleted
     * @param tag         a tag to identify the request
     * @return a future with the {@code MultiStatusResponse} returned by the
     * server
     */
    protected CompletableFuture<MultiStatusResponse> executeDeleteRequest(String endpoint,
                                                                          Collection<String> idsToDelete,
                                                                          String tag) {
        String strIdsToDelete = String.join(",", idsToDelete);
        String url = resourceUrl(endpoint, strIdsToDelete);
        return executeRequest(builder ->
                        builder.uri(url).method(RequestBuilder.Method.DELETE),
                createMultiStatusProcessor(tag),
                tag);
    }

    /**
     * Executes a request to SW360 with authentication and retry logic that
     * expects an optional JSON response. This method extends
     * {@link #executeJsonRequest(Consumer, Class, String)} by a handling of
     * responses with status code 204 NO CONTENT. If such a response is
     * received, the supplier for the default result is invoked, and this
     * result object is returned.
     *
     * @param producer      the {@code RequestProducer}
     * @param resultClass   the class to which the JSON payload is to be
     *                      converted
     * @param tag           a tag to identify the result
     * @param defaultResult the producer of the default result
     * @param <T>           the type of the result
     * @return a future with the result of the request
     */
    protected <T> CompletableFuture<T> executeJsonRequestWithDefault(Consumer<? super RequestBuilder> producer,
                                                                     Class<T> resultClass, String tag,
                                                                     Supplier<? extends T> defaultResult) {
        ResponseProcessor<T> processor = response ->
                (response.statusCode() == HttpConstants.STATUS_NO_CONTENT) ?
                        defaultResult.get() :
                        HttpUtils.jsonResult(getClientConfig().getObjectMapper(), resultClass).process(response);
        return executeRequest(producer, processor, tag);
    }

    /**
     * Generates a URL pointing to a specific resource of the SW360 server.
     * This method concatenates the given path segments and appends them to the
     * base REST URL of the server. It can be used by derived classes to
     * generate the URLs to be requested.
     *
     * @param paths the path segments to add to the base URL
     * @return a string with the resulting URL
     */
    protected String resourceUrl(String... paths) {
        return getClientConfig().getRestURL() + URL_PATH_SEPARATOR + String.join(URL_PATH_SEPARATOR, paths);
    }

    /**
     * Returns a URI with the same path as the given URI, but that is relative
     * to the configured REST base URI. This method should be used to deal with
     * self links to make sure that they are correctly resolved against the
     * base URI. (SW360 may return incorrect self links if it runs behind a
     * load balancer or proxy.)
     *
     * @param uri the URI to be resolved
     * @return the resolved URI
     * @throws IllegalArgumentException if the passed in URI is invalid
     */
    protected URI resolveAgainstBase(String uri) {
        URI base = getClientConfig().getBaseURI();
        URI source = URI.create(uri);
        String path = resolvePath(base, source);
        try {
            return new URI(base.getScheme(), base.getAuthority(), path,
                    source.getQuery(), source.getFragment());
        } catch (URISyntaxException e) {
            // should normally not happen as the components come from valid URIs
            throw new IllegalArgumentException("Invalid URI to resolve: " + uri);
        }
    }

    /**
     * Implements the actual request execution logic including a retry
     * mechanism if the current access token may have expired. This method
     * generates a future that obtains the current access token and executes
     * the request defined by the parameters with it. If the {@code canRetry}
     * parameter is <strong>true</strong> (which it is only for the first time
     * the request is executed), the future is enhanced with special retry
     * logic. This logic checks whether a failure has occurred indicating an
     * expired access token. If this is the case, another request execution is
     * chained to the original future.
     *
     * @param producer  the {@code RequestProducer}
     * @param processor the {@code ResponseProcessor}
     * @param tag       a tag to identify the request
     * @param canRetry  a flag whether a retry is possible
     * @param <T>       the type of the result
     * @return a future with the result of the request
     */
    private <T> CompletableFuture<T> manageTokenAndExecute(Consumer<? super RequestBuilder> producer,
                                                           ResponseProcessor<T> processor,
                                                           String tag,
                                                           boolean canRetry) {
        LOG.debug("Executing request '{}'{}.", tag, canRetry ? "" : " (retry)");

        CompletableFuture<T> futRequest = getTokenProvider().doWithToken(accessToken ->
                getClientConfig().getHttpClient().execute(accessToken.tokenProducer(producer),
                        HttpUtils.checkResponse(processor, tag)));
        return canRetry ?
                FutureUtils.wrapFutureForConditionalFallback(futRequest,
                        this::checkIfRetry,
                        () -> manageTokenAndExecute(producer, processor, tag, false)) :
                futRequest;
    }

    /**
     * Checks whether a request needs to be retried because the access token
     * may have expired. The method checks whether the request failed with the
     * status code UNAUTHORIZED.
     *
     * @param exception the exception the request failed with if any
     * @return a flag whether a retry should be attempted
     */
    private boolean checkIfRetry(Throwable exception) {
        return FutureUtils.isFailedRequestWithStatus(exception, HttpConstants.STATUS_ERR_UNAUTHORIZED);
    }

    /**
     * Returns a {@code ResponseProcessor} to process a multi-status response.
     * This processor expects a response with status code 207 whose content is
     * a JSON multi-status response representation.
     *
     * @param tag a tag to identify the request
     * @return the processor for the multi-status response
     */
    private ResponseProcessor<MultiStatusResponse> createMultiStatusProcessor(String tag) {
        ResponseProcessor<MultiStatusResponse> processor =
                response -> MultiStatusResponse.fromJson(getClientConfig().getObjectMapper(), response.bodyStream());
        return HttpUtils.checkResponse(processor, HttpUtils.hasStatus(HttpConstants.STATUS_MULTI_STATUS), tag);
    }

    /**
     * Determines the path for the source URI relative to the base URI. The
     * path of the source URI is appended to the base URI, but common path
     * components are excluded.
     *
     * @param base   the base URI
     * @param source the source URI
     * @return the resulting path
     */
    private static String resolvePath(URI base, URI source) {
        String[] baseComponents = base.getPath().split(URI_SEPARATOR);
        String[] sourceComponents = source.getPath().split(URI_SEPARATOR);
        int minLength = Math.min(baseComponents.length, sourceComponents.length);
        int idx = 0;
        while (idx < minLength && baseComponents[idx].equals(sourceComponents[idx])) {
            idx++;
        }

        StringBuilder buf = new StringBuilder();
        buf.append(base.getPath());
        for (int i = idx; i < sourceComponents.length; i++) {
            buf.append(URI_SEPARATOR).append(sourceComponents[i]);
        }
        return buf.toString();
    }
}
