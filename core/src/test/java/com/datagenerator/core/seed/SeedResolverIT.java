package com.datagenerator.core.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.core.IntegrationTest;
import com.datagenerator.core.exception.SeedResolutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for SeedResolver with actual file system and environment.
 *
 * <p>Run with: ./gradlew :core:integrationTest
 */
class SeedResolverIT extends IntegrationTest {

  private final SeedResolver resolver = new SeedResolver();

  @TempDir Path tempDir;

  @Test
  void shouldResolveEmbeddedSeed() {
    // Given: Embedded seed configuration
    SeedConfig config = new SeedConfig.EmbeddedSeed("embedded", 12345L);

    // When: Resolve seed
    long seed = resolver.resolve(config);

    // Then: Returns parsed value
    assertThat(seed).isEqualTo(12345L);
  }

  @Test
  void shouldResolveSeedFromFile() throws IOException {
    // Given: Seed stored in file
    Path seedFile = tempDir.resolve("seed.txt");
    Files.writeString(seedFile, "987654321");

    SeedConfig config = new SeedConfig.FileSeed("file", seedFile.toString());

    // When: Resolve seed
    long seed = resolver.resolve(config);

    // Then: Returns value from file
    assertThat(seed).isEqualTo(987654321L);
  }

  @Test
  void shouldResolveSeedFromFileWithWhitespace() throws IOException {
    // Given: Seed file with whitespace
    Path seedFile = tempDir.resolve("seed-whitespace.txt");
    Files.writeString(seedFile, "  42  \n");

    SeedConfig config = new SeedConfig.FileSeed("file", seedFile.toString());

    // When: Resolve seed
    long seed = resolver.resolve(config);

    // Then: Whitespace trimmed
    assertThat(seed).isEqualTo(42L);
  }

  @Test
  void shouldFailWhenFileNotFound() {
    // Given: Non-existent file path
    SeedConfig config = new SeedConfig.FileSeed("file", "/nonexistent/path/seed.txt");

    // When/Then: Throws exception
    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Seed file not found");
  }

  @Test
  void shouldResolveSeedFromEnvironmentVariable() {
    // Given: Environment variable with seed
    String envVar = "TEST_SEED_" + System.currentTimeMillis();
    String seedValue = "555666777";

    // Set environment variable (note: this won't work in actual JVM runtime,
    // so we'll use a system property as a workaround for testing)
    System.setProperty(envVar, seedValue);

    try {
      SeedConfig config = new SeedConfig.EnvSeed("env", envVar);

      // When: Resolve seed (SeedResolver should check system properties if env var not found)
      long seed = resolver.resolve(config);

      // Then: Returns value from environment
      assertThat(seed).isEqualTo(555666777L);
    } finally {
      System.clearProperty(envVar);
    }
  }

  @Test
  void shouldFailWhenEnvironmentVariableNotSet() {
    // Given: Non-existent environment variable
    SeedConfig config = new SeedConfig.EnvSeed("env", "NONEXISTENT_VAR_XYZ");

    // When/Then: Throws exception
    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Environment variable");
  }

  @Test
  void shouldHandleMultipleFileReads() throws IOException {
    // Given: Multiple seed files
    Path seedFile1 = tempDir.resolve("seed1.txt");
    Path seedFile2 = tempDir.resolve("seed2.txt");
    Files.writeString(seedFile1, "111");
    Files.writeString(seedFile2, "222");

    SeedConfig config1 = new SeedConfig.FileSeed("file", seedFile1.toString());
    SeedConfig config2 = new SeedConfig.FileSeed("file", seedFile2.toString());

    // When: Resolve seeds from different files
    long seed1 = resolver.resolve(config1);
    long seed2 = resolver.resolve(config2);

    // Then: Different seeds returned
    assertThat(seed1).isEqualTo(111L);
    assertThat(seed2).isEqualTo(222L);
  }

  @Test
  void shouldHandleFileWithLargeSeedValue() throws IOException {
    // Given: File with large seed value
    Path seedFile = tempDir.resolve("large-seed.txt");
    long largeSeed = Long.MAX_VALUE - 1000;
    Files.writeString(seedFile, String.valueOf(largeSeed));

    SeedConfig config = new SeedConfig.FileSeed("file", seedFile.toString());

    // When: Resolve seed
    long seed = resolver.resolve(config);

    // Then: Large value handled correctly
    assertThat(seed).isEqualTo(largeSeed);
  }

  @Test
  void shouldFailOnInvalidSeedFormat() throws IOException {
    // Given: File with non-numeric content
    Path seedFile = tempDir.resolve("invalid-seed.txt");
    Files.writeString(seedFile, "not-a-number");

    SeedConfig config = new SeedConfig.FileSeed("file", seedFile.toString());

    // When/Then: Throws exception
    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Invalid seed value");
  }

  @Test
  void shouldHandleEmptyFile() throws IOException {
    // Given: Empty seed file
    Path seedFile = tempDir.resolve("empty-seed.txt");
    Files.writeString(seedFile, "");

    SeedConfig config = new SeedConfig.FileSeed("file", seedFile.toString());

    // When/Then: Throws exception
    assertThatThrownBy(() -> resolver.resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("empty");
  }
}
