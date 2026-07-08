/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.formats.avro;

import com.datagenerator.core.security.UrlValidator;
import com.datagenerator.core.util.LogUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP implementation of {@link SchemaRegistryClient} backed by {@link java.net.http.HttpClient}.
 *
 * <p><b>Schema caching:</b> Schema IDs are cached in memory per subject after first successful
 * registration. Subsequent calls for the same subject return the cached ID without an HTTP round
 * trip.
 *
 * <p><b>Auth:</b>
 *
 * <ul>
 *   <li>{@code bearer} — {@code Authorization: Bearer <token>}
 *   <li>{@code basic} — {@code Authorization: Basic base64(user:password)} where token is {@code
 *       user:password}
 *   <li>{@code null} / none — no auth header
 * </ul>
 */
@Slf4j
public final class HttpSchemaRegistryClient implements SchemaRegistryClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String CONTENT_TYPE = "application/vnd.schemaregistry.v1+json";
  private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newHttpClient();

  /**
   * Cap on response-body characters embedded in exception messages (CWE-209/532: avoid unbounded
   * log growth / leaking excessive server-side detail).
   */
  private static final int MAX_RESPONSE_BODY_LOG_CHARS = 200;

  private final String baseUrl;
  private final String authHeader;
  private final HttpClient httpClient;
  private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

  public HttpSchemaRegistryClient(String url, String authType, String token) {
    if (url == null || url.isBlank()) {
      throw new SchemaRegistryException("schema_registry_url is required for avro-registry format");
    }
    UrlValidator.validate(url, "Schema Registry URL");
    this.baseUrl = normalizeUrl(url);
    this.authHeader = buildAuthHeader(authType, token);
    if (this.authHeader != null && !UrlValidator.isHttps(url)) {
      log.warn(
          "Sending Schema Registry credentials over plain http to {} — use https to protect "
              + "credentials in transit",
          url);
    }
    this.httpClient = SHARED_HTTP_CLIENT;
  }

  /** Package-private constructor for tests (no auth). */
  HttpSchemaRegistryClient(String url, HttpClient client) {
    this(url, client, null);
  }

  /** Package-private constructor for tests (with explicit auth header value). */
  HttpSchemaRegistryClient(String url, HttpClient client, String header) {
    this.baseUrl = normalizeUrl(url);
    this.authHeader = header;
    this.httpClient = client;
  }

  private static String normalizeUrl(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  @Override
  public int registerSchema(String subject, String avroSchemaJson) {
    Integer cached = cache.get(subject);
    if (cached != null) {
      return cached;
    }

    // URL-encode the subject as a single path segment so a config-derived subject containing
    // '/', '?', '#', '..' or whitespace cannot break out of the intended path (CWE-88).
    String encodedSubject = URLEncoder.encode(subject, StandardCharsets.UTF_8);
    String url = baseUrl + "/subjects/" + encodedSubject + "/versions";
    String body = buildRequestBody(avroSchemaJson);

    log.debug("Registering schema for subject '{}' at {}", subject, url);

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

    if (authHeader != null) {
      requestBuilder.header("Authorization", authHeader);
    }

    HttpResponse<String> response;
    try {
      response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SchemaRegistryException("Failed to connect to Schema Registry at " + baseUrl, e);
    } catch (IOException e) {
      throw new SchemaRegistryException("Failed to connect to Schema Registry at " + baseUrl, e);
    }

    if (response.statusCode() != 200) {
      throw new SchemaRegistryException(
          "Schema Registry returned HTTP "
              + response.statusCode()
              + " for subject '"
              + subject
              + "': "
              + LogUtils.truncate(response.body(), MAX_RESPONSE_BODY_LOG_CHARS));
    }

    int id = parseSchemaId(response.body(), subject);
    cache.put(subject, id);
    log.debug("Schema registered for subject '{}', id={}", subject, id);
    return id;
  }

  private static String buildRequestBody(String avroSchemaJson) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("schema", avroSchemaJson);
    return node.toString();
  }

  private static int parseSchemaId(String responseBody, String subject) {
    try {
      JsonNode root = MAPPER.readTree(responseBody);
      JsonNode idNode = root.get("id");
      if (idNode == null || !idNode.isInt()) {
        throw new SchemaRegistryException(
            "Schema Registry response missing 'id' field for subject '" + subject + "'");
      }
      return idNode.intValue();
    } catch (IOException e) {
      throw new SchemaRegistryException(
          "Failed to parse Schema Registry response for subject '" + subject + "'", e);
    }
  }

  private static String buildAuthHeader(String authType, String token) {
    if (authType == null || authType.isBlank()) return null;
    String normalizedType = authType.toLowerCase(Locale.ROOT);
    return switch (normalizedType) {
      case "bearer" -> {
        requireToken(token, normalizedType);
        yield "Bearer " + token;
      }
      case "basic" -> {
        requireToken(token, normalizedType);
        yield "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
      }
      case null, default ->
          throw new SchemaRegistryException(
              "Unsupported schema_registry_auth: '" + authType + "'; supported: bearer, basic");
    };
  }

  private static void requireToken(String token, String normalizedType) {
    if (token == null || token.isBlank()) {
      throw new SchemaRegistryException(
          "schema_registry_token is required when schema_registry_auth is '"
              + normalizedType
              + "'");
    }
  }
}
