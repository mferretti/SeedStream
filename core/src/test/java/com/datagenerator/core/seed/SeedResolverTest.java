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

package com.datagenerator.core.seed;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.datagenerator.core.exception.SeedResolutionException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SeedResolverTest {
  private static final String TYPE_REMOTE = "remote";
  private static final String REMOTE_URL = "https://api.example.com/seed";
  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String ENV_SEED_KEY = "SEEDSTREAM_TEST_ENV_SEED_42";

  private SeedResolver resolver;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    resolver = new SeedResolver();
  }

  @Test
  void shouldResolveEmbeddedSeed() {
    SeedConfig.EmbeddedSeed config = new SeedConfig.EmbeddedSeed("embedded", 12345L);

    long seed = resolver.resolve(config);

    assertThat(seed).isEqualTo(12345L);
  }

  @Test
  void shouldResolveFileSeed() throws Exception {
    Path seedFile = tempDir.resolve("seed.txt");
    Files.writeString(seedFile, "99999");
    SeedConfig.FileSeed config = new SeedConfig.FileSeed("file", seedFile.toString());

    long seed = resolver.resolve(config);

    assertThat(seed).isEqualTo(99999L);
  }

  @Test
  void shouldHandleWhitespaceInFile() throws Exception {
    Path seedFile = tempDir.resolve("seed.txt");
    Files.writeString(seedFile, "  42  \n");
    SeedConfig.FileSeed config = new SeedConfig.FileSeed("file", seedFile.toString());

    long seed = resolver.resolve(config);

    assertThat(seed).isEqualTo(42L);
  }

  @Test
  void shouldFailWhenFileDoesNotExist() {
    SeedConfig.FileSeed config = new SeedConfig.FileSeed("file", "/nonexistent/seed.txt");

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void shouldFailWhenFileContainsInvalidNumber() throws Exception {
    Path seedFile = tempDir.resolve("invalid.txt");
    Files.writeString(seedFile, "not-a-number");
    SeedConfig.FileSeed config = new SeedConfig.FileSeed("file", seedFile.toString());

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Invalid seed value");
  }

  @Test
  void shouldResolveEnvSeed() {
    SeedResolver envResolver =
        new SeedResolver(null, key -> ENV_SEED_KEY.equals(key) ? "99887766" : null);
    SeedConfig.EnvSeed config = new SeedConfig.EnvSeed("env", ENV_SEED_KEY);
    assertThat(envResolver.resolve(config)).isEqualTo(99887766L);
  }

  @Test
  void shouldFailWhenEnvVarNotSet() {
    SeedConfig.EnvSeed config = new SeedConfig.EnvSeed("env", "NONEXISTENT_VAR_12345");

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("not set or empty");
  }

  static Stream<Arguments> remoteAuthScenarios() {
    return Stream.of(
        Arguments.of(
            new SeedConfig.RemoteSeed.AuthConfig("bearer", "secret-token", null, null, null, null),
            "77777",
            "Authorization",
            "Bearer secret-token"),
        Arguments.of(
            new SeedConfig.RemoteSeed.AuthConfig("basic", null, "user", "pass", null, null),
            "88888",
            "Authorization",
            "Basic dXNlcjpwYXNz"), // base64("user:pass")
        Arguments.of(
            new SeedConfig.RemoteSeed.AuthConfig(
                "api_key", null, null, null, API_KEY_HEADER, "my-key-123"),
            "66666",
            API_KEY_HEADER,
            "my-key-123"));
  }

  @ParameterizedTest
  @MethodSource("remoteAuthScenarios")
  void shouldResolveRemoteSeedWithAuth(
      SeedConfig.RemoteSeed.AuthConfig auth, String body, String headerName, String headerValue)
      throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(body);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, auth);

    long seed = customResolver.resolve(config);

    assertThat(seed).isEqualTo(Long.parseLong(body));
    verify(mockClient)
        .send(
            argThat(req -> req.headers().firstValue(headerName).orElse("").equals(headerValue)),
            any());
  }

  @Test
  void shouldRejectRedirectResponse() throws Exception {
    // Core-1 / CWE-918: redirects are not followed; a 3xx surfaces as a non-200 error rather than
    // reaching an unvalidated (possibly internal) target.
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(302);
    when(mockResponse.body()).thenReturn("redirecting");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("302");
  }

  @Test
  void shouldSetRequestTimeout() throws Exception {
    // Core-2 / CWE-400: every remote-seed request carries a hard timeout.
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("123");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    customResolver.resolve(new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, null));

    verify(mockClient).send(argThat(req -> req.timeout().isPresent()), any());
  }

  static Stream<Arguments> badRemoteResponses() {
    return Stream.of(
        // Core-1: a redirect (3xx) is not followed — surfaces as a rejected non-200 status.
        Arguments.of(302, "redirecting", "302"),
        Arguments.of(500, "Internal Server Error", "status 500"),
        Arguments.of(200, "invalid", "Invalid seed value"));
  }

  @ParameterizedTest
  @MethodSource("badRemoteResponses")
  void shouldFailOnBadRemoteResponse(int status, String body, String expectedMessage)
      throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(status);
    when(mockResponse.body()).thenReturn(body);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining(expectedMessage);
  }

  @Test
  void shouldTruncateLargeErrorBodyInExceptionMessage() throws Exception {
    String hugeBody = "x".repeat(10_000);
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(mockResponse.body()).thenReturn(hugeBody);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("… (10000 chars total)")
        .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThanOrEqualTo(300));
  }

  @Test
  void shouldFailWhenRemoteThrowsIOException() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Network error"));

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Failed to fetch");
  }

  @Test
  void shouldReturnDefaultSeedWhenConfigIsNull() {
    long seed = resolver.resolve(null);

    assertThat(seed).isZero();
  }

  @Test
  void shouldFailWhenBearerTokenMissing() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("bearer", null, null, null, null, null);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Bearer token is required");
  }

  @Test
  void shouldFailWhenBasicAuthCredentialsMissing() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("basic", null, "user", null, null, null);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Username and password required");
  }

  @Test
  void shouldFailWhenApiKeyMissing() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("api_key", null, null, null, API_KEY_HEADER, null);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("API key name and value are required");
  }

  @Test
  void shouldFailWhenAuthTypeIsNull() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig(null, null, null, null, null, null);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Auth type cannot be null");
  }

  @Test
  void shouldFailWhenAuthTypeIsUnsupported() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("oauth", null, null, null, null, null);
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, REMOTE_URL, auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Unsupported auth type: oauth");
  }

  @Test
  void shouldFailWhenSeedFileIsEmpty() throws Exception {
    Path seedFile = tempDir.resolve("empty.txt");
    Files.writeString(seedFile, "");
    SeedConfig.FileSeed config = new SeedConfig.FileSeed("file", seedFile.toString());

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("empty");
  }
}
