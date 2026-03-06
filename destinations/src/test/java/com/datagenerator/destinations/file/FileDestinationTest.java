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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.destinations.DestinationException;
import com.datagenerator.formats.csv.CsvSerializer;
import com.datagenerator.formats.json.JsonSerializer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDestinationTest {
  @TempDir Path tempDir;

  private FileDestinationConfig.FileDestinationConfigBuilder configBuilder;

  @BeforeEach
  void setUp() {
    configBuilder = FileDestinationConfig.builder();
  }

  @Test
  void shouldWriteJsonRecordsToFile() throws Exception {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    Map<String, Object> record1 = Map.of("name", "John", "age", 42);
    Map<String, Object> record2 = Map.of("name", "Jane", "age", 35);

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.write(record1);
      destination.write(record2);
      destination.flush();
    }

    assertThat(outputFile).exists();
    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).contains("John");
    assertThat(lines.get(1)).contains("Jane");
  }

  @Test
  void shouldWriteCsvRecordsWithHeader() throws Exception {
    Path outputFile = tempDir.resolve("output.csv");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    Map<String, Object> record1 = new LinkedHashMap<>();
    record1.put("name", "John");
    record1.put("age", 42);

    Map<String, Object> record2 = new LinkedHashMap<>();
    record2.put("name", "Jane");
    record2.put("age", 35);

    try (FileDestination destination = new FileDestination(config, new CsvSerializer())) {
      destination.open();
      destination.write(record1);
      destination.write(record2);
    }

    assertThat(outputFile).exists();
    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(3); // Header + 2 records
    assertThat(lines.get(0)).isEqualTo("\"name\",\"age\""); // Header
    assertThat(lines.get(1)).isEqualTo("\"John\",\"42\"");
    assertThat(lines.get(2)).isEqualTo("\"Jane\",\"35\"");
  }

  @Test
  void shouldCompressOutputWithGzip() throws Exception {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).compress(true).build();

    Map<String, Object> record = Map.of("name", "John", "age", 42);

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.write(record);
    }

    // File should have .gz extension
    Path gzFile = Path.of(outputFile.toString() + ".gz");
    assertThat(gzFile).exists();

    // Decompress and verify content
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new GZIPInputStream(Files.newInputStream(gzFile))))) {
      String line = reader.readLine();
      assertThat(line).contains("John");
    }
  }

  @Test
  void shouldAppendToExistingFile() throws Exception {
    Path outputFile = tempDir.resolve("output.json");

    // Write first record
    FileDestinationConfig config1 = configBuilder.filePath(outputFile).build();
    try (FileDestination destination = new FileDestination(config1, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "John"));
    }

    // Append second record
    FileDestinationConfig config2 = configBuilder.filePath(outputFile).append(true).build();
    try (FileDestination destination = new FileDestination(config2, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "Jane"));
    }

    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).contains("John");
    assertThat(lines.get(1)).contains("Jane");
  }

  @Test
  void shouldOverwriteFileByDefault() throws Exception {
    Path outputFile = tempDir.resolve("output.json");

    // Write first record
    FileDestinationConfig config1 = configBuilder.filePath(outputFile).build();
    try (FileDestination destination = new FileDestination(config1, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "John"));
    }

    // Overwrite with second record
    FileDestinationConfig config2 = configBuilder.filePath(outputFile).build();
    try (FileDestination destination = new FileDestination(config2, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "Jane"));
    }

    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(1); // Only second record
    assertThat(lines.get(0)).contains("Jane");
  }

  @Test
  void shouldCreateParentDirectories() throws Exception {
    Path outputFile = tempDir.resolve("nested/dir/output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "John"));
    }

    assertThat(outputFile).exists();
    assertThat(outputFile.getParent()).exists();
  }

  @Test
  void shouldFlushWrites() throws Exception {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "John"));
      destination.flush();

      // File should exist after flush (even before close)
      assertThat(outputFile).exists();
      assertThat(Files.size(outputFile)).isGreaterThan(0);
    }
  }

  @Test
  void shouldThrowExceptionWhenWritingBeforeOpen() {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    assertThatThrownBy(() -> destination.write(Map.of("name", "John")))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("not open");
  }

  @Test
  void shouldHandleEmptyRecords() throws Exception {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of());
    }

    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).isEqualTo("{}");
  }

  @Test
  void shouldReturnCorrectDestinationType() {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    assertThat(destination.getDestinationType()).isEqualTo("file");
  }

  @Test
  void shouldHandleMultipleOpenCallsGracefully() throws Exception {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.open(); // Second open should be no-op
      destination.write(Map.of("name", "John"));
    }

    assertThat(outputFile).exists();
  }

  @Test
  void shouldHandleMultipleCloseCallsGracefully() throws Exception {
    Path outputFile = tempDir.resolve("output.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    FileDestination destination = new FileDestination(config, new JsonSerializer());
    destination.open();
    destination.write(Map.of("name", "John"));
    destination.close();
    destination.close(); // Second close should be no-op

    assertThat(outputFile).exists();
  }

  @Test
  void shouldWriteLargeNumberOfRecords() throws Exception {
    Path outputFile = tempDir.resolve("large.json");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();

      for (int i = 0; i < 10000; i++) {
        destination.write(Map.of("id", i, "name", "User" + i));
      }
    }

    assertThat(outputFile).exists();
    long lineCount = Files.lines(outputFile).count();
    assertThat(lineCount).isEqualTo(10000);
  }
}
