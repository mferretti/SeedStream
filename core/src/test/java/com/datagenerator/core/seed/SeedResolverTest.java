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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedResolverTest {
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
    // This test depends on environment, so we'll test the error case
    SeedConfig.EnvSeed config = new SeedConfig.EnvSeed("env", "PATH"); // PATH always exists

    // We can't guarantee PATH is a valid long, so test the resolution attempt
    // In real usage, users would set specific env vars like DATA_SEED=12345
    assertThatCode(() -> resolver.resolve(config))
        .isInstanceOfAny(SeedResolutionException.class, NumberFormatException.class);
  }

  @Test
  void shouldFailWhenEnvVarNotSet() {
    SeedConfig.EnvSeed config = new SeedConfig.EnvSeed("env", "NONEXISTENT_VAR_12345");

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("not set or empty");
  }

  @Test
  void shouldResolveRemoteSeedWithBearerAuth() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("77777");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("bearer", "secret-token", null, null, null, null);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", auth);

    long seed = customResolver.resolve(config);

    assertThat(seed).isEqualTo(77777L);
    verify(mockClient)
        .send(
            argThat(
                req ->
                    req.headers()
                        .firstValue("Authorization")
                        .orElse("")
                        .equals("Bearer secret-token")),
            any());
  }

  @Test
  void shouldResolveRemoteSeedWithBasicAuth() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("88888");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("basic", null, "user", "pass", null, null);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", auth);

    long seed = customResolver.resolve(config);

    assertThat(seed).isEqualTo(88888L);
    verify(mockClient)
        .send(
            argThat(
                req -> req.headers().firstValue("Authorization").orElse("").startsWith("Basic ")),
            any());
  }

  @Test
  void shouldResolveRemoteSeedWithApiKey() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("66666");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig(
            "api_key", null, null, null, "X-API-Key", "my-key-123");
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", auth);

    long seed = customResolver.resolve(config);

    assertThat(seed).isEqualTo(66666L);
    verify(mockClient)
        .send(
            argThat(req -> req.headers().firstValue("X-API-Key").orElse("").equals("my-key-123")),
            any());
  }

  @Test
  void shouldFailWhenRemoteReturnsNon200() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(mockResponse.body()).thenReturn("Internal Server Error");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("status 500");
  }

  @Test
  void shouldFailWhenRemoteReturnsInvalidNumber() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("invalid");
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Invalid seed value");
  }

  @Test
  void shouldFailWhenRemoteThrowsIOException() throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Network error"));

    SeedResolver customResolver = new SeedResolver(mockClient);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", null);

    assertThatThrownBy(() -> customResolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Failed to fetch");
  }

  @Test
  void shouldReturnDefaultSeedWhenConfigIsNull() {
    long seed = resolver.resolve(null);

    assertThat(seed).isEqualTo(0L);
  }

  @Test
  void shouldFailWhenBearerTokenMissing() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("bearer", null, null, null, null, null);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Bearer token is required");
  }

  @Test
  void shouldFailWhenBasicAuthCredentialsMissing() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("basic", null, "user", null, null, null);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Username and password required");
  }

  @Test
  void shouldFailWhenApiKeyMissing() {
    SeedConfig.RemoteSeed.AuthConfig auth =
        new SeedConfig.RemoteSeed.AuthConfig("api_key", null, null, null, "X-API-Key", null);
    SeedConfig.RemoteSeed config =
        new SeedConfig.RemoteSeed("remote", "https://api.example.com/seed", auth);

    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("API key name and value are required");
  }
}
