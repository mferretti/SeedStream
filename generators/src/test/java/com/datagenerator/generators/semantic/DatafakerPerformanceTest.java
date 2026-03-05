package com.datagenerator.generators.semantic;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Performance tests for Datafaker generators to measure throughput (records per second).
 *
 * <p>These tests help establish performance baselines and identify any regressions.
 */
class DatafakerPerformanceTest {
  private DatafakerGenerator generator;
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    generator = new DatafakerGenerator();
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  // ==================================================================================
  // SIMPLE RECORD GENERATION THROUGHPUT
  // ==================================================================================

  @Test
  void shouldMeasureThroughputForSimpleNameGeneration() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    int recordCount = 10000;

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String name = (String) generator.generate(random, nameType);
        assertThat(name).isNotEmpty(); // Verify valid generation
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Simple NAME generation: %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      // Performance assertion: should generate at least 3000 names per second
      assertThat(recordsPerSecond).as("Names per second").isGreaterThan(3000.0);
    }
  }

  @Test
  void shouldMeasureThroughputForEmailGeneration() {
    PrimitiveType emailType = new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null);
    int recordCount = 10000;

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String email = (String) generator.generate(random, emailType);
        assertThat(email).contains("@");
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Simple EMAIL generation: %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      assertThat(recordsPerSecond).as("Emails per second").isGreaterThan(3000.0);
    }
  }

  @Test
  void shouldMeasureThroughputForAddressGeneration() {
    PrimitiveType addressType = new PrimitiveType(PrimitiveType.Kind.ADDRESS, null, null);
    int recordCount = 10000;

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String address = (String) generator.generate(random, addressType);
        assertThat(address).isNotEmpty();
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Simple ADDRESS generation: %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      assertThat(recordsPerSecond).as("Addresses per second").isGreaterThan(2000.0);
    }
  }

  @Test
  void shouldMeasureThroughputForPhoneNumberGeneration() {
    PrimitiveType phoneType = new PrimitiveType(PrimitiveType.Kind.PHONE_NUMBER, null, null);
    int recordCount = 10000;

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String phone = (String) generator.generate(random, phoneType);
        assertThat(phone).isNotEmpty();
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Simple PHONE_NUMBER generation: %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      assertThat(recordsPerSecond).as("Phone numbers per second").isGreaterThan(3000.0);
    }
  }

  @Test
  void shouldMeasureThroughputForUUIDGeneration() {
    PrimitiveType uuidType = new PrimitiveType(PrimitiveType.Kind.UUID, null, null);
    int recordCount = 10000;

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String uuid = (String) generator.generate(random, uuidType);
        assertThat(uuid).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Simple UUID generation: %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      assertThat(recordsPerSecond).as("UUIDs per second").isGreaterThan(3000.0);
    }
  }

  // ==================================================================================
  // MIXED TYPE GENERATION THROUGHPUT
  // ==================================================================================

  @Test
  void shouldMeasureThroughputForMixedTypeGeneration() {
    // Simulate generating a simple record with multiple fields
    PrimitiveType[] types = {
      new PrimitiveType(PrimitiveType.Kind.NAME, null, null),
      new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null),
      new PrimitiveType(PrimitiveType.Kind.PHONE_NUMBER, null, null),
      new PrimitiveType(PrimitiveType.Kind.ADDRESS, null, null),
      new PrimitiveType(PrimitiveType.Kind.COMPANY, null, null)
    };

    int recordCount = 5000; // 5 fields per record

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        for (DataType type : types) {
          String value = (String) generator.generate(random, type);
          assertThat(value).isNotEmpty();
        }
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Mixed type generation (5 fields/record): %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      // Should generate at least 1500 records per second (with 5 fields each)
      assertThat(recordsPerSecond).as("Mixed records per second").isGreaterThan(1500.0);
    }
  }

  // ==================================================================================
  // LARGE BATCH GENERATION
  // ==================================================================================

  @Test
  void shouldHandleLargeBatchGenerationEfficiently() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    int recordCount = 100000; // 100K records

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String name = (String) generator.generate(random, nameType);
        // Don't validate every record for performance
        if (i % 10000 == 0) {
          assertThat(name).isNotEmpty();
        }
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "Large batch NAME generation: %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      // Should maintain good throughput even for large batches
      assertThat(recordsPerSecond).as("Large batch throughput").isGreaterThan(3000.0);
    }
  }

  // ==================================================================================
  // MULTI-LOCALE GENERATION THROUGHPUT
  // ==================================================================================

  @Test
  void shouldMeasureThroughputAcrossMultipleLocales() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String[] locales = {"usa", "italy", "germany", "france", "japan", "brazil"};
    int recordsPerLocale = 1000;
    int totalRecords = locales.length * recordsPerLocale;

    Instant start = Instant.now();

    for (String locale : locales) {
      try (var ctx = GeneratorContext.enter(factory, locale)) {
        Random random = new Random(12345L);

        for (int i = 0; i < recordsPerLocale; i++) {
          String name = (String) generator.generate(random, nameType);
          assertThat(name).isNotEmpty();
        }
      }
    }

    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);
    double seconds = duration.toMillis() / 1000.0;
    double recordsPerSecond = totalRecords / seconds;

    System.out.printf(
        "Multi-locale generation (%d locales): %d records in %.3f seconds = %.0f records/sec%n",
        locales.length, totalRecords, seconds, recordsPerSecond);

    // Should maintain good throughput across locales
    assertThat(recordsPerSecond).as("Multi-locale throughput").isGreaterThan(2000.0);
  }

  // ==================================================================================
  // PERFORMANCE COMPARISON TESTS
  // ==================================================================================

  @Test
  void shouldComparePerformanceOfDifferentSemanticTypes() {
    PrimitiveType.Kind[] typesToTest = {
      PrimitiveType.Kind.NAME,
      PrimitiveType.Kind.EMAIL,
      PrimitiveType.Kind.PHONE_NUMBER,
      PrimitiveType.Kind.UUID,
      PrimitiveType.Kind.ADDRESS,
      PrimitiveType.Kind.COMPANY,
      PrimitiveType.Kind.URL,
      PrimitiveType.Kind.CREDIT_CARD
    };

    int recordCount = 5000;

    System.out.println("\n=== Performance Comparison (5000 records each) ===");

    for (PrimitiveType.Kind kind : typesToTest) {
      PrimitiveType type = new PrimitiveType(kind, null, null);

      try (var ctx = GeneratorContext.enter(factory, "usa")) {
        Random random = new Random(12345L);

        Instant start = Instant.now();

        for (int i = 0; i < recordCount; i++) {
          String value = (String) generator.generate(random, type);
          assertThat(value).isNotEmpty();
        }

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);
        double seconds = duration.toMillis() / 1000.0;
        double recordsPerSecond = recordCount / seconds;

        System.out.printf(
            "  %-15s: %.3f seconds = %7.0f records/sec%n", kind, seconds, recordsPerSecond);
      }
    }
  }

  // ==================================================================================
  // WARMUP AND STEADY STATE TESTS
  // ==================================================================================

  @Test
  void shouldShowPerformanceAfterWarmup() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      // Warmup phase
      Random warmupRandom = new Random(12345L);
      for (int i = 0; i < 1000; i++) {
        generator.generate(warmupRandom, nameType);
      }

      // Actual measurement after warmup
      Random random = new Random(12345L);
      int recordCount = 10000;

      Instant start = Instant.now();

      for (int i = 0; i < recordCount; i++) {
        String name = (String) generator.generate(random, nameType);
        assertThat(name).isNotEmpty();
      }

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);
      double seconds = duration.toMillis() / 1000.0;
      double recordsPerSecond = recordCount / seconds;

      System.out.printf(
          "NAME generation (after warmup): %d records in %.3f seconds = %.0f records/sec%n",
          recordCount, seconds, recordsPerSecond);

      // Performance should be good after warmup
      assertThat(recordsPerSecond).as("Warmed-up throughput").isGreaterThan(3000.0);
    }
  }
}
