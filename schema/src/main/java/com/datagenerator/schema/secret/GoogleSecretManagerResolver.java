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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves secrets from Google Cloud Secret Manager.
 *
 * <p><b>Path syntax:</b>
 *
 * <ul>
 *   <li>{@code my-secret} — latest version of the secret
 *   <li>{@code my-secret/5} — specific version number
 *   <li>{@code my-secret#field} — latest version, extract {@code field} from a JSON secret
 *   <li>{@code my-secret/5#field} — specific version + field extraction
 * </ul>
 *
 * <p><b>Authentication:</b> Application Default Credentials — reads from the {@code
 * GOOGLE_APPLICATION_CREDENTIALS} environment variable (path to a service account JSON file), GKE
 * workload identity, Compute Engine instance service account, or {@code gcloud auth
 * application-default login}. Credentials are never read from job YAML.
 *
 * <p><b>GCP project:</b> Set via {@code gcp_project_id} in the {@code secrets:} block. Required.
 *
 * <p><b>Security:</b> Resolved values are never logged.
 */
@Slf4j
public final class GoogleSecretManagerResolver implements SecretResolver {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SecretManagerServiceClient client;
  private final String projectId;

  public GoogleSecretManagerResolver(String projectId) {
    try {
      this.client = SecretManagerServiceClient.create();
    } catch (IOException e) {
      throw new SecretResolutionException(
          "Failed to create GCP Secret Manager client: " + e.getMessage(), e);
    }
    this.projectId = projectId;
  }

  /** Package-private constructor for injecting a mock client in unit tests. */
  GoogleSecretManagerResolver(SecretManagerServiceClient client, String projectId) {
    this.client = client;
    this.projectId = projectId;
  }

  @Override
  public String resolve(String path) {
    SecretPath sp = SecretPath.parse(path);
    String field = sp.field();

    String secretName = sp.id();
    String version = "latest";
    int slash = sp.id().indexOf('/');
    if (slash >= 0) {
      secretName = sp.id().substring(0, slash);
      version = sp.id().substring(slash + 1);
    }

    log.debug(
        "Resolving GCP secret: project={} secret={} version={}", projectId, secretName, version);

    SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretName, version);
    AccessSecretVersionResponse response;
    try {
      response = client.accessSecretVersion(secretVersionName);
    } catch (ApiException e) {
      if (e.getStatusCode().getCode() == StatusCode.Code.NOT_FOUND) {
        throw new SecretResolutionException(
            "GCP secret not found: '" + secretName + "' in project '" + projectId + "'", e);
      }
      throw new SecretResolutionException(
          "GCP Secret Manager error for '" + secretName + "': " + e.getMessage(), e);
    }

    String payload = response.getPayload().getData().toStringUtf8();
    return extractValue(payload, field, sp.id());
  }

  private String extractValue(String payload, String field, String secretPath) {
    if (field == null) {
      return payload;
    }

    try {
      JsonNode root = MAPPER.readTree(payload);
      if (root.isObject()) {
        return SecretPath.extractNodeField(root, field, "GCP secret: " + secretPath);
      }
    } catch (SecretResolutionException e) {
      throw e;
    } catch (JsonProcessingException e) {
      // Not valid JSON — fall through
    }

    throw new SecretResolutionException(
        "GCP secret '" + secretPath + "' is a plain string and does not support #field suffix");
  }
}
