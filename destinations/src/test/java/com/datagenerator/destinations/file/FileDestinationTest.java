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
import com.datagenerator.formats.avro.AvroSerializer;
import com.datagenerator.formats.csv.CsvSerializer;
import com.datagenerator.formats.json.JsonSerializer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDestinationTest {
  private static final String ALICE = "Alice";
  private static final String OUTPUT_JSON = "output.json";

  @TempDir Path tempDir;

  private FileDestinationConfig.FileDestinationConfigBuilder configBuilder;

  @BeforeEach
  void setUp() {
    configBuilder = FileDestinationConfig.builder();
  }

  @Test
  void shouldWriteJsonRecordsToFile() throws Exception {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
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
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
    FileDestinationConfig config = configBuilder.filePath(outputFile).compress(true).build();

    Map<String, Object> record1 = Map.of("name", "Alice", "age", 30);
    Map<String, Object> record2 = Map.of("name", "Bob", "age", 25);
    Map<String, Object> record3 = Map.of("name", "Carol", "age", 28);

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.write(record1);
      destination.write(record2);
      destination.write(record3);
    }

    // File should have .gz extension
    Path gzFile = Path.of(outputFile.toString() + ".gz");
    assertThat(gzFile).exists();
    assertThat(Files.size(gzFile)).isGreaterThan(0);

    // Decompress and verify all records round-trip
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new GZIPInputStream(Files.newInputStream(gzFile))))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }
    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).contains("Alice").contains("\"age\":30");
    assertThat(lines.get(1)).contains("Bob").contains("\"age\":25");
    assertThat(lines.get(2)).contains("Carol").contains("\"age\":28");
  }

  @Test
  void shouldAppendToExistingFile() throws Exception {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);

    // Write first data
    FileDestinationConfig config1 = configBuilder.filePath(outputFile).build();
    try (FileDestination destination = new FileDestination(config1, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "John"));
    }

    // Append second data
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
    Path outputFile = tempDir.resolve(OUTPUT_JSON);

    // Write first data
    FileDestinationConfig config1 = configBuilder.filePath(outputFile).build();
    try (FileDestination destination = new FileDestination(config1, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "John"));
    }

    // Overwrite with second data
    FileDestinationConfig config2 = configBuilder.filePath(outputFile).build();
    try (FileDestination destination = new FileDestination(config2, new JsonSerializer())) {
      destination.open();
      destination.write(Map.of("name", "Jane"));
    }

    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(1); // Only second data
    assertThat(lines.get(0)).contains("Jane");
  }

  @Test
  void shouldCreateParentDirectories() {
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
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
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
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    Map<String, Object> payload = Map.of("name", "John");
    assertThatThrownBy(() -> destination.write(payload))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("not open");
  }

  @Test
  void shouldHandleEmptyRecords() throws Exception {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
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
  void shouldWriteAvroContainerFileReadableByDataFileReader() throws Exception {
    Path outputFile = tempDir.resolve("output.avro");
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    Map<String, Object> record1 = new LinkedHashMap<>();
    record1.put("name", ALICE);
    record1.put("age", 30);

    Map<String, Object> record2 = new LinkedHashMap<>();
    record2.put("name", "Bob");
    record2.put("age", 25);

    try (FileDestination destination = new FileDestination(config, new AvroSerializer())) {
      destination.open();
      destination.write(record1);
      destination.write(record2);
    }

    assertThat(outputFile).exists();
    List<GenericRecord> records = new ArrayList<>();
    try (DataFileReader<GenericRecord> reader =
        new DataFileReader<>(outputFile.toFile(), new GenericDatumReader<>())) {
      reader.forEach(records::add);
    }
    assertThat(records).hasSize(2);
    assertThat(records.get(0).get("name")).hasToString(ALICE);
    assertThat(records.get(0).get("age")).isEqualTo(30);
    assertThat(records.get(1).get("name")).hasToString("Bob");
    assertThat(records.get(1).get("age")).isEqualTo(25);
  }

  @Test
  void shouldWriteAvroWithDeflateWhenCompressEnabled() throws Exception {
    Path outputFile = tempDir.resolve("output.avro");
    FileDestinationConfig config = configBuilder.filePath(outputFile).compress(true).build();

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", ALICE);
    data.put("age", 30);

    try (FileDestination destination = new FileDestination(config, new AvroSerializer())) {
      destination.open();
      destination.write(data);
    }

    // File readable by DataFileReader — codec handled internally
    assertThat(outputFile).exists();
    List<GenericRecord> records = new ArrayList<>();
    try (DataFileReader<GenericRecord> reader =
        new DataFileReader<>(outputFile.toFile(), new GenericDatumReader<>())) {
      reader.forEach(records::add);
    }
    assertThat(records).hasSize(1);
    assertThat(records.get(0).get("name")).hasToString(ALICE);
  }

  @Test
  void shouldReturnCorrectDestinationType() {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    assertThat(destination.getDestinationType()).isEqualTo("file");
  }

  @Test
  void shouldHandleMultipleOpenCallsGracefully() {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      destination.open(); // Second open should be no-op
      destination.write(Map.of("name", "John"));
    }

    assertThat(outputFile).exists();
  }

  @Test
  void shouldHandleMultipleCloseCallsGracefully() {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    FileDestination destination = new FileDestination(config, new JsonSerializer());
    destination.open();
    destination.write(Map.of("name", "John"));
    destination.close();
    destination.close(); // Second close should be no-op

    assertThat(outputFile).exists();
  }

  // ── Write coalescing (issue #193) ────────────────────────────────────────────

  @Test
  void shouldReportWriteCoalescingSupportForJson() {
    FileDestinationConfig config = configBuilder.filePath(tempDir.resolve(OUTPUT_JSON)).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    assertThat(destination.supportsSerializedWrite()).isTrue();
    assertThat(destination.supportsWriteCoalescing()).isTrue();
  }

  @Test
  void shouldNotReportWriteCoalescingSupportForCsvOrAvro() {
    FileDestination csvDestination =
        new FileDestination(
            configBuilder.filePath(tempDir.resolve("output.csv")).build(), new CsvSerializer());
    FileDestination avroDestination =
        new FileDestination(
            configBuilder.filePath(tempDir.resolve("output.avro")).build(), new AvroSerializer());

    assertThat(csvDestination.supportsWriteCoalescing()).isFalse();
    assertThat(avroDestination.supportsWriteCoalescing()).isFalse();
  }

  @Test
  void shouldCoalescePayloadsWithTrailingNewlinePerRecord() {
    FileDestinationConfig config = configBuilder.filePath(tempDir.resolve(OUTPUT_JSON)).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    byte[] p1 = "{\"id\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] p2 = "{\"id\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] p3 = "{\"id\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    byte[] coalesced = destination.coalesce(List.of(p1, p2, p3));

    // Must be byte-identical to what the OLD per-record path produced: payload + '\n', in order,
    // with no extra/missing bytes — i.e. exactly outputStream.write(p); outputStream.write('\n')
    // for each payload, concatenated.
    byte[] expected =
        (new String(p1, java.nio.charset.StandardCharsets.UTF_8)
                + "\n"
                + new String(p2, java.nio.charset.StandardCharsets.UTF_8)
                + "\n"
                + new String(p3, java.nio.charset.StandardCharsets.UTF_8)
                + "\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(coalesced).isEqualTo(expected);
  }

  @Test
  void shouldCoalesceSinglePayloadChunk() {
    FileDestinationConfig config = configBuilder.filePath(tempDir.resolve(OUTPUT_JSON)).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    byte[] payload = "{\"id\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] coalesced = destination.coalesce(List.of(payload));

    assertThat(coalesced)
        .isEqualTo(("{\"id\":42}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void shouldWriteCoalescedChunkRawWithoutAddingExtraFraming() throws Exception {
    Path outputFile = tempDir.resolve(OUTPUT_JSON);
    FileDestinationConfig config = configBuilder.filePath(outputFile).build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();

      byte[] p1 = "{\"id\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      byte[] p2 = "{\"id\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      byte[] coalesced = destination.coalesce(List.of(p1, p2));

      destination.writeSerializedChunk(coalesced);
      destination.flush();
    }

    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).containsExactly("{\"id\":1}", "{\"id\":2}");
  }

  @Test
  void writeSerializedChunkThrowsWhenNotOpen() {
    FileDestinationConfig config = configBuilder.filePath(tempDir.resolve(OUTPUT_JSON)).build();
    FileDestination destination = new FileDestination(config, new JsonSerializer());

    byte[] coalesced = "{}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThatThrownBy(() -> destination.writeSerializedChunk(coalesced))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("not open");
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
