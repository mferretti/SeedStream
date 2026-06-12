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
}
