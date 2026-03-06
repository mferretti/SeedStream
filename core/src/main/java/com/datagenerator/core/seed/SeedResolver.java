package com.datagenerator.core.seed;

import com.datagenerator.core.exception.SeedResolutionException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves seed values from different sources (embedded, file, environment variable, remote API).
 */
@Slf4j
public class SeedResolver {
  private static final long DEFAULT_SEED = 0L;
  private volatile HttpClient httpClient; // Lazy initialized only when needed

  public SeedResolver() {
    // HttpClient created lazily when needed for remote seeds
  }

  // Constructor for testing with mock HttpClient
  SeedResolver(HttpClient httpClient) {
    this.httpClient = httpClient;
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
    return seed;
  }

  private long resolveFile(SeedConfig.FileSeed fileSeed) {
    Path path = Path.of(fileSeed.getPath());
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
      return seed;
    } catch (IOException e) {
      throw new SeedResolutionException("Failed to read seed file: " + path, e);
    } catch (NumberFormatException e) {
      throw new SeedResolutionException("Invalid seed value in file: " + path, e);
    }
  }

  private long resolveEnv(SeedConfig.EnvSeed envSeed) {
    String varName = envSeed.getName();
    String value = System.getenv(varName);
    
    // Fallback to system property if environment variable not found (useful for testing)
    if (value == null || value.isBlank()) {
      value = System.getProperty(varName);
    }
    
    if (value == null || value.isBlank()) {
      throw new SeedResolutionException("Environment variable not set or empty: " + varName);
    }

    try {
      long seed = Long.parseLong(value.trim());
      log.debug("Resolved seed from environment variable {}: {}", varName, seed);
      return seed;
    } catch (NumberFormatException e) {
      throw new SeedResolutionException(
          "Invalid seed value in environment variable " + varName + ": " + value, e);
    }
  }

  private long resolveRemote(SeedConfig.RemoteSeed remoteSeed) {
    String url = remoteSeed.getUrl();
    SeedConfig.RemoteSeed.AuthConfig auth = remoteSeed.getAuth();

    // Lazy initialization of HttpClient
    if (httpClient == null) {
      synchronized (this) {
        if (httpClient == null) {
          httpClient =
              HttpClient.newBuilder()
                  .connectTimeout(Duration.ofSeconds(10))
                  .followRedirects(HttpClient.Redirect.NORMAL)
                  .build();
        }
      }
    }

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).GET();

    // Add authentication headers
    if (auth != null) {
      switch (auth.getType()) {
        case "bearer":
          if (auth.getToken() == null) {
            throw new SeedResolutionException("Bearer token is required but not provided");
          }
          requestBuilder.header("Authorization", "Bearer " + auth.getToken());
          break;
        case "basic":
          if (auth.getUsername() == null || auth.getPassword() == null) {
            throw new SeedResolutionException("Username and password required for basic auth");
          }
          String credentials = auth.getUsername() + ":" + auth.getPassword();
          String encoded = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
          requestBuilder.header("Authorization", "Basic " + encoded);
          break;
        case "api_key":
          if (auth.getKey() == null || auth.getValue() == null) {
            throw new SeedResolutionException("API key name and value are required");
          }
          requestBuilder.header(auth.getKey(), auth.getValue());
          break;
        default:
          throw new SeedResolutionException("Unsupported auth type: " + auth.getType());
      }
    }

    try {
      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new SeedResolutionException(
            "Remote seed API returned status " + response.statusCode() + ": " + response.body());
      }

      long seed = Long.parseLong(response.body().trim());
      log.debug("Resolved seed from remote API {}: {}", url, seed);
      return seed;
    } catch (NumberFormatException e) {
      throw new SeedResolutionException("Invalid seed value from remote API: " + url, e);
    } catch (IOException | InterruptedException e) {
      throw new SeedResolutionException("Failed to fetch seed from remote API: " + url, e);
    }
  }
}
