package com.datagenerator.benchmarks;

import com.datagenerator.destinations.file.FileDestination;
import com.datagenerator.destinations.file.FileDestinationConfig;
import com.datagenerator.formats.json.JsonSerializer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for destination adapters. Measures optimized file I/O throughput with batching and
 * large buffers.
 *
 * <p><b>Goal:</b> Validate optimizations achieve 500+ MB/s file writes
 *
 * <p><b>Scenarios:</b>
 *
 * <ul>
 *   <li>Raw file writes (baseline ceiling for I/O)
 *   <li>JSON serialization + file write with batching (end-to-end pipeline)
 * </ul>
 *
 * <p><b>Optimizations Applied:</b>
 *
 * <ul>
 *   <li>64KB buffer (up from 8KB)
 *   <li>1000-record batching
 *   <li>Single write call per newline
 * </ul>
 *
 * <p><b>Note:</b> Kafka benchmarks require running Kafka instance and are excluded for now
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class DestinationBenchmark {

  private Path tempFile;
  private BufferedWriter rawWriter;
  private FileDestination fileDestination;
  private JsonSerializer jsonSerializer;

  private Map<String, Object> complexRecord;
  private String preSerializedJson;

  @Setup(Level.Iteration)
  public void setup() throws IOException {
    // Create temp file for benchmarks
    tempFile = Files.createTempFile("benchmark-", ".json");

    // Complex record for realistic testing
    complexRecord = new LinkedHashMap<>();
    complexRecord.put("id", 67890);
    complexRecord.put("name", "Jane Smith");
    complexRecord.put("email", "jane.smith@example.com");
    complexRecord.put("phone", "+1-555-123-4567");
    complexRecord.put("address", "123 Main Street, Apartment 4B");
    complexRecord.put("city", "New York");
    complexRecord.put("company", "Tech Solutions Inc.");
    complexRecord.put("birthDate", LocalDate.of(1990, 5, 15));
    complexRecord.put("createdAt", Instant.parse("2024-03-15T10:30:00Z"));

    // Pre-serialize for raw write benchmark
    jsonSerializer = new JsonSerializer();
    preSerializedJson = jsonSerializer.serialize(complexRecord) + "\n";

    // Setup raw writer
    rawWriter =
        Files.newBufferedWriter(
            tempFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

    // Setup FileDestination with optimized defaults (64KB buffer, 1000 batch size)
    FileDestinationConfig config =
        FileDestinationConfig.builder()
            .filePath(tempFile)
            .bufferSize(65536)
            .batchSize(1000)
            .compress(false)
            .build();

    fileDestination = new FileDestination(config, jsonSerializer);
    fileDestination.open();
  }

  @TearDown(Level.Iteration)
  public void tearDown() throws IOException {
    if (rawWriter != null) {
      rawWriter.close();
    }
    if (fileDestination != null) {
      fileDestination.close();
    }
    Files.deleteIfExists(tempFile);
  }

  /**
   * Benchmark: Raw string write to file (baseline - measures pure I/O throughput without
   * serialization overhead)
   */
  @Benchmark
  public void benchmarkRawFileWrite() throws IOException {
    rawWriter.write(preSerializedJson);
  }

  /**
   * Benchmark: Serialize + write using FileDestination (measures end-to-end pipeline including
   * serialization)
   */
  @Benchmark
  public void benchmarkFileDestinationWrite() {
    fileDestination.write(complexRecord);
  }
}
