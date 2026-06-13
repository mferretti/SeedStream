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

  private static final String SECRET_NAME = "my-secret";
  private static final String MY_PASSWORD = "my-password";
  private static final String API_PORT = "api-port";
  private static final String MISSING_SECRET = "missing-secret";
  private static final String FORBIDDEN_SECRET = "forbidden-secret";
  private static final String NULL_SECRET = "null-secret";
  private static final String DB_PASS = "db-pass";

  @Mock private SecretClient client;

  private AzureKeyVaultResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new AzureKeyVaultResolver(client);
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  void shouldResolvePlainSecret() {
    KeyVaultSecret secret = new KeyVaultSecret(MY_PASSWORD, "s3cr3t!");
    when(client.getSecret(MY_PASSWORD)).thenReturn(secret);

    assertThat(resolver.resolve(MY_PASSWORD)).isEqualTo("s3cr3t!");
  }

  @Test
  void shouldResolveSecretWithVersion() {
    KeyVaultSecret secret = new KeyVaultSecret(MY_PASSWORD, "versioned-value");
    when(client.getSecret(MY_PASSWORD, "abc123")).thenReturn(secret);

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
    KeyVaultSecret secret = new KeyVaultSecret(API_PORT, "8443");
    when(client.getSecret(API_PORT)).thenReturn(secret);

    assertThat(resolver.resolve(API_PORT)).isEqualTo("8443");
  }

  // ── Error handling ─────────────────────────────────────────────────────────

  @Test
  void shouldThrowSecretResolutionExceptionOnResourceNotFound() {
    when(client.getSecret(MISSING_SECRET))
        .thenThrow(new ResourceNotFoundException("Secret not found", null));

    assertThatThrownBy(() -> resolver.resolve(MISSING_SECRET))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("not found")
        .hasMessageContaining(MISSING_SECRET);
  }

  @Test
  void shouldThrowSecretResolutionExceptionOnHttpError() {
    when(client.getSecret(FORBIDDEN_SECRET))
        .thenThrow(new HttpResponseException("Forbidden", null));

    assertThatThrownBy(() -> resolver.resolve(FORBIDDEN_SECRET))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining(FORBIDDEN_SECRET);
  }

  @Test
  void shouldThrowWhenSecretValueIsNull() {
    KeyVaultSecret secret = new KeyVaultSecret(NULL_SECRET, null);
    when(client.getSecret(NULL_SECRET)).thenReturn(secret);

    assertThatThrownBy(() -> resolver.resolve(NULL_SECRET))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining(NULL_SECRET);
  }

  @Test
  void shouldPreserveCauseOnResourceNotFound() {
    ResourceNotFoundException cause = new ResourceNotFoundException("not found", null);
    when(client.getSecret(SECRET_NAME)).thenThrow(cause);

    assertThatThrownBy(() -> resolver.resolve(SECRET_NAME))
        .isInstanceOf(SecretResolutionException.class)
        .hasCause(cause);
  }

  // ── Version parsing ────────────────────────────────────────────────────────

  @Test
  void shouldSplitNameAndVersionAtFirstSlash() {
    KeyVaultSecret secret = new KeyVaultSecret(DB_PASS, "value-v2");
    when(client.getSecret(DB_PASS, "v2")).thenReturn(secret);

    assertThat(resolver.resolve("db-pass/v2")).isEqualTo("value-v2");

    verify(client).getSecret(DB_PASS, "v2");
    verify(client, never()).getSecret(DB_PASS);
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

  @Test
  void shouldRejectNonHttpVaultUri() {
    // SSRF guard: vault_uri receives an Azure bearer token, so non-HTTP(S) schemes are rejected
    // before the SDK is built (finding #11).
    assertThatThrownBy(() -> new AzureKeyVaultResolver("file:///etc/passwd"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("http");
  }

  @Test
  void shouldRejectMalformedVaultUri() {
    assertThatThrownBy(() -> new AzureKeyVaultResolver("not-a-valid-uri"))
        .isInstanceOf(SecretResolutionException.class);
  }

  // ── Auth error ─────────────────────────────────────────────────────────────

  @Test
  void shouldThrowSecretResolutionExceptionOnAuthenticationError() {
    AzureException authError = new AzureException("Authentication failed: invalid client secret");
    when(client.getSecret(SECRET_NAME)).thenThrow(authError);

    assertThatThrownBy(() -> resolver.resolve(SECRET_NAME))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining(SECRET_NAME)
        .hasCause(authError);
  }

  @Test
  void shouldUseLatestVersionWhenNoSlash() {
    KeyVaultSecret secret = new KeyVaultSecret(DB_PASS, "latest-value");
    when(client.getSecret(DB_PASS)).thenReturn(secret);

    resolver.resolve(DB_PASS);

    verify(client).getSecret(DB_PASS);
    verify(client, never()).getSecret(eq(DB_PASS), anyString());
  }
}
