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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** #492 end to end: {@code inspect} a DDL, then {@code execute} against what it wrote. */
class InspectNullableE2ETest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int COUNT = 2000;

  private static final String DDL =
      """
      CREATE TABLE person (
        id       BIGINT PRIMARY KEY,
        name     VARCHAR(40) NOT NULL,
        nickname VARCHAR(40),
        age      INT NOT NULL,
        score    INT,
        born     DATE
      );
      """;

  @TempDir Path tempDir;

  @Test
  void shouldWriteNullableRightAfterDatatypeOnlyForNullableNonKeyColumns() throws Exception {
    Path structures = inspect();

    List<String> lines = Files.readAllLines(structures.resolve("person.yaml"));

    assertThat(lines).filteredOn(l -> l.contains("nullable:")).hasSize(3);
    for (String field : List.of("nickname", "score", "born")) {
      int at = lines.indexOf("  " + field + ":");
      assertThat(at).as("field %s present", field).isGreaterThanOrEqualTo(0);
      assertThat(lines.get(at + 1)).startsWith("    datatype:");
      assertThat(lines.get(at + 2)).isEqualTo("    nullable: 0.05");
    }
    for (String field : List.of("id", "name", "age")) {
      int at = lines.indexOf("  " + field + ":");
      assertThat(at).as("field %s present", field).isGreaterThanOrEqualTo(0);
      assertThat(lines.get(at + 2)).as("no nullable under %s", field).doesNotContain("nullable");
    }
  }

  @Test
  void shouldLoadWithTheRealParser() throws Exception {
    Path structures = inspect();

    DataStructure loaded = new DataStructureParser().parse(structures.resolve("person.yaml"));
    loaded.getData().values().forEach(f -> new TypeParser().parse(f.getDatatype()));

    assertThat(loaded.getData().get("nickname").getNullable()).isEqualTo(0.05);
    assertThat(loaded.getData().get("name").getNullable()).isNull();
    assertThat(loaded.getData().get("id").getNullable()).isNull();
  }

  @Test
  void shouldGenerateNullsAtAboutFivePercentOnlyInNullableColumns() throws Exception {
    Path structures = inspect();

    List<JsonNode> records = execute(structures);

    assertThat(records).hasSize(COUNT);
    for (String nullable : List.of("nickname", "score", "born")) {
      long nulls = records.stream().filter(r -> r.get(nullable).isNull()).count();
      assertThat(nulls)
          .as("null count of %s over %d records", nullable, COUNT)
          .isBetween(60L, 140L);
    }
    for (String notNull : List.of("id", "name", "age")) {
      assertThat(records).as("no nulls in %s", notNull).noneMatch(r -> r.get(notNull).isNull());
    }
  }

  private Path inspect() throws IOException {
    Path sql = tempDir.resolve("schema.sql");
    Files.writeString(sql, DDL);
    Path out = tempDir.resolve("structures");
    int code = new CommandLine(new InspectCommand()).execute(sql.toString(), "-o", out.toString());
    assertThat(code).as("inspect exit code").isZero();
    return out;
  }

  private List<JsonNode> execute(Path structures) throws IOException {
    Path outPrefix = tempDir.resolve("generated");
    String job =
        String.join(
            "\n",
            "source: person.yaml",
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
            .execute("--job", jobFile.toString(), "--count", Integer.toString(COUNT));
    assertThat(code).as("execute exit code").isZero();

    return Files.readAllLines(Path.of(outPrefix + ".json")).stream()
        .filter(l -> !l.isBlank())
        .map(InspectNullableE2ETest::parse)
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
