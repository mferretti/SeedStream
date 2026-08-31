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
import java.nio.charset.StandardCharsets;
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

  // ── Per-chunk gzip mode (issue #210) ──────────────────────────────────────

  @Test
  void shouldGzipCoalescedChunkPerMember() throws Exception {
    // With compress_mode: per_chunk, coalesce should return a gzipped byte[] of the combined
    // payloads (each joined with a trailing '\n'), forming one complete gzip member.
    Path outputFile = tempDir.resolve("per_chunk.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("per_chunk").build();

    FileDestination destination = new FileDestination(config, new JsonSerializer());
    destination.open();

    byte[] p1 = "{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] p2 = "{\"b\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    byte[] member = destination.coalesce(List.of(p1, p2));

    // Decompress with GZIPInputStream — should equal "{\"a\":1}\n{\"b\":2}\n"
    java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(member);
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    try (GZIPInputStream gz = new GZIPInputStream(bais)) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = gz.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
    }

    byte[] decompressed = baos.toByteArray();
    String expected = "{\"a\":1}\n{\"b\":2}\n";
    assertThat(decompressed).isEqualTo(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    destination.close();
  }

  @Test
  void shouldWriteValidMultiMemberGzipFile() throws Exception {
    // Full flow: open per_chunk dest, write two coalesced chunks, close; read the .gz file
    // with a single GZIPInputStream (Java reads consecutive members transparently) and verify
    // all content is present in order.
    Path outputFile = tempDir.resolve("per_chunk_full.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("per_chunk").build();

    byte[] p1 = "{\"id\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] p2 = "{\"id\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] p3 = "{\"id\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] p4 = "{\"id\":4}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();

      byte[] chunk1 = destination.coalesce(List.of(p1, p2));
      // Each coalesced chunk must be an independent gzip member (magic 0x1f 0x8b) — this is what
      // distinguishes per_chunk from stream mode, where coalesce() returns raw concatenated bytes.
      assertThat(chunk1[0] & 0xff).isEqualTo(0x1f);
      assertThat(chunk1[1] & 0xff).isEqualTo(0x8b);
      destination.writeSerializedChunk(chunk1);

      byte[] chunk2 = destination.coalesce(List.of(p3, p4));
      assertThat(chunk2[0] & 0xff).isEqualTo(0x1f);
      assertThat(chunk2[1] & 0xff).isEqualTo(0x8b);
      destination.writeSerializedChunk(chunk2);
    }

    // File should have .gz extension
    Path gzFile = Path.of(outputFile.toString() + ".gz");
    assertThat(gzFile).exists();

    // Decompress and read all records
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new GZIPInputStream(Files.newInputStream(gzFile))))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }

    assertThat(lines).hasSize(4);
    assertThat(lines.get(0)).isEqualTo("{\"id\":1}");
    assertThat(lines.get(1)).isEqualTo("{\"id\":2}");
    assertThat(lines.get(2)).isEqualTo("{\"id\":3}");
    assertThat(lines.get(3)).isEqualTo("{\"id\":4}");
  }

  @Test
  void shouldWriteValidEmptyGzipWhenNoRecords() throws Exception {
    // open + close with zero writes should yield a valid empty .gz that GZIPInputStream
    // can read without exception.
    Path outputFile = tempDir.resolve("per_chunk_empty.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("per_chunk").build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      // No writes
    }

    Path gzFile = Path.of(outputFile.toString() + ".gz");
    assertThat(gzFile).exists();

    // Should be readable and yield zero bytes without exception
    int bytesRead = 0;
    try (GZIPInputStream gz = new GZIPInputStream(Files.newInputStream(gzFile))) {
      byte[] buffer = new byte[1024];
      bytesRead = gz.read(buffer);
    }

    assertThat(bytesRead).isEqualTo(-1); // EOF immediately
  }

  @Test
  void shouldFailFastWhenPerChunkWithoutCompress() {
    Path outputFile = tempDir.resolve("per_chunk_no_compress.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(false).compressMode("per_chunk").build();

    FileDestination destination = new FileDestination(config, new JsonSerializer());

    assertThatThrownBy(destination::open)
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("compress: true");
  }

  @Test
  void shouldFailFastWhenPerChunkWithCsv() {
    Path outputFile = tempDir.resolve("per_chunk_csv.csv");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("per_chunk").build();

    FileDestination destination = new FileDestination(config, new CsvSerializer());

    assertThatThrownBy(destination::open)
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("NDJSON");
  }

  @Test
  void shouldFailFastOnUnknownCompressMode() {
    Path outputFile = tempDir.resolve("per_chunk_unknown.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("zstd").build();

    FileDestination destination = new FileDestination(config, new JsonSerializer());

    assertThatThrownBy(destination::open)
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("compress_mode");
  }

  @Test
  void shouldRejectPlainWriteWhenPerChunkGzipEnabled() {
    // write() (the record-object path) is not supported once per_chunk gzip is active — the
    // engine must route through the serialized/coalesced write path instead.
    Path outputFile = tempDir.resolve("per_chunk_plain_write.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("per_chunk").build();

    FileDestination destination = new FileDestination(config, new JsonSerializer());
    destination.open();

    Map<String, Object> row = Map.of("id", 1);
    try {
      assertThatThrownBy(() -> destination.write(row))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining("serialized write path");
    } finally {
      destination.close();
    }
  }

  @Test
  void shouldGzipPayloadAsIndependentMemberWhenWriteSerializedCalledInPerChunkMode()
      throws Exception {
    // Defensive path (issue #210): the engine normally routes per_chunk through
    // coalesce()/writeSerializedChunk(), but writeSerialized() must still gzip-wrap a single
    // payload correctly if ever invoked directly.
    Path outputFile = tempDir.resolve("per_chunk_direct.json");
    FileDestinationConfig config =
        configBuilder.filePath(outputFile).compress(true).compressMode("per_chunk").build();

    try (FileDestination destination = new FileDestination(config, new JsonSerializer())) {
      destination.open();
      byte[] payload = "{\"id\":99}".getBytes(StandardCharsets.UTF_8);
      destination.writeSerialized(payload);
    }

    Path gzFile = Path.of(outputFile.toString() + ".gz");
    assertThat(gzFile).exists();

    List<String> lines = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new GZIPInputStream(Files.newInputStream(gzFile))))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }
    assertThat(lines).containsExactly("{\"id\":99}");
  }
}
