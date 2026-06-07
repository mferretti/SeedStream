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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datagenerator.schema.exception.SecretResolutionException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleSecretManagerResolverTest {

  private static final String PROJECT_ID = "my-test-project";

  @Mock SecretManagerServiceClient mockClient;
  @Mock ApiException mockApiException;
  @Mock StatusCode mockStatusCode;

  private AccessSecretVersionResponse responseWith(String value) {
    return AccessSecretVersionResponse.newBuilder()
        .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value)).build())
        .build();
  }

  @Test
  void shouldResolvePlainStringSecret() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("my-plain-token"));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThat(resolver.resolve("my-secret")).isEqualTo("my-plain-token");
  }

  @Test
  void shouldResolveJsonSecretWithFieldSuffix() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("{\"username\": \"admin\", \"password\": \"secret123\"}"));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThat(resolver.resolve("my-db-secret#password")).isEqualTo("secret123");
  }

  @Test
  void shouldUseLatestVersionByDefault() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("value"));

    ArgumentCaptor<SecretVersionName> captor = ArgumentCaptor.forClass(SecretVersionName.class);
    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    resolver.resolve("my-secret");

    verify(mockClient).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecretVersion()).isEqualTo("latest");
  }

  @Test
  void shouldUseSpecificVersionWhenProvided() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("versioned-value"));

    ArgumentCaptor<SecretVersionName> captor = ArgumentCaptor.forClass(SecretVersionName.class);
    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    resolver.resolve("my-secret/5");

    verify(mockClient).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecretVersion()).isEqualTo("5");
    assertThat(captor.getValue().getSecret()).isEqualTo("my-secret");
  }

  @Test
  void shouldResolveSpecificVersionWithFieldSuffix() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("{\"api_key\": \"versioned-key\"}"));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThat(resolver.resolve("my-secret/3#api_key")).isEqualTo("versioned-key");
  }

  @Test
  void shouldThrowWhenSecretNotFound() {
    when(mockStatusCode.getCode()).thenReturn(StatusCode.Code.NOT_FOUND);
    when(mockApiException.getStatusCode()).thenReturn(mockStatusCode);
    when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenThrow(mockApiException);

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThatThrownBy(() -> resolver.resolve("nonexistent-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("not found")
        .hasMessageContaining("nonexistent-secret");
  }

  @Test
  void shouldThrowOnOtherApiException() {
    when(mockStatusCode.getCode()).thenReturn(StatusCode.Code.PERMISSION_DENIED);
    when(mockApiException.getStatusCode()).thenReturn(mockStatusCode);
    when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenThrow(mockApiException);

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThatThrownBy(() -> resolver.resolve("my-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("GCP Secret Manager error");
  }

  @Test
  void shouldThrowWhenFieldMissingInJsonSecret() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("{\"username\": \"admin\"}"));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThatThrownBy(() -> resolver.resolve("my-secret#password"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("password");
  }

  @Test
  void shouldThrowWhenFieldSuffixOnPlainStringSecret() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("plain-value"));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThatThrownBy(() -> resolver.resolve("my-secret#field"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("plain string");
  }

  @Test
  void shouldThrowWhenJsonFieldValueIsNull() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("{\"password\": null}"));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThatThrownBy(() -> resolver.resolve("my-secret#password"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("password");
  }

  @Test
  void shouldReturnRawPayloadWhenNoFieldSuffixEvenIfJson() {
    String json = "{\"username\": \"admin\", \"password\": \"secret\"}";
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith(json));

    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    assertThat(resolver.resolve("my-secret")).isEqualTo(json);
  }

  @Test
  void shouldPassProjectIdToSecretVersionName() {
    when(mockClient.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(responseWith("value"));

    ArgumentCaptor<SecretVersionName> captor = ArgumentCaptor.forClass(SecretVersionName.class);
    GoogleSecretManagerResolver resolver = new GoogleSecretManagerResolver(mockClient, PROJECT_ID);
    resolver.resolve("my-secret");

    verify(mockClient).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getProject()).isEqualTo(PROJECT_ID);
  }
}
