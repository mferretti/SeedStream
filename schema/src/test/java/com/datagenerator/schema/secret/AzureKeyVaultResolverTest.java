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
import static org.mockito.Mockito.*;

import com.azure.core.exception.AzureException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.datagenerator.schema.exception.SecretResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureKeyVaultResolverTest {

  @Mock private SecretClient client;

  private AzureKeyVaultResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new AzureKeyVaultResolver(client);
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  void shouldResolvePlainSecret() {
    KeyVaultSecret secret = new KeyVaultSecret("my-password", "s3cr3t!");
    when(client.getSecret("my-password")).thenReturn(secret);

    assertThat(resolver.resolve("my-password")).isEqualTo("s3cr3t!");
  }

  @Test
  void shouldResolveSecretWithVersion() {
    KeyVaultSecret secret = new KeyVaultSecret("my-password", "versioned-value");
    when(client.getSecret("my-password", "abc123")).thenReturn(secret);

    assertThat(resolver.resolve("my-password/abc123")).isEqualTo("versioned-value");
  }

  @Test
  void shouldResolveSecretWithLongName() {
    String name = "my-app-prod-database-password";
    KeyVaultSecret secret = new KeyVaultSecret(name, "prod-secret");
    when(client.getSecret(name)).thenReturn(secret);

    assertThat(resolver.resolve(name)).isEqualTo("prod-secret");
  }

  @Test
  void shouldResolveNumericSecret() {
    KeyVaultSecret secret = new KeyVaultSecret("api-port", "8443");
    when(client.getSecret("api-port")).thenReturn(secret);

    assertThat(resolver.resolve("api-port")).isEqualTo("8443");
  }

  // ── Error handling ─────────────────────────────────────────────────────────

  @Test
  void shouldThrowSecretResolutionExceptionOnResourceNotFound() {
    when(client.getSecret("missing-secret"))
        .thenThrow(new ResourceNotFoundException("Secret not found", null));

    assertThatThrownBy(() -> resolver.resolve("missing-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("not found")
        .hasMessageContaining("missing-secret");
  }

  @Test
  void shouldThrowSecretResolutionExceptionOnHttpError() {
    when(client.getSecret("forbidden-secret"))
        .thenThrow(new HttpResponseException("Forbidden", null));

    assertThatThrownBy(() -> resolver.resolve("forbidden-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("forbidden-secret");
  }

  @Test
  void shouldThrowWhenSecretValueIsNull() {
    KeyVaultSecret secret = new KeyVaultSecret("null-secret", null);
    when(client.getSecret("null-secret")).thenReturn(secret);

    assertThatThrownBy(() -> resolver.resolve("null-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("null-secret");
  }

  @Test
  void shouldPreserveCauseOnResourceNotFound() {
    ResourceNotFoundException cause = new ResourceNotFoundException("not found", null);
    when(client.getSecret("my-secret")).thenThrow(cause);

    assertThatThrownBy(() -> resolver.resolve("my-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasCause(cause);
  }

  // ── Version parsing ────────────────────────────────────────────────────────

  @Test
  void shouldSplitNameAndVersionAtFirstSlash() {
    KeyVaultSecret secret = new KeyVaultSecret("db-pass", "value-v2");
    when(client.getSecret("db-pass", "v2")).thenReturn(secret);

    assertThat(resolver.resolve("db-pass/v2")).isEqualTo("value-v2");

    verify(client).getSecret("db-pass", "v2");
    verify(client, never()).getSecret("db-pass");
  }

  // ── Constructor validation ──────────────────────────────────────────────────

  @Test
  void shouldThrowWhenVaultUriIsNull() {
    assertThatThrownBy(() -> new AzureKeyVaultResolver((String) null))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_uri");
  }

  @Test
  void shouldThrowWhenVaultUriIsBlank() {
    assertThatThrownBy(() -> new AzureKeyVaultResolver("   "))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_uri");
  }

  // ── Auth error ─────────────────────────────────────────────────────────────

  @Test
  void shouldThrowSecretResolutionExceptionOnAuthenticationError() {
    AzureException authError = new AzureException("Authentication failed: invalid client secret");
    when(client.getSecret("my-secret")).thenThrow(authError);

    assertThatThrownBy(() -> resolver.resolve("my-secret"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("my-secret")
        .hasCause(authError);
  }

  @Test
  void shouldUseLatestVersionWhenNoSlash() {
    KeyVaultSecret secret = new KeyVaultSecret("db-pass", "latest-value");
    when(client.getSecret("db-pass")).thenReturn(secret);

    resolver.resolve("db-pass");

    verify(client).getSecret("db-pass");
    verify(client, never()).getSecret(eq("db-pass"), anyString());
  }
}
