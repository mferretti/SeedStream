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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.schema.IntegrationTest;
import com.datagenerator.schema.exception.SecretResolutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

class AwsSecretsManagerResolverIT extends IntegrationTest {

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
          .withServices(LocalStackContainer.Service.SECRETSMANAGER);

  @BeforeAll
  static void createSecrets() {
    try (SecretsManagerClient admin = buildAdminClient()) {
      admin.createSecret(
          CreateSecretRequest.builder()
              .name("myapp/token")
              .secretString("plain-token-value")
              .build());
      admin.createSecret(
          CreateSecretRequest.builder()
              .name("myapp/db")
              .secretString("{\"username\": \"admin\", \"password\": \"db-secret\"}")
              .build());
    }
  }

  private static SecretsManagerClient buildAdminClient() {
    return SecretsManagerClient.builder()
        .endpointOverride(
            localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .region(Region.of(localstack.getRegion()))
        .build();
  }

  private AwsSecretsManagerResolver buildResolver() {
    return new AwsSecretsManagerResolver(buildAdminClient());
  }

  @Test
  void shouldResolvePlainStringSecret() {
    assertThat(buildResolver().resolve("myapp/token")).isEqualTo("plain-token-value");
  }

  @Test
  void shouldResolveJsonSecretFieldWithHashSuffix() {
    assertThat(buildResolver().resolve("myapp/db#password")).isEqualTo("db-secret");
  }

  @Test
  void shouldResolveJsonSecretFieldUser() {
    assertThat(buildResolver().resolve("myapp/db#username")).isEqualTo("admin");
  }

  @Test
  void shouldThrowForNonExistentSecret() {
    assertThatThrownBy(() -> buildResolver().resolve("nonexistent/secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void shouldThrowForMissingFieldInJsonSecret() {
    assertThatThrownBy(() -> buildResolver().resolve("myapp/db#nonexistent"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void shouldThrowForFieldSuffixOnPlainStringSecret() {
    assertThatThrownBy(() -> buildResolver().resolve("myapp/token#field"))
        .isInstanceOf(SecretResolutionException.class);
  }
}
