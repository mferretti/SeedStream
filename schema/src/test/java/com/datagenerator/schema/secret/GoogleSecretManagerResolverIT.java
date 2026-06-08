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
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

class GoogleSecretManagerResolverIT extends IntegrationTest {

  private static final String PROJECT_ID = "test-project";
  private static final String EMULATOR_IMAGE =
      "ghcr.io/blackwell-systems/gcp-secret-manager-emulator-dual:latest";

  @Container
  static GenericContainer<?> emulator =
      new GenericContainer<>(DockerImageName.parse(EMULATOR_IMAGE))
          .withExposedPorts(9090, 8080)
          .waitingFor(Wait.forListeningPort());

  private static SecretManagerServiceClient adminClient;
  private static ManagedChannel channel;

  @BeforeAll
  static void setUp() throws IOException {
    channel =
        ManagedChannelBuilder.forAddress(emulator.getHost(), emulator.getMappedPort(9090))
            .usePlaintext()
            .build();

    SecretManagerServiceSettings settings =
        SecretManagerServiceSettings.newBuilder()
            .setTransportChannelProvider(
                FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
            .setCredentialsProvider(NoCredentialsProvider.create())
            .build();

    adminClient = SecretManagerServiceClient.create(settings);
    createTestSecrets();
  }

  @AfterAll
  static void tearDown() {
    if (adminClient != null) adminClient.close();
    if (channel != null) channel.shutdownNow();
  }

  private static void createTestSecrets() {
    Secret template =
        Secret.newBuilder()
            .setReplication(
                Replication.newBuilder()
                    .setAutomatic(Replication.Automatic.newBuilder().build())
                    .build())
            .build();

    String parent = "projects/" + PROJECT_ID;

    Secret plain = adminClient.createSecret(parent, "plain-secret", template);
    adminClient.addSecretVersion(
        plain.getName(),
        SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("my-plain-token")).build());

    Secret json = adminClient.createSecret(parent, "json-secret", template);
    adminClient.addSecretVersion(
        json.getName(),
        SecretPayload.newBuilder()
            .setData(
                ByteString.copyFromUtf8("{\"username\": \"admin\", \"password\": \"db-pass\"}"))
            .build());

    Secret multiVersion = adminClient.createSecret(parent, "multi-version-secret", template);
    adminClient.addSecretVersion(
        multiVersion.getName(),
        SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("version-1-value")).build());
    adminClient.addSecretVersion(
        multiVersion.getName(),
        SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("version-2-value")).build());
  }

  private GoogleSecretManagerResolver resolver() {
    return new GoogleSecretManagerResolver(adminClient, PROJECT_ID);
  }

  @Test
  void shouldResolvePlainStringSecret() {
    assertThat(resolver().resolve("plain-secret")).isEqualTo("my-plain-token");
  }

  @Test
  void shouldResolveJsonSecretWithFieldSuffix() {
    assertThat(resolver().resolve("json-secret#password")).isEqualTo("db-pass");
  }

  @Test
  void shouldResolveLatestVersionByDefault() {
    assertThat(resolver().resolve("multi-version-secret")).isEqualTo("version-2-value");
  }

  @Test
  void shouldResolveSpecificVersion() {
    assertThat(resolver().resolve("multi-version-secret/1")).isEqualTo("version-1-value");
  }

  @Test
  void shouldThrowForNonExistentSecret() {
    var r = resolver();
    assertThatThrownBy(() -> r.resolve("does-not-exist"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("does-not-exist");
  }

  @Test
  void shouldThrowForMissingFieldInJsonSecret() {
    var r = resolver();
    assertThatThrownBy(() -> r.resolve("json-secret#nonexistent"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("nonexistent");
  }
}
