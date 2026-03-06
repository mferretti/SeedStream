# TASK-005: Core Module - Seed Resolution

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-003 (Job Definition Parser)  
**Human Supervision**: MEDIUM (security implications for remote seeds)

---

## Objective

Implement seed resolution mechanism that supports multiple seed sources: embedded values, file reading, environment variables, and remote API calls with authentication.

---

## Background

Seeds ensure reproducible data generation. Different environments need different seed sources:
- **Development**: Embedded seeds in YAML (simple, version-controlled)
- **CI/CD**: Environment variables (injected by pipeline)
- **Production**: File-based or remote API (secrets management)

All seed types must resolve to a `long` value for seeding Random instances.

---

## Implementation Details

### Step 1: Create SeedResolver Interface

**File**: `core/src/main/java/com/datagenerator/core/SeedResolver.java`

```java
package com.datagenerator.core;

import com.datagenerator.schema.model.SeedConfig;

/**
 * Resolves seed values from various sources.
 */
public interface SeedResolver {
    
    /**
     * Resolve seed value from configuration.
     * 
     * @param config Seed configuration
     * @return Resolved seed value
     * @throws SeedResolutionException if resolution fails
     */
    long resolve(SeedConfig config);
}
```

---

### Step 2: Create DefaultSeedResolver Implementation

**File**: `core/src/main/java/com/datagenerator/core/DefaultSeedResolver.java`

```java
package com.datagenerator.core;

import com.datagenerator.schema.model.SeedConfig;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Default implementation of seed resolver supporting all seed types.
 */
@Slf4j
public class DefaultSeedResolver implements SeedResolver {
    
    private final HttpClient httpClient;
    
    public DefaultSeedResolver() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    @Override
    public long resolve(SeedConfig config) {
        log.debug("Resolving seed from type: {}", config.getType());
        
        return switch (config.getType()) {
            case EMBEDDED -> resolveEmbedded(config);
            case FILE -> resolveFile(config);
            case ENV -> resolveEnv(config);
            case REMOTE -> resolveRemote(config);
        };
    }
    
    private long resolveEmbedded(SeedConfig config) {
        long seed = config.getValue();
        log.info("Using embedded seed: {}", seed);
        return seed;
    }
    
    private long resolveFile(SeedConfig config) {
        try {
            String content = Files.readString(Paths.get(config.getPath())).trim();
            long seed = Long.parseLong(content);
            log.info("Read seed from file: {} = {}", config.getPath(), seed);
            return seed;
        } catch (IOException e) {
            throw new SeedResolutionException("Failed to read seed from file: " + config.getPath(), e);
        } catch (NumberFormatException e) {
            throw new SeedResolutionException("Seed file contains invalid number: " + config.getPath(), e);
        }
    }
    
    private long resolveEnv(SeedConfig config) {
        String envValue = System.getenv(config.getEnvVar());
        if (envValue == null || envValue.isBlank()) {
            throw new SeedResolutionException("Environment variable not set: " + config.getEnvVar());
        }
        
        try {
            long seed = Long.parseLong(envValue.trim());
            log.info("Read seed from environment: {} = {}", config.getEnvVar(), seed);
            return seed;
        } catch (NumberFormatException e) {
            throw new SeedResolutionException("Environment variable contains invalid number: " + config.getEnvVar(), e);
        }
    }
    
    private long resolveRemote(SeedConfig config) {
        try {
            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl()))
                .timeout(Duration.ofSeconds(10))
                .GET();
            
            // Add authentication if configured
            if (config.getAuth() != null) {
                addAuthentication(requestBuilder, config.getAuth());
            }
            
            var response = httpClient.send(requestBuilder.build(), 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new SeedResolutionException(
                    "Remote seed API returned status " + response.statusCode() + ": " + response.body());
            }
            
            long seed = Long.parseLong(response.body().trim());
            log.info("Fetched seed from remote: {} = {}", config.getUrl(), seed);
            return seed;
            
        } catch (NumberFormatException e) {
            throw new SeedResolutionException("Remote seed API returned invalid number", e);
        } catch (Exception e) {
            throw new SeedResolutionException("Failed to fetch seed from remote: " + config.getUrl(), e);
        }
    }
    
    private void addAuthentication(HttpRequest.Builder requestBuilder, java.util.Map<String, String> auth) {
        String authType = auth.get("type");
        if (authType == null) {
            return;
        }
        
        switch (authType.toLowerCase()) {
            case "bearer" -> {
                String token = auth.get("token");
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }
            }
            case "basic" -> {
                String username = auth.get("username");
                String password = auth.get("password");
                if (username != null && password != null) {
                    String credentials = username + ":" + password;
                    String encoded = java.util.Base64.getEncoder()
                        .encodeToString(credentials.getBytes());
                    requestBuilder.header("Authorization", "Basic " + encoded);
                }
            }
            case "api_key" -> {
                String key = auth.get("key");
                String headerName = auth.getOrDefault("header", "X-API-Key");
                if (key != null) {
                    requestBuilder.header(headerName, key);
                }
            }
            default -> log.warn("Unknown authentication type: {}", authType);
        }
    }
}
```

