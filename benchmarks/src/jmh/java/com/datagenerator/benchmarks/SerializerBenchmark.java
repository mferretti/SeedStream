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

package com.datagenerator.benchmarks;

import com.datagenerator.formats.csv.CsvSerializer;
import com.datagenerator.formats.json.JsonSerializer;
import com.datagenerator.formats.protobuf.ProtobufSerializer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for format serializers (JSON, CSV, and Protobuf). Measures serialization overhead and
 * helps identify bottlenecks in file I/O pipeline.
 *
 * <p><b>Goal:</b> Determine serialization performance and size efficiency across formats
 *
 * <p><b>Scenarios:</b>
 *
 * <ul>
 *   <li>Simple flat record (primitives only)
 *   <li>Complex record with Datafaker fields
 *   <li>Nested structure with objects and arrays
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class SerializerBenchmark {

  private JsonSerializer jsonSerializer;
  private CsvSerializer csvSerializer;
  private ProtobufSerializer protobufSerializer;

  private Map<String, Object> simpleRecord;
  private Map<String, Object> complexRecord;
  private Map<String, Object> nestedRecord;

  /** A full chunk (default engine chunkSize) used by the chunk-serialization alloc comparison. */
  private static final int CHUNK_SIZE = 256;

  private List<Map<String, Object>> simpleChunk;
  // Reused across invocations, exactly as a worker reuses them across chunks.
  private java.io.ByteArrayOutputStream reusedChunkBuffer;
  private com.datagenerator.formats.FormatSerializer.StreamWriter reusedChunkWriter;

  @Setup
  public void setup() throws java.io.IOException {
    jsonSerializer = new JsonSerializer();
    csvSerializer = new CsvSerializer();
    protobufSerializer = new ProtobufSerializer();

    // Simple flat record with primitives only
    simpleRecord = new LinkedHashMap<>();
    simpleRecord.put("id", 12345);
    simpleRecord.put("name", "JohnDoe");
    simpleRecord.put("age", 35);
    simpleRecord.put("active", true);
    simpleRecord.put("balance", new BigDecimal("1234.56"));

    // Complex record with realistic Datafaker data
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
    complexRecord.put("balance", new BigDecimal("45678.90"));

    // Nested record with objects and arrays
    nestedRecord = new LinkedHashMap<>();
    nestedRecord.put("orderId", 99999);
    nestedRecord.put("customerName", "Alice Johnson");

    Map<String, Object> shippingAddress = new LinkedHashMap<>();
    shippingAddress.put("street", "456 Oak Avenue");
    shippingAddress.put("city", "Boston");
    shippingAddress.put("zip", "02101");
    nestedRecord.put("shippingAddress", shippingAddress);

    List<Map<String, Object>> items = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("productId", 1000 + i);
      item.put("name", "Product " + i);
      item.put("quantity", i + 1);
      item.put("price", new BigDecimal(String.valueOf(19.99 + i)));
      items.add(item);
    }
    nestedRecord.put("items", items);
    nestedRecord.put("total", new BigDecimal("299.95"));

    // One full chunk of simple records for the chunk-serialization alloc comparison below.
    simpleChunk = new ArrayList<>(CHUNK_SIZE);
    for (int i = 0; i < CHUNK_SIZE; i++) {
      simpleChunk.add(simpleRecord);
    }
    reusedChunkBuffer = new java.io.ByteArrayOutputStream(64 * 1024);
    reusedChunkWriter = jsonSerializer.createStreamWriter(reusedChunkBuffer);
  }

  // Chunk serialization: old per-record serializeToBytes + coalesce vs new one-pass serializeChunk.
  // Measure with the gc profiler (-prof gc) — the win is gc.alloc.rate.norm (B/op), not throughput.

  /**
   * OLD coalescing path: serialize each record to its own {@code byte[]}, then concatenate all of
   * them (with newline framing) into one combined {@code byte[]}. Allocates {@code chunkSize}
   * per-record arrays + the growing concatenation buffer + its final copy.
   */
  @Benchmark
  public byte[] benchmarkJsonChunkPerRecordCoalesce() {
    List<byte[]> payloads = new ArrayList<>(simpleChunk.size());
    int size = 0;
    for (Map<String, Object> record : simpleChunk) {
      byte[] bytes = jsonSerializer.serializeToBytes(record);
      payloads.add(bytes);
      size += bytes.length + 1;
    }
    byte[] combined = new byte[size];
    int pos = 0;
    for (byte[] payload : payloads) {
      System.arraycopy(payload, 0, combined, pos, payload.length);
      pos += payload.length;
      combined[pos++] = '\n';
    }
    return combined;
  }

  /**
   * NEW per-chunk path: one streaming pass over the chunk through a reused {@code StreamWriter} +
   * reused buffer — no per-record {@code byte[]}, no separate concatenation pass. Only the final
   * {@code toByteArray()} copy is allocated per chunk.
   */
  @Benchmark
  public byte[] benchmarkJsonChunkSerialize() throws java.io.IOException {
    for (Map<String, Object> record : simpleChunk) {
      reusedChunkWriter.writeRecord(record);
    }
    byte[] chunk = reusedChunkBuffer.toByteArray();
    reusedChunkBuffer.reset();
    return chunk;
  }

  // JSON Serialization Benchmarks

  @Benchmark
  public String benchmarkJsonSimpleRecord() {
    return jsonSerializer.serialize(simpleRecord);
  }

  @Benchmark
  public String benchmarkJsonComplexRecord() {
    return jsonSerializer.serialize(complexRecord);
  }

  @Benchmark
  public String benchmarkJsonNestedRecord() {
    return jsonSerializer.serialize(nestedRecord);
  }

  // CSV Serialization Benchmarks

  @Benchmark
  public String benchmarkCsvSimpleRecord() {
    return csvSerializer.serialize(simpleRecord);
  }

  @Benchmark
  public String benchmarkCsvComplexRecord() {
    return csvSerializer.serialize(complexRecord);
  }

  @Benchmark
  public String benchmarkCsvNestedRecord() {
    return csvSerializer.serialize(nestedRecord);
  }

  // Protobuf Serialization Benchmarks

  @Benchmark
  public String benchmarkProtobufSimpleRecord() {
    return protobufSerializer.serialize(simpleRecord);
  }

  @Benchmark
  public String benchmarkProtobufComplexRecord() {
    return protobufSerializer.serialize(complexRecord);
  }

  @Benchmark
  public String benchmarkProtobufNestedRecord() {
    return protobufSerializer.serialize(nestedRecord);
  }
}
