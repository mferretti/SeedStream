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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.datagenerator.schema.exception.SecretResolutionException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class VaultSecretResolverTest {

  private static final String VAULT_ADDR = "http://vault:8200";
  private static final String TEST_TOKEN = "test-vault-token";
  private static final String VAULT_TOKEN_KEY = "VAULT_TOKEN";
  private static final String SECRET_PATH = "secret/data/app#key";

  /** Env reader that always returns the test token for VAULT_TOKEN. */
  private static final UnaryOperator<String> TEST_ENV =
      key -> VAULT_TOKEN_KEY.equals(key) ? TEST_TOKEN : null;

  @Mock HttpClient mockClient;
  @Mock HttpResponse<String> mockResponse;

  static Stream<Arguments> vaultResolveScenarios() {
    return Stream.of(
        Arguments.of(
            "{\"data\": {\"data\": {\"password\": \"secret123\"}, \"metadata\": {}}}",
            "secret/data/myapp/db#password",
            "secret123"),
        Arguments.of(
            "{\"data\": {\"password\": \"kvv1-secret\"}}",
            "secret/myapp/db#password",
            "kvv1-secret"),
        Arguments.of(
            "{\"data\": {\"value\": \"single-value\"}}", "secret/myapp/token", "single-value"));
  }

  @ParameterizedTest
  @MethodSource("vaultResolveScenarios")
  void shouldResolveVaultSecret(String body, String path, String expected) throws Exception {
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    doReturn(mockResponse).when(mockClient).send(any(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThat(resolver.resolve(path)).isEqualTo(expected);
  }

  @Test
  void shouldThrowOnNon200Response() throws Exception {
    when(mockResponse.statusCode()).thenReturn(403);
    doReturn(mockResponse).when(mockClient).send(any(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve(SECRET_PATH))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("403");
  }

  @Test
  void shouldThrowWhenFieldMissingInSecret() throws Exception {
    String body = "{\"data\": {\"data\": {\"username\": \"admin\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    doReturn(mockResponse).when(mockClient).send(any(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve("secret/data/app#nonexistent"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void shouldThrowWhenMultipleFieldsAndNoSuffix() throws Exception {
    String body = "{\"data\": {\"data\": {\"username\": \"admin\", \"password\": \"secret\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    doReturn(mockResponse).when(mockClient).send(any(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve("secret/data/app"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("multiple fields");
  }

  @Test
  void shouldSendVaultNamespaceHeader() throws Exception {
    String body = "{\"data\": {\"data\": {\"token\": \"abc\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    doReturn(mockResponse).when(mockClient).send(captor.capture(), any());

    VaultSecretResolver resolver =
        new VaultSecretResolver(VAULT_ADDR, "myteam", mockClient, TEST_ENV);
    resolver.resolve("secret/data/app#token");

    assertThat(captor.getValue().headers().firstValue("X-Vault-Namespace")).hasValue("myteam");
  }

  @Test
  void shouldSendVaultTokenHeader() throws Exception {
    String body = "{\"data\": {\"data\": {\"key\": \"val\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    doReturn(mockResponse).when(mockClient).send(captor.capture(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    resolver.resolve(SECRET_PATH);

    assertThat(captor.getValue().headers().firstValue("X-Vault-Token")).hasValue(TEST_TOKEN);
  }

  @Test
  void shouldThrowWhenVaultTokenNotSet() {
    VaultSecretResolver resolver =
        new VaultSecretResolver(VAULT_ADDR, null, mockClient, key -> null);
    assertThatThrownBy(() -> resolver.resolve(SECRET_PATH))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining(VAULT_TOKEN_KEY);
  }

  @Test
  void shouldThrowOnNetworkFailure() throws Exception {
    when(mockClient.send(any(), any())).thenThrow(new IOException("connection refused"));

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    // Error must not leak the Vault address (info-disclosure hardening, finding #10).
    assertThatThrownBy(() -> resolver.resolve(SECRET_PATH))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("Failed to contact Vault")
        .hasMessageNotContaining(VAULT_ADDR);
  }

  @Test
  void shouldAcceptAny2xxResponse() throws Exception {
    // Vault (and proxies) may answer with 2xx codes other than 200 (finding #10).
    when(mockResponse.statusCode()).thenReturn(204);
    when(mockResponse.body()).thenReturn("{\"data\": {\"data\": {\"key\": \"v\"}}}");
    doReturn(mockResponse).when(mockClient).send(any(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThat(resolver.resolve(SECRET_PATH)).isEqualTo("v");
  }

  @Test
  void shouldThrowOn3xxResponse() throws Exception {
    when(mockResponse.statusCode()).thenReturn(301);
    doReturn(mockResponse).when(mockClient).send(any(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve(SECRET_PATH))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("301");
  }

  @Test
  void shouldNormalizeTrailingSlashInVaultAddr() throws Exception {
    String body = "{\"data\": {\"data\": {\"key\": \"value\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    doReturn(mockResponse).when(mockClient).send(captor.capture(), any());

    VaultSecretResolver resolver =
        new VaultSecretResolver("http://vault:8200/", null, mockClient, TEST_ENV);
    resolver.resolve(SECRET_PATH);

    assertThat(captor.getValue().uri()).hasToString("http://vault:8200/v1/secret/data/app");
  }

  @Test
  void shouldLeaveOrdinaryPathUnencoded() throws Exception {
    String body = "{\"data\": {\"data\": {\"key\": \"value\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    doReturn(mockResponse).when(mockClient).send(captor.capture(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    resolver.resolve("secret/data/app#key");

    assertThat(captor.getValue().uri()).hasToString(VAULT_ADDR + "/v1/secret/data/app");
  }

  @Test
  void shouldEncodeQueryStringInjectionAttempt() throws Exception {
    String body = "{\"data\": {\"data\": {\"key\": \"value\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    doReturn(mockResponse).when(mockClient).send(captor.capture(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    resolver.resolve("secret/data/app?x=y#key");

    // '?' must be percent-encoded so it cannot start a query string on the Vault request.
    assertThat(captor.getValue().uri()).hasToString(VAULT_ADDR + "/v1/secret/data/app%3Fx%3Dy");
  }

  @Test
  void shouldEncodePathTraversalSegments() throws Exception {
    String body = "{\"data\": {\"data\": {\"key\": \"value\"}}}";
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    doReturn(mockResponse).when(mockClient).send(captor.capture(), any());

    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    resolver.resolve("../sys/health#key");

    assertThat(captor.getValue().uri()).hasToString(VAULT_ADDR + "/v1/../sys/health");
  }

  @Test
  void shouldRejectBlankPath() {
    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve("#key"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void shouldRejectLeadingSlash() {
    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve("/secret/data/app#key"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("must not start with '/'");
  }

  @Test
  void shouldRejectWhitespaceInPath() {
    VaultSecretResolver resolver = new VaultSecretResolver(VAULT_ADDR, null, mockClient, TEST_ENV);
    assertThatThrownBy(() -> resolver.resolve("secret/data/app with space#key"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("whitespace");
  }
}
