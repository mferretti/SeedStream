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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End to end: {@code serial[min..max]} output equals {@code serial[min]}; ceiling fails fast. */
class SerialCeilingExecuteTest {
  private static final String OPT_JOB = "--job";
  private static final String OPT_COUNT = "--count";

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);
  }

  private Path writeStructure(String name, String idType) throws IOException {
    Files.writeString(
        structDir.resolve(name + ".yaml"),
        "name: "
            + name
            + "\ndata:\n  id:\n    datatype: \""
            + idType
            + "\"\n"
            + "  x:\n    datatype: \"int[1..1000000]\"\n");
    return job(name, "out_" + name);
  }

  private Path job(String name, String outSub) throws IOException {
    Path jobFile = tempDir.resolve("job_" + outSub + ".yaml");
    Files.writeString(
        jobFile,
        String.join(
            "\n",
            "source: " + name + ".yaml",
            "type: file",
            "structures_path: " + structDir.toAbsolutePath(),
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + outDir.toAbsolutePath() + "/" + outSub + "/output",
            ""));
    return jobFile;
  }

  private List<String> output(String name, Path jobFile, int count, int threads)
      throws IOException {
    CliTestSupport.Result r =
        CliTestSupport.run(
            "execute",
            OPT_JOB,
            jobFile.toString(),
            OPT_COUNT,
            String.valueOf(count),
            "--threads",
            String.valueOf(threads));
    assertThat(r.exit()).as(r.err()).isZero();
    return Files.readAllLines(outDir.resolve("out_" + name + "/output.json"));
  }

  private void assertIdentical(String ceiling, String plain, int count) throws IOException {
    Path jobPlain = writeStructure("plain", plain);
    List<String> reference = output("plain", jobPlain, count, 1);
    assertThat(reference).hasSize(count);
    Path jobCeil = writeStructure("ceil", ceiling);
    for (int threads : new int[] {1, 4, 8}) {
      assertThat(output("ceil", jobCeil, count, threads))
          .as("%s with %d threads vs %s", ceiling, threads, plain)
          .isEqualTo(reference);
    }
  }

  @Test
  void shouldMatchPlainSerialWhenCeilingStartsAtOne() throws Exception {
    assertIdentical("serial[1..10]", "serial", 10);
  }

  @Test
  void shouldMatchSerialMinWhenCeilingHasCustomMin() throws Exception {
    assertIdentical("serial[100..200]", "serial[100]", 101);
  }

  @Test
  void shouldMatchSerialMinWhenCountIsBelowCeiling() throws Exception {
    assertIdentical("serial[-10..10]", "serial[-10]", 7);
  }

  @Test
  void shouldAcceptHugeRangeWhenCountIsSmall() throws Exception {
    assertIdentical("serial[0..9223372036854775807]", "serial[0]", 25);
  }

  @Test
  void shouldRejectBeforeWritingAnythingWhenCountExceedsCeiling() throws Exception {
    Path jobFile = writeStructure("ceil", "serial[1..10]");

    CliTestSupport.Result r =
        CliTestSupport.run("execute", OPT_JOB, jobFile.toString(), OPT_COUNT, "11");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("field 'id': serial[1..10] holds 10 values but --count is 11");
    assertThat(outDir.resolve("out_ceil/output.json")).doesNotExist();
  }

  @Test
  void shouldRejectBeforeWritingAnythingWhenCeilingIsUnderArray() throws Exception {
    Path jobFile = writeStructure("ceil", "array[serial[1..50], 1..3]");

    CliTestSupport.Result r =
        CliTestSupport.run("execute", OPT_JOB, jobFile.toString(), OPT_COUNT, "2");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("serial field 'ceil.id' is inside array[...]");
    assertThat(outDir.resolve("out_ceil/output.json")).doesNotExist();
  }

  @Test
  void guardShouldKeepPlainSerialUnboundedAtStartup() throws Exception {
    Path jobFile = writeStructure("plain", "serial[100]");
    assertThat(output("plain", jobFile, 50, 2)).hasSize(50);
  }
}
