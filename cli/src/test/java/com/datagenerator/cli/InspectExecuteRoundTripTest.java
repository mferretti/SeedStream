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

package com.datagenerator.cli;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * End-to-end round trip for the {@code inspect --nest} pipeline that {@code
 * docs/INSPECT-V1-SPEC.md} §9 calls for but the unit tests stop short of: inspect a DDL, write the
 * structure YAML, then actually {@code execute} a job against it and assert the generated output
 * has the expected embedded shape.
 *
 * <p>Unlike the {@code *IT} tests this needs no Testcontainers — it is pure in-process file I/O —
 * so it lives in the default {@code test} task (untagged) and runs in CI, not in {@code
 * integrationTest}.
 */
class InspectExecuteRoundTripTest {

  @TempDir Path tempDir;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** customer -(1:n)-> invoice -(1:n)-> invoice_item: the canonical nesting chain from §5. */
  private static final String CHAIN =
      """
      CREATE TABLE customer (
        id    BIGINT PRIMARY KEY,
        name  VARCHAR(120),
        email VARCHAR(255)
      );
      CREATE TABLE invoice (
        id          BIGINT PRIMARY KEY,
        customer_id BIGINT,
        total       DECIMAL(10,2),
        CONSTRAINT fk_cust FOREIGN KEY (customer_id) REFERENCES customer(id)
      );
      CREATE TABLE invoice_item (
        id          BIGINT PRIMARY KEY,
        invoice_id  BIGINT,
        sku         VARCHAR(40),
        quantity    INT,
        CONSTRAINT fk_inv FOREIGN KEY (invoice_id) REFERENCES invoice(id)
      );
      """;

  /**
   * A &lt;-&gt; B two-node cycle (§6): auto breaks it at the back-edge — one FK stays a flat {@code
   * ref}, the other inverts — and the embedded child becomes a leaf, so generation must terminate.
   */
  private static final String CYCLE =
      """
      CREATE TABLE a (id BIGINT PRIMARY KEY, b_id BIGINT,
        CONSTRAINT fk_b FOREIGN KEY (b_id) REFERENCES b(id));
      CREATE TABLE b (id BIGINT PRIMARY KEY, a_id BIGINT,
        CONSTRAINT fk_a FOREIGN KEY (a_id) REFERENCES a(id));
      """;

  @Test
  void nestedChainRoundTripsToEmbeddedJson() throws Exception {
    Path structures = inspect(CHAIN, "--nest");

    // The emitted parent carries the inverted nested arrays (sanity check before generating).
    assertThat(Files.readString(structures.resolve("customer.yaml")))
        .contains("array[object[invoice], 1..10]");

    List<JsonNode> records = execute(structures, "customer", 5);

    assertThat(records).hasSize(5);
    for (JsonNode customer : records) {
      assertThat(customer.has("invoices")).as("customer embeds invoices").isTrue();
      JsonNode invoices = customer.get("invoices");
      assertThat(invoices.isArray()).isTrue();
      assertThat(invoices).as("array[..., 1..10] yields at least one invoice").isNotEmpty();

      for (JsonNode invoice : invoices) {
        // FK column was dropped from the embedded copy (§5.7).
        assertThat(invoice.has("customer_id")).as("embedded FK dropped").isFalse();
        assertThat(invoice.has("invoice_items")).as("invoice embeds items").isTrue();
        JsonNode items = invoice.get("invoice_items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).isNotEmpty();
        for (JsonNode item : items) {
          assertThat(item.has("invoice_id")).as("embedded FK dropped").isFalse();
          assertThat(item.has("sku")).isTrue();
        }
      }
    }
  }

  @Test
  void cyclicSchemaStaysFlatAndGeneratesWithoutInfiniteRecursion() throws Exception {
    Path structures = inspect(CYCLE, "--nest");

    // Cycle broken on the a→b edge: that FK survives as a flat scalar ref with an ID-pool range.
    assertThat(Files.readString(structures.resolve("a.yaml"))).contains("ref[b.id, 1..count]");

    // The real assertion: generation completes (no CircularReferenceException / stack overflow).
    List<JsonNode> records = execute(structures, "a", 3);

    assertThat(records).hasSize(3).allSatisfy(a -> assertThat(a.has("b_id")).isTrue());
  }

  /** Runs {@code inspect} on the given DDL and returns the structures output directory. */
  private Path inspect(String ddl, String... extraArgs) throws Exception {
    Path sql = tempDir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    Path out = tempDir.resolve("structures");

    String[] args =
        Stream.concat(Stream.of(sql.toString(), "-o", out.toString()), Stream.of(extraArgs))
            .toArray(String[]::new);
    int code = new CommandLine(new InspectCommand()).execute(args);
    assertThat(code).as("inspect exit code").isZero();
    return out;
  }

  /**
   * Writes a file/json job for {@code source}, runs {@code execute}, parses the emitted records.
   */
  private List<JsonNode> execute(Path structures, String source, int count) throws Exception {
    Path outPrefix = tempDir.resolve("generated");
    // Assembled line-by-line (not a format string) so SpotBugs' VA_FORMAT_STRING_USES_NEWLINE does
    // not fire — YAML needs literal '\n', not the platform separator '%n'.
    String job =
        String.join(
            "\n",
            "source: " + source + ".yaml",
            "type: file",
            "structures_path: " + structures,
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + outPrefix,
            "  format: json",
            "");
    Path jobFile = tempDir.resolve("job.yaml");
    Files.writeString(jobFile, job);

    int code =
        new CommandLine(new ExecuteCommand())
            .execute("--job", jobFile.toString(), "--count", Integer.toString(count));
    assertThat(code).as("execute exit code (non-zero ⇒ generation failed)").isZero();

    Path output = locateOutput(outPrefix);
    try (Stream<String> lines = Files.lines(output)) {
      return lines.filter(l -> !l.isBlank()).map(this::parse).toList();
    }
  }

  /** The file destination appends a format extension to the configured path prefix. */
  private Path locateOutput(Path prefix) throws Exception {
    Path prefixName = Objects.requireNonNull(prefix.getFileName(), "prefix has no file name");
    Path dir = Objects.requireNonNull(prefix.getParent(), "prefix has no parent");
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> fileName(p).startsWith(prefixName.toString()))
          .filter(p -> fileName(p).endsWith(".json"))
          .findFirst()
          .orElseThrow(() -> new AssertionError("no generated .json under " + prefix));
    }
  }

  private static String fileName(Path p) {
    Path name = p.getFileName();
    return name == null ? "" : name.toString();
  }

  private JsonNode parse(String line) {
    try {
      return MAPPER.readTree(line);
    } catch (Exception e) {
      throw new AssertionError("generated line is not valid JSON: " + line, e);
    }
  }
}
