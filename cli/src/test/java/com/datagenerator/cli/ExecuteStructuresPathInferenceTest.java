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
import com.datagenerator.cli.CliTestSupport.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end check that {@code execute --job jobs/x.yaml} (job path relative to the working
 * directory, no {@code structures_path}) finds the sibling {@code structures/} dir. Java cannot
 * change the working directory, so the layout is created under the real CWD (the module dir under
 * Gradle) with unique file names, and removed afterwards.
 */
class ExecuteStructuresPathInferenceTest {

  @TempDir Path outDir;

  private final List<Path> created = new ArrayList<>();

  @AfterEach
  void cleanUp() throws IOException {
    for (Path p : created.reversed()) {
      Files.deleteIfExists(p); // dirs only vanish if empty, i.e. only ours
    }
  }

  private Path write(Path file, String content) throws IOException {
    Path parent = file.getParent();
    List<Path> toCreate = new ArrayList<>();
    for (Path d = parent; d != null && !Files.exists(d); d = d.getParent()) {
      toCreate.addFirst(d);
    }
    Files.createDirectories(parent);
    created.addAll(toCreate);
    Files.writeString(file, content);
    created.add(file);
    return file;
  }

  @Test
  void shouldFindSiblingStructuresWhenJobPathIsRelativeToCwd() throws IOException {
    Path cwd = Path.of("").toAbsolutePath();
    String id = "wp5_" + UUID.randomUUID().toString().replace('-', '_');
    String structureName = id + "_struct";
    String structure = structureName + ".yaml";
    write(
        cwd.resolve("structures").resolve(structure),
        "name: " + structureName + "\ndata:\n  id:\n    datatype: \"int[1..9]\"\n");
    write(
        cwd.resolve("jobs").resolve(id + ".yaml"),
        """
        source: %s
        type: file
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(structure, outDir.toAbsolutePath()));

    Result r = CliTestSupport.run("execute", "--job", "jobs/" + id + ".yaml", "--count", "3");

    assertThat(r.err()).doesNotContain("data structure file not found");
    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(Files.readAllLines(outDir.resolve("output.json"))).hasSize(3);
    // the resolved structures dir is logged at INFO exactly once
    String resolved = cwd.resolve("structures").toString();
    assertThat(
            r.logs().stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .filter(e -> e.getFormattedMessage().contains(resolved))
                .count())
        .isEqualTo(1);
  }
}
