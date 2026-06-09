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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.datagenerator.schema.exception.SecretResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerResolverTest {

  private static final String SECRET_PATH = "myapp/db#password";

  @Mock SecretsManagerClient mockClient;

  private GetSecretValueResponse responseWith(String secretString) {
    return GetSecretValueResponse.builder().secretString(secretString).build();
  }

  @Test
  void shouldResolvePlainStringSecret() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("my-plain-token"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThat(resolver.resolve("myapp/token")).isEqualTo("my-plain-token");
  }

  @Test
  void shouldResolveJsonSecretWithFieldSuffix() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("{\"username\": \"admin\", \"password\": \"secret123\"}"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThat(resolver.resolve(SECRET_PATH)).isEqualTo("secret123");
  }

  @Test
  void shouldResolveSingleKeyJsonSecretWithoutSuffix() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("{\"api_key\": \"abc-xyz-123\"}"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThat(resolver.resolve("myapp/api_key")).isEqualTo("abc-xyz-123");
  }

  @Test
  void shouldThrowForMultipleFieldsWithoutSuffix() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("{\"username\": \"admin\", \"password\": \"secret\"}"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThatThrownBy(() -> resolver.resolve("myapp/db"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("multiple fields");
  }

  @Test
  void shouldThrowWhenFieldMissingInJsonSecret() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("{\"username\": \"admin\"}"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThatThrownBy(() -> resolver.resolve(SECRET_PATH))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("password");
  }

  @Test
  void shouldThrowForBinarySecret() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().build()); // secretString() == null

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThatThrownBy(() -> resolver.resolve("myapp/binary"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("binary");
  }

  @Test
  void shouldThrowOnResourceNotFound() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(ResourceNotFoundException.builder().message("not found").build());

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThatThrownBy(() -> resolver.resolve("myapp/missing"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void shouldThrowOnSdkClientException() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(SdkClientException.builder().message("connection refused").build());

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThatThrownBy(() -> resolver.resolve(SECRET_PATH))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("myapp/db");
  }

  @Test
  void shouldThrowWhenFieldSuffixOnPlainStringSecret() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("plain-value"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThatThrownBy(() -> resolver.resolve("myapp/token#field"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("plain string");
  }

  @Test
  void shouldResolveSecretByArn() {
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(responseWith("{\"password\": \"arn-secret\"}"));

    AwsSecretsManagerResolver resolver = new AwsSecretsManagerResolver(mockClient);
    assertThat(
            resolver.resolve(
                "arn:aws:secretsmanager:us-east-1:123456789012:secret:myapp/db#password"))
        .isEqualTo("arn-secret");
  }
}
