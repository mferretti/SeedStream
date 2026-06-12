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

import com.datagenerator.core.exception.SeedResolutionException;
import com.datagenerator.core.security.PathValidator;
import com.datagenerator.core.security.UrlValidator;
import com.datagenerator.core.util.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves seed values from different sources (embedded, file, environment variable, remote API).
 */
@Slf4j
public class SeedResolver {
  private static final long DEFAULT_SEED = 0L;

  // LazyHolder defers HttpClient construction until resolveRemote() is first called
  private static final class HttpClientHolder {
    static final HttpClient INSTANCE =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  // Optional test-injected client (package-private constructor)
  private final HttpClient injectedHttpClient;
  // Injectable env reader for testing (defaults to System::getenv)
  private final java.util.function.UnaryOperator<String> envReader;

  public SeedResolver() {
    this.injectedHttpClient = null;
    this.envReader = System::getenv;
  }

  SeedResolver(HttpClient client) {
    this.injectedHttpClient = client;
    this.envReader = System::getenv;
  }

  /** Package-private constructor for injecting both HttpClient and env reader in unit tests. */
  SeedResolver(HttpClient client, java.util.function.UnaryOperator<String> envReader) {
    this.injectedHttpClient = client;
    this.envReader = envReader;
  }

  /**
   * Resolve seed from configuration. Returns default seed (0) with warning if config is null.
   *
   * @param seedConfig the seed configuration (can be null)
   * @return the resolved seed value
   * @throws SeedResolutionException if seed resolution fails
   */
  public long resolve(SeedConfig seedConfig) {
    if (seedConfig == null) {
      log.warn(
          "No seed configuration provided. Using default seed ({}). "
              + "Data will be deterministic but not reproducible across runs without explicit seed.",
          DEFAULT_SEED);
      return DEFAULT_SEED;
    }

    return switch (seedConfig) {
      case SeedConfig.EmbeddedSeed embedded -> resolveEmbedded(embedded);
      case SeedConfig.FileSeed file -> resolveFile(file);
      case SeedConfig.EnvSeed env -> resolveEnv(env);
      case SeedConfig.RemoteSeed remote -> resolveRemote(remote);
    };
  }

  private long resolveEmbedded(SeedConfig.EmbeddedSeed embedded) {
    long seed = embedded.getValue();
    log.debug("Using embedded seed: {}", seed);

    if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
      log.trace("Resolved embedded seed value: {}", seed);
    }

    return seed;
  }

  private long resolveFile(SeedConfig.FileSeed fileSeed) {
    Path path = PathValidator.validate(fileSeed.getPath(), null, "seed file path");
    if (!Files.exists(path)) {
      throw new SeedResolutionException("Seed file not found: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new SeedResolutionException("Seed file not readable: " + path);
    }

    try {
      String content = Files.readString(path).trim();
      if (content.isEmpty()) {
        throw new SeedResolutionException("Seed file is empty: " + path);
      }
      long seed = Long.parseLong(content);
      log.debug("Resolved seed from file {}: {}", path, seed);

      if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
        log.trace("Read seed from file: path={}, content={}, seed={}", path, content, seed);
      }

      return seed;
    } catch (IOException e) {
      throw new SeedResolutionException("Failed to read seed file: " + path, e);
    } catch (NumberFormatException e) {
      throw new SeedResolutionException("Invalid seed value in file: " + path, e);
    }
  }

  private long resolveEnv(SeedConfig.EnvSeed envSeed) {
    String varName = envSeed.getName();
    String value = envReader.apply(varName);

    if (value == null || value.isBlank()) {
      throw new SeedResolutionException(
          "Environment variable not set or empty: %s".formatted(varName));
    }

    try {
      long seed = Long.parseLong(value.trim());
      log.debug("Resolved seed from environment variable {}: {}", varName, seed);

      if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
        log.trace("Resolved env seed: variable={}, value={}, seed={}", varName, value, seed);
      }

      return seed;
    } catch (NumberFormatException e) {
      throw new SeedResolutionException(
          "Invalid seed value in environment variable " + varName + ": " + value, e);
    }
  }

  private long resolveRemote(SeedConfig.RemoteSeed remoteSeed) {
    String url = remoteSeed.getUrl();
    UrlValidator.validate(url, "remote seed URL");
    HttpClient client = injectedHttpClient != null ? injectedHttpClient : HttpClientHolder.INSTANCE;
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).GET();

    applyAuth(requestBuilder, remoteSeed.getAuth());

    try {
      HttpResponse<String> response =
          client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new SeedResolutionException(
            "Remote seed API returned status " + response.statusCode() + ": " + response.body());
      }

      long seed = Long.parseLong(response.body().trim());
      log.debug("Resolved seed from remote API {}: {}", url, seed);
      return seed;
    } catch (NumberFormatException e) {
      throw new SeedResolutionException("Invalid seed value from remote API: " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SeedResolutionException("Failed to fetch seed from remote API: " + url, e);
    } catch (IOException e) {
      throw new SeedResolutionException("Failed to fetch seed from remote API: " + url, e);
    }
  }

  private static void applyAuth(
      HttpRequest.Builder builder, SeedConfig.RemoteSeed.AuthConfig auth) {
    if (auth == null) return;
    switch (auth.getType()) {
      case "bearer" -> {
        if (auth.getToken() == null) {
          throw new SeedResolutionException("Bearer token is required but not provided");
        }
        builder.header("Authorization", "Bearer " + auth.getToken());
      }
      case "basic" -> {
        if (auth.getUsername() == null || auth.getPassword() == null) {
          throw new SeedResolutionException("Username and password required for basic auth");
        }
        String encoded =
            Base64.getEncoder()
                .encodeToString(
                    (auth.getUsername() + ":" + auth.getPassword())
                        .getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + encoded);
      }
      case "api_key" -> {
        if (auth.getKey() == null || auth.getValue() == null) {
          throw new SeedResolutionException("API key name and value are required");
        }
        builder.header(auth.getKey(), auth.getValue());
      }
      case null -> throw new SeedResolutionException("Auth type cannot be null");
      default -> throw new SeedResolutionException("Unsupported auth type: " + auth.getType());
    }
  }
}
