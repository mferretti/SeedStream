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

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Prefix-stability contract: which unique/serial forms keep their first N rows when --count grows.
 */
class PrefixStabilityTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir Path tempDir;

  private Path structDir;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    Files.createDirectories(structDir);
    Files.writeString(
        structDir.resolve("fixed.yaml"),
        """
        name: fixed
        data:
          s:
            datatype: "serial"
          u:
            datatype: "unique[1..1000]"
          a:
            datatype: "unique[p, 1..20]"
          b:
            datatype: "unique[p, 1..50]"
        """);
    Files.writeString(
        structDir.resolve("counted.yaml"),
        """
        name: counted
        data:
          u:
            datatype: "unique[1..count]"
        """);
    Files.writeString(
        structDir.resolve("reffed.yaml"),
        """
        name: reffed
        data:
          u:
            datatype: "ref[customers.id, 1..count, unique]"
        """);
  }

  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  private List<String> run(String structure, int count) throws IOException {
    Path out = tempDir.resolve(structure + "_" + count);
    Path job = tempDir.resolve(structure + "_" + count + ".yaml");
    Files.writeString(
        job,
        """
        source: %s.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(structure, structDir.toAbsolutePath(), out));
    int code =
        new CommandLine(new ExecuteCommand())
            .execute("--job", job.toString(), "--count", String.valueOf(count));
    assertThat(code).isZero();
    return Files.readAllLines(out.resolve("output.json"));
  }

  private static List<Long> values(List<String> lines) throws IOException {
    return lines.stream().map(l -> read(l)).toList();
  }

  private static long read(String line) {
    try {
      return MAPPER.readTree(line).get("u").asLong();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void shouldKeepFirstHundredLinesIdenticalWhenCountGrowsForFixedRanges() throws IOException {
    List<String> small = run("fixed", 100);
    List<String> big = run("fixed", 200);
    assertThat(small).hasSize(100);
    assertThat(big.subList(0, 100)).isEqualTo(small);
  }

  @Test
  void shouldChangeValuesWhenCountGrowsForCountRange() throws IOException {
    List<String> small = run("counted", 100);
    List<String> big = run("counted", 200);
    assertThat(big.subList(0, 100)).isNotEqualTo(small);
    assertThat(values(small))
        .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 100).boxed().toList());
    assertThat(values(big))
        .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 200).boxed().toList());
  }

  @Test
  void shouldBehaveAsUniqueWhenRefUniqueCountRange() throws IOException {
    assertThat(values(run("reffed", 100)))
        .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 100).boxed().toList());
  }
}