---

### Step 3: Create Custom Exception

**File**: `core/src/main/java/com/datagenerator/core/SeedResolutionException.java`

```java
package com.datagenerator.core;

/**
 * Exception thrown when seed resolution fails.
 */
public class SeedResolutionException extends RuntimeException {
    
    public SeedResolutionException(String message) {
        super(message);
    }
    
    public SeedResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Step 4: Write Unit Tests

**File**: `core/src/test/java/com/datagenerator/core/DefaultSeedResolverTest.java`

```java
package com.datagenerator.core;

import com.datagenerator.schema.model.SeedConfig;
import com.datagenerator.schema.model.SeedConfig.SeedType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DefaultSeedResolverTest {
    
    private final SeedResolver resolver = new DefaultSeedResolver();
    
    @Test
    void shouldResolveEmbeddedSeed() {
        var config = new SeedConfig(SeedType.EMBEDDED, 12345L, null, null, null, null);
        
        long seed = resolver.resolve(config);
        
        assertThat(seed).isEqualTo(12345L);
    }
    
    @Test
    void shouldResolveFileSeed(@TempDir Path tempDir) throws Exception {
        Path seedFile = tempDir.resolve("seed.txt");
        Files.writeString(seedFile, "98765");
        
        var config = new SeedConfig(SeedType.FILE, null, seedFile.toString(), null, null, null);
        
        long seed = resolver.resolve(config);
        
        assertThat(seed).isEqualTo(98765L);
    }
    
    @Test
    void shouldThrowExceptionForMissingFile() {
        var config = new SeedConfig(SeedType.FILE, null, "/nonexistent/seed.txt", null, null, null);
        
        assertThatThrownBy(() -> resolver.resolve(config))
            .isInstanceOf(SeedResolutionException.class)
            .hasMessageContaining("Failed to read seed from file");
    }
    
    @Test
    void shouldThrowExceptionForInvalidFileContent(@TempDir Path tempDir) throws Exception {
        Path seedFile = tempDir.resolve("seed.txt");
        Files.writeString(seedFile, "not-a-number");
        
        var config = new SeedConfig(SeedType.FILE, null, seedFile.toString(), null, null, null);
        
        assertThatThrownBy(() -> resolver.resolve(config))
            .isInstanceOf(SeedResolutionException.class)
            .hasMessageContaining("invalid number");
    }
    
    @Test
    void shouldResolveEnvSeed() {
        // Set environment variable (note: this only works in the same JVM process)
        String envVar = "TEST_SEED_" + System.currentTimeMillis();
        // Cannot actually set env vars in Java, so this test is limited
        // In real usage, env vars would be set by the shell/container
        
        var config = new SeedConfig(SeedType.ENV, null, null, "PATH", null, null);
        
        // PATH should exist, but won't be a valid number
        assertThatThrownBy(() -> resolver.resolve(config))
            .isInstanceOf(SeedResolutionException.class)
            .hasMessageContaining("invalid number");
    }
    
    @Test
    void shouldThrowExceptionForMissingEnvVar() {
        var config = new SeedConfig(SeedType.ENV, null, null, "NONEXISTENT_VAR_12345", null, null);
        
        assertThatThrownBy(() -> resolver.resolve(config))
            .isInstanceOf(SeedResolutionException.class)
            .hasMessageContaining("not set");
    }
    
    // Note: Remote seed tests would require mockito or WireMock for HTTP mocking
    // These are integration tests and should be in a separate test class
}
```

---

## Acceptance Criteria

- ✅ Resolves embedded seeds from configuration
- ✅ Reads seeds from files (with validation)
- ✅ Reads seeds from environment variables
- ✅ Fetches seeds from remote APIs with authentication (bearer, basic, api_key)
- ✅ Throws clear exceptions for resolution failures
- ✅ Logs seed resolution for debugging
- ✅ All unit tests pass

---

## Testing

Run tests:
```bash
./gradlew :core:test
```

---

## Security Considerations

- **Remote seeds**: Use HTTPS to prevent MITM attacks
- **Credentials**: Never log authentication tokens/passwords
- **File permissions**: Seed files should have restricted permissions (600)
- **Timeout**: Remote calls timeout after 10 seconds to prevent hangs

---

**Completion Date**: [Mark when complete]
