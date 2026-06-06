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
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Resolves secrets from AWS Secrets Manager.
 *
 * <p><b>Path syntax:</b> Secret name, ARN, or {@code name#field} to extract a single key from a
 * JSON secret. Examples:
 *
 * <ul>
 *   <li>{@code myapp/db#password} — JSON secret, extract {@code password} field
 *   <li>{@code arn:aws:secretsmanager:us-east-1:123:secret:myapp/db#password} — by ARN
 *   <li>{@code myapp/token} — plain-string secret, returned as-is
 * </ul>
 *
 * <p><b>Authentication:</b> Default AWS credential chain (env vars → ~/.aws/credentials → EC2
 * instance profile → ECS task role). Credentials are never read from job YAML.
 *
 * <p><b>Region:</b> From {@code aws_region} config field, then {@code AWS_DEFAULT_REGION} env var.
 * If neither is set the SDK uses its own region resolution chain.
 *
 * <p><b>Security:</b> Resolved values are never logged.
 */
@Slf4j
public final class AwsSecretsManagerResolver implements SecretResolver {

  private final SecretsManagerClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AwsSecretsManagerResolver(String awsRegion) {
    String region = awsRegion;
    if (region == null || region.isBlank()) {
      region = System.getenv("AWS_DEFAULT_REGION");
    }
    if (region == null || region.isBlank()) {
      region = System.getProperty("AWS_DEFAULT_REGION");
    }

    var builder = SecretsManagerClient.builder();
    if (region != null && !region.isBlank()) {
      builder.region(Region.of(region));
    }
    this.client = builder.build();
  }

  /** Package-private constructor for injecting a mock client in unit tests. */
  AwsSecretsManagerResolver(SecretsManagerClient smClient) {
    this.client = smClient;
  }

  @Override
  public String resolve(String path) {
    String secretId = path;
    String field = null;
    int hash = path.indexOf('#');
    if (hash >= 0) {
      secretId = path.substring(0, hash);
      field = path.substring(hash + 1);
    }

    log.debug("Resolving AWS Secrets Manager secret: id={}", secretId);

    GetSecretValueResponse response;
    try {
      response = client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());
    } catch (ResourceNotFoundException e) {
      throw new SecretResolutionException("AWS secret not found: '" + secretId + "'", e);
    } catch (SdkException e) {
      throw new SecretResolutionException(
          "AWS Secrets Manager error for '" + secretId + "': " + e.getMessage(), e);
    }

    String secretString = response.secretString();
    if (secretString == null) {
      throw new SecretResolutionException(
          "AWS secret '" + secretId + "' is a binary secret; only string secrets are supported");
    }

    return extractValue(secretString, field, secretId);
  }

  private String extractValue(String secretString, String field, String secretId) {
    try {
      JsonNode root = mapper.readTree(secretString);
      if (root.isObject()) {
        if (field != null) {
          JsonNode fieldNode = root.get(field);
          if (fieldNode == null || fieldNode.isNull()) {
            throw new SecretResolutionException(
                "Field '" + field + "' not found in AWS secret: " + secretId);
          }
          return fieldNode.asText();
        }
        if (root.size() == 1) {
          return root.elements().next().asText();
        }
        throw new SecretResolutionException(
            "AWS secret '"
                + secretId
                + "' contains multiple fields; use #fieldname suffix to select one");
      }
    } catch (SecretResolutionException e) {
      throw e;
    } catch (JsonProcessingException e) {
      // Not valid JSON — fall through to plain-string handling
    }

    if (field != null) {
      throw new SecretResolutionException(
          "AWS secret '" + secretId + "' is a plain string and does not support #field suffix");
    }
    return secretString;
  }
}
