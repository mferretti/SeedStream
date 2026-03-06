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

package com.datagenerator.destinations.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.destinations.IntegrationTest;
import com.datagenerator.formats.csv.CsvSerializer;
import com.datagenerator.formats.json.JsonSerializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for FileDestination using temporary files.
 *
 * <p>Run with: ./gradlew :destinations:integrationTest
 */
class FileDestinationIT extends IntegrationTest {

  @TempDir Path tempDir;

  private FileDestination destination;
  private Path outputFile;

  @AfterEach
  void tearDown() {
    if (destination != null) {
      destination.close();
    }
  }

  @Test
  void shouldWriteJsonRecordsToFile() throws Exception {
    // Given: File destination with JSON serializer
    outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config =
        FileDestinationConfig.builder().filePath(outputFile).append(false).bufferSize(8192).build();

    destination = new FileDestination(config, new JsonSerializer());
    destination.open();

    // When: Write 3 records
    Map<String, Object> record1 = Map.of("id", 1, "name", "Alice");
    Map<String, Object> record2 = Map.of("id", 2, "name", "Bob");
    Map<String, Object> record3 = Map.of("id", 3, "name", "Charlie");

    destination.write(record1);
    destination.write(record2);
    destination.write(record3);
    destination.close();
    destination = null; // Prevent double-close in tearDown

    // Then: Verify file contents
    assertThat(outputFile).exists();
    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).contains("\"id\":1", "\"name\":\"Alice\"");
    assertThat(lines.get(1)).contains("\"id\":2", "\"name\":\"Bob\"");
    assertThat(lines.get(2)).contains("\"id\":3", "\"name\":\"Charlie\"");
  }

  @Test
  void shouldWriteCsvRecordsToFile() throws Exception {
    // Given: File destination with CSV serializer
    outputFile = tempDir.resolve("output.csv");
    FileDestinationConfig config =
        FileDestinationConfig.builder().filePath(outputFile).append(false).build();

    destination = new FileDestination(config, new CsvSerializer());
    destination.open();

    // When: Write records
    Map<String, Object> record1 = Map.of("id", 1, "name", "Alice", "age", 30);
    Map<String, Object> record2 = Map.of("id", 2, "name", "Bob", "age", 25);
    Map<String, Object> record3 = Map.of("id", 3, "name", "Charlie", "age", 35);

    destination.write(record1);
    destination.write(record2);
    destination.write(record3);
    destination.close();
    destination = null;

    // Then: Verify CSV file with header
    assertThat(outputFile).exists();
    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSizeGreaterThanOrEqualTo(4); // Header + 3 records

    // Verify header contains expected columns
    String header = lines.get(0);
    assertThat(header).containsAnyOf("id", "name", "age");

    // Verify data rows (CSV format with quotes)
    String allContent = String.join("\n", lines);
    assertThat(allContent).contains("Alice", "Bob", "Charlie");
  }

  @Test
  void shouldAppendToExistingFile() throws Exception {
    // Given: Existing file with content
    outputFile = tempDir.resolve("append.json");
    Files.writeString(outputFile, "{\"id\":0,\"name\":\"Existing\"}\n");

    FileDestinationConfig config =
        FileDestinationConfig.builder()
            .filePath(outputFile)
            .append(true) // Append mode
            .build();

    destination = new FileDestination(config, new JsonSerializer());
    destination.open();

    // When: Write additional records
    Map<String, Object> record1 = Map.of("id", 1, "name", "New1");
    Map<String, Object> record2 = Map.of("id", 2, "name", "New2");

    destination.write(record1);
    destination.write(record2);
    destination.close();
    destination = null;

    // Then: Verify file contains both old and new records
    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(3); // 1 existing + 2 new
    assertThat(lines.get(0)).contains("\"id\":0", "\"name\":\"Existing\"");
    assertThat(lines.get(1)).contains("\"id\":1", "\"name\":\"New1\"");
    assertThat(lines.get(2)).contains("\"id\":2", "\"name\":\"New2\"");
  }

  @Test
  void shouldCreateParentDirectories() throws Exception {
    // Given: Path with non-existent parent directories
    outputFile = tempDir.resolve("nested/deep/directory/output.json");
    assertThat(outputFile.getParent()).doesNotExist();

    FileDestinationConfig config =
        FileDestinationConfig.builder().filePath(outputFile).append(false).build();

    destination = new FileDestination(config, new JsonSerializer());

    // When: Open destination (should create directories)
    destination.open();
    destination.write(Map.of("id", 1));
    destination.close();
    destination = null;

    // Then: Parent directories created and file exists
    assertThat(outputFile.getParent()).exists();
    assertThat(outputFile).exists();
  }

  @Test
  void shouldHandleLargeNumberOfRecords() throws Exception {
    // Given: File destination with batching
    outputFile = tempDir.resolve("large.json");
    FileDestinationConfig config =
        FileDestinationConfig.builder()
            .filePath(outputFile)
            .append(false)
            .bufferSize(65536)
            .batchSize(1000)
            .build();

    destination = new FileDestination(config, new JsonSerializer());
    destination.open();

    // When: Write 10,000 records
    int recordCount = 10_000;
    for (int i = 0; i < recordCount; i++) {
      Map<String, Object> record = Map.of("id", i, "value", "Record-" + i);
      destination.write(record);
    }
    destination.close();
    destination = null;

    // Then: Verify all records written
    long lineCount = Files.lines(outputFile).count();
    assertThat(lineCount).isEqualTo(recordCount);
  }

  @Test
  void shouldHandleGzipCompression() throws Exception {
    // Given: File destination with gzip compression
    outputFile = tempDir.resolve("compressed.json.gz");
    FileDestinationConfig config =
        FileDestinationConfig.builder().filePath(outputFile).append(false).compress(true).build();

    destination = new FileDestination(config, new JsonSerializer());
    destination.open();

    // When: Write records
    for (int i = 0; i < 100; i++) {
      destination.write(Map.of("id", i, "data", "x".repeat(100))); // Compressible data
    }
    destination.close();
    destination = null;

    // Then: File exists and is compressed (smaller than uncompressed)
    assertThat(outputFile).exists();

    // Compressed file should be significantly smaller than raw JSON
    long compressedSize = Files.size(outputFile);
    long estimatedUncompressedSize = 100 * 120; // ~120 bytes per record
    assertThat(compressedSize).isLessThan(estimatedUncompressedSize / 2);
  }
}
