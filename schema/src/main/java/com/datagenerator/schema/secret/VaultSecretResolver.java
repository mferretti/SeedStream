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

package com.datagenerator.schema.secret;

import com.datagenerator.schema.exception.SecretResolutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves secrets from HashiCorp Vault KV (v1 and v2).
 *
 * <p><b>Path syntax:</b> {@code secret/data/myapp/db#password} — the path before {@code #} is the
 * Vault KV path (include {@code data/} for KV v2); the fragment after {@code #} names the field to
 * extract.
 *
 * <p>KV v2 responses ({@code data.data.*}) are detected automatically. KV v1 ({@code data.*}) is
 * used as a fallback.
 *
 * <p><b>Authentication:</b> Vault token is read from the {@code VAULT_TOKEN} environment variable
 * (or {@code VAULT_TOKEN} system property for tests). It is never read from YAML config.
 *
 * <p><b>Security:</b> Resolved values are never logged.
 */
@Slf4j
public final class VaultSecretResolver implements SecretResolver {

  private static final String TOKEN_ENV = "VAULT_TOKEN";

  private final String vaultAddr;
  private final String namespace;
  private final HttpClient httpClient;
  private final ObjectMapper mapper = new ObjectMapper();

  public VaultSecretResolver(String vaultAddr, String namespace) {
    this(
        vaultAddr,
        namespace,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  /** Package-private constructor for injecting a mock HttpClient in unit tests. */
  VaultSecretResolver(String vaultAddr, String namespace, HttpClient httpClient) {
    this.vaultAddr =
        vaultAddr.endsWith("/") ? vaultAddr.substring(0, vaultAddr.length() - 1) : vaultAddr;
    this.namespace = namespace;
    this.httpClient = httpClient;
  }

  @Override
  public String resolve(String path) {
    String secretPath = path;
    String field = null;
    int hash = path.indexOf('#');
    if (hash >= 0) {
      secretPath = path.substring(0, hash);
      field = path.substring(hash + 1);
    }

    String token = System.getenv(TOKEN_ENV);
    if (token == null) {
      token = System.getProperty(TOKEN_ENV);
    }
    if (token == null) {
      throw new SecretResolutionException(
          "VAULT_TOKEN environment variable not set; required for Vault secret resolver");
    }

    HttpRequest.Builder reqBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(vaultAddr + "/v1/" + secretPath))
            .header("X-Vault-Token", token)
            .timeout(Duration.ofSeconds(10))
            .GET();

    if (namespace != null && !namespace.isBlank()) {
      reqBuilder.header("X-Vault-Namespace", namespace);
    }

    log.debug("Resolving Vault secret: path={}", secretPath);

    HttpResponse<String> response;
    try {
      response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SecretResolutionException("Interrupted while contacting Vault: " + vaultAddr, e);
    } catch (IOException e) {
      throw new SecretResolutionException("Failed to contact Vault at " + vaultAddr, e);
    }

    if (response.statusCode() != 200) {
      throw new SecretResolutionException(
          "Vault returned HTTP "
              + response.statusCode()
              + " for path '"
              + secretPath
              + "' at "
              + vaultAddr);
    }

    return extractValue(response.body(), field, secretPath);
  }

  private String extractValue(String body, String field, String path) {
    try {
      JsonNode root = mapper.readTree(body);

      // Detect KV v2 (data.data) vs KV v1 (data)
      JsonNode data = root.path("data");
      JsonNode inner = data.path("data");
      JsonNode values = inner.isMissingNode() ? data : inner;

      if (field != null) {
        JsonNode fieldNode = values.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
          throw new SecretResolutionException(
              "Field '" + field + "' not found in Vault secret at: " + path);
        }
        return fieldNode.asText();
      }

      // No field suffix — return as string if exactly one scalar value
      if (values.isValueNode()) {
        return values.asText();
      }
      if (values.isObject() && values.size() == 1) {
        return values.elements().next().asText();
      }
      throw new SecretResolutionException(
          "Vault secret at '"
              + path
              + "' contains multiple fields; use #fieldname suffix to select one");

    } catch (SecretResolutionException e) {
      throw e;
    } catch (Exception e) {
      throw new SecretResolutionException("Failed to parse Vault response for: " + path, e);
    }
  }
}
