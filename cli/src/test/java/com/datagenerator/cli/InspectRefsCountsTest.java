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

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #442: {@code inspect} emits the bare {@code ref[t.c]} form and writes a {@code counts.yaml}
 * skeleton next to the structures, so inspect output runs with {@code counts: counts.yaml}.
 */
class InspectRefsCountsTest {

  private static final String COUNTS_FILE = "counts.yaml";
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String SCHEMA =
      """
      CREATE TABLE categories (id INT PRIMARY KEY, name VARCHAR(40));
      CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(40));
      CREATE TABLE orders (
        id INT PRIMARY KEY,
        customer_id INT REFERENCES customers(id),
        category_id INT REFERENCES categories(id)
      );
      """;

  @TempDir Path tempDir;

  private Path outDir() {
    return tempDir.resolve("out");
  }

  private Path sql(String ddl) throws IOException {
    Path file = tempDir.resolve("schema.sql");
    Files.writeString(file, ddl);
    return file;
  }

  private CliTestSupport.Result inspect(String ddl, String... extra) throws IOException {
    List<String> args = new ArrayList<>(List.of("inspect", sql(ddl).toString()));
    args.addAll(List.of("-o", outDir().toString()));
    args.addAll(List.of(extra));
    return CliTestSupport.run(args.toArray(String[]::new));
  }

  private static List<String> entries(Path countsFile) throws IOException {
    return Files.readAllLines(countsFile).stream()
        .map(String::strip)
        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
        .toList();
  }

  // ---------------------------------------------------------------- structures

  @Test
  void shouldWriteBareRefsIntoTheStructureFiles() throws IOException {
    assertThat(inspect(SCHEMA).exit()).isZero();

    String orders = Files.readString(outDir().resolve("orders.yaml"));
    assertThat(orders).contains("ref[customers.id]").contains("ref[categories.id]");
    assertThat(orders).doesNotContain("1..count");
  }

  // ---------------------------------------------------------------- counts.yaml

  @Test
  void shouldWriteCountsSkeletonWithEveryTableAtOneHundredInEmissionOrder() throws IOException {
    CliTestSupport.Result r = inspect(SCHEMA);

    assertThat(r.exit()).isZero();
    Path counts = outDir().resolve(COUNTS_FILE);
    assertThat(counts).exists();
    assertThat(entries(counts)).containsExactly("categories: 100", "customers: 100", "orders: 100");
  }

  @Test
  void shouldWriteHeaderCommentExplainingTheFile() throws IOException {
    inspect(SCHEMA);

    String body = Files.readString(outDir().resolve(COUNTS_FILE));
    assertThat(body).startsWith("#");
    assertThat(body.lines().takeWhile(l -> l.startsWith("#")).toList())
        .anyMatch(l -> l.contains("counts: ../structures/counts.yaml"))
        .anyMatch(l -> l.contains("ref[table.column]"))
        .anyMatch(l -> l.toLowerCase(Locale.ROOT).contains("placeholder"));
  }

  @Test
  void shouldWriteCountsFileTheEngineReaderAccepts() throws IOException {
    inspect(SCHEMA);

    Map<String, Long> counts = ParentRefResolver.readCountsFile(outDir().resolve(COUNTS_FILE));

    assertThat(counts)
        .containsOnly(
            Map.entry("categories", 100L), Map.entry("customers", 100L), Map.entry("orders", 100L));
  }

  @Test
  void shouldMentionCountsFileInTheInspectSummary() throws IOException {
    CliTestSupport.Result r = inspect(SCHEMA);

    assertThat(r.logged(Level.INFO, COUNTS_FILE))
        .as("an INFO line naming %s", COUNTS_FILE)
        .isTrue();
  }

  @Test
  void shouldListOnlyTopLevelStructuresWhenNesting() throws IOException {
    String ddl =
        """
        CREATE TABLE customer (id INT PRIMARY KEY);
        CREATE TABLE shipper (id INT PRIMARY KEY);
        CREATE TABLE invoice (
          id INT PRIMARY KEY,
          customer_id INT REFERENCES customer(id),
          shipper_id INT REFERENCES shipper(id)
        );
        """;

    assertThat(inspect(ddl, "--nest").exit()).isZero();

    // the embedded child is still written (object[child] loads it) but is not a top-level table
    assertThat(outDir().resolve("invoice.yaml")).exists();
    assertThat(entries(outDir().resolve(COUNTS_FILE)))
        .containsExactly("customer: 100", "shipper: 100");
  }

