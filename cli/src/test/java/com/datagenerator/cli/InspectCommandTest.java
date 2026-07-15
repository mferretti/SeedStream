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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InspectCommandTest {

  @TempDir Path tempDir;

  private static final String CHAIN =
      """
      CREATE TABLE customer (
        id   BIGINT PRIMARY KEY,
        name VARCHAR(120)
      );
      CREATE TABLE invoice (
        id          BIGINT PRIMARY KEY,
        customer_id BIGINT,
        CONSTRAINT fk_cust FOREIGN KEY (customer_id) REFERENCES customer(id)
      );
      """;

  private static final String GOOD_AND_BAD =
      """
      CREATE TABLE customer (
        id   BIGINT PRIMARY KEY,
        name VARCHAR(120)
      );
      CREATE TABLE bad (id BIGINT, , );
      """;

  private static final String CYCLE =
      """
      CREATE TABLE a (id BIGINT PRIMARY KEY, b_id BIGINT,
        CONSTRAINT fk_b FOREIGN KEY (b_id) REFERENCES b(id));
      CREATE TABLE b (id BIGINT PRIMARY KEY, a_id BIGINT,
        CONSTRAINT fk_a FOREIGN KEY (a_id) REFERENCES a(id));
      """;

  private static final String MINIMAL_OPENAPI =
      """
      openapi: 3.0.3
      info: {title: t, version: "1"}
      components:
        schemas:
          Thing:
            type: object
            properties:
              id: {type: string, format: uuid}
      """;

  // JSON kept as a concatenated string: google-java-format mangles a text block starting with '{'.
  private static final String JSON_SCHEMA =
      "{ \"title\": \"Widget\", \"type\": \"object\","
          + " \"properties\": {"
          + "   \"code\": { \"type\": \"string\", \"pattern\": \"^[A-Z]{3}$\" },"
          + "   \"name\": { \"type\": \"string\" } } }";

  private static final String YAML_SCHEMA =
      """
      $schema: "https://json-schema.org/draft/2020-12/schema"
      title: Gadget
      type: object
      properties:
        id: {type: string, format: uuid}
      """;

  private int run(String... args) {
    return new CommandLine(new InspectCommand()).execute(args);
  }

  private Path write(String name, String content) throws Exception {
    Path file = tempDir.resolve(name);
    Files.writeString(file, content);
    return file;
  }

  private Path outDir() {
    return tempDir.resolve("out");
  }

  @Test
  void plainDdlInspectSucceeds() throws Exception {
    Path sql = write("schema.sql", CHAIN);
    assertThat(run(sql.toString(), "-o", outDir().toString())).isZero();
    assertThat(outDir().resolve("invoice.yaml")).exists();
  }

  @Test
  void malformedDdlFailsStrictByDefault() throws Exception {
    Path sql = write("schema.sql", GOOD_AND_BAD);
    assertThat(run(sql.toString(), "-o", outDir().toString())).isEqualTo(2);
    // Strict failure writes nothing — not even the parseable table.
    assertThat(outDir().resolve("customer.yaml")).doesNotExist();
  }

  @Test
  void malformedDdlSucceedsWithBestEffort() throws Exception {
    Path sql = write("schema.sql", GOOD_AND_BAD);
    assertThat(run(sql.toString(), "-o", outDir().toString(), "--best-effort")).isZero();
    assertThat(outDir().resolve("customer.yaml")).exists();
  }

  @Test
  void nestEmitsNestedArrayOnParent() throws Exception {
    Path sql = write("schema.sql", CHAIN);
    assertThat(run(sql.toString(), "-o", outDir().toString(), "--nest")).isZero();
    String customer = Files.readString(outDir().resolve("customer.yaml"));
    assertThat(customer).contains("array[object[invoice], 1..10]");
  }

  @Test
  void nestDefaultCountIsHonored() throws Exception {
    Path sql = write("schema.sql", CHAIN);
    assertThat(
            run(
                sql.toString(),
                "-o",
                outDir().toString(),
                "--nest=auto",
                "--nest-default-count",
                "3..7"))
        .isZero();
    assertThat(Files.readString(outDir().resolve("customer.yaml")))
        .contains("array[object[invoice], 3..7]");
  }

  @Test
  void nestAllErrorsOnCycle() throws Exception {
    Path sql = write("cycle.sql", CYCLE);
    assertThat(run(sql.toString(), "-o", outDir().toString(), "--nest=all")).isEqualTo(2);
  }

  @Test
  void invalidNestModeReturnsTwo() throws Exception {
    Path sql = write("schema.sql", CHAIN);
    assertThat(run(sql.toString(), "-o", outDir().toString(), "--nest=sideways")).isEqualTo(2);
  }

  @Test
  void invalidNestDefaultCountReturnsTwo() throws Exception {
    Path sql = write("schema.sql", CHAIN);
    assertThat(
            run(
                sql.toString(),
                "-o",
                outDir().toString(),
                "--nest",
                "--nest-default-count",
                "lots"))
        .isEqualTo(2);
  }

  @Test
  void nestIgnoredForOpenApiButStillSucceeds() throws Exception {
    Path spec = write("api.yaml", MINIMAL_OPENAPI);
    assertThat(run(spec.toString(), "-o", outDir().toString(), "--nest")).isZero();
    assertThat(outDir().resolve("thing.yaml")).exists();
  }

  @Test
  void jsonSchemaByExtensionAutoDetectedAndWritesFakerTypes() throws Exception {
    Path schema = write("payload.schema.json", JSON_SCHEMA);
    assertThat(run(schema.toString(), "-o", outDir().toString())).isZero();
    assertThat(outDir().resolve("widget.yaml")).exists();
    // the regex `pattern` field emits a companion faker-types file the user can feed back
    assertThat(outDir().resolve("inspect-faker-types.yaml")).exists();
  }

  @Test
  void jsonSchemaOnYamlDetectedByContentPeek() throws Exception {
    // .yaml is ambiguous — the $schema root key disambiguates it as JSON Schema, not OpenAPI.
    Path schema = write("schema.yaml", YAML_SCHEMA);
    assertThat(run(schema.toString(), "-o", outDir().toString())).isZero();
    assertThat(outDir().resolve("gadget.yaml")).exists();
  }

  @Test
  void jsonSchemaExplicitFormatOverride() throws Exception {
    Path schema =
        write(
            "plain.json",
            "{ \"type\": \"object\", \"properties\": { \"x\": { \"type\": \"string\" } } }");
    assertThat(run(schema.toString(), "-o", outDir().toString(), "--format", "jsonschema"))
        .isZero();
    assertThat(outDir().resolve("plain.yaml")).exists();
  }

  @Test
  void nestIgnoredForJsonSchemaButStillSucceeds() throws Exception {
    Path schema = write("payload.schema.json", JSON_SCHEMA);
    assertThat(run(schema.toString(), "-o", outDir().toString(), "--nest")).isZero();
    assertThat(outDir().resolve("widget.yaml")).exists();
  }

  @Test
  void companionFakerTypesNotClobberedOnRerun() throws Exception {
    Path schema = write("payload.schema.json", JSON_SCHEMA);
    assertThat(run(schema.toString(), "-o", outDir().toString())).isZero();
    Path companion = outDir().resolve("inspect-faker-types.yaml");
    String firstContent = Files.readString(companion);

    // rerun without --force: the companion exists → skipped, left byte-for-byte intact.
    assertThat(run(schema.toString(), "-o", outDir().toString())).isZero();
    assertThat(Files.readString(companion)).isEqualTo(firstContent);
  }

  @Test
  void companionNotWrittenOverFakerTypesInput() throws Exception {
    Path schema = write("payload.schema.json", JSON_SCHEMA);
    assertThat(run(schema.toString(), "-o", outDir().toString())).isZero();
    Path companion = outDir().resolve("inspect-faker-types.yaml");
    String before = Files.readString(companion);

    // point --faker-types at the companion itself: must refuse to overwrite its own input,
    // even with --force.
    assertThat(
            run(
                schema.toString(),
                "-o",
                outDir().toString(),
                "--force",
                "--faker-types",
                companion.toString()))
        .isZero();
    assertThat(Files.readString(companion)).isEqualTo(before);
  }

  @Test
  void invalidFormatOverrideReturnsTwo() throws Exception {
    Path schema = write("payload.schema.json", JSON_SCHEMA);
    assertThat(run(schema.toString(), "-o", outDir().toString(), "--format", "bogus")).isEqualTo(2);
  }

  @Test
  void unknownExtensionCannotAutoDetectReturnsTwo() throws Exception {
    Path file = write("schema.txt", JSON_SCHEMA);
    assertThat(run(file.toString(), "-o", outDir().toString())).isEqualTo(2);
  }

  @Test
  void nonObjectJsonRootPeeksAsOpenApiThenFails() throws Exception {
    // A JSON array root is neither OpenAPI nor JSON Schema → peek defaults to OpenAPI, which then
    // fails (no components.schemas) with exit 2.
    Path file = write("array.json", "[1, 2, 3]");
    assertThat(run(file.toString(), "-o", outDir().toString())).isEqualTo(2);
  }

  @Test
  void malformedJsonPeekFallsBackToOpenApiThenFails() throws Exception {
    // Unparseable content → the peek swallows the IOException and defaults to OpenAPI, which then
    // fails to read it; detection itself never hard-crashes.
    Path file = write("broken.json", "{ not valid json ");
    assertThat(run(file.toString(), "-o", outDir().toString())).isEqualTo(2);
  }

  @Test
  void yamlWithoutMarkersFallsBackToOpenApi() throws Exception {
    // No openapi/swagger key and no $schema/$defs/definitions → content-peek defaults to OpenAPI.
    Path spec =
        write(
            "spec.yaml",
            """
            components:
              schemas:
                Thing:
                  type: object
                  properties:
                    id: {type: string}
            """);
    assertThat(run(spec.toString(), "-o", outDir().toString())).isZero();
    assertThat(outDir().resolve("thing.yaml")).exists();
  }
}
