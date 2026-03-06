package com.datagenerator.benchmarks;

import com.datagenerator.formats.csv.CsvSerializer;
import com.datagenerator.formats.json.JsonSerializer;
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
 * Benchmarks for format serializers (JSON and CSV). Measures serialization overhead and helps
 * identify bottlenecks in file I/O pipeline.
 *
 * <p><b>Goal:</b> Determine if 2.1 MB/sec file I/O bottleneck is due to serialization or actual
 * I/O operations
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

  private Map<String, Object> simpleRecord;
  private Map<String, Object> complexRecord;
  private Map<String, Object> nestedRecord;

  @Setup
  public void setup() {
    jsonSerializer = new JsonSerializer();
    csvSerializer = new CsvSerializer();

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
}