  @Test
  void shouldNotWriteCountsFileWhenNoBareRefWasEmitted() throws IOException {
    assertThat(inspect("CREATE TABLE a (id INT PRIMARY KEY, n INT);").exit()).isZero();
    assertThat(outDir().resolve("a.yaml")).exists();
    assertThat(outDir().resolve(COUNTS_FILE)).doesNotExist();
  }

  @Test
  void shouldNotWriteCountsFileWhenOnlySelfReferencesKeepTheExplicitPool() throws IOException {
    String ddl = "CREATE TABLE emp (id INT PRIMARY KEY, boss INT REFERENCES emp(id));";

    assertThat(inspect(ddl).exit()).isZero();

    assertThat(Files.readString(outDir().resolve("emp.yaml"))).contains("ref[emp.id, 1..count]");
    assertThat(outDir().resolve(COUNTS_FILE)).doesNotExist();
  }

  @Test
  void shouldNotWriteCountsFileForNonDdlInput() throws IOException {
    Path spec = tempDir.resolve("api.yaml");
    Files.writeString(
        spec,
        """
        openapi: 3.0.3
        info: {title: t, version: "1"}
        components:
          schemas:
            Thing:
              type: object
              properties:
                id: {type: string, format: uuid}
        """);

    assertThat(CliTestSupport.run("inspect", spec.toString(), "-o", outDir().toString()).exit())
        .isZero();

    assertThat(outDir().resolve("thing.yaml")).exists();
    assertThat(outDir().resolve(COUNTS_FILE)).doesNotExist();
  }

  @Test
  void shouldSkipExistingCountsFileWithoutForceAndOverwriteItWithForce() throws IOException {
    Files.createDirectories(outDir());
    Path counts = outDir().resolve(COUNTS_FILE);
    String handTuned = "customers: 7\n";
    Files.writeString(counts, handTuned);

    CliTestSupport.Result skipped = inspect(SCHEMA);
    assertThat(skipped.exit()).isZero();
    assertThat(Files.readString(counts)).isEqualTo(handTuned);
    assertThat(skipped.logged(Level.WARN, COUNTS_FILE)).as("a skip notice").isTrue();

    CliTestSupport.Result forced = inspect(SCHEMA, "--force");
    assertThat(forced.exit()).isZero();
    assertThat(entries(counts)).containsExactly("categories: 100", "customers: 100", "orders: 100");
  }

  // ---------------------------------------------------------------- inspect -> execute

  private Path job(String name, String source, Path structures) throws IOException {
    Path jobFile = structures.resolve("job_" + name + ".yaml");
    Files.writeString(
        jobFile,
        String.join(
            "\n",
            "source: " + source + ".yaml",
            "type: file",
            "structures_path: " + structures.toAbsolutePath(),
            "counts: " + COUNTS_FILE,
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + tempDir.resolve("gen/" + name).toAbsolutePath() + "/output",
            ""));
    return jobFile;
  }

  private Set<Long> generatedValues(String name, String source, String field) throws IOException {
    CliTestSupport.Result r =
        CliTestSupport.run(
            "execute",
            "--job",
            job(name, source, outDir()).toString(),
            "--count",
            "100",
            "--threads",
            "1");
    assertThat(r.exit()).as(r.err()).isZero();
    Set<Long> values = new HashSet<>();
    for (String line : Files.readAllLines(tempDir.resolve("gen/" + name + "/output.json"))) {
      values.add(JSON.readTree(line).get(field).asLong());
    }
    return values;
  }

  @Test
  void shouldGenerateChildForeignKeysThatExistInTheGeneratedParent() throws IOException {
    String ddl =
        """
        CREATE TABLE owner (id INT PRIMARY KEY, name VARCHAR(40));
        CREATE TABLE child (id INT PRIMARY KEY, owner_id INT REFERENCES owner(id));
        CREATE TABLE detail (owner_id INT PRIMARY KEY REFERENCES owner(id), note VARCHAR(40));
        """;
    assertThat(inspect(ddl).exit()).isZero();
    assertThat(outDir().resolve(COUNTS_FILE)).exists();
    assertThat(Files.readString(outDir().resolve("child.yaml"))).contains("ref[owner.id]");

    Set<Long> parentIds = generatedValues("owner", "owner", "id");
    Set<Long> childFks = generatedValues("child", "child", "owner_id");
    Set<Long> detailFks = generatedValues("detail", "detail", "owner_id");

    assertThat(parentIds).hasSize(100);
    assertThat(childFks).isNotEmpty().isSubsetOf(parentIds);
    assertThat(detailFks).as("a 1:1 ref is a permutation of the parent keys").isEqualTo(parentIds);
  }
}
