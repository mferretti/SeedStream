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

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * #440 end to end: {@code inspect} a DDL, then {@code execute} against what it wrote and check that
 * no generated value overflows the column it was inspected from.
 */
class InspectMaxLengthE2ETest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int COUNT = 2000;
  private static final Path PG_FIXTURE =
      Path.of("../inspector/src/test/resources/northwind/postgres/northwind-pg-dump.sql");

  @TempDir Path tempDir;

  @Test
  void shouldWriteMaxLengthRightAfterDatatypeAndLoadWithTheRealParser() throws Exception {
    Path structures = inspect(PG_FIXTURE);

    DataStructure customers = new DataStructureParser().parse(structures.resolve("customers.yaml"));
    customers.getData().values().forEach(f -> new TypeParser().parse(f.getDatatype()));
    List<String> lines = Files.readAllLines(structures.resolve("customers.yaml"));

    assertThat(customers.getData().get("city").getMaxLength()).isEqualTo(15);
    assertThat(customers.getData().get("country").getMaxLength()).isEqualTo(15);
    assertThat(customers.getData().get("address").getMaxLength()).isEqualTo(60);
    assertThat(customers.getData().get("city").isLengthInBytes()).isFalse();
    assertThat(lines.get(lines.indexOf("  city:") + 2)).isEqualTo("    max_length: 15");
  }

  @Test
  void shouldGenerateNoCustomerValueLongerThanItsColumn() throws Exception {
    Path structures = inspect(PG_FIXTURE);

    List<JsonNode> records = execute(structures, "customers");

    assertThat(records).hasSize(COUNT);
    assertWithinCaps(records, Map.of("city", 15, "country", 15, "address", 60));
  }

  @Test
  void shouldGenerateNoEmployeeValueLongerThanItsColumn() throws Exception {
    Path structures = inspect(PG_FIXTURE);

    List<JsonNode> records = execute(structures, "employees");

    assertThat(records).hasSize(COUNT);
    assertWithinCaps(records, Map.of("first_name", 10, "title", 30, "city", 15, "country", 15));
  }

  @Test
  void shouldCountOracleByteSemanticsColumnsInUtf8Bytes() throws Exception {
    Path sql = tempDir.resolve("oracle.sql");
    Files.writeString(
        sql,
        "CREATE TABLE places (id NUMBER(9,0) PRIMARY KEY, city VARCHAR2(15 BYTE),"
            + " country VARCHAR2(15 CHAR));\n");
    Path structures = inspect(sql);

    DataStructure places = new DataStructureParser().parse(structures.resolve("places.yaml"));
    List<JsonNode> records = execute(structures, "places");

    assertThat(places.getData().get("city").isLengthInBytes()).isTrue();
    assertThat(places.getData().get("country").isLengthInBytes()).isFalse();
    for (JsonNode r : records) {
      assertThat(r.get("city").asText().getBytes(StandardCharsets.UTF_8).length)
          .as("city %s in bytes", r.get("city"))
          .isLessThanOrEqualTo(15);
      assertThat(r.get("country").asText().codePointCount(0, r.get("country").asText().length()))
          .as("country %s in chars", r.get("country"))
          .isLessThanOrEqualTo(15);
    }
  }

  private static void assertWithinCaps(List<JsonNode> records, Map<String, Integer> caps) {
    caps.forEach(
        (field, cap) ->
            assertThat(records)
                .as("every %s fits %d chars", field, cap)
                .allSatisfy(
                    r -> {
                      JsonNode v = r.get(field);
                      // a nullable column may be null; a present value must fit
                      if (v != null && !v.isNull()) {
                        String text = v.asText();
                        assertThat(text.codePointCount(0, text.length()))
                            .as("%s=%s", field, text)
                            .isLessThanOrEqualTo(cap);
                      }
                    }));
  }

  private Path inspect(Path sql) {
    Path out = tempDir.resolve("structures");
    int code = new CommandLine(new InspectCommand()).execute(sql.toString(), "-o", out.toString());
    assertThat(code).as("inspect exit code").isZero();
    return out;
  }

  private List<JsonNode> execute(Path structures, String structure) throws IOException {
    Path outPrefix = tempDir.resolve("generated-" + structure);
    String job =
        String.join(
            "\n",
            "source: " + structure + ".yaml",
            "type: file",
            "structures_path: " + structures,
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + outPrefix,
            "  format: json",
            "");
    Path jobFile = tempDir.resolve("job-" + structure + ".yaml");
    Files.writeString(jobFile, job);

    int code =
        new CommandLine(new ExecuteCommand())
            .execute("--job", jobFile.toString(), "--count", Integer.toString(COUNT));
    assertThat(code).as("execute exit code for %s", structure).isZero();

    return Files.readAllLines(Path.of(outPrefix + ".json")).stream()
        .filter(l -> !l.isBlank())
        .map(InspectMaxLengthE2ETest::parse)
        .toList();
  }

  private static JsonNode parse(String line) {
    try {
      return JSON.readTree(line);
    } catch (IOException e) {
      throw new AssertionError("not JSON: " + line, e);
    }
  }
}
